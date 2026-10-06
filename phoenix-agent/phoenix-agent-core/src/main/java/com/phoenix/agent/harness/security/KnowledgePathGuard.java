package com.phoenix.agent.harness.security;

import java.util.List;
import java.util.Locale;

/**
 * 知识库原件路径护栏（spec kb-access-isolation 的 R-02 / R-03，任务 T-07）。
 *
 * <p>**纯函数、与框架解耦**：既便于表驱动验证（T-07），也便于在 shell / 文件工具层接入（T-08）。
 *
 * <p>**策略（一期，用户 2026-10-06 裁定方案 A「务实边界」）**：
 * <ol>
 * <li>**白名单优先**：目标路径位于"本会话工作区"之内 ⇒ 放行（保证 agent 正常工作区读写不误伤）；</li>
 * <li>**上传根收敛**：`/app/uploads`（上传根，可由参数注入）之下的**任何非工作区路径** ⇒ 拒绝
 * —— 这一条同时覆盖知识库原件（新老位置）与会话工作区之外的共享文件；</li>
 * <li>**显式黑名单**：知识库原件目录的**相对写法**（如 `data-agent/agent-knowledge/...`，
 * 相对上传根或相对工作目录）⇒ 拒绝。</li>
 * </ol>
 *
 * <p>**能力边界（必须如实声明，依据 BUG-109 实测）**：agent shell 与后端 JVM **同容器同 UID**，
 * 属主/权限无法区分二者，故本类是**软件层拦截**：只能覆盖"经过本方法判定"的调用点（T-08 接入 shell/文件工具），
 * **不承诺**"权限级不可读"，也存在绕过面（解释器直读、编码变形等，见 T-09 的如实记录）。
 * 真正硬隔离见 backlog BL-32/BL-33。
 */
public final class KnowledgePathGuard {

	/** 上传根（容器内默认值；调用方可用 {@code PHOENIX_UPLOAD_ROOT} 覆盖）。 */
	public static final String DEFAULT_UPLOAD_ROOT = "/app/uploads";

	/** 知识库原件的容器内目录（相对上传根的写法见 {@link #DENIED_RELATIVE_PREFIXES}）。 */
	public static final String AGENT_KNOWLEDGE_DIR = "data-agent/agent-knowledge";

	/** 显式黑名单：相对写法（相对上传根，或相对工作目录）。 */
	private static final List<String> DENIED_RELATIVE_PREFIXES = List.of(AGENT_KNOWLEDGE_DIR, "data-agent",
			"agent-workspace");

	private KnowledgePathGuard() {
	}

	/**
	 * 判定某路径是否**禁止**被智能体运行时读取。
	 * @param rawPath 待判定的路径（可为绝对或相对、可含 {@code .}/{@code ..}/反斜杠/重复斜杠）
	 * @param allowedWorkspaceRoot 允许的会话工作区根（绝对或相对）；为空表示无白名单
	 * @return {@code true} = 拒绝
	 */
	public static boolean isDenied(String rawPath, String allowedWorkspaceRoot) {
		return isDenied(rawPath, allowedWorkspaceRoot, DEFAULT_UPLOAD_ROOT);
	}

	/**
	 * 同 {@link #isDenied(String, String)}，但可指定上传根（便于测试与不同部署形态）。
	 * @param rawPath 待判定的路径
	 * @param allowedWorkspaceRoot 允许的会话工作区根
	 * @param uploadRoot 上传根（如 {@code /app/uploads}）
	 * @return {@code true} = 拒绝
	 */
	public static boolean isDenied(String rawPath, String allowedWorkspaceRoot, String uploadRoot) {
		if (rawPath == null || rawPath.isBlank()) {
			return false;
		}
		String path = normalize(rawPath);
		if (path.isEmpty()) {
			return false;
		}
		String workspace = normalize(allowedWorkspaceRoot);
		// 0) **显式黑名单先判**（相对写法，如 `data-agent/agent-knowledge/x.md`、`agent-workspace/...`）：
		// 必须排在白名单之前，否则"相对路径按工作区解析"会把它解析进工作区而被白名单短路放行（表驱动已验证）。
		for (String denied : DENIED_RELATIVE_PREFIXES) {
			if (path.equals(denied) || path.startsWith(denied + "/")) {
				return true;
			}
		}
		// 相对路径按「工作目录 = 会话工作区」解析（shell 的 cwd 即工作区）；绝对路径原样
		String resolved = (path.startsWith("/") || workspace.isEmpty()) ? path : normalize(workspace + "/" + path);
		if (!workspace.isEmpty() && isSameOrUnder(resolved, workspace)) {
			return false; // 1) 白名单优先（工作区内读写不误伤）
		}
		String root = normalize(uploadRoot);
		// 2) 上传根收敛：**解析后**的路径落在上传根下（且不在工作区内，上面已判） ⇒ 拒绝。
		// 覆盖：知识库原件（绝对/相对两种写法）、跨会话工作区目录、以及 `../../data-agent/...` 相对逃逸。
		// 注意：不可写成"相对路径拼上传根后再判"——那会把 note.md 这类**工作区内**的相对路径全判为拒绝（表驱动已验证）。
		if (!root.isEmpty() && isSameOrUnder(resolved, root)) {
			return true;
		}
		return false;
	}

	/** 路径归一化：反斜杠转正、折叠 {@code .}/{@code ..}、去重复斜杠、统一小写比较用副本。 */
	public static String normalize(String raw) {
		if (raw == null) {
			return "";
		}
		String unified = raw.trim().replace('\\', '/');
		if (unified.isEmpty()) {
			return "";
		}
		boolean absolute = unified.startsWith("/");
		String[] segments = unified.split("/+");
		java.util.Deque<String> stack = new java.util.ArrayDeque<>();
		for (String segment : segments) {
			if (segment.isEmpty() || ".".equals(segment)) {
				continue;
			}
			if ("..".equals(segment)) {
				if (!stack.isEmpty() && !"..".equals(stack.peek())) {
					stack.removeLast();
				}
				else if (!absolute) {
					stack.addLast("..");
				}
				continue;
			}
			stack.addLast(segment);
		}
		String joined = String.join("/", stack);
		String result = (absolute ? "/" : "") + joined;
		if (result.isEmpty()) {
			return absolute ? "/" : "";
		}
		return result;
	}

	/** 归一化后判断 {@code candidate} 是否等于 {@code base} 或位于其下；比较用大小写不敏感（避免大小写绕过）。 */
	private static boolean isSameOrUnder(String candidate, String base) {
		if (candidate.isEmpty() || base.isEmpty()) {
			return false;
		}
		String c = candidate.toLowerCase(Locale.ROOT);
		String b = base.toLowerCase(Locale.ROOT);
		return c.equals(b) || c.startsWith(b.endsWith("/") ? b : b + "/");
	}

}
