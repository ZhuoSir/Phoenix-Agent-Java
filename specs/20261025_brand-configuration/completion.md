> 版本: v1.0.0 | 状态: 完成（开发+自测，**待用户确认合并**） | 更新: 2026-10-25

# 完成清单：品牌外观配置化（brand-configuration，BL-66）

## 结论
**9/9 任务完成并自测**。功能已在 `feature/brand-configuration` 分支验证通过（含真栈与 Playwright 端到端），
**尚未合并回 `2.1.0`**（等用户口令）。

## 已完成
| 任务 | 内容 | 验证证据 |
|---|---|---|
| T-01 | 单行表 `tbl_platform_brand_config` + 默认行 + 约束（id=1 / 色值正则） | 六条断言：结构/默认值/幂等/单行约束拒绝/色值约束拒绝/回滚件可执行 |
| T-02 | 匿名 `GET /platform/brand-config`（4 公开字段 + 60s 缓存） | 匿名 200、无内部字段、带 token 一致、改库 60s 内旧值/65s 后新值 |
| T-03 | 超管 `PUT`（校验 + 审计 + 清缓存 + 方法内自校验） | 合法值落库 `by=…0001`；red / 41 字 / `javascript:` 均拒；**临时撤角色 → 拒绝且未落库**（角色已恢复） |
| T-04 | `POST /asset` 上传（2MB + 白名单 + 禁 SVG） | png 返回 URL；svg/exe 拒；3MB 提示上限；无 token 401 |
| T-05 | brand store + 启动注入（title/favicon/主色派生） | Playwright：默认 `#006BE6`；改配置仅刷新 → 名称/颜色/色阶全部变化；还原后回默认（R-05） |
| T-06 | 6 处硬编码替换 + 零残留 | `grep` 仅剩 `stores/brand.ts` 默认值常量 1 处 |
| T-07 | 「外观设置」页 + 菜单 | Playwright 自动登录：侧栏出现菜单 → 页面回填 → 改名改色保存 → 库内更新 → 刷新 title/主色生效 |
| T-08 | 端到端回归 | 登录/知识库/组列表/品牌/附件上限 全 200；对话 thinking 帧 **105**；7 个色阶变量全部派生 |
| T-09 | 发布物料 | `V2.1.0_04`（sql/ + releases 镜像 + rollback）+ `RELEASE-NOTES.md` 更新（附件 5→50、品牌能力、2 个新配置项、标题偏差） |

## 交付面
- 后端：`BrandConfig` 实体 / `BrandConfigVO` / `BrandConfigUpdateDTO` / Mapper / Service(+Impl) /
  `BrandAssetServiceImpl` / `BrandConfigController`（GET 匿名、PUT+上传超管）
- 前端：`api/core/brand-config.ts`、`stores/brand.ts`、`bootstrap` 注入、`views/system-management/brand-settings`
- 迁移：`sql/V2.1.0_04__brand_config.sql`（表 + 默认行 + 菜单行，幂等）+ rollback
- 配置：`phoenix.platform.brand.cache-seconds`、`phoenix.platform.brand.asset-max-bytes`（工程参数，符合 L-113）

## 已知偏差 / 限制
1. **浏览器标题**由旧版 `Phoenix Admin` 变为 `<系统名称> - 页面名`（R-01 三处统一的结果，已写入发布说明）
2. **普通管理员菜单不可见**未做端到端验证：菜单 ACL 依赖既有角色-模块授权体系；写接口越权已在 T-03 证明拒绝
3. 不开放 SVG 上传（防 XSS）；需要矢量请填外部 URL
4. 主题色只配主色，色阶自动派生（暗色模式不在本 spec）

## 下一步（等用户）
- **合并**：`git checkout 2.1.0 && git merge --no-ff feature/brand-configuration`（等口令）
- 合并后建议真栈再验一次登录页标题与「外观设置」入口
