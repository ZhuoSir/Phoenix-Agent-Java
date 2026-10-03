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
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessChatServiceImpl implements HarnessChatService {
    private final HarnessStaticLoader harnessStaticLoader;
    private final HarnessAgentRegistry harnessAgentRegistry;
    private final HitlCacheService hitlCacheService;
    private final SkillExplicitInjectionService skillExplicitInjectionService;
    private final WorkspaceArtifactScanner workspaceArtifactScanner;
    private final com.phoenix.agent.service.AgentRuntimeConfigService agentRuntimeConfigService;

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
        final Integer effMaxIters = resolveEffectiveMaxIters(request.getAgentId());
        Flux<NodeOutput> body = harnessAgent
            .streamEvents(buildUserMessage(request), buildRuntimeContext(request, injection.block()))
            .map(event -> toNodeOutput(event, sessionId, textDeltaSeen, effMaxIters));
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

    /** 生效上限：配置值或 null（=框架默认，文案省略数字）。DB 轻查询，每轮一次。 */
    private Integer resolveEffectiveMaxIters(Long agentId) {
        if (agentId == null) {
            return null;
        }
        try {
            return agentRuntimeConfigService.resolve(agentId).getMaxIterations();
        }
        catch (Exception e) {
            log.warn("读取迭代上限配置失败（忽略，按默认文案）: {}", e.toString());
            return null;
        }
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
        // BUG-59 根治步骤：批准时显式退出计划模式（框架公开 API）。此前缺失导致模型续跑时
        // 仍认为自己处于 plan 阶段——只输出"请退出计划模式"而不执行工具（用户实测 2/3 无正文的真身）。
        RuntimeContext planCtx = RuntimeContext.builder().userId(request.getUserId())
                .sessionId(request.getSessionId()).build();
        if (request.isAllowed()) {
            // BUG-59 二发：isPlanModeActive(ctx) 会误报 false（会话 fcca0457 实测：笼还在、查询说不在，
            // 模型"Let's go"后零工具零正文）——改为无条件 exitPlanMode（已退出时为无害 no-op）
            boolean wasActive = false;
            try {
                wasActive = harnessAgent.isPlanModeActive(planCtx);
            }
            catch (Exception ignored) {
            }
            try {
                harnessAgent.exitPlanMode(planCtx);
                log.info("[hitl] 计划已批准，已退出计划模式 session={} (查询态={})", request.getSessionId(), wasActive);
            }
            catch (Exception e) {
                log.warn("[hitl] exitPlanMode 失败（继续续跑）session={}: {}", request.getSessionId(), e.toString());
            }
        }
        // BUG-59 缓解：框架 resume 时 maybePatchPendingToolCalls 先于 applyConfirmResults 执行，
        // plan 模式的 plan_exit 块无 ASKING 豁免会被自动置错——确认元数据可能迟到失效。
        // 文案改为强指令，保证模型即便看到 plan_exit 报错也继续执行并产出正文。
        String confirmText = request.isAllowed()
                ? "用户已批准该计划，系统已退出计划模式，你现在处于执行阶段。请立即直接调用工具执行计划中的剩余步骤，"
                        + "全部完成后必须用正文输出执行总结（即使只有一句话也必须输出，禁止只调用工具不说话就结束）。"
                        + "忽略此前计划提交工具的报错（系统已代为处理），"
                        + "不要再请求确认或等待任何批准，不要只在思考中输出而不产出正文。"
                : "用户拒绝了本次操作。请停止该操作，并用正文简要询问用户的替代意图。";
        UserMessage confirmMsg = UserMessage.builder()
                .name("system")
                .textContent(confirmText)
                .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(result)))
                .build();
        java.util.concurrent.atomic.AtomicBoolean textDeltaSeen = new java.util.concurrent.atomic.AtomicBoolean(false);
        return harnessAgent.streamEvents(confirmMsg, RuntimeContext.builder().userId(request.getUserId()).sessionId(request.getSessionId()).build())
                .map(event -> toNodeOutput(event, request.getSessionId(), textDeltaSeen,
                    resolveEffectiveMaxIters(null)));
    }

    private NodeOutput toNodeOutput(AgentEvent event, String sessionId,
            java.util.concurrent.atomic.AtomicBoolean textDeltaSeen, Integer effMaxIters) {
        // BUG-46：超限终止必须可见（此前被 mapper 吞掉，表现为"执行一半戛然而止"）
        if (event instanceof io.agentscope.core.event.ExceedMaxItersEvent) {
            // runtime-max-iterations R-04：带上生效值；未配置省略数字（A-04，不猜框架默认）
            String warn = "\n\n⚠️ 已达到最大迭代次数" + (effMaxIters != null ? "（" + effMaxIters + "）" : "")
                    + "，本轮任务被中断（工具调用次数超限）。可在「运行时配置」调高「工具迭代上限」或重试、拆分任务。";
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
        // 工具/模型生命周期事件：不上屏；ModelCall 起止留 INFO（BUG-55 静默根因追踪）
        String simple = event.getClass().getSimpleName();
        if (simple.startsWith("ModelCall")) {
            log.info("[model-call] {} sessionId={} @{}", simple, sessionId, System.currentTimeMillis());
        }
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
            // thinking-display R-01：思考增量不再伪装成正文 chunk——经 thinking_text 状态键走
            // 独立通道，由 mapper 透出 eventMap.thinking；content 从此只含回答正文
            Map<String, Object> thinkingData = new HashMap<>();
            thinkingData.put("thinking_text", thinkingEvent.getDelta());
            return NodeOutput.of("harness_agent", "harness", new OverAllState(thinkingData), null);
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
