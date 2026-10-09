package com.phoenix.agent.harness.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * BUG-163（BUG-77 复发的根因修复）：**每次模型调用前**补全缺失的 reasoning 块。
 *
 * <p>背景：DeepSeek 思考模式硬性要求——带 {@code tool_calls} 的助手消息必须回传
 * {@code reasoning_content}，否则请求 400
 * （{@code The reasoning_content in the thinking mode must be passed back to the API}）。
 *
 * <p>为何轮前修复不够：既有 {@code HarnessStateRepairService} 只在**轮次开始前**修
 * {@code tbl_harness_store_state}，而**轮中**由框架 {@code CompactionMiddleware} 发起的压缩调用
 * 同样受该约束（实测压缩即 400，该轮空正文/中断）。本中间件挂在 {@code onReasoning} 钩子上，
 * 对**每一次**发往模型的消息列表做同样补全 —— 正常调用、工具续轮、压缩调用一并覆盖。
 *
 * <p>与轮前修复的关系：互补。轮前修复改的是**持久化状态**（后续所有轮次受益）；
 * 本件改的是**本次调用的入参**（当轮即时生效，且不依赖状态表实际落在哪一层存储）。
 */
@Slf4j
public class ReasoningRepairMiddleware implements MiddlewareBase {

    /** reasoning 占位文案（必须非空，provider 要求该字段有值）；与 HarnessStateRepairService 保持一致 */
    private static final String PLACEHOLDER = "[reasoning unavailable: turn was interrupted]";

    @Override
    public Flux<AgentEvent> onReasoning(Agent agent, RuntimeContext ctx, ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        ReasoningInput patched = patchIfNeeded(input);
        return next.apply(patched == null ? input : patched);
    }

    /**
     * 扫描消息列表，为「含 ToolUseBlock 但无 ThinkingBlock」的助手消息补占位 thinking 块。
     *
     * @return 需要修复时返回替换后的入参；无需修复返回 null（调用方沿用原入参，零开销）
     */
    private ReasoningInput patchIfNeeded(ReasoningInput input) {
        if (input == null) {
            return null;
        }
        List<Msg> messages = input.messages();
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        List<Msg> patched = null;
        int fixed = 0;
        for (int i = 0; i < messages.size(); i++) {
            Msg msg = messages.get(i);
            if (msg == null || msg.getRole() != MsgRole.ASSISTANT) {
                continue;
            }
            if (!msg.hasContentBlocks(ToolUseBlock.class) || msg.hasContentBlocks(ThinkingBlock.class)) {
                continue;
            }
            List<ContentBlock> content = new ArrayList<>();
            content.add(ThinkingBlock.builder().thinking(PLACEHOLDER).build());
            if (msg.getContent() != null) {
                content.addAll(msg.getContent());
            }
            Msg repaired = Msg.builder()
                .id(msg.getId())
                .name(msg.getName())
                .role(msg.getRole())
                .content(content)
                .metadata(msg.getMetadata())
                .timestamp(msg.getTimestamp())
                .usage(msg.getUsage())
                .build();
            if (patched == null) {
                patched = new ArrayList<>(messages);
            }
            patched.set(i, repaired);
            fixed++;
        }
        if (patched == null) {
            return null;
        }
        log.warn("[reasoning-repair] 模型调用前补全 {} 条缺失 reasoning 的 tool_use 助手消息（BUG-163）", fixed);
        return new ReasoningInput(patched, input.tools(), input.options());
    }

}
