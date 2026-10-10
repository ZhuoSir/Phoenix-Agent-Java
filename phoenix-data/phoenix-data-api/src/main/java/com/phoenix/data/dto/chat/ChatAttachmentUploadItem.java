package com.phoenix.data.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 控制器（reactive 段）读出的单个附件，交给 service 在弹性线程做校验与落库。
 *
 * <p>{@code readError} 非空表示**读取阶段**已失败（如超过大小上限被 DataBuffer 限流拦截），
 * service 直接判为 rejected，不再触碰存储。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachmentUploadItem {

	private String fileName;

	private byte[] content;

	private String readError;

}
