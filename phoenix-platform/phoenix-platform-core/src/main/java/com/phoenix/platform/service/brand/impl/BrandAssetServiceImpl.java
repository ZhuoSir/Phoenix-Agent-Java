package com.phoenix.platform.service.brand.impl;

import com.phoenix.data.service.file.FileStorageService;
import com.phoenix.data.service.file.FileStorageServiceFactory;
import com.phoenix.platform.service.brand.BrandAssetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Locale;
import java.util.Set;

/**
 * 品牌图上传实现（T-04）。
 *
 * <p>白名单：png / jpg / jpeg / webp / ico —— **不开放 svg**（避免脚本型 XSS；需要矢量请自备 URL）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandAssetServiceImpl implements BrandAssetService {

	private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "webp", "ico");

	private final FileStorageServiceFactory fileStorageServiceFactory;

	@Value("${phoenix.platform.brand.asset-max-bytes:2097152}")
	private long assetMaxBytes = 2_097_152;

	@Override
	public Mono<String> upload(FilePart file, String operator) {
		if (file == null) {
			return Mono.error(new IllegalArgumentException("未收到文件"));
		}
		String name = file.filename() == null ? "" : file.filename();
		String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
		if (!ALLOWED_EXT.contains(ext)) {
			return Mono.error(new IllegalArgumentException("仅支持 png/jpg/jpeg/webp/ico（不支持 SVG，可改用外部 URL）"));
		}
		return file.content().reduce(0, (n, buf) -> n + buf.readableByteCount()).flatMap(size -> {
			if (size > assetMaxBytes) {
				return Mono.error(new IllegalArgumentException(
						"文件过大（" + size + " 字节），上限 " + assetMaxBytes + " 字节（2MB）"));
			}
			FileStorageService storage = fileStorageServiceFactory.getObject();
			// storeFile() 返回的是**磁盘相对路径**（data-agent/brand/xxx.png），不是可访问 URL。
			// 直接回给前端 ⇒ 浏览器按站内相对地址请求 /data-agent/... ⇒ 命中 nginx 的 SPA 兜底拿回 index.html
			// ⇒ 图标显示不出来（BUG-214，2026-10-31 用户现场）。与头像上传同构：再过一道 getFileUrl()
			// 换成 /uploads/** 下的访问地址（本地盘由静态资源处理器出图，OSS 模式返回其域名地址）。
			return storage.storeFile(file, "brand").map(path -> {
				String url = storage.getFileUrl(path);
				log.info("[brand] 上传 by={}, ext={}, bytes={}, path={}, url={}", operator, ext, size, path, url);
				return url;
			});
		});
	}
}
