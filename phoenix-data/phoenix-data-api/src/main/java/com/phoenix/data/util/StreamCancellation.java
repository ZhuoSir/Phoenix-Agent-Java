package com.phoenix.data.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 图执行协作式取消注册表（BUG-24 T-06，Q3 拍板 ①+② 档）。
 * GraphService 在流生命周期登记/标记/注销；NodeBeanUtil 的守卫在每个节点动作入口检查。
 * 说明：底层阻塞调用（JDBC 执行中/子进程等待中）不被本机制硬中断（③档已批准延期为技术债），
 * 取消斩断的是「后续每一轮节点执行」——即后续模型调用与 SQL，token 消耗大头。
 */
public final class StreamCancellation {

	private static final ConcurrentHashMap<String, AtomicBoolean> FLAGS = new ConcurrentHashMap<>();

	private StreamCancellation() {
	}

	public static void register(String threadId) {
		if (threadId == null || threadId.isBlank()) {
			return;
		}
		FLAGS.put(threadId, new AtomicBoolean(false));
	}

	/** 标记取消：即使注册表项已不在也留 true 痕迹（竞态后到者判真更安全） */
	public static void markCancelled(String threadId) {
		if (threadId == null || threadId.isBlank()) {
			return;
		}
		AtomicBoolean flag = FLAGS.get(threadId);
		if (flag == null) {
			FLAGS.computeIfAbsent(threadId, k -> new AtomicBoolean(true)).set(true);
		}
		else {
			flag.set(true);
		}
	}

	public static boolean isCancelled(String threadId) {
		if (threadId == null || threadId.isBlank()) {
			return false;
		}
		AtomicBoolean flag = FLAGS.get(threadId);
		return flag != null && flag.get();
	}

	public static void unregister(String threadId) {
		if (threadId != null && !threadId.isBlank()) {
			FLAGS.remove(threadId);
		}
	}

	public static void checkOrThrow(String threadId) {
		if (isCancelled(threadId)) {
			throw new IllegalStateException(
					"STREAM_CANCELLED: 客户端已断开，提前终止图执行 (threadId=" + threadId + ")");
		}
	}

}
