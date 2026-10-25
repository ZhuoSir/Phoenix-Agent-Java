> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-25 | 确认人: 陈卓 | 确认日期: 2026-10-25

# 任务清单：图标与前端静态资源本地化（offline-icons）

## 组 1 · 图标本地化

- [x] T-01 图标集合落静态目录（lucide/ant-design/ep/meteor-icons/fluent-mdl2）
  关联: R-01, R-02, R-03
  验证方式: `public/icons/*.json` 存在且内容为 Iconify 集合格式（含 `prefix`/`icons`）；
    构建后 `dist/icons/` 同步；nginx `GET /icons/lucide.json` 返回 `application/json` 200。
  验收标准: 5 个集合文件可访问且 Content-Type 正确。

- [x] T-02 Iconify provider 指向本站 `/icons`
  关联: R-01, R-02
  验证方式: `bootstrap.ts` 调用 `addAPIProvider('', { resources: ['/icons'] })`；
    登录后 Network 中图标请求全部为 `/icons/*.json`，**0 个** `api.iconify.design`。
  验收标准: 外网请求 0；图标正常渲染。

- [x] T-03 图标渲染回归（菜单/按钮/图标选择器）
  关联: R-02, R-07
  验证方式: 拦截外网后遍历 登录页/组管理/菜单管理/智能体列表/外观设置：iconify svg 数量 > 0、
    **empty-svg（broken）= 0**、`/icons/` 请求 404 数 = 0。
  验收标准: 三个数字达标（实测 iconifySvgs=22、broken=0、404=0）。

## 组 2 · 报表静态资源本地化

- [x] T-04 marked/echarts 落 `public/js/vendor/`
  关联: R-05
  验证方式: 两个文件存在且可访问（`/js/vendor/marked.min.js` 100KB、`echarts.min.js` 1.1MB，均 200）。
  验收标准: 两文件 200 且体积合理。

- [x] T-05 报表模板改本站路径
  关联: R-05
  验证方式: 两处 `report-html-template.ts` 常量改为 `/js/vendor/*`；`grep cdn.jsdelivr.net` 在 dist 中命中 **0**。
  验收标准: 替换断言 + dist 零 CDN 命中。

## 组 3 · 发布与验证

- [x] T-06 nginx 精确规则（缺失 404，防 SPA 兜底静默）
  关联: R-03, R-04
  验证方式: `/icons/不存在.json` 返回 **404**（不是 index.html）；`/icons/lucide.json` 200 JSON；
    首页/登录接口仍 200（无回归）。
  验收标准: 三条全部实测。

- [x] T-07 断网端到端验证
  关联: R-01, R-04
  验证方式: Playwright 拦截 `api.iconify.design`/`cdn.jsdelivr.net`/`fonts.googleapis`/`unpkg` →
    登录并遍历 4 个页面 → `blockedCount=0`、`icon404=[]`、`brokenIcons=0`。
  验收标准: 三项达标（实测 0 / [] / 0）。

- [x] T-08 体积实测与记录
  关联: R-06
  验证方式: `du -sh public/icons`（2.3MB）、`public/js/vendor`（1.2MB）、`dist` 总 14MB；写入 completion。
  验收标准: 数字入档。

- [x] T-09 文档与发布物料
  关联: R-05, R-07
  验证方式: RELEASE-NOTES 增加"内网离线资源"说明（图标集合与 vendor 的来源/体积/更新方式）；
    completion.md 汇总证据。
  验收标准: 两文档齐备。
