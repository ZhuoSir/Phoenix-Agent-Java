package com.phoenix.agent.harness.support;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.state.AgentState;
import io.agentscope.core.state.Task;
import io.agentscope.core.state.TaskContextState;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * BUG-205：todo 状态跨轮一致性修复。
 *
 * <p><b>背景（现场实证 2026-10-24）</b>：轮次被工具熔断/取消/超时强行终止时，agent 的 todo 里可能残留
 * 多个 IN_PROGRESS；框架 todo 工具要求「同一时刻最多一个 in_progress」，于是**下一轮**模型先被这个
 * 不一致绊住 —— 实测输出 634 字全在解释 "multiple tasks were marked as in_progress…"，而不是回答问题。
 *
 * <p><b>做法（平台侧兜底）</b>：每轮开始前规范化一次 —— 保留**最后一个** IN_PROGRESS，其余降级为
 * PENDING；PENDING/COMPLETED 一律不动。变化后由框架随 AgentState 落库。
 *
 * <p><b>坑</b>：harness 流式路径上 `agent.getAgentState()` 返回 null，必须用
 * {@link RuntimeContext#resolveAgentState(RuntimeContext, HarnessAgent)} 才能拿到真正的会话状态。
 *
 * <p><b>局限（如实记录）</b>：事后清理，并非从根上阻止"多 in_progress"被写入；根治需框架侧在写入处校验。
 */
@Slf4j
public final class TodoStateRepair {

	private TodoStateRepair() {
	}

	/** 从运行时上下文解析状态后规范化（流式路径应使用本方法） */
	public static boolean normalize(RuntimeContext ctx, HarnessAgent agent) {
		if (ctx == null || agent == null) {
			return false;
		}
		try {
			return normalizeState(RuntimeContext.resolveAgentState(ctx, agent));
		}
		catch (Exception e) {
			log.warn("[todo-repair] 解析 AgentState 失败（忽略）: {}", e.toString());
			return false;
		}
	}

	/** agent 自带 state 可用时规范化（为空则安全跳过） */
	public static boolean normalize(HarnessAgent agent) {
		if (agent == null) {
			return false;
		}
		try {
			return normalizeState(agent.getAgentState());
		}
		catch (Exception e) {
			log.warn("[todo-repair] 读取 AgentState 失败（忽略）: {}", e.toString());
			return false;
		}
	}

	/** 核心逻辑：把 todo 规范化为「最多一个 IN_PROGRESS」 */
	public static boolean normalizeState(AgentState st) {
		if (st == null) {
			return false;
		}
		try {
			TaskContextState tc = st.getTasksContext();
			if (tc == null) {
				return false;
			}
			List<Task> tasks = tc.getTasks();
			if (tasks == null || tasks.isEmpty()) {
				// 没有 todo 的会话（绝大多数）完全静默，避免每轮刷日志
				return false;
			}
			int lastInProgress = -1;
			int inProgressCount = 0;
			for (int i = 0; i < tasks.size(); i++) {
				Task t = tasks.get(i);
				if (t != null && t.getState() == Task.State.IN_PROGRESS) {
					lastInProgress = i;
					inProgressCount++;
				}
			}
			log.info("[todo-repair] 读到 todo: tasks={}, inProgress={}", tasks.size(), inProgressCount);
			if (inProgressCount <= 1) {
				return false;
			}
			List<Task> mutable = tc.tasksMutable();
			boolean changed = false;
			for (int i = 0; i < tasks.size(); i++) {
				Task t = tasks.get(i);
				if (t == null || t.getState() != Task.State.IN_PROGRESS || i == lastInProgress) {
					continue;
				}
				mutable.set(i, copyWithState(t, Task.State.PENDING));
				changed = true;
			}
			log.warn("[todo-repair] 检测到 {} 个 IN_PROGRESS（BUG-205），已保留最后一个、其余降级为 PENDING", inProgressCount);
			return changed;
		}
		catch (Exception e) {
			log.warn("[todo-repair] 规范化失败（忽略，不影响本轮）: {}", e.toString());
			return false;
		}
	}

	/** Task 不可变 ⇒ 复制全部字段、只换 state */
	private static Task copyWithState(Task t, Task.State state) {
		return Task.builder()
			.subject(t.getSubject())
			.description(t.getDescription())
			.metadata(t.getMetadata())
			.createdAt(t.getCreatedAt())
			.state(state)
			.id(t.getId())
			.owner(t.getOwner())
			.blocks(t.getBlocks())
			.blockedBy(t.getBlockedBy())
			.build();
	}

}
