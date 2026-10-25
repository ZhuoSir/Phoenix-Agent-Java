# 变更记录：offline-icons（BL-74）

## 2026-10-25 创建 → 挂载 → 决策 → 确认①

- 来源：用户「现在的图标都是线上链接，客户内网无法访问，把这些图标走本地环境的方式」
- 挂载：**v2.1.0**
- 决策（用户）：Q1 挂 2.1.0；Q2 同时做图标与报表资源；Q3 **本地静态资源**（非构建期打包）；Q4 **lucide 全量**
- 确认①：用户「直接完成这个spec」→ requirements 定版 v1.0.0
- 审计事实：产物含 `https://api.iconify.design` 运行时加载器；代码 39 种 `lucide:`；报表模板内嵌 jsDelivr marked/echarts

## 2026-10-25 实施完成（9/9，断网实测通过）

- 图标集合本地化：`public/icons/*.json`（lucide/ant-design/ep/meteor-icons/fluent-mdl2，共 2.3MB）
- `bootstrap.ts` 注入 `addAPIProvider('', {resources:['/icons']})`；`@vben-core/icons` 补导出该 API
- 报表 vendor 本地化：`public/js/vendor/{marked,echarts}.min.js`（1.2MB），两处模板改 `/js/vendor/*`
- nginx 增 `/icons/`、`/js/vendor/` 精确规则（缺失 404，防 SPA 兜底静默）
- **断网实测**：拦截 `api.iconify.design`/`cdn.jsdelivr.net`/`fonts.googleapis`/`unpkg` → 外网请求 0、图标 404 0、破损图标 0
- 踩坑：`@iconify/vue` 不可解析→改从 `@vben/icons` 导出；漏 ep/meteor-icons/fluent-mdl2→爬取补齐；
  nginx 双 server 块导致规则不生效→覆盖 `default.conf`
