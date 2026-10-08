package com.phoenix.data.service.chat;

import com.phoenix.data.dto.chat.ChatAttachmentUploadItem;
import com.phoenix.data.entity.ChatAttachment;
import com.phoenix.data.vo.ChatAttachmentVO;
import com.phoenix.data.vo.ChatAttachmentUploadResultVO;

import java.util.List;

/**
 * 对话附件服务（chat-attachment-understanding T-02）。
 */
public interface ChatAttachmentService {

	/** 单文件大小上限（R-04，20MB） */
	long MAX_FILE_BYTES = 20L * 1024 * 1024;

	/** 单次上传数量上限（R-04，5 个） */
	int MAX_FILES_PER_REQUEST = 5;

	/**
	 * 批量上传：逐个做「扩展名 + 真实内容类型」双判定与大小校验，合法者落盘并落库。
	 * 非法者**单独**记入 rejected（R-03：不整批静默失败、不留悬挂记录）。
	 * @param items 已读出的附件（含读取阶段错误）
	 * @param uploaderId 归属用户 id（服务端由 token 反查，不信任入参）
	 * @param sessionId 会话 id，可空
	 */
	ChatAttachmentUploadResultVO uploadBatch(List<ChatAttachmentUploadItem> items, String uploaderId, String sessionId);

	/**
	 * R-11：按 id 取附件并做归属校验 —— 非本人且非超管 ⇒ 403；不存在 ⇒ 404。
	 * 存储路径一律取自库中记录（L-19：不接受入参拼路径）。
	 */
	ChatAttachment requireAccessible(Long id, String viewerId, boolean superAdmin);

	/**
	 * R-10 + L-58：按会话列出附件（与单对象校验**成对**实施）。
	 * {@code ownerIdOrNull} 非空 = 仅该创建人（普通用户）；null = 不过滤（超管）。
	 */
	List<ChatAttachmentVO> listForSession(String sessionId, String ownerIdOrNull);

	/** 打开附件原件（Resource，供下载端点；调用前必须先 requireAccessible） */
	org.springframework.core.io.Resource openResource(ChatAttachment attachment);

	/** 生成图片缩略图（仅 IMAGE；失败则回退原图字节，仍受鉴权保护） */
	byte[] buildThumb(ChatAttachment attachment);

	/**
	 * R-10：把附件绑定到已保存的消息（回填 message_id）。
	 * 由既有 {@code POST /api/sessions/{sid}/messages} 的副作用调用（plan 共享面 S9）：
	 * metadata 不含 attachmentIds 时**不会被调用**；解析失败由调用方降级为 WARN，不得影响消息保存。
	 * 幂等：重复绑定同一 message 不产生副作用；仅绑定**属于该用户**（或超管）的附件。
	 * @return 实际回填的行数
	 */
	int bindToMessage(List<Long> attachmentIds, Long messageId, String uploaderId, boolean superAdmin);

}
