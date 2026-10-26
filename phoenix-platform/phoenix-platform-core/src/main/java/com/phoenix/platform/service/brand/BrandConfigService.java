package com.phoenix.platform.service.brand;

import com.phoenix.platform.vo.brand.BrandConfigVO;

/**
 * 品牌外观配置服务（brand-configuration）。
 */
public interface BrandConfigService {

	/**
	 * 读取公开品牌信息（带本地缓存；未配置/异常时返回默认值，绝不抛给登录页）。
	 */
	BrandConfigVO getPublic();

	/**
	 * 更新品牌配置（T-03）：校验 + 落库 + 审计 + 清缓存。
	 * @param dto 入参
	 * @param operator 操作人账号 id
	 * @return 更新后的公开信息
	 * @throws IllegalArgumentException 校验失败（消息即用户可见文案）
	 */
	com.phoenix.platform.vo.brand.BrandConfigVO update(
			com.phoenix.platform.dto.brand.BrandConfigUpdateDTO dto, String operator);

	/** 清缓存（更新后调用）。 */
	void evictCache();
}
