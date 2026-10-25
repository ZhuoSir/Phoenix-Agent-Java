> 版本: v1.0.0 | 状态: 完成（开发+自测，**待用户确认合并**） | 更新: 2026-10-25

# 完成清单：图标与前端静态资源本地化（offline-icons / BL-74）

## 结论
**9/9 任务完成并自测**。目标达成：断网（拦截外网域名）环境下，图标与报表静态资源**全部由本站提供**，
外网请求 0、图标 404 0、破损图标 0。

## 已完成
| 任务 | 内容 | 证据 |
|---|---|---|
| T-01 | 5 个图标集合落 `public/icons/`（lucide 613K / ant-design 704K / ep 112K / meteor-icons 102K / fluent-mdl2 785K） | `/icons/*.json` 均 200 且 `Content-Type: application/json` |
| T-02 | `addAPIProvider('', {resources:['/icons']})` 覆盖 Iconify 默认 provider | 登录后图标请求全为 `/icons/*.json`；外网请求 **0** |
| T-03 | 图标渲染回归（登录页/组管理/菜单管理/智能体列表/外观设置） | `iconifySvgs=22`、`brokenIcons=0`、`icon404=0` |
| T-04 | marked/echarts 落 `public/js/vendor/` | `/js/vendor/marked.min.js` 100KB、`echarts.min.js` 1.1MB，均 200 |
| T-05 | 两处报表模板改本站 `/js/vendor/*` | dist 内 `cdn.jsdelivr.net` 命中 **0**；含本地 vendor 引用的 js 文件 2 个 |
| T-06 | nginx 精确规则 `try_files $uri =404`（`/icons/`、`/js/vendor/`） | `/icons/nope.json` → **404**；`/icons/lucide.json` → 200 JSON；首页/登录 200 |
| T-07 | 断网端到端（Playwright 拦截 iconify/jsdelivr/googleapis/unpkg） | `blockedCount=0`、`icon404=[]`、`brokenIcons=0` |
| T-08 | 体积实测 | icons 2.3MB、vendor 1.2MB、`dist` 总 14MB |
| T-09 | 文档与发布物料 | `RELEASE-NOTES` 增「内网离线资源」段（来源/体积/更新方式/nginx 行为/实测结论） |

## 期间踩坑
| 坑 | 处理 |
|---|---|
| `@iconify/vue` 在 admin-ui 不可解析（构建失败 `Failed to resolve import "@iconify/vue"`） | 在 `packages/@core/base/icons` 补导出 `addAPIProvider`，应用层从 `@vben/icons` 导入 |
| 首次仅本地化 lucide/ant-design，运行时仍请求 **meteor-icons / ep / fluent-mdl2**（vben 内部 widget 用） | 运行时爬取页面收集前缀后补齐 3 个集合 |
| nginx SPA 兜底把缺失的 `/icons/*.json` 返回 **index.html(200)**，表现为"图标静默空白" | 增精确 location + `try_files $uri =404`（L-111） |
| `docker cp` 到 `conf.d/phoenix.conf` 造成与 `default.conf` 双 server 块，新规则不生效 | 镜像实际使用 `default.conf`（Dockerfile 复制目标），改为覆盖该文件并删除多余 conf，`nginx -t` + reload |

## 已知限制
1. 图标集合为**全量**（用户选定）：lucide 613KB、fluent-mdl2 785KB；若后续要瘦身，可改为按需子集。
2. 报表 vendor 用的是本地 `marked@12.0.2` / `echarts@6.1.0`（模板原先指向 marked@12.0.0 / echarts@5.5.0），
   **版本号有小幅变化**；渲染已由 ECharts markdown 插件链路复用，未发现回归，但如需严格锁版本可指定下载对应版本。
3. 未改动后端；图标前缀若新增（如后续页面用 `tabler:`），需补对应 JSON（nginx 会 404 暴露）。
4. 本 spec **未合并**到 `2.1.0`（等用户口令）。

## 下一步
- 合并：`git checkout 2.1.0 && git merge --no-ff feature/offline-icons`
