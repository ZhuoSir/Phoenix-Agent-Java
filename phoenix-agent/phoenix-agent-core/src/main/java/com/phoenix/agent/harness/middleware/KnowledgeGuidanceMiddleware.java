package com.phoenix.agent.harness.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.harness.agent.middleware.HarnessRuntimeMiddleware;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 知识库访问指引中间件（spec kb-access-isolation 的 R-04 / R-05，任务 T-10）。
 *
 * <p>**要治的问题（BUG-104，2026-10-06 离线逐字实证）**：框架 `WorkspaceContextMiddleware` 会注入
 * "## Domain Knowledge — The workspace `knowledge/` tree … **treat that directory as the source of truth** …
 * injected `knowledge/KNOWLEDGE.md` … plus a **full list of knowledge file paths**"，
 * 而本项目**从不物化 `knowledge/` 目录** ⇒ 模型按提示先去工作区翻找、扑空后才绕路（实测
 * `list_files knowledge` → "Empty or not a directory"，随后全盘 `find`/`sed` 直读知识库原件，
 * 这也是 BUG-86 的诱因链一环）。
 *
 * <p>**做法**：不删框架段（离线已证该段与 `## Memory Recall`/`## Memory Persistence` **同源同块**，
 * 整体 `disableWorkspaceContext()` 会连带关掉记忆指路），而是在**系统提示词末尾追加**本项目权威指引 ——
 * 末尾位置使其具备最高优先级，且措辞显式声明"上文任何关于工作区 knowledge/ 的描述均不适用"。
 *
 * <p>**能力边界**：本中间件只**引导**模型；真正的拦截由 {@link KnowledgePathGuardMiddleware}（T-08）承担，
 * 且同 UID 下仍是软件层拦截（BUG-109）。指引 ≠ 强制。
 */
@Slf4j
public class KnowledgeGuidanceMiddleware implements HarnessRuntimeMiddleware {

	/** 追加到系统提示词末尾的权威指引（中文，与实际实现一致）。 */
	static final String GUIDE = """

			## 知识库访问规则（本项目权威，优先级高于上文任何关于工作区 knowledge/ 的描述）

			1. 本工作区**没有** `knowledge/` 目录，也不存在 `knowledge/KNOWLEDGE.md` 或"知识库文件路径清单"。
			   上文若称"工作区 `knowledge/` 树是知识库的事实源 / 会注入 KNOWLEDGE.md 与全量路径清单"，
			   **均不适用于本项目**，请勿据此用 ls / glob / find / grep 去工作区翻找知识库。
			2. 知识库**只能**通过检索工具 `getRagInfo` 访问（传入自然语言关键词，返回该智能体**绑定知识库**的片段）。
			   当问题涉及业务、制度、产品、调研、文档事实时，**第一步就调用 getRagInfo**，不要先翻文件系统。
			3. **禁止**用 shell 或文件工具（execute / cat / head / grep / sed / find / read_file / python 等）读取
			   `/app/uploads/data-agent/agent-knowledge/**` 或任何知识库原件；此类调用会被拦截并记录审计日志。
			4. 若 `getRagInfo` 返回"**检索失败**…"，请**如实告知用户"知识库检索服务当前不可用"**（附上给出的原因），
			   绝不可改去文件系统查找、也不可编造内容；若返回"没有找到相关文档"，可更换 1~2 组关键词重试后再作答。
			""";

	@Override
	public Mono<String> onSystemPrompt(Agent agent, RuntimeContext ctx, String sysPrompt) {
		String base = (sysPrompt == null) ? "" : sysPrompt;
		if (base.contains("知识库访问规则")) {
			return Mono.just(base);
		}
		log.debug("注入知识库权威指引: agentId={}", agent == null ? null : agent.getAgentId());
		return Mono.just(base + GUIDE);
	}

}
