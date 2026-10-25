package com.phoenix.platform.model.brand;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 品牌外观配置（单行表 tbl_platform_brand_config，id 固定 1）。
 *
 * <p>brand-configuration T-01/T-02；字段与 V2.1.0_04 迁移件一一对应。
 */
@Data
@Table("tbl_platform_brand_config")
public class BrandConfig {

	@Id
	private Integer id;

	/** 系统名称（登录页品牌区 / 侧边栏标题 / 浏览器标签统一使用） */
	private String systemName;

	/** Logo 图片 URL */
	private String logoUrl;

	/** favicon URL */
	private String faviconUrl;

	/** 主题主色 #RRGGBB（色阶由前端派生） */
	private String primaryColor;

	/** 最后修改人（审计） */
	private String updatedBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
