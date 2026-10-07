# 交付物清单 · 20261007_user-role-group-model（v2.0.0）

> 统计日期: 2026-10-07 | 与 `git log` footer（`Task: T-01~T-17`）可交叉核对

## 一、升级件 SQL（`sql/`）—— 正向 8 件 / 回滚 8 件（**件数配对**）

| 序号 | 正向件 | 回滚件 | 类型 | 内容 |
|---|---|---|---|---|
| 01 | `V2.0.0_01__org_dimension_drop_ddl.sql` | `rollback/V2.0.0_01__org_dimension_drop_ddl_rollback.sql` | DDL | DROP 组织 3 表 + `tbl_platform_platform_info`；DROP 9 列（含解除 `user.company_id/dept_id` NOT NULL）。回滚自包含：结构 + 9 条列值 UPDATE + 内联 T-01 COPY 数据 |
| 02 | `V2.0.0_02__role_backfill_dml.sql` | `rollback/V2.0.0_02__role_backfill_dml_rollback.sql` | DML | 存量用户补默认角色（`upper(sn)='COMMON'`） |
| 03 | `V2.0.0_03__org_menu_cleanup_dml.sql` | `rollback/V2.0.0_03__org_menu_cleanup_dml_rollback.sql` | DML | 删 4 条组织菜单 + 4 条 ACL（回滚含原始 8 行保真重建） |
| 04 | `V2.0.0_04__acl_baseline_rebuild_dml.sql` | `rollback/V2.0.0_04__acl_baseline_rebuild_dml_rollback.sql` | DML | ACL 基线重建（超管全量 + 普通 7），含 JSONB 备份表与脏行清理 |
| 08 | `V2.0.0_08__usertype_code_drop_ddl.sql` | `rollback/V2.0.0_08__usertype_code_drop_ddl_rollback.sql` | DDL/DML | R-14：用户类型/IDM 与工号整体下线（删 `code`/`user_type`/`it_user_id`/`it_user_name`/`user_no` 5 列；组归属 `account_name` 改用户名；自检为环境无关不变量） |
| 07 | `V2.0.0_07__account_prune_dml.sql` | `rollback/V2.0.0_07__account_prune_dml_rollback.sql` | DML | R-13：账号收敛为 admin + chenzhuo；删除 5 个账号及其账号域数据（角色绑定/登录日志/智能体绑定/聊天会话与消息/向量记忆）（自检为环境无关不变量） |
| 06 | `V2.0.0_06__menu_merge_system_management_dml.sql` | `rollback/V2.0.0_06__menu_merge_system_management_dml_rollback.sql` | DML | R-12：权限管理→系统管理（原地更名，id 不变）+ 组管理迁入相邻 + 5 子菜单 URL 统一为 `/system-management/*` + 删前台「账号管理」与空目录「前台管理」及其 ACL（自检为**环境无关不变量**） |
| 05 | `V2.0.0_05__three_party_menu_cleanup_dml.sql` | `rollback/V2.0.0_05__three_party_menu_cleanup_dml_rollback.sql` | DML | 删「三方平台」+ 父目录「基础管理」2 行（回滚保真重建） |

**执行序**：01 → 02 → 03 → 04 → 05 → 06 → 07 → 08（幂等，可重跑；全新库重放与 drill 正反向均已演练）
**数据备份**：`backups/pre_v2.0.0_full_20261007_165618.sql`（迁移前全量）、`backups/pre_v2.0.0_orgdim_20261007_165618.sql`（组织维度专项）

## 二、基线文件（`sql/`）

| 文件 | 变更 |
|---|---|
| `sql/all_schema.sql` | 删 10 行（4 组织菜单 + 4 ACL + 三方平台/基础管理 2 行） |
| `sql/all_data.sql` | 删 10 行（同上）；并**修 BUG-127**：5 处 `DROP TABLE` 后补 `CREATE SEQUENCE IF NOT EXISTS` |

## 三、后端代码（`phoenix-*`）

| 模块 | 变更摘要 |
|---|---|
| `phoenix-privilege-core` | `LoginServiceImpl`：菜单/权限位按角色过滤（唯一入口 + 祖先补全 + 按钮级）+ 删 session ACL 快照 + `isSuperAdmin` 双条件；`PrivilegeUserServiceImpl`：事务化建号/更新（roleIds/groupIds）+ **BUG-123 修复**；删 2 个死 XML mapper + `getByCompanyId` 链路 |
| `phoenix-privilege-api` | 实体/VO/DTO 去组织字段；`PrivilegeUserDTO` 增 roleIds/groupIds |
| `phoenix-agent-*` | 组侧技能/MCP 授权：`GroupSkillInfoService(+Impl)`、`GroupMcpInfoService(+Impl)`、`GroupSkillController`、`GroupMcpController`、`SkillIdsDTO`/`McpIdsDTO`、`GroupMcpInfo` 补逻辑删 |
| `phoenix-platform-core/api` | 组授权读侧补 `del_flag=0`（`GroupAgentInfo/AccountGroupInfo/GroupInfo`）；`AccountInfo` 去组织字段 |
| `phoenix-data-*` | `AdminRoleGuard`（新增）；`AgentPresetQuestionController` 两写接口鉴权 |
| 删除 | 业务代码 55 个文件（T-02 25 + T-04 30）+ 2 个死 XML mapper |

## 四、前端代码（`web-frontend/`）

| 应用 | 变更摘要 |
|---|---|
| `apps/admin-ui` | 删 20 文件（组织三维页面/API/组件 + platform-info 三件）；改 7 文件（引用解除、`ColPage`→`Page`、去组织字段）；新增 `api/core/group-{skill,mcp}.ts`、`assign-{skill,mcp}-form.vue`、组管理页两动作；`assign-menu.vue` 修 R-10；用户表单三维度化 |
| `apps/mobile-ui` | 删 3 个 SSO 专用文件；`main.ts` 重写为「无 token → 密码登录页」 |
| 验收 | admin-ui：typecheck **207 < 基线 211**、`pnpm build` exit=0；mobile-ui：typecheck 零新增、`build:prod` exit=0 |

## 五、证据（`evidence/`）

| 类别 | 文件 |
|---|---|
| 逐任务结论 | `T-01_result.txt` ~ `T-16_result.txt`（含 T-07/08/09/10/12/13/14/15 的「延期至 T-16」与勘误段） |
| 真实命令输出 | `T-07_compile.raw.txt`、`T-07_logic-replica.txt`、`T-09_compile.raw.txt`、`T-09_delflag-impact.txt`、`T-10_compile.raw.txt`、`T-10_guard-replica.txt`、`T-13_compile.raw.txt`、`T-13_sql-probe.txt`、`T-14_compile.raw.txt`、`T-14_drill.txt`、`T-14_fresh-replay.txt`、`T-15_drill.txt` |
| UI 留证 | `T-16_ui-nav-common-user.jpg`（普通角色导航仅 智能体中心/智能体管理/知识库） |

## 六、台账与发布件

| 文件 | 变更 |
|---|---|
| `specs/_project/bugs.md` | 新增 BUG-123~129（7 条）；BUG-116/117/118/121/122/123/124/127 翻转「已修复/已验证(v2.0.0)」 |
| `specs/_project/lessons.md` | 新增 L-43~L-50（8 条，含 L-32 复发记录） |
| `specs/_project/backlog.md` | BL-31 → 实现完成(v2.0.0，待发版冻结) |
| `releases/v2.0.0/MILESTONE.md` | 需求挂接表状态、纳入缺陷表、进度与审计记录同步 |

## 六·补、镜像与部署（2026-10-07）

| 项 | 值 |
|---|---|
| 后端镜像 | `phoenix-backend:v2.0.0`（1.57GB；`.stage/phoenix-admin.jar` 413M 为唯一新增层） |
| 前端镜像 | `phoenix-frontend:v2.0.0`（58.2MB；`.stage/dist` 9.1M） |
| 部署端口 | **8090**（`docker/.env` 的 `PHOENIX_HTTP_PORT`；compose 参数化，未改 compose 文件） |
| 迁移执行 | 容器 `phoenix-release-migrator-1` 按 `tbl_phoenix_release` 台账应用 V2.0.0_01~05（5 行） |
| 迁移前备份 | `backups/pre_v2.0.0_deploy_20261007_183127.sql`（44M） |
| 部署验证 | `evidence/T-18_deploy-verify.txt` |

## 六·补二、R-12 追加（2026-10-07，T-18）

| 项 | 值 |
|---|---|
| 升级件 | `V2.0.0_06__menu_merge_system_management_dml.sql`（+ rollback） |
| 前端删除 | `apps/admin-ui/src/views/account/account-info/{index.vue,form.vue,data.ts,group-form.vue}` |
| 前端保留 | `api/core/platform-account-info.ts`、`platform-account-group-info.ts`、`platform-account-tenant-info.ts`（仍被 user.ts / 账号表单 / 组管理页引用） |
| 基线同步 | `sql/all_data.sql`、`sql/all_schema.sql` 各删 4 行、改写 5 行 URL |
| 部署验证 | `evidence/T-18_deploy-verify-r12.txt`、`evidence/T-18_drill.txt`、`evidence/T-18_result.txt` |

## 六·补三、R-13 追加（2026-10-07，T-19）

| 项 | 值 |
|---|---|
| 升级件 | `V2.0.0_07__account_prune_dml.sql`（+ rollback，70 条保真重建 INSERT） |
| 初始化数据 | `sql/all_data.sql` / `sql/all_schema.sql` 各删 88 条种子语句、注入 chenzhuo 一套 5 条 |
| 保留 | admin（`docker/init/10_seed_admin.sql`）、chenzhuo（含角色/前台账号/组/智能体绑定） |
| 验证 | `evidence/T-19_result.txt`、`T-19_drill.txt`、`T-19_deploy-verify.txt` |
| 遗留处置 | 用户裁定**不加迁移件**，2026-10-07 直接清理活库孤儿账号域数据（user_role 12 / login_log 1 / chat_message 39 / chat_session 5 行），备份 `backups/orphan_cleanup_20261007_202052.sql`；证据 `evidence/T-19_orphan_cleanup.txt` |

## 六·补四、R-14 追加（2026-10-07，T-20）

| 项 | 值 |
|---|---|
| 升级件 | `V2.0.0_08__usertype_code_drop_ddl.sql`（+ rollback，含列与原值保真恢复） |
| 后端 | 17 文件：删 `PrivilegeUser.{code,userType,itUserId,itUserName}` 等 8 个字段/接口/死枚举（UserTypeEnum + 3 条死异常）；工号语义替换为用户名 |
| 前端 | 7 文件：账号列表/表单工号与用户类型、组管理工号搜索与列、api 类型、store、mobile 类型；`router/guard.ts` 死门移除 |
| 兼容加固 | 4 处会话读取点 Jackson `FAIL_ON_UNKNOWN_PROPERTIES=false`（L-54） |
| 验证 | `evidence/T-20_scan.txt`、`T-20_drill.txt`、`T-20_deploy-verify.txt`、`T-20_result.txt` |
| 保留 | `tbl_platform_account_info.code`（前台账号自身标识，前台登录/查询/搜索在用） |

## 六·补五、R-15 追加（2026-10-07，T-21）

| 项 | 值 |
|---|---|
| 迁移件 | **无**（R-15 无表结构变更；正向/回滚仍各 8 件） |
| 后端 | `PrivilegeUserServiceImpl`（补 `mobile` 检索 + `isSuperAdmin`/`updateStatus`/`updateStatusBatch`/`canDisable`）、`PrivilegeUserController`（+2 端点 + 保护）、2 个新 DTO、`LoginServiceImpl`（委托统一口径） |
| 前端 | `account/{data.ts,index.vue}`、`api/core/privilege-user.ts`；删除 `account/role-form.vue` |
| 缺陷 | 修 **BUG-130**（手机号搜索失效）；登记 **BUG-131**（用户管理端点缺管理员守卫） |
| 验证 | `evidence/T-21_result.txt` |

## 六·补六、R-16 追加（2026-10-07，T-22）

| 项 | 值 |
|---|---|
| 迁移件 | **无**（纯代码改动；正向/回滚仍各 8 件） |
| 后端 | `PrivilegeUserServiceImpl`（`isProtectedAdmin` + 删除/启停/编辑兜底）、`IPrivilegeUserService`、`PrivilegeUserController`（三端点校验 + 删除端点补齐） |
| 前端 | `account/index.vue`（操作列置灰 + tooltip + 批量预检） |
| 缺陷 | 修 **BUG-132**（删除端点零保护：可删自己 / 可删最后一个启用超管） |
| 验证 | `evidence/T-22_result.txt` |

## 七、计数自检

- 正向 SQL 件数 = 回滚 SQL 件数 = **8** ✅
- 任务数 22，完成 22（T-17 收口 + T-18 R-12 + T-19 R-13 + T-20 R-14 + T-21 R-15 + T-22 R-16）✅
- 提交 footer `Task: T-xx` 覆盖 T-01~T-17 ✅（`git log --grep="Task: T-"` 可核）
