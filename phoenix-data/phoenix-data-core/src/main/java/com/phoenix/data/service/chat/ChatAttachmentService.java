package com.phoenix.data.service.chat;

import com.phoenix.data.dto.chat.ChatAttachmentUploadItem;
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

}
