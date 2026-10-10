package com.phoenix.data.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量上传结果（chat-attachment-understanding R-03 场景：一批中非法者单独指明，不整批静默失败）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachmentUploadResultVO {

	private List<ChatAttachmentVO> accepted;

	private List<Rejected> rejected;

	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Rejected {

		private String fileName;

		private String reason;

	}

}
