package com.phoenix.platform.vo.brand;

/**
 * 品牌公开信息（匿名可读，**只含 4 个公开字段**，不含审计字段）。
 *
 * <p>brand-configuration T-02 / R-01~R-03。
 */
public record BrandConfigVO(String systemName, String logoUrl, String faviconUrl, String primaryColor) {
}
