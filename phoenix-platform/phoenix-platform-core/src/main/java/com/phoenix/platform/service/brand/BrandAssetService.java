package com.phoenix.platform.service.brand;

import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * 品牌图上传（brand-configuration T-04）：2MB 上限 + 类型白名单 + 不开放 SVG。
 */
public interface BrandAssetService {

	/**
	 * 上传品牌图（Logo / favicon）。
	 * @param file 上传文件
	 * @param operator 操作人（审计）
	 * @return 可访问 URL
	 * @throws IllegalArgumentException 校验失败（文案可直接展示）
	 */
	Mono<String> upload(FilePart file, String operator);
}
