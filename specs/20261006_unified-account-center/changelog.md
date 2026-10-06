# Changelog: unified-account-center

## v0.1.0（2026-10-06）
- 挂载: v1.7.0（2026-10-06，用户口令「还是1.7.0」；不新开版本）
- 初始化创建（requirements/plan/tasks 草稿）
- 同轮纳入 **BUG-86**（P0 跨库读）——用户口令「bug 86 一起做」；落位（本 spec 需求组 vs 独立 fix spec）见 requirements「待确认问题 1」
- 背景口令原文：「统一账号中心（消除双账号体系），还是1.7.0，bug 86一起做」
- 用户裁定（2026-10-06）：① BUG-86 作为**本 spec 的一个需求组**（不另立 spec）；② 统一终态=**一套账号 + 保留两个登录入口**
- **Phase 1 取证完成**（只读调研，未改任何源码）：产出双账号体系事实清单——两套主表零外键互联（`tbl_privilege_user` / `tbl_platform_account_info`，自然人连接点 `tbl_privilege_employee.employee_id`）、约 12 个认证端点两套并存（均 sa-token / `phoenix-token`）、**8 类列混存两套 id**（`user_id`/`account_id`/`admin_id`/`creator`）、前端 **3 个 app**（admin-ui/pc-ui/mobile-ui）受影响、三条统一路线影响面
- requirements 写入 EARS 条款：**A 组 R-01~R-10**（身份源/认证/token/两入口一致性/凭据单点生效/权限组织承载/登出/用户信息/放行面收敛/调用面一致/无损迁移回滚）＋ **B 组 R-20~R-21**（BUG-86 隔离与可举证）；含 Non-goals、假设 7 条、待确认问题 3 条
- 调研发现既有缺陷 **6 条已登记**：BUG-94（P0 安全·认证绕过）/ BUG-95（前台改密明文致无法登录）/ BUG-96（前台登出无效）/ BUG-97（前台用户信息 null）/ BUG-98（前端调用不存在端点）/ BUG-99（硬编码 JWT 死代码）
- 状态：requirements 仍为 **v0.1.0 草稿**，等用户 ① 确认后升 v1.0.0
