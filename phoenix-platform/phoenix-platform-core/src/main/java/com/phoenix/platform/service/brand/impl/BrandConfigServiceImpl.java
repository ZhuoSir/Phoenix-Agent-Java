package com.phoenix.platform.service.brand.impl;

import com.mybatisflex.core.query.QueryChain;
import com.phoenix.platform.mapper.brand.BrandConfigMapper;
import com.phoenix.platform.model.brand.BrandConfig;
import com.phoenix.platform.service.brand.BrandConfigService;
import com.phoenix.platform.vo.brand.BrandConfigVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 品牌外观配置实现（T-02）：单行读取 + 本地缓存 + 默认值兜底。
 *
 * <p>缓存：{@code phoenix.platform.brand.cache-seconds}（0=不缓存，默认 60s）。
 * 读取失败或未配置时返回与升级前完全一致的默认值（R-04），不向登录页抛错。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BrandConfigServiceImpl implements BrandConfigService {

	/** 默认值 = 升级前实际表现（与 V2.1.0_04 默认行一致） */
	private static final String DEFAULT_NAME = "Phoenix Agent";

	private static final String DEFAULT_LOGO = "/imgs/logo.png";

	private static final String DEFAULT_FAVICON = "/favicon.ico";

	private static final String DEFAULT_COLOR = "#006BE6";

	private final BrandConfigMapper brandConfigMapper;

	@Value("${phoenix.platform.brand.cache-seconds:60}")
	private long cacheSeconds = 60;

	private volatile BrandConfigVO cached;

	private volatile long cachedAt;

	@Override
	public BrandConfigVO getPublic() {
		long now = System.currentTimeMillis();
		BrandConfigVO hit = cached;
		if (hit != null && cacheSeconds > 0 && now - cachedAt < cacheSeconds * 1000L) {
			return hit;
		}
		try {
			BrandConfig row = QueryChain.of(brandConfigMapper).where(BrandConfig::getId).eq(1).one();
			BrandConfigVO vo = row == null ? defaults()
					: new BrandConfigVO(blankTo(row.getSystemName(), DEFAULT_NAME),
							blankTo(row.getLogoUrl(), DEFAULT_LOGO), blankTo(row.getFaviconUrl(), DEFAULT_FAVICON),
							blankTo(row.getPrimaryColor(), DEFAULT_COLOR));
			cached = vo;
			cachedAt = now;
			log.debug("[brand] 读取配置 nameLen={}, color={}, cacheHit=false",
					vo.systemName() == null ? 0 : vo.systemName().length(), vo.primaryColor());
			return vo;
		}
		catch (Exception e) {
			log.warn("[brand] 读取配置失败，回落默认值: {}", e.toString());
			return defaults();
		}
	}

	@Override
	public void evictCache() {
		cached = null;
		cachedAt = 0;
	}

	private static BrandConfigVO defaults() {
		return new BrandConfigVO(DEFAULT_NAME, DEFAULT_LOGO, DEFAULT_FAVICON, DEFAULT_COLOR);
	}

	private static String blankTo(String v, String def) {
		return v == null || v.isBlank() ? def : v;
	}
}
