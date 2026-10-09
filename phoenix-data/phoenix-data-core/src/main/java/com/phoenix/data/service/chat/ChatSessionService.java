package com.phoenix.data.service.chat;

import com.mybatisflex.core.service.IService;
import com.phoenix.data.entity.ChatSession;

import java.util.List;

/**
 * 聊天会话服务接口，提供会话的 CRUD、置顶、重命名及软删除功能。
 */
public interface ChatSessionService extends IService<ChatSession> {

	/**
	 * 根据Agent ID 和用户id 获取会话列表
	 * @return
	 */
	List<ChatSession> findByAgentIdAndUserId(Integer agentId, String userId);

	/** 会话空间常量（CR-03） */
	String SOURCE_ADMIN_RUN = "ADMIN_RUN";

	String SOURCE_FRONT_CHAT = "FRONT_CHAT";

	/** scope 入参归一：null/空/非法 ⇒ FRONT_CHAT（旧客户端兼容，CR-03 拍板） */
	static String normalizeSource(String scope) {
		return SOURCE_ADMIN_RUN.equalsIgnoreCase(scope) ? SOURCE_ADMIN_RUN : SOURCE_FRONT_CHAT;
	}

	/** CR-03：按空间过滤的会话列表（R-13） */
	List<ChatSession> findByAgentIdAndUserId(Integer agentId, String userId, String source);

	/** CR-03：带空间创建（R-13） */
	ChatSession createSession(Integer agentId, String title, String userId, String source);

	/** CR-03：仅清指定空间（防运行页清空误删聊天历史，R-13） */
	void clearSessionsByAgentIdAndSource(Integer agentId, String source);

	/** CR-03：查会话所属空间；不存在返回 null（R-11 叠加校验用） */
	String findSourceBySessionId(String sessionId);

	/**
	 * 根据 Agent ID 获取会话列表
	 */
	List<ChatSession> findByAgentId(Integer agentId);

	/**
	 * 创建新会话
	 */
	ChatSession createSession(Integer agentId, String title, String userId);

	/**
	 * 根据会话 ID 查找会话
	 */
	ChatSession findBySessionId(String sessionId);

	/**
	 * 清空指定 Agent 的所有会话（软删除）
	 */
	void clearSessionsByAgentId(Integer agentId);

	/**
	 * 更新会话的最后活动时间
	 */
	void updateSessionTime(String sessionId);

	/**
	 * 置顶或取消置顶会话
	 */
	void pinSession(String sessionId, boolean isPinned);

	/**
	 * 重命名会话
	 */
	void renameSession(String sessionId, String newTitle);

	/**
	 * 删除单个会话（软删除）
	 */
	void deleteSession(String sessionId);

}
