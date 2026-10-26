# 升级件登记：brand-configuration

> 版本: v2.1.0 | 更新: 2026-10-25

| 类型 | 文件/键 | 说明 | 回滚 |
|---|---|---|---|
| SQL DDL + DML | `sql/V2.1.0_04__brand_config.sql` | 建单行表 `tbl_platform_brand_config`（`CHECK(id=1)` + 色值正则）+ 幂等默认行 + 幂等插入菜单行（外观设置） | `sql/rollback/V2.1.0_04__brand_config_rollback.sql`（DROP TABLE） |
| 配置 | `phoenix.platform.brand.cache-seconds` | 品牌公开信息缓存秒数（默认 60，0=不缓存） | 配置项，改回/删除即可 |
| 配置 | `phoenix.platform.brand.asset-max-bytes` | 品牌图上传上限（默认 2097152=2MB） | 同上 |

**默认值**（= 升级前表现）：`Phoenix Agent` / `/imgs/logo.png` / `/favicon.ico` / `#006BE6`。
**已知偏差**：浏览器标签标题由旧版 `Phoenix Admin` 变为 `<系统名称> - 页面名`（R-01 三处统一的结果）。
**回滚**：先执行 rollback SQL 删表；菜单行随表无关联（如需彻底回退，另 DELETE `tbl_privilege_module` 中 `id='b7a1c2d3e4f5061728394a5b6c7d8e9f'`）。
