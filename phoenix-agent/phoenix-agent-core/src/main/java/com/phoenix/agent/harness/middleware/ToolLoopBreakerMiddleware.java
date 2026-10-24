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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * BUG-188 引入、**BUG-202 修正**：工具调用死循环熔断。
 *
 * <p>背景（用户实测，BUG-188）：本地小模型在工具连续空结果时不具备停止自省能力 ——
 * 实测 `memory_search` 连续 44 次、`execute` 连续 60 次，直至耗尽 maxIterations，
 * 助手输出停在"我需要先搜索一下…"，用户既拿不到答案也白烧算力。
 *
 * <p>做法：在 {@code onActing} 钩子上比对**本次请求的工具签名**（工具名 + 参数）。
 * 同一签名**连续**出现达到阈值即判定死循环 ⇒ 不再执行，直接以
 * {@link RequestStopEvent} 结束本轮，并给出可读原因。
 *
 * <p><b>BUG-202 修正两点</b>（2026-10-24，spec chat-runtime-hardening T-13/T-14）：
 * <ol>
 *   <li><b>计数语义回归"连续"</b>：只保留**最后一个签名**及其连续次数，签名一变即清零。
 *       原实现在 {@code map.size() == 1} 时才清零，一旦表里出现第二个签名就**永久累加**；
 *       且计数表与实例（=会话）同寿命、跨轮不重置 ⇒ 正常的多轮检索也会攒到阈值 ⇒ 误熔断并停轮。</li>
 *   <li><b>日志只打摘要</b>：签名做 sha256 取前 8 位，附截断后的签名预览，避免超长参数刷爆日志；
 *       现场核对"到底是不是同一条调用"看 digest 即可。</li>
 * </ol>
 *
 * <p>另：计数键补上 {@link RuntimeContext#getSessionId()}（实例若被多个上下文复用时可隔离；
 * 当前 {@code HarnessAgentRegistry} 按 {@code agentId@sessionId} 缓存实例，实例级已是会话级）。
 *
 * <p>局限（如实记录）：框架未提供"注入消息让模型收尾作答"的钩子（也无工具许可回调），
 * 故本实现选择"停轮 + 明确原因"，而非"熔断后强制作答"；后者需框架侧支持，已在 BUG-188 备注。
 */
@Slf4j
public class ToolLoopBreakerMiddleware implements MiddlewareBase {

	/** 允许的连续重复次数（达到即熔断） */
	private final int repeatThreshold;

	/** 键 = agent|sessionId；值 = 最近一次签名 + 连续次数（有界清理，防内存膨胀） */
	private final Map<String, LastCall> counters = new ConcurrentHashMap<>();

	private static final int MAX_KEYS = 512;

	/** 最近一次调用的签名与连续次数 */
	private record LastCall(String signature, int count) {
	}

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
		String sessionId = ctx == null ? null : ctx.getSessionId();
		String key = agentKey + "|" + (sessionId == null || sessionId.isBlank() ? "-" : sessionId);
		String signature = signature(calls);
		int count = bump(key, signature);
		if (count >= repeatThreshold) {
			String reason = "重复工具调用熔断：同一工具（" + brief(signature) + "）已连续调用 " + count
					+ " 次且无参数变化，已停止本轮以避免死循环。请补充更具体的信息，或改用支持工具编排的更强模型（如 qwen3:8b / 云端模型）。";
			log.warn("[tool-breaker] key={}, 连续重复 {}/{}，熔断停轮: digest={}, sig={}", key, count, repeatThreshold,
					digest(signature), brief(signature));
			// 计数清零：避免下一次请求立刻又被熔断
			counters.remove(key);
			return Flux.just(new RequestStopEvent(reason, GenerateReason.INTERRUPTED));
		}
		log.debug("[tool-breaker] key={}, 连续重复 {}/{}: digest={}", key, count, repeatThreshold, digest(signature));
		return next.apply(input);
	}

	/**
	 * 计数：**签名一变即清零**（只保留最后一个签名），与 Javadoc 的"连续"语义一致。
	 * 用 {@code compute} 保证读-改-写原子，避免并发下的计数丢失。
	 */
	private int bump(String key, String signature) {
		if (counters.size() > MAX_KEYS) {
			counters.clear();
		}
		int[] out = new int[1];
		counters.compute(key, (k, last) -> {
			int c = (last != null && last.signature().equals(signature)) ? last.count() + 1 : 1;
			out[0] = c;
			return new LastCall(signature, c);
		});
		return out[0];
	}

	/** 本次请求的签名（全部工具调用按序拼接：工具名 + 参数 JSON） */
	private String signature(List<ToolUseBlock> calls) {
		StringBuilder sb = new StringBuilder();
		for (ToolUseBlock call : calls) {
			sb.append(call.getName()).append('(').append(call.getInput()).append(");");
		}
		return sb.toString();
	}

	/** 签名摘要（sha256 前 8 位，十六进制）——现场核对"是否同一条调用"用 */
	private String digest(String signature) {
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(signature.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 4; i++) {
				sb.append(String.format("%02x", h[i]));
			}
			return sb.toString();
		}
		catch (Exception e) {
			return "n/a";
		}
	}

	/** 签名预览（最多 160 字符，日志用） */
	private String brief(String signature) {
		return signature.length() <= 160 ? signature : signature.substring(0, 160) + "…";
	}

}
