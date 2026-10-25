> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-25 | 确认人: 陈卓 | 确认日期: 2026-10-25

# 技术方案：图标与前端静态资源本地化（offline-icons）

## 一、坑核对（已核对 113 条 active 坑，相交 4 条）

| 坑 | 关系 | 做法 |
|---|---|---|
| **L-110** 构建通过≠运行可用 | 本次核心风险：构建成功但图标仍走 CDN | 验收必须**拦截外网域名**后实测（用 Playwright route 拦截 `api.iconify.design`/`cdn.jsdelivr.net`），并检查 0 个 404、0 个外网请求 |
| **L-113** 业务配置优先工程配置 | 图标集合属**资源**而非配置，走静态文件；仅把 provider 地址写死为本站相对路径 | 不新增 env/yml |
| **L-104** 字符串替换要断言命中 | 替换 2 个报表模板的 CDN URL | 替换后 `grep` 断言只剩 `/js/vendor/...`，且 dist 内 `cdn.jsdelivr.net` 命中 0 |
| **L-111** 静默失效 | nginx 的 SPA 兜底会把**缺失的 /icons/x.json 返回 index.html(200)**，表现为"图标静默空白" | nginx 增 `location /icons/`、`/js/vendor/` 的 `try_files $uri =404`，实测缺失文件返回 404 |

## 二、方案（本地静态资源，不做构建期内联）

```
构建机（一次性）                      运行期（客户内网）
@iconify-json/{lucide,ant-design,    →  public/icons/*.json  → nginx /icons/*.json
  ep,meteor-icons,fluent-mdl2}          直接用包内 icons.json        ↑ Iconify provider 指向 /icons
marked@12 / echarts@6 静态文件        →  public/js/vendor/*.js  → nginx /js/vendor/*
```

- 前端启动时 `addAPIProvider('', { resources: ['/icons'], ... })` 覆盖 Iconify 默认的公网 provider
- 图标名不变（仍 `lucide:xxx` / `ep:xxx`），数据由本站 JSON 提供
- 报表 HTML 模板两个常量改成本站 `/js/vendor/*.js`

## 三、改动面

| 文件 | 改动 |
|---|---|
| `apps/admin-ui/public/icons/*.json`（新，5 个：lucide/ant-design/ep/meteor-icons/fluent-mdl2） | 图标数据静态文件（共 2.3MB） |
| `apps/admin-ui/public/js/vendor/{marked.min.js,echarts.min.js}`（新） | 报表用 vendor（共 1.2MB） |
| `apps/admin-ui/src/bootstrap.ts` | `addAPIProvider('', { resources: ['/icons'] })` |
| `packages/@core/base/icons/src/index.ts` | 补导出 `addAPIProvider`（供应用层注入 provider） |
| `components/run/charts/report-html-template.ts`、`views/front/components/report/report-html-template.ts` | CDN → `/js/vendor/*` |
| `docker/nginx/phoenix.conf` | 新增 `location /icons/`、`/js/vendor/` 精确规则（`try_files $uri =404`，缺失不回落 index.html） |

## 四、接口/数据流

无后端改动。运行期请求：`GET /icons/lucide.json?icons=a,b,c` → nginx 静态返回整份 JSON（忽略 query）；Iconify 内部裁剪所需图标。

## 五、关键设计决策

1. **本地静态而非 JS 内联**（用户 Q3）：图标数据不进 bundle，产物只增加静态文件；换图标集合不必重新构建 JS。
2. **lucide 全量**（用户 Q4）：613KB，避免"用到新图标就要重新生成子集"的维护成本。
3. 图标前缀集合由**运行时爬取**确定：登录后遍历主要页面收集 `/icons/*.json` 请求，实际得到 lucide / meteor-icons / ep / fluent-mdl2（+ 图标选择器用 ant-design）。
4. nginx 对 `/icons/` 做**精确 404**，把"图标静默空白"变成"可见 404"，便于现场排障（L-111）。

## 六、配置项

无新增配置项（provider 地址为本站相对路径，写死在 bootstrap）。

## 七、共享面身份矩阵

| 共享对象 | 既有身份 | 变更后预期 |
|---|---|---|
| Iconify provider（默认 ''） | ① 菜单/按钮图标 ② 图标选择器（ant-design） ③ vben 内部 widget（ep/fluent-mdl2/meteor-icons） | 三处全部走 `/icons`；外网请求 0 |
| 报表 HTML 模板 | ① run 视图（后台对话）② front 报表面板 | 两处均引用 `/js/vendor/*`；离线可渲染 |
| nginx 静态规则 | 既有 `/js|css|assets` 长缓存、`/` SPA 兜底 | 新增两条精确规则，不改既有行为（首页/接口仍 200 实测） |

## 八、日志点

无后端日志新增。前端：图标加载失败由 Iconify 自身 console.warn（保留）；nginx 侧 404 可由 access log 观察。

## 九、被拒绝方案

| 方案 | 拒绝理由 |
|---|---|
| `addCollection` 把 JSON 打进 JS | 用户明确要求"改成本地的，不是构建期"；且换图标需重构建 |
| 自建 Iconify API 服务（Node/容器） | 多一个运行时服务，内网运维成本高；静态文件已足够 |
| 只打用到的 39 个图标子集 | 用户选全量；且新页面用新图标时子集需重新生成 |
| 保留 CDN + 客户自建反向代理 | 客户内网无外网，反代也需要外网出口 |

## 十、风险与规避

| 风险 | 规避 |
|---|---|
| 新页面用到未收集的前缀 → 图标空白 | nginx 精确 404 可暴露；本轮已按运行时爬取覆盖 4 个前缀 + 选择器 1 个 |
| 静态资源体积增加 3.5MB | 已在计划内（lucide 全量 613KB + fluent-mdl2 785KB 等）；R-06 已记录体积 |
| 图标 JSON 与前端 Iconify 版本不兼容 | 二者同为 Iconify 集合格式；实测 0 个 broken 图标 |
| nginx 改动影响既有路由 | 仅新增两条精确前缀规则；首页/接口/上传/SPA 回归均实测 |
