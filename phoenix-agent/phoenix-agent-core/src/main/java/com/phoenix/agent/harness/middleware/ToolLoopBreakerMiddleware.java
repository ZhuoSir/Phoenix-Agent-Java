package com.phoenix.agent.harness.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.RequestStopEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * BUG-188：工具调用死循环熔断。
 *
 * <p>背景（用户实测）：本地小模型（qwen2.5:7b）在工具**连续空结果**时不具备停止自省能力 ——
 * 实测两次分别为 `memory_search` 连续 44 次、`execute` 连续 60 次，直至耗尽 `maxIterations`，
 * 助手输出停在"我需要先搜索一下…"，用户既拿不到答案也白烧算力。
 *
 * <p>做法：在 {@code onActing} 钩子上比对**本次请求的工具签名**（工具名 + 参数）。
 * 同一签名**连续**出现达到阈值（默认 3 次）即判定死循环 ⇒ 不再执行，直接以
 * {@link RequestStopEvent} 结束本轮，并给出可读原因（用户可据此补充信息或换用更强模型）。
 * 参数变化即视为"有新进展"，计数清零，避免误伤正常的多轮检索。
 *
 * <p>局限（如实记录）：框架未提供"注入消息让模型收尾作答"的钩子（也无工具许可回调），
 * 故本实现选择"停轮 + 明确原因"，而非"熔断后强制作答"；后者需框架侧支持，已在 BUG-188 备注。
 */
@Slf4j
public class ToolLoopBreakerMiddleware implements MiddlewareBase {

	/** 允许的连续重复次数（达到即熔断） */
	private final int repeatThreshold;

	/** 会话/智能体 → (工具签名 → 连续次数)。有界清理：仅保留最近 512 个键，防内存膨胀 */
	private final Map<String, Map<String, Integer>> counters = new ConcurrentHashMap<>();

	private static final int MAX_KEYS = 512;

	public ToolLoopBreakerMiddleware(int repeatThreshold) {
		this.repeatThreshold = Math.max(2, repeatThreshold);
	}

	@Override
	public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
			Function<ActingInput, Flux<AgentEvent>> next) {
		List<ToolUseBlock> calls = input == null ? List.of() : input.toolCalls();
		if (calls == null || calls.isEmpty()) {
			return next.apply(input);
		}
		String agentKey = agent == null ? "unknown" : String.valueOf(agent.getName());
		String signature = signature(calls);
		int count = bump(agentKey, signature);
		if (count >= repeatThreshold) {
			String reason = "重复工具调用熔断：同一工具（" + signature + "）已连续调用 " + count
					+ " 次且无参数变化，已停止本轮以避免死循环。请补充更具体的信息，或改用支持工具编排的更强模型（如 qwen3:8b / 云端模型）。";
			log.warn("[tool-breaker] agent={}, 连续重复 {}/{}，熔断停轮: {}", agentKey, count, repeatThreshold, signature);
			// 计数清零：避免下一次请求立刻又被熔断
			reset(agentKey);
			return Flux.just(new RequestStopEvent(reason, GenerateReason.INTERRUPTED));
		}
		log.debug("[tool-breaker] agent={}, 连续重复 {}/{}: {}", agentKey, count, repeatThreshold, signature);
		return next.apply(input);
	}

	/** 本次请求的签名（全部工具调用按序拼接：工具名 + 参数 JSON + 内容） */
	private String signature(List<ToolUseBlock> calls) {
		StringBuilder sb = new StringBuilder();
		for (ToolUseBlock call : calls) {
			sb.append(call.getName()).append('(').append(call.getInput()).append(");");
		}
		return sb.toString();
	}

	private int bump(String agentKey, String signature) {
		Map<String, Integer> map = counters.computeIfAbsent(agentKey, k -> new ConcurrentHashMap<>());
		if (map.size() > MAX_KEYS) {
			map.clear();
		}
		// 参数变化 ⇒ 视为新进展：整体清零后重新计数
		if (map.size() == 1 && !map.containsKey(signature)) {
			map.clear();
		}
		return map.merge(signature, 1, Integer::sum);
	}

	private void reset(String agentKey) {
		Map<String, Integer> map = counters.get(agentKey);
		if (map != null) {
			map.clear();
		}
	}

}
