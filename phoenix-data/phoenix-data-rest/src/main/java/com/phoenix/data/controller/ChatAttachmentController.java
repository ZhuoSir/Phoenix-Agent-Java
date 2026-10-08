package com.phoenix.data.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.data.dto.chat.ChatAttachmentUploadItem;
import com.phoenix.data.service.chat.ChatAttachmentService;
import com.phoenix.data.vo.ApiResponse;
import com.phoenix.data.vo.ChatAttachmentUploadResultVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话附件上传端点（chat-attachment-understanding T-02；R-01~R-04）。
 *
 * <p>共享面 S7：新路径 {@code /api/chat/attachment}，**不复用** {@code /api/upload/avatar}（避免稀释其 image-only 语义）。
 * <p>L-65：登录态用 {@code phoenix-token} 头 + {@link StpUtil#getLoginIdByToken} **线程无关**反查，
 * 绝不在 {@code Mono.fromCallable}/{@code boundedElastic} 内取 Sa-Token 上下文（BUG-140 原型）。
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/attachment")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class ChatAttachmentController {

	private final ChatAttachmentService chatAttachmentService;

	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public Mono<ApiResponse<ChatAttachmentUploadResultVO>> upload(ServerWebExchange exchange,
			@RequestHeader(value = "phoenix-token", required = false) String token) {
		String uploader = loginIdFromToken(token);
		if (uploader == null) {
			return Mono.just(ApiResponse.error("未登录或登录已失效"));
		}
		return exchange.getMultipartData().flatMap(parts -> {
			List<Part> fileParts = parts.getOrDefault("file", List.of());
			if (fileParts.isEmpty()) {
				return Mono.just(ApiResponse.error("未收到文件"));
			}
			// R-04：数量上限，提示含具体上限值
			if (fileParts.size() > ChatAttachmentService.MAX_FILES_PER_REQUEST) {
				return Mono.just(ApiResponse.error("单次最多上传 " + ChatAttachmentService.MAX_FILES_PER_REQUEST + " 个附件（当前 "
						+ fileParts.size() + " 个）"));
			}
			String sessionId = firstFormValue(parts.get("sessionId"));
			List<Mono<ChatAttachmentUploadItem>> reads = new ArrayList<>();
			for (Part p : fileParts) {
				if (!(p instanceof FilePart fp)) {
					continue;
				}
				String name = fp.filename();
				// 限流读取：超过上限即抛 DataBufferLimitException ⇒ 转成该文件的 rejected（不打爆内存）
				reads.add(DataBufferUtils.join(fp.content(), (int) ChatAttachmentService.MAX_FILE_BYTES + 1).map(buf -> {
					try {
						byte[] b = new byte[buf.readableByteCount()];
						buf.read(b);
						return new ChatAttachmentUploadItem(name, b, null);
					}
					finally {
						DataBufferUtils.release(buf);
					}
				}).onErrorResume(DataBufferLimitException.class,
						e -> Mono.just(new ChatAttachmentUploadItem(name, null,
								"文件超过大小上限 " + (ChatAttachmentService.MAX_FILE_BYTES / 1024 / 1024) + "MB")))
					.onErrorResume(e -> Mono
						.just(new ChatAttachmentUploadItem(name, null, "文件读取失败：" + e.getMessage()))));
			}
			// mergeSequential：并发订阅但**保持顺序**（结果与用户选择顺序一致）
			return Flux.mergeSequential(reads)
				.collectList()
				.flatMap(items -> Mono.fromCallable(() -> chatAttachmentService.uploadBatch(items, uploader, sessionId))
					.subscribeOn(Schedulers.boundedElastic()))
				.map(vo -> ApiResponse.success("上传完成", vo));
		});
	}

	/** 线程无关地由 token 反查登录 id（L-65）；无效/缺失返回 null */
	private String loginIdFromToken(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			Object id = StpUtil.getLoginIdByToken(token);
			return id == null ? null : String.valueOf(id);
		}
		catch (Exception e) {
			log.warn("附件上传鉴权失败: {}", e.getMessage());
			return null;
		}
	}

	private String firstFormValue(List<Part> ps) {
		if (ps == null || ps.isEmpty()) {
			return null;
		}
		return (ps.get(0) instanceof FormFieldPart f) ? f.value() : null;
	}

}
