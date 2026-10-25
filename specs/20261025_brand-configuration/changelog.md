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
