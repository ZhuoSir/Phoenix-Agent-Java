package com.phoenix.platform.dto.brand;

import lombok.Data;

/**
 * 品牌配置更新入参（brand-configuration T-03）。
 */
@Data
public class BrandConfigUpdateDTO {

	/** 系统名称（1~40 字） */
	private String systemName;

	/** Logo URL（相对路径或 http(s)） */
	private String logoUrl;

	/** favicon URL（相对路径或 http(s)） */
	private String faviconUrl;

	/** 主题主色 #RRGGBB */
	private String primaryColor;
}
