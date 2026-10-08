package com.phoenix.data.service.chat;

import com.phoenix.data.dto.chat.ChatAttachmentUploadItem;
import com.phoenix.data.entity.ChatAttachment;
import com.phoenix.data.mapper.ChatAttachmentMapper;
import com.phoenix.data.service.file.ByteArrayMultipartFile;
import com.phoenix.data.service.file.FileStorageService;
import com.phoenix.data.vo.ChatAttachmentUploadResultVO;
import com.phoenix.data.vo.ChatAttachmentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 对话附件服务实现（chat-attachment-understanding T-02）。
 *
 * <p>R-01/R-02 白名单**双判定**：扩展名在白名单内 **且** Tika 探测的真实内容类型与该扩展名相容；
 * 二者任一不符即拒（防"改扩展名伪装"）。R-03：非法者单独记 rejected，**不落库不落盘**。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAttachmentServiceImpl implements ChatAttachmentService {

	/** 存储子目录（与 avatar 的 avatars 隔离 —— 共享面 S4 对面断言） */
	private static final String SUB_PATH = "chat-attachments";

	private static final String TYPE_MSG = "不支持的文件类型：仅支持文档（word/pdf/excel/txt/md）与常见图片格式（png/jpg/jpeg/gif/webp/bmp）";

	/** 文档类：扩展名 → 相容的真实内容类型集合（R-01） */
	private static final Map<String, Set<String>> DOC_TYPES = Map.of("doc", Set.of("application/msword", "application/x-tika-msoffice"),
			"docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "application/x-tika-ooxml"),
			"xls", Set.of("application/vnd.ms-excel", "application/x-tika-msoffice"), "xlsx",
			Set.of("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/zip", "application/x-tika-ooxml"),
			// Tika 3.x 对 .md 实测给 text/x-web-markdown（另兼容 text/markdown、纯文本与 x-webview）
			"pdf", Set.of("application/pdf"), "txt", Set.of("text/plain"), "md",
			Set.of("text/plain", "text/markdown", "text/x-web-markdown", "text/x-webview"));

	/** 图片类：扩展名 → 相容的真实内容类型集合（R-02；.svg 刻意排除，可内嵌脚本） */
	private static final Map<String, Set<String>> IMG_TYPES = Map.of("png", Set.of("image/png"), "jpg", Set.of("image/jpeg"), "jpeg",
			Set.of("image/jpeg"), "gif", Set.of("image/gif"), "webp", Set.of("image/webp"), "bmp", Set.of("image/bmp", "image/x-ms-bmp"));

	private final FileStorageService fileStorageService;

	private final ChatAttachmentMapper chatAttachmentMapper;

	private final Tika tika = new Tika();

	@Override
	public ChatAttachmentUploadResultVO uploadBatch(List<ChatAttachmentUploadItem> items, String uploaderId, String sessionId) {
		List<ChatAttachmentVO> accepted = new ArrayList<>();
		List<ChatAttachmentUploadResultVO.Rejected> rejected = new ArrayList<>();
		for (ChatAttachmentUploadItem item : items) {
			String name = StringUtils.hasText(item.getFileName()) ? item.getFileName() : "(未命名)";
			// 读取阶段已失败（如超大小被限流拦截）⇒ 直接拒，不触碰存储
			if (item.getReadError() != null) {
				rejected.add(new ChatAttachmentUploadResultVO.Rejected(name, item.getReadError()));
				continue;
			}
			byte[] content = item.getContent();
			if (content == null || content.length == 0) {
				rejected.add(new ChatAttachmentUploadResultVO.Rejected(name, "文件内容为空"));
				continue;
			}
			if (content.length > MAX_FILE_BYTES) {
				rejected.add(new ChatAttachmentUploadResultVO.Rejected(name, sizeMsg()));
				continue;
			}
			String ext = extOf(name);
			Set<String> docOk = DOC_TYPES.get(ext);
			Set<String> imgOk = IMG_TYPES.get(ext);
			if (docOk == null && imgOk == null) {
				rejected.add(new ChatAttachmentUploadResultVO.Rejected(name, TYPE_MSG));
				continue;
			}
			String mime;
			try {
				// R-01 双判定：真实内容类型（不信扩展名、也不信前端给的 Content-Type）
				mime = tika.detect(content, name);
			}
			catch (Exception e) {
				log.warn("附件类型探测失败: name={}, reason={}", name, e.getMessage());
				rejected.add(new ChatAttachmentUploadResultVO.Rejected(name, "无法识别文件内容类型，已拒绝"));
				continue;
			}
			Set<String> allow = (docOk != null) ? docOk : imgOk;
			if (mime == null || !allow.contains(mime)) {
				rejected.add(new ChatAttachmentUploadResultVO.Rejected(name, "文件内容与扩展名不符（检测类型：" + mime + "）"));
				continue;
			}
			String kind = (docOk != null) ? "DOCUMENT" : "IMAGE";
			String path = null;
			try {
				path = fileStorageService.storeFile(new ByteArrayMultipartFile(content, name, mime), SUB_PATH);
				ChatAttachment e = new ChatAttachment();
				e.setSessionId(sessionId);
				e.setUploaderId(uploaderId);
				e.setKind(kind);
				e.setExt(ext);
				e.setMime(mime);
				e.setFileName(name);
				e.setSizeBytes((long) content.length);
				e.setStoragePath(path);
				e.setStatus("ACTIVE");
				e.setCreateTime(LocalDateTime.now());
				e.setUpdateTime(LocalDateTime.now());
				chatAttachmentMapper.insert(e);
				accepted.add(toVo(e));
				log.info("对话附件上传: id={}, name={}, kind={}, size={}, by={}", e.getId(), name, kind, content.length, uploaderId);
			}
			catch (Exception e) {
				// R-03：不留悬挂产物 —— 落盘成功但落库失败时回收文件
				if (path != null) {
					try {
						fileStorageService.deleteFile(path);
					}
					catch (Exception ignore) {
						log.warn("附件回收失败（落库异常后）: path={}", path);
					}
				}
				log.warn("附件上传失败: name={}, reason={}", name, e.getMessage());
				rejected.add(new ChatAttachmentUploadResultVO.Rejected(name, "上传失败：" + e.getMessage()));
			}
		}
		return new ChatAttachmentUploadResultVO(accepted, rejected);
	}

	private ChatAttachmentVO toVo(ChatAttachment e) {
		return ChatAttachmentVO.builder()
			.id(e.getId())
			.fileName(e.getFileName())
			.kind(e.getKind())
			.ext(e.getExt())
			.sizeBytes(e.getSizeBytes())
			// 刻意给受鉴权端点而非存储直链（R-11 / L-58：可见性与归属校验成对）
			.url("/api/chat/attachment/" + e.getId())
			.build();
	}

	private String extOf(String fileName) {
		int i = fileName.lastIndexOf('.');
		if (i < 0 || i == fileName.length() - 1) {
			return "";
		}
		return fileName.substring(i + 1).toLowerCase(Locale.ROOT);
	}

	private String sizeMsg() {
		return "文件超过大小上限 " + (MAX_FILE_BYTES / 1024 / 1024) + "MB";
	}

}
