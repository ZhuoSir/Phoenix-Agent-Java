package com.phoenix.agent.harness.send.impl;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.phoenix.agent.harness.agent.HarnessStaticLoader;
import com.phoenix.agent.harness.factory.HarnessAgentRegistry;
import com.phoenix.agent.harness.request.ConfirmRequest;
import com.phoenix.agent.harness.request.HarnessRequest;
import com.phoenix.agent.harness.send.HarnessChatService;
import com.phoenix.agent.harness.service.HitlCacheService;
import com.phoenix.agent.harness.skill.ExplicitSkillMiddleware;
import com.phoenix.agent.harness.skill.SkillExplicitInjectionService;
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
        Flux<NodeOutput> body = harnessAgent
            .streamEvents(buildUserMessage(request), buildRuntimeContext(request, injection.block()))
            .map(event -> toNodeOutput(event, sessionId));
        if (injection.skillNames().isEmpty()) {
            return body;
        }
        // 让前端可见本轮实际加载了哪些技能（R-05）
        Map<String, Object> leadData = new HashMap<>();
        leadData.put("loaded_skills", String.join(",", injection.skillNames()));
        return Flux.concat(Flux.just(NodeOutput.of("harness_agent", "harness", new OverAllState(leadData), null)), body);
    }

    /** 拒绝原因用内容增量事件承载，前端 content 直接可见 */
    private NodeOutput errorOutput(String msg) {
        Map<String, Object> data = new HashMap<>();
        data.put("error_message", msg);
        return new StreamingOutput<>(msg, "harness_agent", "harness", new OverAllState(data));
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
        return harnessAgent.streamEvents(confirmMsg, RuntimeContext.builder().userId(request.getUserId()).sessionId(request.getSessionId()).build())
                .map(event -> toNodeOutput(event, request.getSessionId()));
    }

    private NodeOutput toNodeOutput(AgentEvent event, String sessionId) {
        Map<String, Object> data = new HashMap<>();
        data.put("agent_event", event);
        // 1. 确认事件优先处理（可能同时是 AGENT_END，必须放在前面）
        if (event instanceof RequireUserConfirmEvent confirmEvent) {
            hitlCacheService.savePendingConfirm(sessionId, confirmEvent);
            return NodeOutput.of("harness_agent", "harness", new OverAllState(data), null);
        }
        // 2. 文本增量事件
        if (event.getType() == AgentEventType.TEXT_BLOCK_DELTA && event instanceof TextBlockDeltaEvent textEvent) {
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
