package com.phoenix.data.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.data.component.AdminRoleGuard;
import com.phoenix.data.dto.chat.ChatAttachmentUploadItem;
import com.phoenix.data.entity.ChatAttachment;
import com.phoenix.data.service.chat.ChatAttachmentService;
import com.phoenix.data.vo.ApiResponse;
import com.phoenix.data.vo.ChatAttachmentUploadResultVO;
import com.phoenix.data.vo.ChatAttachmentVO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

	/** R-11：超管豁免沿用 R-18/R-08 同一守卫（单一口径，不新造判定） */
	private final AdminRoleGuard adminRoleGuard;

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

	/**
	 * R-10 + L-58：按会话列出附件（**与单对象校验成对**）。普通用户仅见本人；超管见全部。
	 */
	@GetMapping
	public ApiResponse<List<ChatAttachmentVO>> list(@RequestParam(required = false) String sessionId,
			@RequestHeader(value = "phoenix-token", required = false) String token) {
		String viewer = loginIdFromToken(token);
		if (viewer == null) {
			return ApiResponse.error("未登录或登录已失效");
		}
		boolean superAdmin = adminRoleGuard.isAdmin(viewer);
		return ApiResponse.success("查询成功", chatAttachmentService.listForSession(sessionId, superAdmin ? null : viewer));
	}

	/**
	 * R-11：下载原件 —— 非本人且非超管 ⇒ 403；强制 {@code Content-Disposition: attachment}，
	 * 不内联回显（防文本/HTML 被浏览器执行）。
	 */
	@GetMapping("/{id}")
	public ResponseEntity<Resource> download(@PathVariable Long id,
			@RequestHeader(value = "phoenix-token", required = false) String token) {
		String viewer = loginIdFromToken(token);
		if (viewer == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		ChatAttachment att = chatAttachmentService.requireAccessible(id, viewer, adminRoleGuard.isAdmin(viewer));
		Resource res = chatAttachmentService.openResource(att);
		String encoded = URLEncoder.encode(att.getFileName() == null ? ("attachment-" + id) : att.getFileName(),
				StandardCharsets.UTF_8).replace("+", "%20");
		return ResponseEntity.ok()
			// 强制 attachment：不内联回显（防文本/HTML 被浏览器执行）
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
			// 下载的是用户上传件 ⇒ 禁 MIME 嗅探（纵深防御，即使 CT 为真实类型也不被当脚本执行）
			.header("X-Content-Type-Options", "nosniff")
			.contentType(MediaType.APPLICATION_OCTET_STREAM)
			.body(res);
	}

	/**
	 * R-10：图片缩略图（仅 IMAGE 类）。同样受归属校验保护；
	 * 因 {@code <img src>} 无法携带鉴权头，前端须用 fetch+blob 加载（见 T-07 实现注记）。
	 */
	@GetMapping("/{id}/thumb")
	public ResponseEntity<byte[]> thumb(@PathVariable Long id,
			@RequestHeader(value = "phoenix-token", required = false) String token) {
		String viewer = loginIdFromToken(token);
		if (viewer == null) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		ChatAttachment att = chatAttachmentService.requireAccessible(id, viewer, adminRoleGuard.isAdmin(viewer));
		if (!"IMAGE".equals(att.getKind())) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
		}
		byte[] bytes = chatAttachmentService.buildThumb(att);
		return ResponseEntity.ok()
			.header("X-Content-Type-Options", "nosniff")
			.contentType(MediaType.IMAGE_PNG)
			.body(bytes);
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
