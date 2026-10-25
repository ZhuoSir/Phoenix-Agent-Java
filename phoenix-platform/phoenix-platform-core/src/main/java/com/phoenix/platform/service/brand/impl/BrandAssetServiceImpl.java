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
			return storage.storeFile(file, "brand").doOnNext(url -> log.info(
					"[brand] 上传 by={}, ext={}, bytes={}, url={}", operator, ext, size, url));
		});
	}
}
