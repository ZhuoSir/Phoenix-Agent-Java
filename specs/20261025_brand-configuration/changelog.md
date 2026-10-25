# 变更记录：brand-configuration（BL-66）

## 2026-10-25 v0.1.0 → v0.2.0 创建 + 挂载

- 来源：用户 2026-10-25「BL-66，先把这个做了」
- 挂载: **v2.1.0**（Q1 用户确认）
- 清点硬编码 6 处；条款 R-01~R-08；非目标 5 项
- 决策（用户 2026-10-25）：Q2=**DB+管理页**、Q3=**包含文案联动**、Q4=**只配主色**、Q5=**支持上传**
- 待确认①：requirements 定版 v1.0.0

## 2026-10-25 requirements v1.0.0 确认①

- 用户「确认」→ 定版 v1.0.0（陈卓 2026-10-25）
- 决策：挂 v2.1.0；DB+管理页；含文案联动；只配主色；支持上传

## 2026-10-25 plan v1.0.0 确认②

- 用户「BL-66 确认」→ plan 定版 v1.0.0（陈卓 2026-10-25）
- 方案要点：单行表 + 匿名 GET（4 公开字段）+ 超管 PUT/上传 + 前端启动拉取注入 CSS 变量
- 坑核对 112 条相交 5 条（L-113 例外理由已记录）；共享面 5 行；日志 5 条；被拒方案 5 个；风险 5 条

## 2026-10-25 tasks v1.0.0 确认③ + 建施工分支

- 用户「确认」→ tasks 定版 v1.0.0（陈卓 2026-10-25）
- 三重确认自检：requirements v1.0.0 / plan v1.0.0 / tasks v1.0.0 均「已确认」且确认人非空
- 施工分支：`feature/brand-configuration`（自 `2.1.0`）

## 2026-10-25 T-01 完成（建表 + 默认值，真库断言全过）

- `sql/V2.1.0_04__brand_config.sql`（+ rollback + `releases/v2.1.0/sql/` 镜像）：单行表 `tbl_platform_brand_config`
  （`CHECK (id=1)` 单行约束 + `CHECK (primary_color ~ '^#[0-9a-fA-F]{6}$')` 色值约束）+ 幂等默认行
- **默认值（= 升级前实际表现）**：`Phoenix Agent` / `/imgs/logo.png` / `/favicon.ico` / `#006BE6`
  （`#006BE6` 等价于现状 vben 默认 `hsl(212 100% 45%)`）
- **实测断言**：① 表结构 8 列符合预期 ② 默认行值逐字正确 ③ 重跑幂等（`INSERT 0 0`，行数仍 1）
  ④ 插入 id=2 被 `ck_brand_config_single_row` 拒绝 ⑤ `primary_color='red'` 被 `ck_brand_config_color` 拒绝
  ⑥ 回滚件执行后表消失、重新应用后恢复（行数 1）
- **已知偏差（已写入迁移件注释与升级说明待办）**：浏览器标签标题将从现状 `Phoenix Admin`
  变为 `<system_name> - 页面名`（R-01「三处统一」的必然结果，非缺陷）

## 2026-10-25 T-02 完成（匿名 GET + 60s 缓存，实测）

- 新增：`BrandConfig`（实体）/`BrandConfigVO`（record，仅 4 公开字段）/`BrandConfigMapper`/
  `BrandConfigService(+Impl)`/`BrandConfigController`；`application.yml` 增 `phoenix.platform.brand.cache-seconds: 60`
- Sa-Token 白名单增 `/platform/brand-config`（登录页渲染前需要）；**注意**：路径级排除对 PUT 同样生效
  ⇒ T-03 的写接口必须在方法内自校验超管（已写入代码注释）
- **实测**：
  ① 匿名 `GET /platform/brand-config` → `{systemName:Phoenix Agent, logoUrl:/imgs/logo.png, faviconUrl:/favicon.ico, primaryColor:#006BE6}` ✅
  ② 响应**不含** updatedBy/createTime/updateTime ✅
  ③ 带 token 结果与匿名一致 ✅
  ④ 缓存行为：改库为 `#123456` 后 60s 内接口仍返回 `#006BE6`；65s 后返回 `#123456`；还原后回 `#006BE6` ✅
- 踩坑：把 `phoenix:` 块**追加**到 application.yml 产生重复顶层键 ⇒ SnakeYAML `DuplicateKeyException`，后端启动失败（502）；
  已合并为单块。**教训：改 yml 后必须看启动日志，不能只看编译通过**

## 2026-10-25 T-03 / T-04 完成（写接口 + 上传，实测含越权）

**T-03 超管更新**
- `PUT /platform/brand-config`：**token 显式解析** + `AdminRoleGuard` 校验超管 + 名称/色值/URL 校验 + 审计 + 清缓存
- 实测：①超管合法值 → 200 且库更新（`by=1000000000000000001`）②GET 立即反映新值（缓存已清）
  ③`primaryColor=red` → 「主题色需为 #RRGGBB 格式」④名称 41 字 → 「系统名称长度需在 1~40 字」
  ⑤`logoUrl=javascript:...` → 「需为 / 开头或 http(s)」⑥**临时移除 admin 角色后 PUT → 「仅超级管理员可修改品牌配置」且未落库**，
  随后角色已恢复（3 行）

**T-04 品牌图上传**
- `POST /platform/brand-config/asset`：2MB（`phoenix.platform.brand.asset-max-bytes`）+ 白名单 png/jpg/jpeg/webp/ico + **不开放 SVG**
- 实测：png → 返回 `data=URL`；svg/exe → 拒绝；3MB → 「文件过大（3145732 字节），上限 2097152 字节」；无 token → 401
- **踩坑**：大文件 multipart 下 `StpUtil.getLoginIdAsString()` 抛 `SaTokenContextException`（Reactor 上下文未初始化）
  ⇒ 改为 `StpUtil.getLoginIdByToken(token)` 显式解析，PUT 与上传统一
- **踩坑**：`ReturnVo.ok(url)` 命中了 `ok(String msg)` 重载 ⇒ URL 落到 msg、data 为 null ⇒ 改为 `ok("上传成功", url)`

## 2026-10-25 T-05 / T-06 完成（前端注入 + 硬编码替换，Playwright 实测）

**T-05 品牌 store + 启动注入**
- 新增 `api/core/brand-config.ts`（GET/PUT/上传）、`stores/brand.ts`（load/apply + 默认回落 + 3s 超时）
- `bootstrap.ts` 挂载前 `await loadBrand()`，失败仅 console.warn（不阻塞）
- 注入内容：`document.title`、`link[rel=icon]`、`--el-color-primary` 及 `light-3/5/7/8/9`、`dark-2`
- **Playwright 实测（登录页）**：
  · 默认：`title=Phoenix Agent - Phoenix Admin`、`--el-color-primary=#006BE6`、`light-3=rgb(77,151,238)`
  · 后端 PUT 改为「测试品牌B / #1F6FEB」后**仅刷新页面**（未重建、未重启）：`title=测试品牌B - Phoenix Admin`、
    `--el-color-primary=#1F6FEB`、`light-3=rgb(98,154,241)`、品牌文案变为测试品牌B ✅
  · 还原后再刷新回到默认值 ✅（R-05 达成）

**T-06 硬编码替换 + 零残留**
- 替换点：`BrandPanel.vue`（logo/名称/版权）、`PolicyDialog.vue`、`privacy-agreement.vue`、`terms-of-service.vue`、
  `index.html` meta keywords
- `grep -rn "Phoenix Agent|Phoenix Admin" apps/admin-ui/src apps/admin-ui/index.html` → **仅剩默认值常量 1 处**（`stores/brand.ts`），其余 0 ✅

## 2026-10-25 T-07 / T-08 / T-09 完成

**T-07 外观设置页**（`/system-management/brand-settings`）
- 页面：名称（≤40）/ 主色（color picker + 文本）/ Logo 与 favicon 上传 / 预览 / 保存（失败必弹错）
- 菜单：`V2.1.0_04` 迁移件幂等插入 `tbl_privilege_module` 行（pid=系统管理，component 指向新页）
- **实测（Playwright 自动登录 + 滑块）**：侧边栏出现「外观设置」→ 页面渲染（名称回填 `Phoenix Agent`、色值 `#006be6`）
  → 改为「××标准院智能平台 / #0AA06E」保存 → 库内 `system_name=××标准院智能平台, primary_color=#0aa06e, by=1000000000000000001`
  → 刷新后 `title=外观设置 - ××标准院智能平台`、`--el-color-primary=#0aa06e`；随后已还原默认
- **未自测项（如实）**：普通管理员「看不到菜单」未做端到端（菜单 ACL 由既有角色-模块授权体系控制；写接口越权已在 T-03 验证为拒绝）

**T-08 端到端回归（五链路 + 色阶）**
- 登录 200 / 知识库列表 200 / 组列表 200 / 品牌匿名 200 / 附件上限 `{maxCount:50}` / 对话 thinking 帧 **105** + 回答 43 字
- 派生色阶（页面计算值）：`primary=#006BE6`、`light-3=rgb(77,151,238)`、`light-5=rgb(128,181,243)`、
  `light-7=rgb(179,211,248)`、`light-8=rgb(204,225,250)`、`light-9=rgb(230,240,253)`、`dark-2=rgb(0,86,184)` ✅

**T-09 发布物料**
- `sql/V2.1.0_04__brand_config.sql` + `releases/v2.1.0/sql/` 镜像 + `rollback/`
- `RELEASE-NOTES.md` 更新：附件上限 5→**50**、品牌配置化能力、新增 2 个工程配置项、`V2.1.0_04` 升级件与回滚件、
  以及「浏览器标题由 Phoenix Admin 变为 <系统名称> - 页面」的已知偏差

## 踩坑记录（本轮）
- YAML 追加第二个 `phoenix:` 顶层键 → SnakeYAML `DuplicateKeyException` → 后端启动失败（已改为单块）
- 大文件 multipart 下 `StpUtil.getLoginIdAsString()` 抛 `SaTokenContextException` → 改 token 显式解析
- `ReturnVo.ok(url)` 命中 `ok(String msg)` 重载 → URL 落到 msg（改 `ok("上传成功", url)`）
- 路由切换会覆盖 `document.title` ⇒ `applyBrand` 同步 `preferences.app.name`（否则标题不含品牌名）

## 2026-10-25 收尾（completion.md）

- 9/9 完成并自测；completion.md 已生成
- **未合并**（等用户口令）；合并后建议再验一次登录页标题与设置入口
