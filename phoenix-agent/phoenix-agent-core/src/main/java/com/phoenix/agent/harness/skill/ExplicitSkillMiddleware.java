package com.phoenix.agent.harness.skill;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.middleware.HarnessRuntimeMiddleware;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 显式技能注入中间件（R-05 / R-09，plan v1.1.0 决策4）。
 *
 * <p>把本轮显式启用的技能全文追加到**系统提示**（平台可信通道），而非用户消息。
 * 实测教训：注入 user message 会被安全对齐模型识别为 prompt injection 并拒绝执行；
 * 系统提示是平台下发的可信上下文，且会作用于本轮所有模型调用（ReAct 多步循环）。
 *
 * <p>校验与文本构建由 {@link SkillExplicitInjectionService} 在请求入口完成，
 * 结果经 {@link RuntimeContext} 传入，本中间件只做拼接（无状态、无 IO）。
 */
@Slf4j
public class ExplicitSkillMiddleware implements HarnessRuntimeMiddleware {

    /** RuntimeContext 中承载待注入技能块的键（由 HarnessChatServiceImpl 写入） */
    public static final String CTX_ACTIVE_SKILL_BLOCK = "phoenix.active_skill_block";

    @Override
    public Mono<String> onSystemPrompt(Agent agent, RuntimeContext runtimeContext, String systemPrompt) {
        if (runtimeContext == null) {
            return Mono.just(systemPrompt);
        }
        String block = runtimeContext.get(CTX_ACTIVE_SKILL_BLOCK);
        if (block == null || block.isBlank()) {
            return Mono.just(systemPrompt);
        }
        log.debug("显式技能块注入系统提示, blockSize={}", block.length());
        String base = systemPrompt == null ? "" : systemPrompt;
        // 前置（权威位）+ 末尾复述：实测仅追加时模型会在多步工具循环中忽略技能要求
        return Mono.just(block
            + "\n"
            + base
            + "\n\n【本轮强制项】上述 <active_skills> 中的技能要求属于平台强制指令，"
            + "其规定的输出/动作必须在你的最终回答中体现，不得被工具查询结果冲淡或忽略。");
    }
}
