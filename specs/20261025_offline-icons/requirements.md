> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-25 | 确认人: （待确认）

# 需求规格：图标与前端静态资源本地化（离线内网可用）

## 背景（实测结论）

客户环境为**内网（无外网）**，当前前端有两类资源在运行期**从公网加载**，内网会白图/报错：

| # | 现状 | 证据（2026-10-25 实测） |
|---|---|---|
| 1 | 图标按 Iconify 名称解析（`lucide:xxx` 等），运行时**向 `https://api.iconify.design` 拉取图标数据** | 构建产物 `dist/js/src-*.js`、`bootstrap-*.js` 内含 `https://api.iconify.design/collection?prefix=` 与 Iconify 加载器；代码里 **39 种 `lucide:` 图标**；icon-picker 默认前缀 `ant-design` 也会走同一 API |
| 2 | 生成的「报表 HTML」内嵌 **jsDelivr CDN** 的 marked / echarts | `components/run/charts/report-html-template.ts` 与 `views/front/components/report/report-html-template.ts` 首两行为 `https://cdn.jsdelivr.net/npm/marked@12.0.0/…`、`…/echarts@5.5.0/…` |

本地已有的离线集合（`node_modules` 内，无需联网）：`@iconify-json/logos`、`octicon`、`simple-icons`、`vscode-icons`；
**缺少** `lucide` 与 `ant-design` 两个集合。

## 需求条款（EARS）

- **R-01 图标零外网**：WHEN 页面渲染任意图标（菜单/按钮/树/空态/图标选择器）, 系统 SHALL **不发起任何外网请求**即可正确显示图标。
- **R-02 图标名称兼容**：WHEN 现有代码与数据库里已使用 `lucide:*` / `ant-design:*` 图标名, 系统 SHALL **保持这些名称可用**（不要求逐个改代码改库）。
- **R-03 构建产物自包含**：WHEN 构建前端产物并通过 nginx 对内提供服务, 系统 SHALL 使图标数据随产物一起发布（不依赖运行时下载）。
- **R-04 离线可验证**：WHEN 在**断网**环境（屏蔽外网域名）打开页面, 系统 SHALL 让菜单图标、按钮图标、图标选择器均正常显示，浏览器 Network 面板无 iconify 外网请求。
- **R-05 报表 HTML 离线可用**：WHEN 生成的报表 HTML 在离线环境打开, 系统 SHALL 能渲染 markdown 与图表（不再依赖 jsDelivr）。
- **R-06 包体可控**：WHEN 引入离线图标数据, 系统 SHALL 只包含**实际使用**的图标集合（或子集），不得盲目全量打包导致产物体积暴涨（给出体积变化实测）。
- **R-07 不回归**：WHEN 上述改动生效, 系统 SHALL 保持既有页面/菜单/按钮图标外观一致（同名同图），且不影响其它功能。

## 验收场景

**R-01/R-04**
- GIVEN 屏蔽外网（`api.iconify.design`、`cdn.jsdelivr.net` 不可达）, WHEN 打开登录页与后台菜单/列表页, THEN 所有图标正常显示，Network 无上述域名请求

**R-02**
- GIVEN 数据库菜单 `image='lucide:network'` 等, WHEN 打开侧边栏/菜单管理, THEN 图标正常显示（名称未改）

**R-05**
- GIVEN 断网, WHEN 打开一份生成的报表 HTML, THEN markdown 正常渲染、图表正常绘制

**R-06**
- GIVEN 打包后的 `dist` 体积, WHEN 与本 spec 之前对比, THEN 输出体积增量并说明取舍（例如 lucide 全量 vs 仅用到的 39 个）

## 非目标（本 spec 不做）

- 不改图标库选型（仍用 Iconify 名称 + Lucide/Ant Design 图形）
- 不做图标 CDN/私有对象存储方案
- 不改后端菜单数据结构
- 不处理 PDF/字体等其它资源（如后续发现再立项）

## 我正在做的假设

1. 采用 **构建期打包离线集合**：新增 `@iconify-json/lucide`（必做）与 `@iconify-json/ant-design`（图标选择器需要），
   在应用启动处 `addCollection()` 注册；**不引入运行时 API 请求**。
2. 报表 HTML 的 marked/echarts 改为**随产物发布的本地静态文件**（拷贝到 `dist/js/vendor/`），HTML 内引用相对路径。
3. 允许 `package.json`/`pnpm-lock.yaml` 变更（新增两个 `@iconify-json/*` 依赖，packages 体积计入包体）。
4. 若 lucide 全量过大，则只打包**用到的 39 个**（脚本生成子集 JSON），R-06 要求给出体积实测。

现在纠正，否则按此执行。

## 待确认问题

- **Q1 版本归属**：挂 v2.1.0 还是新开 v2.2.0？
- **Q2 范围**：只做「图标本地化」（R-01~R-04、R-06）？还是**同时**做报表 HTML 的 CDN 资源本地化（R-05）？
- **Q3 方案**：构建期打包离线集合（推荐，简单可靠）还是自托管 Iconify API（nginx 提供 JSON，多一层服务）？
- **Q4 取舍**：lucide 打包**全量集合**（简单、体积大）还是**仅用到的 39 个**（省体积、需生成脚本）？
