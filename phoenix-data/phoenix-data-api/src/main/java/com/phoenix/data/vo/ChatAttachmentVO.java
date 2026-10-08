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

}
