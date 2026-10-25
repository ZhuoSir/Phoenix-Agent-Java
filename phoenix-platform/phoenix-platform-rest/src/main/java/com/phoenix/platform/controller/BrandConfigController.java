package com.phoenix.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.data.component.AdminRoleGuard;
import com.phoenix.platform.dto.brand.BrandConfigUpdateDTO;
import com.phoenix.platform.service.brand.BrandAssetService;
import com.phoenix.platform.service.brand.BrandConfigService;
import com.phoenix.platform.vo.brand.BrandConfigVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.publisher.Mono;

/**
 * 品牌外观配置（brand-configuration T-02）。
 *
 * <p>**GET 为匿名接口**（登录页必须先拿到品牌）：只返回 4 个公开字段，不含审计信息。
 * 写在 Sa-Token 白名单里的路径级排除对 PUT 同样生效 ⇒ T-03 的写接口必须在方法内自行校验超管。
 */
@RestController
@RequestMapping("/platform/brand-config")
@RequiredArgsConstructor
public class BrandConfigController {

	private final BrandConfigService brandConfigService;

	private final AdminRoleGuard adminRoleGuard;

	private final BrandAssetService brandAssetService;

	@GetMapping
	public ReturnVo<BrandConfigVO> get() {
		return ReturnVo.ok(brandConfigService.getPublic());
	}

	/**
	 * 更新品牌配置（T-03）。
	 *
	 * <p>该路径在 Sa-Token 白名单里（GET 需匿名）⇒ 这里**必须自行校验**：已登录 + 超管。
	 */
	@PutMapping
	public ReturnVo<BrandConfigVO> update(@RequestBody BrandConfigUpdateDTO dto,
			@RequestHeader(value = "phoenix-token", required = false) String token) {
		String userId = resolveUserId(token);
		if (userId == null) {
			return ReturnVo.fail("未登录或登录已失效");
		}
		if (!adminRoleGuard.isAdmin(userId)) {
			return ReturnVo.fail("仅超级管理员可修改品牌配置");
		}
		try {
			return ReturnVo.ok(brandConfigService.update(dto, userId));
		}
		catch (IllegalArgumentException e) {
			return ReturnVo.fail(e.getMessage());
		}
	}

	/** 品牌图上传（T-04）：仅超管；2MB；png/jpg/jpeg/webp/ico；不开放 SVG */
	@PostMapping("/asset")
	public Mono<ReturnVo<String>> uploadAsset(@RequestPart("file") FilePart file,
			@RequestHeader(value = "phoenix-token", required = false) String token) {
		// 注意：大文件 multipart 下 Sa-Token 的 Reactor 上下文可能未初始化 ⇒ 用 token 显式解析
		String userId = resolveUserId(token);
		if (userId == null) {
			return Mono.just(ReturnVo.<String>fail("未登录或登录已失效"));
		}
		if (!adminRoleGuard.isAdmin(userId)) {
			return Mono.just(ReturnVo.<String>fail("仅超级管理员可修改品牌配置"));
		}
		return brandAssetService.upload(file, userId)
			.<ReturnVo<String>>map(url -> ReturnVo.ok("上传成功", url))
			.onErrorResume(IllegalArgumentException.class,
					e -> Mono.just(ReturnVo.<String>fail(e.getMessage())));
	}

	/** 由 token 显式解析登录账号（不依赖 Sa-Token Reactor 上下文） */
	private String resolveUserId(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		try {
			Object id = StpUtil.getLoginIdByToken(token);
			return id == null ? null : id.toString();
		}
		catch (Exception e) {
			org.slf4j.LoggerFactory.getLogger(BrandConfigController.class)
				.warn("[brand] token 解析失败: {}", e.toString());
			return null;
		}
	}
}
