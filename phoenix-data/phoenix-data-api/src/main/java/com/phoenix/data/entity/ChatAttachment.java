package com.phoenix.data.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话附件（chat-attachment-understanding R-01~R-04/R-10/R-11）。
 *
 * <p>不设 del_flag（plan 决策4，避开 L-21 墓碑行隐形）；清理走 status + 物理删。
 * uploader_id 由服务端从 token 反查写入，**不信任前端入参**。
 */
@Data
@Table("tbl_data_chat_attachment")
public class ChatAttachment {

	@Id(keyType = KeyType.Auto)
	private Long id;

	/** 会话 id（上传时可能尚未建会话 ⇒ 可空） */
	private String sessionId;

	/** 发送后回填，建立"附件↔消息"关系（R-10） */
	private Long messageId;

	/** 归属用户 id（R-11 鉴权依据） */
	private String uploaderId;

	/** DOCUMENT | IMAGE */
	private String kind;

	/** 小写扩展名（白名单判定留痕） */
	private String ext;

	/** Tika 探测出的真实内容类型（R-01 双判定留痕） */
	private String mime;

	private String fileName;

	private Long sizeBytes;

	/** 写入方生成；读取端不得由入参拼接（L-19） */
	private String storagePath;

	/** ACTIVE | ORPHAN | EXTRACT_FAILED */
	private String status;

	/** 文档抽取字符数（T-04 截断告知与排障用） */
	private Integer extractedChars;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

}
