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
## v1.0.0（2026-10-06）确认人: 陈卓
- **① 确认通过** → requirements **v1.0.0 已确认**
- 确认时四项裁定（D1~D4）：照写确认；**会话不落 Redis**（BUG-100 出范围）；**接受两期**（一期=R-01~R-04/R-06~R-10+B 组；**R-05 移二期**）；**BUG-94~BUG-99 全部随本 spec 一并修**
- 门控：requirements 已确认，进入 Phase 2（plan）
- **BUG-94 实测复核（诚实性更正）**：未带凭据调用 `POST /api/privilege/auth/doLogin?username=zhang&password=123456` → HTTP 200 `登录成功`（服务端确执行 `StpUtil.login(10001)`），但响应**无 `Set-Cookie`/token 头** ⇒ 未证实"外部可直接取得 token"，定级由 **P0 更正为 P1（生产残留演示桩）**；要求=删端点或移出放行名单
- **新发现并登记 BUG-100**：sa-token 会话**未落 Redis**（`phoenix-platform-core/pom.xml:40` 有依赖、全仓无 `SaTokenDao` 装配、实测 Redis `dbsize=0`）⇒ 会话在进程内存，**后端重启即全员掉线**；已据此收窄"待确认问题 1"（强制重登影响面比预期小），并新增假设 6（是否落 Redis 由用户定，默认不在范围）
