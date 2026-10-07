> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-07

# 技术方案：user-role-group-model

> ⚠️ **本文件当前为 Phase 0 初始化骨架**：Phase 1（requirements）尚未确认①，范围未冻结，此处不做方案设计。
> Phase 2 启动时按模板逐节填写，其中「坑核对」节必须核对 `specs/_project/lessons.md` 全部 active 坑
> （当前编号至 L-42）后成文——**届时沉默或留空即为未核对，确认②不放行**。

## 坑核对（Phase 2 填写，必填节）
待填。

## 方案概述
待填。

## 涉及模块与数据流
待填（已知涉及：`phoenix-privilege`、`phoenix-platform`、`phoenix-common`（同步策略）、`phoenix-admin-manager`、`web-frontend/apps/admin-ui`）。

## 接口设计
待填。

## 数据模型变更
待填（预期含 DDL：删 `tbl_privilege_{company,department,employee}` + `tbl_privilege_user` 组织列，含回滚配对）。

## 共享面身份矩阵（判据见 SKILL.md §3）
待填。**预判为共享面、Phase 2 必须逐身份评估的对象**（本清单由立项调研得出，非最终矩阵）：

- `tbl_privilege_user`（用户表，被登录/鉴权/平台同步/账号管理/前台 chat 多路读）
- `/organization/**` 路由与四条菜单记录（导航渲染 + 直接 URL 访问两身份）
- `tbl_privilege_user_role`（角色关联，新老用户均读）
- 平台同步入口 `/platform/sync` 与 4 个同步策略
- `tbl_platform_group_*` 四张资源关联表（组授权 → 前台可见性）

## 关键决策
待填（Phase 2 需给出「被拒绝的替代方案 + 拒绝理由」，至少覆盖：删表 vs 保留表仅下线 UI；一期 vs 分期；两套组是否合并）。

## 风险与规避
待填（预判风险：227 处 Java 引用与 198 处字段引用的编译面、NOT NULL 列约束解除顺序、存量数据不可逆、三方同步回归、前台可见性骤变导致「用户看不到任何智能体」）。

## 依赖与前置
待填（预判：Q1~Q6 六个待确认问题的答复；`tbl_privilege_acl` 预置数据现状核验；生产库存量账号与角色分布统计）。
