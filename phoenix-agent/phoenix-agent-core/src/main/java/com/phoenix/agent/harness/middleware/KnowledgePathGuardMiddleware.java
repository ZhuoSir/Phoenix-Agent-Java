package com.phoenix.agent.harness.middleware;

import com.phoenix.agent.harness.security.KnowledgePathGuard;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 知识库原件访问护栏中间件（spec kb-access-isolation 的 R-02 / R-03，任务 T-08）。
 *
 * <p>**为什么是中间件而不是官方 deny 规则**：字节码实测（证据 `evidence/T-01-T-02_framework-probe.txt` 第五轮）
 * `ToolBase.matchRule` 的默认实现只有 `return ruleContent == null;`，且**没有任何工具覆盖它**
 * ⇒ 带"路径/命令模式"的 `PermissionRule` **永远不会命中**，用它只会得到"配了规则却毫无效果"。
 * 而 {@link MiddlewareBase#onActing} 能在工具调用**发生前**拿到 `ActingInput`（＝`List<ToolUseBlock>`，
 * 含 `getName()` 与 `getInput()`），故护栏落在这里。
 *
 * <p>**判定**：把每个工具调用参数里的路径/命令文本交给 T-07 的 {@link KnowledgePathGuard}
 * （纯函数，已表驱动 16/16 验证：绝对/相对写法、反斜杠、点段、大小写、相对逃逸、跨会话目录均覆盖）。
 *
 * <p>**模式**（环境变量 {@code PHOENIX_KB_PATH_GUARD}）：
 * <ul>
 * <li>{@code observe}（**默认**）：只记录审计日志，不改变行为 —— 可先上线观察真实的命中情况，零风险；</li>
 * <li>{@code enforce}：从待执行的工具调用中**摘除**被拒调用（模型侧不再拿到该工具的结果）。</li>
 * </ul>
 *
 * <p>**能力边界（必须如实声明，依据 BUG-109 实测）**：agent shell 与后端 JVM 同容器同 UID ⇒ 本中间件是
 * **软件层拦截**，只能约束"经本中间件判定的工具调用"，**不承诺**"权限级不可读"；解释器直读、编码变形等
 * 绕过面由 T-09 逐条实测并如实记录（硬隔离见 BL-32/BL-33）。
 */
@Slf4j
public class KnowledgePathGuardMiddleware implements MiddlewareBase {

	/** 环境变量：护栏模式（observe / enforce）。 */
	public static final String MODE_ENV = "PHOENIX_KB_PATH_GUARD";

	/** 允许的会话工作区根（白名单；由装配处传入，即该会话的 workspace）。 */
	private final String workspaceRoot;

	/** 是否"执行拒绝"（false=observe 只记日志）。 */
	private final boolean enforce;

	/**
	 * @param workspaceRoot 本会话工作区根（绝对路径）
	 */
	public KnowledgePathGuardMiddleware(String workspaceRoot) {
		this(workspaceRoot, MODE_ENV);
	}

	/**
	 * @param workspaceRoot 本会话工作区根（绝对路径）
	 * @param modeEnvName 读取模式的配置项名（便于测试注入）
	 */
	public KnowledgePathGuardMiddleware(String workspaceRoot, String modeEnvName) {
		this.workspaceRoot = workspaceRoot;
		String mode = System.getenv(modeEnvName);
		if (mode == null || mode.isBlank()) {
			mode = System.getProperty(modeEnvName);
		}
		this.enforce = mode != null && "enforce".equalsIgnoreCase(mode.trim());
	}

	@Override
	public Flux<AgentEvent> onActing(Agent agent, RuntimeContext ctx, ActingInput input,
			Function<ActingInput, Flux<AgentEvent>> next) {
		List<ToolUseBlock> calls = (input == null) ? null : input.toolCalls();
		if (calls == null || calls.isEmpty()) {
			return next.apply(input);
		}
		List<ToolUseBlock> allowed = new ArrayList<>(calls.size());
		boolean deniedAny = false;
		for (ToolUseBlock call : calls) {
			String hit = firstDeniedText(call);
			if (hit == null) {
				allowed.add(call);
				continue;
			}
			deniedAny = true;
			// 四要素审计（R-03）：agentId / sessionId / 目标路径 / 命令或工具名
			log.warn("知识库路径护栏命中: agentId={}, sessionId={}, tool={}, 命中路径={}, 参数={}, mode={}",
					agentId(agent), sessionId(ctx), call.getName(), hit, call.getInput(),
					enforce ? "enforce" : "observe");
		}
		if (!deniedAny || !enforce) {
			return next.apply(input);
		}
		return next.apply(new ActingInput(allowed));
	}

	/** 取该工具调用参数中第一个"命中禁止清单"的文本；无命中返回 {@code null}。 */
	private String firstDeniedText(ToolUseBlock call) {
		if (call == null) {
			return null;
		}
		Map<String, Object> args = call.getInput();
		if (args == null || args.isEmpty()) {
			return null;
		}
		for (Object value : args.values()) {
			String text = stringify(value);
			if (text == null || text.isBlank()) {
				continue;
			}
			if (KnowledgePathGuard.isDenied(text, workspaceRoot)) {
				return text;
			}
		}
		return null;
	}

	/** 把参数值摊平成待判定文本（字符串直用；集合/数组拼接；其余走 toString）。 */
	private static String stringify(Object value) {
		if (value == null) {
			return null;
		}
		if (value instanceof String s) {
			return s;
		}
		if (value instanceof Iterable<?> it) {
			StringBuilder sb = new StringBuilder();
			for (Object item : it) {
				String part = stringify(item);
				if (part != null) {
					sb.append(part).append(' ');
				}
			}
			return sb.toString();
		}
		return String.valueOf(value);
	}

	private static String agentId(Agent agent) {
		return agent == null ? null : agent.getAgentId();
	}

	private static String sessionId(RuntimeContext ctx) {
		return ctx == null ? null : ctx.getSessionId();
	}

}
