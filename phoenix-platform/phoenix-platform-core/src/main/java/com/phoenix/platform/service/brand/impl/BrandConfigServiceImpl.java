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
							toAccessUrl(blankTo(row.getLogoUrl(), DEFAULT_LOGO)),
							toAccessUrl(blankTo(row.getFaviconUrl(), DEFAULT_FAVICON)),
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
	@org.springframework.transaction.annotation.Transactional
	public BrandConfigVO update(com.phoenix.platform.dto.brand.BrandConfigUpdateDTO dto, String operator) {
		String name = dto.getSystemName() == null ? "" : dto.getSystemName().trim();
		if (name.isEmpty() || name.length() > 40) {
			throw new IllegalArgumentException("系统名称长度需在 1~40 字");
		}
		String color = dto.getPrimaryColor() == null ? "" : dto.getPrimaryColor().trim();
		if (!color.matches("^#[0-9a-fA-F]{6}$")) {
			throw new IllegalArgumentException("主题色需为 #RRGGBB 格式（例如 #006BE6）");
		}
		String logo = normalizeUrl(dto.getLogoUrl(), "Logo");
		String favicon = normalizeUrl(dto.getFaviconUrl(), "favicon");

		BrandConfig row = QueryChain.of(brandConfigMapper).where(BrandConfig::getId).eq(1).one();
		boolean insert = row == null;
		if (insert) {
			row = new BrandConfig();
			row.setId(1);
			row.setCreateTime(java.time.LocalDateTime.now());
		}
		row.setSystemName(name);
		row.setPrimaryColor(color);
		row.setLogoUrl(logo);
		row.setFaviconUrl(favicon);
		row.setUpdatedBy(operator);
		row.setUpdateTime(java.time.LocalDateTime.now());
		if (insert) {
			brandConfigMapper.insert(row);
		}
		else {
			brandConfigMapper.update(row);
		}
		evictCache();
		log.info("[brand] 更新 by={}, systemName={}, primaryColor={}, logoSet={}, faviconSet={}", operator, name, color,
				logo != null, favicon != null);
		return new BrandConfigVO(name, logo == null ? DEFAULT_LOGO : logo, favicon == null ? DEFAULT_FAVICON : favicon,
				color);
	}

	/** URL 规范化：空 ⇒ null（沿用默认）；相对路径必须以 / 开头；http(s) 放行 */
	private static String normalizeUrl(String raw, String label) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String v = toAccessUrl(raw);
		if (v.length() > 512) {
			throw new IllegalArgumentException(label + " 地址过长（需 ≤512 字符）");
		}
		if (!v.startsWith("/") && !v.startsWith("http://") && !v.startsWith("https://")) {
			throw new IllegalArgumentException(label + " 需为 / 开头的相对路径或 http(s) 地址");
		}
		return v;
	}

	/**
	 * 兜底纠正「存储相对路径」形态（BUG-214）：旧版上传接口曾把 {@code data-agent/brand/xxx.png}
	 * 直接当 URL 回给前端。这种值既显示不出来（浏览器按站内相对地址请求 ⇒ 被 SPA 兜底成 index.html），
	 * 也过不了本类的校验。命中该形态时补上访问前缀 {@code /uploads/}；
	 * 已是站内绝对路径或 http(s) 地址的原样返回。
	 */
	private static String toAccessUrl(String raw) {
		String v = raw.trim();
		if (v.startsWith("/") || v.startsWith("http://") || v.startsWith("https://")) {
			return v;
		}
		if (v.matches("^[A-Za-z0-9._-]+(/[A-Za-z0-9._-]+)+\\.[A-Za-z0-9]{1,8}$")) {
			log.info("[brand] 检测到存储相对路径，兜底补访问前缀 /uploads/: {}", v);
			return "/uploads/" + v;
		}
		return v;
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
