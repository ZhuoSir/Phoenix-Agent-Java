package com.phoenix.data.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 附件出参（chat-attachment-understanding T-02）。
 *
 * <p>{@code url} 指向**受鉴权的**附件端点（T-05），刻意不暴露存储直链——
 * 直链会绕过 R-11 归属校验（L-58：可见性与归属校验必须成对）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachmentVO {

	private Long id;

	private String fileName;

	/** DOCUMENT | IMAGE */
	private String kind;

	private String ext;

	private Long sizeBytes;

	/** 受鉴权的取件端点：/api/chat/attachment/{id} */
	private String url;

	/** 抽取状态：ACTIVE | EXTRACT_FAILED（仅文档类有意义；R-09） */
	private String extractStatus;

	/** 用户可见提示：解析失败原因类别，或"内容已截断"告知（R-08/R-09） */
	private String notice;

	/** 缩略图端点（仅 IMAGE 类非空）；同样受鉴权保护，前端须用 fetch+blob（<img src> 带不了鉴权头） */
	private String thumbUrl;

}
