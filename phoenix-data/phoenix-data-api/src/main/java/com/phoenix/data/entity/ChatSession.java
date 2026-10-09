package com.phoenix.data.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import com.phoenix.data.enums.SessionStatusEnm;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Chat Session Entity Class
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table("tbl_data_chat_session")
public class ChatSession {

	@Id
	private String id; // UUID

	private Integer agentId;

	private String title;
	/**
	 * @see SessionStatusEnm
	 */
	private String status; // active, archived, deleted

	@Builder.Default
	private Boolean isPinned = false; // Whether pinned

	/**
	 * 会话空间（CR-03）: ADMIN_RUN=管理端运行页 / FRONT_CHAT=前台聊天(含mobile)。
	 * 默认 FRONT_CHAT = 存量回填口径 A + 旧客户端不传 scope 的兼容默认。
	 */
	private String source = "FRONT_CHAT";

	private String userId;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	/**
	 * 构造聊天会话对象
	 *
	 * @param id 会话ID
	 * @param agentId 智能体ID
	 * @param title 会话标题
	 * @param status 会话状态
	 * @param userId 用户ID
	 */
	public ChatSession(String id, Integer agentId, String title, String status, String userId) {
		this.id = id;
		this.agentId = agentId;
		this.title = title;
		this.status = status;
		this.isPinned = false;
		this.userId = userId;
	}

}
