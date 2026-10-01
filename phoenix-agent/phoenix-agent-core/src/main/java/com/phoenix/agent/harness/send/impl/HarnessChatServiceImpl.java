package com.phoenix.agent.harness.send.impl;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.phoenix.agent.harness.agent.HarnessStaticLoader;
import com.phoenix.agent.harness.factory.HarnessAgentRegistry;
import com.phoenix.data.exception.InvalidInputException;
import com.phoenix.agent.harness.request.ConfirmRequest;
import com.phoenix.agent.harness.request.HarnessRequest;
import com.phoenix.agent.harness.send.HarnessChatService;
import com.phoenix.agent.harness.service.HitlCacheService;
import com.phoenix.agent.harness.skill.ExplicitSkillMiddleware;
import com.phoenix.agent.harness.skill.SkillExplicitInjectionService;
import com.phoenix.agent.model.AgentFile;
import com.phoenix.agent.service.file.WorkspaceArtifactScanner;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentEventType;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.UserMessage;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class HarnessChatServiceImpl implements HarnessChatService {
    private final HarnessStaticLoader harnessStaticLoader;
    private final HarnessAgentRegistry harnessAgentRegistry;
    private final HitlCacheService hitlCacheService;
    private final SkillExplicitInjectionService skillExplicitInjectionService;
    private final WorkspaceArtifactScanner workspaceArtifactScanner;

    @Override
    public Mono<Msg> call(String sn, HarnessRequest request) {
        HarnessAgent harnessAgent = getHarnessAgent(sn);
        SkillExplicitInjectionService.InjectionResult injection = skillExplicitInjectionService
            .prepare(sn, request.getEnabledSkillIds());
        if (!injection.ok()) {
            return Mono.error(new IllegalArgumentException(injection.errorMsg()));
        }
        return harnessAgent.call(buildUserMessage(request), buildRuntimeContext(request, injection.block()));
    }

    @Override
    public Mono<Msg> call(HarnessRequest request) {
        HarnessAgent harnessAgent = resolveAgent(request);
        SkillExplicitInjectionService.InjectionResult injection = resolveInjection(request);
        if (!injection.ok()) {
            return Mono.error(new IllegalArgumentException(injection.errorMsg()));
        }
        return harnessAgent.call(buildUserMessage(request), buildRuntimeContext(request, injection.block()));
    }

    @Override
    public Flux<NodeOutput> stream(HarnessRequest request) {
        return doStream(resolveAgent(request), request, resolveInjection(request));
    }

    @Override
    public Flux<NodeOutput> stream(String sn, HarnessRequest request) {
        HarnessAgent harnessAgent = getHarnessAgent(sn);
        // R-05：请求入口做三重校验；拒绝时以内容增量事件返回原因并结束流
        SkillExplicitInjectionService.InjectionResult injection = skillExplicitInjectionService
            .prepare(sn, request.getEnabledSkillIds());
        return doStream(harnessAgent, request, injection);
    }

    /**
     * R-08 寻址：agentId（库配置路径，走运行时注册表，存量自注册智能体在注册表内自动回退其 Java 实例）
     * 优先；未传 agentId 时按 harnessSn 走存量静态加载器。
     */
    private HarnessAgent resolveAgent(HarnessRequest request) {
        if (request.getAgentId() != null) {
            return harnessAgentRegistry.get(request.getAgentId());
        }
        if (!org.springframework.util.StringUtils.hasText(request.getHarnessSn())) {
            // 显式入参校验：两者皆缺时给明确错误（原先落到 loadAgent(null) → 500）
            throw new InvalidInputException("agentId 与 harnessSn 至少需要一个");
        }
        return harnessStaticLoader.loadAgent(request.getHarnessSn());
    }

    /** 显式技能校验的技能池同样按寻址路径解析（agentId 路径用 agentId，存量路径用 sn） */
    private SkillExplicitInjectionService.InjectionResult resolveInjection(HarnessRequest request) {
        if (request.getAgentId() != null) {
            return skillExplicitInjectionService.prepareByAgentId(request.getAgentId(),
                request.getEnabledSkillIds());
        }
        return skillExplicitInjectionService.prepare(request.getHarnessSn(), request.getEnabledSkillIds());
    }

    private Flux<NodeOutput> doStream(HarnessAgent harnessAgent, HarnessRequest request,
            SkillExplicitInjectionService.InjectionResult injection) {
        String sessionId = request.getSessionId();
        if (!injection.ok()) {
            return Flux.just(errorOutput(injection.errorMsg()), endOutput());
        }
        // 技能全文经 RuntimeContext 交给 ExplicitSkillMiddleware 注入系统提示（plan v1.1.0 决策4）
        java.util.concurrent.atomic.AtomicBoolean textDeltaSeen = new java.util.concurrent.atomic.AtomicBoolean(false);
        Flux<NodeOutput> body = harnessAgent
            .streamEvents(buildUserMessage(request), buildRuntimeContext(request, injection.block()))
            .map(event -> toNodeOutput(event, sessionId, textDeltaSeen));
        // BL-19：轮末扫 workspace 产物。END 帧从 body 中剥离、在扫描事件之后统一补发——
        // 否则 agentFiles 落在 end=true 之后，前端（按 end 收尾）收不到，还会多渲染一个空消息框。
        java.time.Instant turnStart = java.time.Instant.now();
        Flux<NodeOutput> core = body.filter(output -> !output.isEND());
        Flux<NodeOutput> filesTail = Flux.defer(() -> artifactsTail(request, turnStart));
        Flux<NodeOutput> endFrame = Flux.defer(() -> Flux.just(endOutputStatic()));
        if (injection.skillNames().isEmpty()) {
            return Flux.concat(core, filesTail, endFrame);
        }
        // 让前端可见本轮实际加载了哪些技能（R-05）
        Map<String, Object> leadData = new HashMap<>();
        leadData.put("loaded_skills", String.join(",", injection.skillNames()));
        return Flux.concat(Flux.just(NodeOutput.of("harness_agent", "harness", new OverAllState(leadData), null)), core,
                filesTail, endFrame);
    }

    private Flux<NodeOutput> artifactsTail(HarnessRequest request, java.time.Instant turnStart) {
        // 有界轮询：文件写在轮末紧贴发生（settle 1s 门槛），最多 3 轮、每轮间隔 1.2s，直到某轮无新增
        List<AgentFile> files = new java.util.ArrayList<>();
        for (int round = 0; round < 3; round++) {
            List<AgentFile> added = workspaceArtifactScanner.scanAndRegister(
                    request.getAgentId(), request.getHarnessSn(), request.getUserId(), request.getSessionId(),
                    turnStart);
            files.addAll(added);
            if (added.isEmpty()) {
                break;
            }
            try {
                Thread.sleep(1200);
            }
            catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (files.isEmpty()) {
            return Flux.empty();
        }
        List<Map<String, Object>> payload = files.stream()
                .map(f -> {
                    Map<String, Object> m = new HashMap<String, Object>();
                    m.put("id", f.getId());
                    m.put("fileName", f.getFileName());
                    m.put("sizeBytes", f.getSizeBytes());
                    m.put("mime", f.getMime());
                    m.put("source", f.getSource());
                    return m;
                })
                .toList();
        Map<String, Object> data = new HashMap<>();
        data.put("agent_files", com.phoenix.data.util.JsonUtil.getObjectMapper().writeValueAsString(payload));
        return Flux.just(NodeOutput.of("harness_agent", "harness", new OverAllState(data), null));
    }

    /** 拒绝原因用内容增量事件承载，前端 content 直接可见 */
    private NodeOutput errorOutput(String msg) {
        Map<String, Object> data = new HashMap<>();
        data.put("error_message", msg);
        return new StreamingOutput<>(msg, "harness_agent", "harness", new OverAllState(data));
    }

    private static NodeOutput endOutputStatic() {
        return NodeOutput.of(StateGraph.END, "harness", new OverAllState(new HashMap<>()), null);
    }

    private NodeOutput endOutput() {
        return NodeOutput.of(StateGraph.END, "harness", new OverAllState(new HashMap<>()), null);
    }

    @Override
    public HarnessAgent getHarnessAgent(String sn) {
        return harnessStaticLoader.loadAgent(sn);
    }

    @Override
    public HarnessAgent getHarnessAgent(Long agentId) {
        return harnessAgentRegistry.get(agentId);
    }

    @Override
    public Flux<NodeOutput> confirmStream(ConfirmRequest request) {
        if (request.getAgentId() != null) {
            return confirmStream(harnessAgentRegistry.get(request.getAgentId()), request);
        }
        if (!org.springframework.util.StringUtils.hasText(request.getAgentSn())) {
            throw new InvalidInputException("agentId 与 agentSn 至少需要一个");
        }
        return confirmStream(harnessStaticLoader.loadAgent(request.getAgentSn()), request);
    }

    @Override
    public Flux<NodeOutput> confirmStream(String sn, ConfirmRequest request) {
        return confirmStream(getHarnessAgent(sn), request);
    }

    private Flux<NodeOutput> confirmStream(HarnessAgent harnessAgent, ConfirmRequest request) {
        RequireUserConfirmEvent pendingConfirm = hitlCacheService.getAndRemovePendingConfirm(request.getSessionId());
        if (pendingConfirm == null) {
            Map<String, Object> context = new HashMap<>();
            context.put("confirm", "确认已过期或未找到任务");
            CustomEvent agentEvent = new CustomEvent("confirm", context);
            Map<String, Object> data = new HashMap<>();
            data.put("agent_event", agentEvent);
            return Flux.just(NodeOutput.of("harness_agent", "harness", new OverAllState(data), null));
        }
        ConfirmResult result = new ConfirmResult(
                request.isAllowed(),
                pendingConfirm.getToolCalls().get(0),
                request.isAllowed() ? request.getSuggestedRules() : null
        );
        UserMessage confirmMsg = UserMessage.builder()
                .name("system")
                .textContent("User confirmed, continue.")
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(result)))
                .build();
        java.util.concurrent.atomic.AtomicBoolean textDeltaSeen = new java.util.concurrent.atomic.AtomicBoolean(false);
        return harnessAgent.streamEvents(confirmMsg, RuntimeContext.builder().userId(request.getUserId()).sessionId(request.getSessionId()).build())
                .map(event -> toNodeOutput(event, request.getSessionId(), textDeltaSeen));
    }

    private NodeOutput toNodeOutput(AgentEvent event, String sessionId) {
        return toNodeOutput(event, sessionId, new java.util.concurrent.atomic.AtomicBoolean(true));
    }

    private NodeOutput toNodeOutput(AgentEvent event, String sessionId,
            java.util.concurrent.atomic.AtomicBoolean textDeltaSeen) {
        // BUG-46：超限终止必须可见（此前被 mapper 吞掉，表现为"执行一半戛然而止"）
        if (event instanceof io.agentscope.core.event.ExceedMaxItersEvent) {
            String warn = "\n\n⚠️ 已达到最大迭代次数，本轮任务被中断（工具调用次数超限）。可重试，或将任务拆成更小步骤。";
            Map<String, Object> wd = new HashMap<>();
            return new StreamingOutput<>(warn, "harness_agent", "harness", new OverAllState(wd));
        }
        // BUG-46：结果事件兜底——本轮没有任何流式增量时，用最终 Msg 文本补发内容
        if (event instanceof io.agentscope.core.event.AgentResultEvent resultEvent) {
            if (!textDeltaSeen.get() && resultEvent.getResult() != null) {
                String text = resultEvent.getResult().getContentBlocks(io.agentscope.core.message.TextBlock.class)
                        .stream().map(io.agentscope.core.message.TextBlock::getText)
                        .filter(t -> t != null && !t.isEmpty()).reduce((a, b) -> a + "\n" + b).orElse("");
                if (!text.isBlank()) {
                    Map<String, Object> rd = new HashMap<>();
                    return new StreamingOutput<>(text, "harness_agent", "harness", new OverAllState(rd));
                }
            }
            return NodeOutput.of("harness_agent", "harness", new OverAllState(new HashMap<>()), null);
        }
        // 工具/模型生命周期事件：不上屏、不打 WARN（降噪）
        String simple = event.getClass().getSimpleName();
        if (simple.startsWith("ToolResult") || simple.startsWith("ToolCall") || simple.startsWith("ModelCall")) {
            return NodeOutput.of("harness_agent", "harness", new OverAllState(new HashMap<>()), null);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("agent_event", event);
        // 1. 确认事件优先处理（可能同时是 AGENT_END，必须放在前面）
        if (event instanceof RequireUserConfirmEvent confirmEvent) {
            hitlCacheService.savePendingConfirm(sessionId, confirmEvent);
            return NodeOutput.of("harness_agent", "harness", new OverAllState(data), null);
        }
        // 2. 文本增量事件
        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA && event instanceof TextBlockDeltaEvent textEvent) {
            textDeltaSeen.set(true);
            return new StreamingOutput<>(textEvent.getDelta(), "harness_agent", "harness", new OverAllState(data));
        }
        // 2b. 思考内容增量事件（DeepSeek-R1 等深度思考模型）
        if (event.getType() == AgentEventType.THINKING_BLOCK_DELTA && event instanceof ThinkingBlockDeltaEvent thinkingEvent) {
            return new StreamingOutput<>(thinkingEvent.getDelta(), "harness_agent", "harness", new OverAllState(data));
        }
        // 3. 结束事件
        if (event.getType() == AgentEventType.AGENT_END) {
            return NodeOutput.of(StateGraph.END, "harness", new OverAllState(data), null);
        }
        // 4. 其他事件
        return NodeOutput.of("harness_agent", "harness", new OverAllState(data), null);
    }

    private RuntimeContext buildRuntimeContext(HarnessRequest request, String activeSkillBlock) {
        RuntimeContext.Builder builder = RuntimeContext.builder()
            .sessionId(request.getSessionId())
            .userId(request.getUserId());
        if (activeSkillBlock != null && !activeSkillBlock.isBlank()) {
            builder.put(ExplicitSkillMiddleware.CTX_ACTIVE_SKILL_BLOCK, activeSkillBlock);
        }
        return builder.build();
    }

    private UserMessage buildUserMessage(HarnessRequest request) {
        if (request.getSkillScopeHint() == null || request.getSkillScopeHint().isBlank()) {
            return new UserMessage(request.getMessage());
        }
        // 前台通道技能范围约束（R-09 风险①缓解）；与用户原文拼接在末尾，避免干扰指令解析
        return new UserMessage(request.getMessage() + "\n\n[平台约束] " + request.getSkillScopeHint());
    }
}
