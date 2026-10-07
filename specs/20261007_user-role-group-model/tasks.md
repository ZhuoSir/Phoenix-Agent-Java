> 版本: v1.2.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-07 | 确认记录: 同 requirements v2.2.0 | 变更源: R-13（v2.2.0 新增条款）⇒ 追加 T-19 | 确认记录: 用户 2026-10-07 口令「确认执行（确认人：陈卓）」+ URL 一并改为 /system-management | 变更源: R-12（v2.1.0 新增条款）⇒ 追加 T-18 | 更新: 2026-10-07（v1.0.0：三重确认第③重通过；正文相对 v0.1.0 无改动，仅版本头转正）

# 任务清单：user-role-group-model

> 覆盖自检：**R-01~R-11 每条至少被一个 T 覆盖**（映射见文末「覆盖矩阵」）；无超出需求的 T。
> 依赖纪律：依赖只指向更小编号；触碰共享面的任务，验证方式已按 `plan.md` 共享面身份矩阵**逐身份**给出断言。
> 铁律：Implement 期间发现需求/设计缺陷 → 停编码 → 回改文档 → bump + 重确认。

## 1. 基线与备份

- [x] T-01 取编译基线、生产数据门禁统计与组织维度专项备份
  关联: R-03
  依赖: 无
  验证方式: ① `mvn -q clean compile -Dspring-javaformat.skip=true` 输出留存（基线，供后续比对增量错误数，L-32）；② `pg_dump` 全量 + 三张组织表与 `tbl_platform_platform_info` **专项导出** → `backups/pre_v2.0.0_orgdim_<ts>.sql`；③ 跑门禁统计 SQL：零角色用户数、零组用户数、各角色可用菜单数、`tbl_privilege_group` 行数、`tbl_platform_platform_info` 行数
  验收标准: 三份产物落 `evidence/T-01_*.txt`（基线编译输出 / 导出行数核对 / 门禁统计表）；**组织表行数须与 dump 实测一致**（company 4 / department 17 / employee 14，差异须如实记录）

## 2. 组织维度下线（后端）

- [x] T-02 删除组织维度整文件与死代码 VO
  关联: R-01, R-03, R-09
  依赖: T-01
  验证方式: 删 22 个整文件（三个 `*Controller` / `*Service(+Impl)` / `*Mapper` / `*DTO` / `*VO` / `*Query` / `entity`）+ `DepartmentTreeVO`、`OrganizationTreeVO`（227 处之外的易漏项）+ `PrivilegeGroupVO`；删后全量构建并把**增量错误数**与 T-01 基线比对
  验收标准: 24 个文件不存在；`grep -rn "PrivilegeCompany|PrivilegeDepartment|PrivilegeEmployee|PrivilegeGroupVO" --include=*.java` 命中**仅剩 T-03 的 3 个待改文件**

- [x] T-03 改造三个部分引用文件（组织维度摘除）
  关联: R-01, R-02
  依赖: T-02
  验证方式: 改 `AccountInfoServiceImpl`（import:38 / 字段:67 / 唯一调用 401-404）、`PrivilegeUserServiceImpl`（`pageByQuery` 的 select:136 + leftJoin:137-140 + eq:141-142）、`PrivilegeUserController`（`toVo` 54-72）；构建通过后实测 `GET /api/privilege/user/page` 与 `GET /api/privilege/user/{id}`（**断言响应体业务码** `code`，非 HTTP 码，L-26）
  验收标准: 全量构建通过；用户分页/详情返回体**不含** `companyName`/`deptName`/`companyId`/`deptId`/`employeeId`/`itUserId`/`itUserName`/`isLeader`；上述字段在 Java 侧 grep 为 0

- [x] T-04 三方集成三层后端删除（同步 / 免登 / 平台配置）
  关联: R-06, R-11
  依赖: T-03
  验证方式: 删 `service/sync/**`（含 `*SyncConstants`，整包不留）、`service/thirdparty/**`、`PlatformInfoController`/`Service(+Impl)`/`Mapper`/实体、`service/platform/{DingTalk,Feishu,Weixin}SdkService(+Impl)`、`PlatformTypeEnm`、`AccountInfo.thirdPartyId`、`AccountInfoServiceImpl.thirdPartyLogin`/`getByThirdPartyId`、`AccountLoginController POST /auth/thirdLogin`、`AccountInfoController GET /third-party/{id}`；**共享面逐身份断言**：① `POST /platform/sync/*` → 404 ② `POST /auth/thirdLogin` → 404 ③ `/platform/platform-info/**` → 404 ④ **`POST /auth/login` 后台账号与前台账号均成功（业务码）** ← 唯一保留登录方式的对面断言
  验收标准: 构建通过（增量错误数不高于基线）；①~③ 全 404 且非 500；④ 两个账号均登录成功并取得 token

## 3. 权限与授权生效

- [x] T-05 角色与用户角色数据订正（存量补齐 + 孤儿处置 + 组织菜单清理）
  关联: R-07, R-05
  依赖: T-01
  验证方式: 编写 `V2.0.0_0x__role_backfill_dml.sql`（`INSERT ... WHERE NOT EXISTS` 幂等）与组织菜单清理件（按固定 id 删 4 行菜单 `2bafd881f51f43519a54a429be0dabb5` / `5db7285051204f81867995e2565931c1` / `8ec79aa5e5ab4365805d49a4ff0c5116` / `90511f372a564de1b1ab8df780c640b7` 及其 4 行 ACL 引用）；在 **drill 库**执行并断言零角色用户数=0、组织菜单行=0、其 ACL 引用行=0；孤儿 `user_role` 行**先导出留档再处置**（BUG-118，不擅自删）
  验收标准: drill 库断言全过；**连续执行两次结果一致**（幂等证据）；孤儿行导出文件落 `evidence/`

- [x] T-06 ACL 基线重建（超管全量 + 普通角色基线 + 孤儿清理）
  关联: R-05
  依赖: T-05
  验证方式: 编写 `V2.0.0_0x__acl_baseline_rebuild_dml.sql`：超管 `428007432736870400` 授予全部存活菜单；普通角色 `431285032083144704` 授予非管理类基线集合；清理 `71f2934c-d93b-4075-9492-d9cbf22e1bb2` 名下 50 行与 44 行孤儿 `module_id`（先导出）；逐角色统计可用菜单数
  验收标准: 超管菜单数 = 存活菜单全量；普通角色菜单数 > 0；无 `module_id` 落空；`tbl_privilege_acl` 唯一键 `(release_id, module_id)` 无冲突

- [x] T-07 菜单与权限位按角色过滤生效
  关联: R-05
  依赖: T-06
  验证方式: 改**唯一入口** `LoginServiceImpl.getUserMenus()`（替换 `list()` 全表 + `buildAdminAclMap` 满权限位）；**共享面逐身份断言**：① 超管登录 → 菜单数 = 全量 ② 普通角色登录 → 菜单数 = 基线集合 ③ **未授权菜单 URL 直接访问 → 404/首页**（动态路由不注册）④ **按钮级**：未授权 pvalue 的 `hasAccessByCodes` 为 false（`buildAdminAclMap` 与 `access.ts:123-126` 同批改造）⑤ 零授权账号登录 → **提示页不白屏** ⑥ 改角色后**无需重登**即生效（禁读 session 旧 ACL 快照）
  验收标准: ①~⑥ 全部实测通过；**任一角色可用菜单数为 0 即判失败**（上线门禁）

- [x] T-08 角色↔菜单授权界面修复
  关联: R-10
  依赖: T-07
  验证方式: 修复 `role/index.vue:245-250` 的提前 `return` 与 `assign-menu.vue:84` 的 `currentRole` 未赋值链；界面操作后断言 `tbl_privilege_acl` 新增对应 `(release_id, module_id)` 行且保存接口业务码成功；再撤销并断言该角色用户菜单减少
  验收标准: 授权与撤销均**真实落库**；`saveModuleAclApi`/`saveAllAclApi` 被实际调用（网络面板或后端日志留证）

- [x] T-09 组侧技能/MCP 关联补齐 + 组授权读侧 del_flag 口径统一
  关联: R-04
  依赖: T-02
  验证方式: ① 为技能与 MCP 补**组侧**读写能力（现有仅资源侧 `PUT /api/skill/{id}/groups` 与 `PUT /api/mcp/{id}/groups`），组管理页可勾选并即时生效；② 统一读侧口径（`BaseModel` 补 `isLogicDelete` 或各读点显式 `del_flag=0`，含 `GroupAgentInfoServiceImpl.getByGroupIds:21-25`）；**共享面断言**：撤销组 G 对资源 X 的授权后，G 内用户 U 刷新即不可见（用生产已有墓碑行数据验证，BUG-121）
  验收标准: 四类资源（智能体/知识库/技能/MCP）均可在组管理页增删；撤销后即时不可见；「无任何授权行的资源维持公开」不变（Q-P4 口径回归）

- [x] T-10 预设问题接口角色校验（双越权路径）
  关联: R-08
  依赖: T-07
  验证方式: 在 `AgentPresetQuestionController` 的 `POST /{agentId}/preset-questions`(:53-76) 与 `DELETE /{agentId}/preset-questions/{questionId}`(:81-91) 加角色校验；**正负对照断言业务码**：普通角色 POST/DELETE **被拒**且数据未变更，管理员同接口**成功**
  验收标准: 两条越权路径均被拒；管理员路径可用（证明未误伤）；`GET /{agentId}/{accountId}/preset-questions` 的枚举问题一并记录去向

## 4. 前端

- [x] T-11 admin-ui 组织维度与平台配置页面删除 + 引用解除
  关联: R-01, R-11
  依赖: T-04
  验证方式: 整删 **18** 个文件（`views/organization/**` 9 + `api/core/{privilege-company,privilege-department,privilege-employee,platform-sync}.ts` 4 + `components/dept/{DepartmentSelector,EmployeeSelector}.vue` 2 + `views/platform/platform-info/**` 2 + `api/core/platform-info.ts` 1）；部分改 10 个（`api/core/index.ts` 删导出行 + 两个账号页面的部门树/选择器/字段）；**先解除 `DeptTreeSidebar` 的 3 处引用**再删
  验收标准: `vue-tsc` 错误数**不高于基线**（看增量，L-32）；`pnpm build` 通过；导航无「组织管理」「三方平台」；直接访问 `/organization/*`、`/basic/platform-info` 落 404 或首页

- [x] T-12 mobile-ui 移除 SSO 自动登录，密码登录承接
  关联: R-11
  依赖: T-11
  验证方式: 删 `main.ts:47-89` 的 `trySsoLogin()` 及其调用、`services/platformService.ts` 的 `getEnabledPlatform`/`thirdPartyLogin`；构建后实测启动流程（浏览器环境）
  验收标准: 启动流程**不再发起** SSO/免登请求；停留密码登录页；`POST /auth/login` 登录成功

- [x] T-13 用户表单与列表三维度化（角色/组多选，去组织字段）
  关联: R-02
  依赖: T-11
  验证方式: 改 `views/system-management/account/{index.vue,form.vue,data.ts}` 与 `api/core/privilege-user.ts`：删企业/部门/员工列与表单项，角色与组为多选；实测新建/编辑用户（断言业务码）
  验收标准: 表单无组织字段；只填账号 + 勾 1 个角色即创建成功并可登录；请求与响应中不再出现 `companyName`/`deptName` 等

## 5. 升级件与回归

- [x] T-14 升级件 SQL 落盘并在 drill 库演练（DDL + DML + rollback 配对）
  关联: R-03, R-09, R-11
  依赖: T-04, T-05, T-06
  验证方式: `specs/20261007_user-role-group-model/sql/` 落 `V2.0.0_01__org_dimension_drop_ddl.sql`（含 `DROP TABLE tbl_platform_platform_info` 与 `third_party_id` 列）、`V2.0.0_02__role_backfill_dml.sql`、`V2.0.0_03__org_menu_cleanup_dml.sql`、`V2.0.0_04__acl_baseline_rebuild_dml.sql` 及 `rollback/` 配对；**全新库重放 + drill 库正反向演练**
  验收标准: 全新库重放全绿（幂等，重跑无错）；正向 → 三张组织表与配置表不存在、应用启动无 SQL 报错；反向 → 表与列恢复且行数一致（数据由 T-01 专项备份回填）；`rollback/` 件数 == 正向件数
  ⚠ Implement 期追加（2026-10-07，T-13 实测发现；**不改本条验收口径**，仅锁定顺序依赖）:
  `tbl_privilege_user` 的 `company_id` / `dept_id` 在库中为 **NOT NULL**（T-13 探针实测直接撞
  `ERROR: null value in column "company_id" violates not-null constraint`）⇒ 去掉组织字段后的建号路径，
  必须由本条 DDL 一并解除该约束（DROP COLUMN 或 DROP NOT NULL），否则 T-13 的「只填账号即创建成功」
  在 T-14 落地前**必然失败**。故 T-13 的运行期验收顺序锁定为 **T-14 → T-16**。

- [x] T-15 菜单行删除件（两份基线文件 + 升级件）
  关联: R-01
  依赖: T-05
  验证方式: `sql/all_data.sql` 与 `sql/all_schema.sql` **各改一份**（组织菜单 4 行 + ACL 4 行 + 「三方平台」菜单行）；升级件按固定 id 幂等删除
  验收标准: 两份基线文件与升级件均无这些行；重新导入全新库后菜单表无组织/三方平台项

- [x] T-16 部署与端到端回归
  关联: R-01, R-02, R-03, R-04, R-05, R-06, R-07, R-08, R-09, R-10, R-11
  依赖: T-08, T-09, T-10, T-12, T-13, T-14, T-15
  验证方式: 按 `UPGRADE.md` 顺序部署（备份 → SQL → 应用）并跑回归矩阵：组织维度 404 · 菜单按角色过滤 · 按钮级权限 · 组授权即时生效 · 预设问题越权被拒 · 密码登录（后台/前台）· 三方端点 404 · 零授权用户不白屏
  验收标准: 回归矩阵全绿并落 `evidence/`；关键断言贴**真实命令输出**；部署三段证明（进程 / 健康检查 / 端口）

- [x] T-17 收口：完成清单、升级件登记、台账翻账与 changelog
  关联: R-01, R-02, R-03, R-04, R-05, R-06, R-07, R-08, R-09, R-10, R-11
  依赖: T-16
  验证方式: 生成 `completion.md`（未完成项必须写原因分类与去向）与 `artifacts.md`；`BL-31` 翻「已交付(v2.0.0，已冻结）」、`BUG-116~122` 按实际修复情况翻状态；`releases/v2.0.0/MILESTONE.md` 需求表与进度同步
  验收标准: 四件套 + completion/artifacts 齐备；台账与 `git log` footer 可交叉核对；表格闸 `mdtable_check` 全绿

## 6. R-12 追加（v2.1.0 信息架构调整）

- [x] T-18 菜单合并为「系统管理」+ 账号管理唯一化 + 前台管理空目录清理
  关联: R-12
  依赖: T-15
  验证方式: 落 `V2.0.0_06__menu_merge_system_management_dml.sql`（改名/搬迁/次序/删 2 行 + ACL）与 `rollback/` 配对；
    聚合进 `releases/v2.0.0/sql/`；删 `views/account/account-info/**` 4 文件（API 模块保留）；
    drill 正反向 ×2 + 洁净库重放 01~06；重建前端镜像并重新部署（台账应用 06）
  验收标准: 存活菜单 19；超管 ACL 行数 = 存活菜单数；超管导航只见「系统管理」（其下 角色/账号/组/菜单/权限值/日志，
    且 角色→账号→组 相邻），无「权限管理」「前台管理」；`/platform-account/account-info` → 404；
    普通角色导航仍 7 条；`pnpm typecheck` 不新增错误、`pnpm build` 通过

## 7. R-13 追加（v2.2.0 账号集合收敛）

- [x] T-19 账号收敛为 admin + chenzhuo（存量清理 + 基线种子重写）
  关联: R-13
  依赖: T-18
  验证方式: 落 `V2.0.0_07__account_prune_dml.sql`（删 3 存活 + 2 软删账号及其角色/登录日志/智能体绑定）
    与 `rollback/`（保真重建 5 行及其关联）；重写基线 5 张表种子行为「chenzhuo 一套」；
    drill 正反向 ×2 + 洁净库重放 01~07；部署后实测存活账号与登录行为
  验收标准: 活库与全新库**均只有 admin / chenzhuo 两个存活账号**；两账号均可登录且菜单分别 19 / 7；
    5 个旧账号登录失败；无指向已删账号的残留账号域行；自检遵循 L-51（环境无关不变量）

## 覆盖矩阵（R → T）

| 需求 | 覆盖任务 |
|---|---|
| R-01 组织维度下线 | T-02, T-03, T-11, T-15, T-16 |
| R-02 用户模型三维度化 | T-03, T-13 |
| R-03 结构与数据处置（破坏性） | T-01, T-02, T-14 |
| R-04 组直接关联四类资源 | T-09 |
| R-05 角色过滤生效 | T-05, T-06, T-07 |
| R-06 同步能力下线 | T-04 |
| R-07 存量账号角色与组补齐 | T-05 |
| R-08 预设问题接口角色限制 | T-10 |
| R-09 组语义唯一化 | T-02, T-14 |
| R-10 角色↔菜单授权可维护 | T-08 |
| R-11 免登与平台配置下线 | T-04, T-11, T-12, T-14 |
