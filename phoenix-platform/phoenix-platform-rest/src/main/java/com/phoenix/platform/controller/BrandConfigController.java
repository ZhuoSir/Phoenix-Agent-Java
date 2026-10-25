package com.phoenix.platform.controller;

import com.phoenix.platform.service.brand.BrandConfigService;
import com.phoenix.platform.vo.brand.BrandConfigVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

	@GetMapping
	public ReturnVo<BrandConfigVO> get() {
		return ReturnVo.ok(brandConfigService.getPublic());
	}
}
