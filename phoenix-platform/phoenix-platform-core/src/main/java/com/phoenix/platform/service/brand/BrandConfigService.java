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

	/** 清缓存（更新后调用）。 */
	void evictCache();
}
