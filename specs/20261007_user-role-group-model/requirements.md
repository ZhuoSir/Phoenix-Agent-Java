> 版本: v2.0.0 | 状态: 待重确认 | 更新: 2026-10-07 | 确认人: （待重确认；前次 陈卓 v1.0.0 / 2026-10-07） | 挂载: v2.0.0（在途；与本文档版本号同号属巧合，勿混）
>
> **v2.0.0 变更（改范围 ⇒ MAJOR；铁律 4：已确认文档被改动 ⇒ 前次 v1.0.0 确认作废，待重确认）**
>
> ① **范围变更**（用户 2026-10-07 口令「扫码登录去掉，不要，只保留现在的密码登录」，并经 Q-P5/Q-P6 追问裁定）：第三方免登与三方平台配置**整体下线** ⇒ 新增 **R-11**；R-06 原「保留第三方免登」边界**作废**，其边界 4 的 `*SyncConstants` 保留约束**自动解除**（连登录策略一并删除）；Non-goals 相应反转。
> ② 本版**同时包含未获确认的 v1.1.0 全部内容**（v1.1.0 因未经确认，不单独留版）：新增 R-10 角色↔菜单授权可维护（Q-P2）、R-04 可见性口径（Q-P4）、R-05 边界（Q-P1）、R-02/R-03 三表范围（Q-P3）。
> ③ **术语纠错**：原文「扫码登录」实为**第三方免登（应用内 SSO）**，已按实测订正（明细见 `changelog.md` 事件记录与 `lessons.md` L-44）。


### 背景（立项调研事实，2026-10-07）

当前用户体系里**并排存在五套身份/组织概念**，外加**两套「组」**：

| 概念 | 落地物 | 是否参与权限判定 |
|---|---|---|
| 用户 | `tbl_privilege_user` | 是 |
| 角色 | `tbl_privilege_role` + `tbl_privilege_user_role`（多对多，含 `end_date`/`valid_month` 有效期） | 是（菜单授权） |
| 企业 | `tbl_privilege_company`（含 `idm_company_id`/`third_id` 外部对接列） | **否** |
| 部门 | `tbl_privilege_department`（`company_id` NOT NULL，树形 `pid`） | **否** |
| 员工 | `tbl_privilege_employee` | **否** |
| 组（平台侧） | `tbl_platform_group_info` + `tbl_platform_group_{agent,kbase,skill,mcp}_info` + `tbl_platform_account_group_info` | 用于资源授权 |
| 组（后台侧） | `tbl_privilege_group`（数据组，含 `super_id`/`type`/`state`） | 未接权限判定，**且全仓仅 1 个自声明 VO、零 mapper/service 引用 = 死代码** |

实测引用面（本次立项调研，命令可复现）：

- Java 侧 `PrivilegeCompany|PrivilegeDepartment|PrivilegeEmployee` 引用 **227 处**，横跨 `phoenix-privilege`（实体/DTO/VO/Query/Mapper/Service/Controller）、`phoenix-platform`（`AccountInfo`、`AccountInfoServiceImpl`）、`phoenix-common`（同步策略）。
- `companyId|deptId|employeeId` 字段出现 **198 处**。
- `tbl_privilege_user` 的 `company_id`、`dept_id` 是 **NOT NULL** 列——组织维度是用户表的**强约束**；另有组织衍生列 `employee_id`、`it_user_id`、`it_user_name`、`is_leader`。
- 前端 `web-frontend/apps/admin-ui`：独立「组织管理」菜单族四条路由（`/organization`、`/organization/company`、`/organization/department`、`/organization/employee`）+ 选择器组件（`src/components/dept/DepartmentSelector.vue`、`EmployeeSelector.vue`）+ 三个 API 客户端（`src/api/core/privilege-{company,department,employee}.ts`）。
- 权限设施（`role` / `user_role` / `module` / `acl` / `pvalue`）**不依赖**企业/部门，可原样保留。
- **三方集成有三层，本版整体下线**：① **同步**——`service/sync/**`（`AbstractPlatformSyncStrategy` + 钉钉/飞书/微信策略）+ `PlatformSyncController`（`/platform/sync`）+ 前端 `api/core/platform-sync.ts`；② **免登**——`thirdparty/strategy/{DingTalk,Feishu,WeCom}ThirdPartyLoginStrategy` + `POST /auth/thirdLogin`（唯一调用方为 `apps/mobile-ui` 启动时的 `trySsoLogin()`）；③ **平台配置**——`tbl_platform_platform_info` + `PlatformInfoController` + 三个 SDK 服务 + 前端「三方平台」菜单（`/basic/platform-info`）。三层的**仅存消费方就是彼此**（前两层的存在意义是同步/免登，第三层只被前两层读）⇒ 须**同批下线**，否则留下无消费方的死表与死页面；**账号密码登录（`POST /auth/login`）是唯一保留的登录方式**。

**结论**：组织维度对目标模型（一个用户、若干角色、若干组、组直接授权资源）是**净负担**——它不参与任何权限判定，却强制每个用户必须挂在一个企业和部门上，并让「组」的语义分散在两套表里（其中一套已是死代码）。

### 目标（成功长什么样）

1. 后台用户模型只剩三要素：**用户 → 角色（可多）→ 组（可多）**；企业、部门、员工在 UI、API、库表中完全消失。
2. 建用户只需填账号信息 + 勾角色 + 勾组。
3. 「组」只有一套（平台侧资源授权组），能直接关联**智能体 / 技能 / 知识库 / MCP 插件**四类资源，组内用户据此获得可见性——前台授权不再是「任一登录用户可见」。
4. 菜单按角色过滤生效（不再 26 个菜单对全员可见）。
5. 系统**只保留账号密码登录**；三方平台集成（**用户/组织同步** + **客户端内免登** + **平台配置**）整体下线。

## 需求条款

### R-01 组织维度下线（用户可见面）

WHEN 管理员登录后台管理端并浏览导航,
系统 SHALL 不再呈现「组织管理」及其「企业 / 部门 / 员工」子项，且不再提供任何组织维度的查询、新增、修改、删除入口。

边界：`/organization`、`/organization/company`、`/organization/department`、`/organization/employee` 四条菜单记录及对应前端页面/路由/API 客户端一并移除；直接以 URL 访问时 SHALL 落 404 或首页，不得渲染空白页或残留半截表单。

#### 验收场景

- **GIVEN** 管理员已登录后台
  **WHEN** 打开左侧导航
  **THEN** 无「组织管理」节点及其三个子项
- **GIVEN** 管理员已登录后台
  **WHEN** 手工访问 `/organization/department`
  **THEN** 页面 404 或跳首页，浏览器控制台无未捕获异常
- **GIVEN** 组织维度已删除
  **WHEN** 调用原企业/部门/员工 REST 端点
  **THEN** 返回 404（端点不存在），不是 500

### R-02 用户模型三维度化（用户 / 角色 / 组）

WHEN 管理员新建或编辑一个用户,
系统 SHALL 只要求并提供「账号信息 + 角色（可多选）+ 组（可多选）」三类输入，不得要求或展示企业、部门、员工、工号字段。

边界：用户↔角色、用户↔组均为多对多；角色分配沿用 `tbl_privilege_user_role` 语义（含有效期）。用户模型上的组织列**横跨三张表**（Q-P3 裁定全部移除）：`tbl_privilege_user`（`company_id`、`dept_id`、`employee_id`、`it_user_id`、`it_user_name`、`is_leader`）、`tbl_privilege_role`（`company_id`）、`tbl_platform_account_info`（`employee_id`、`dept_id`、`dept_name`）；`LoginVO.deptIds` 作为只写不读的死载荷一并移除。已确认**无外部 IDM/HR 系统读写**（Q5 裁定，生产库外部对接列全空佐证）。

#### 验收场景

- **GIVEN** 管理员打开用户表单
  **WHEN** 查看字段
  **THEN** 无企业/部门/员工选择项；「角色」「组」为多选控件
- **GIVEN** 管理员只填账号信息并勾选 1 个角色
  **WHEN** 提交
  **THEN** 创建成功，且该用户可登录

### R-03 组织维度的结构与数据处置（破坏性）

WHEN 新版本部署执行升级脚本,
系统 SHALL 删除企业、部门、员工三张表及其在用户表上的组织列，并 SHALL 提供与之**成对**的回滚脚本，使回滚后库结构与数据恢复升级前状态。

边界：组织维度数据**直接删除**（Q3 裁定：不建归档表）；范围含三张组织表与**三张用户模型表的组织列**（见 R-02 边界）；数据处置口径与执行顺序在 plan 定稿并写入 `UPGRADE.md`；回滚脚本 SHALL 与本版 SQL 同目录配对存在，且 SHALL 明示「**结构可回滚、数据依赖前置备份**」。

#### 验收场景

- **GIVEN** 存量库（含公司/部门/员工数据与用户组织归属）
  **WHEN** 执行升级脚本
  **THEN** 三张表与用户表组织列均不存在，应用启动无 SQL 报错
- **GIVEN** 升级后的库
  **WHEN** 执行回滚脚本
  **THEN** 三张表与用户表列恢复，且行数与升级前一致
- **GIVEN** 全新空库
  **WHEN** 重放本版全部 SQL
  **THEN** 全部成功（幂等），无「表已存在/不存在」类错误

### R-04 组直接关联四类资源

WHEN 管理员在组管理中为一个组授予资源,
系统 SHALL 支持关联**智能体、技能、知识库、MCP 插件**四类资源，且关联结果对该组内用户**即时生效**（无需重启、无需重新登录）。

边界：沿用既有 `tbl_platform_group_{agent,skill,kbase,mcp}_info` 语义。**可见性口径（Q-P4 裁定）**：① 资源**已有**授权行时，仅被授权组的用户可见，撤销授权即时不可见（无需重启/重新登录）；② 资源**无任何授权行**时，维持现状兼容语义——已发布且未限定范围的资源**公开可见**。另：**组侧读写能力当前只覆盖智能体与知识库两类**，技能与 MCP 仅有「资源侧」单向授权端点，本需求 SHALL 补齐这两类的组侧关联能力。

#### 验收场景

- **GIVEN** 组 G 含用户 U，且 G 关联智能体 A
  **WHEN** U 打开智能体中心
  **THEN** A 可见
- **GIVEN** 组 G 未关联技能 S
  **WHEN** U 打开技能列表
  **THEN** S 不出现（S **已被授权给其他组**，即有授权行）
- **GIVEN** 技能 T **无任何授权行**
  **WHEN** U 打开技能列表
  **THEN** T 可见（兼容语义，Q-P4 裁定）
- **GIVEN** 管理员刚**撤销**组 G 对智能体 A 的关联
  **WHEN** G 内用户 U 刷新智能体中心
  **THEN** A 不可见（即时生效；须先统一组授权表读侧 `del_flag` 口径，见 BUG-121）
  **THEN** S 不出现
- **GIVEN** 管理员刚为 G 新增知识库 K 的关联
  **WHEN** U 立即刷新知识库列表
  **THEN** K 可见（无需重启/重新登录）

### R-05 角色过滤生效

WHILE 用户已登录后台,
系统 SHALL 只呈现其角色所授权的菜单与操作；未授权菜单不出现在导航中，直接以 URL 访问未授权页面 SHALL 被拒绝。

边界：本期覆盖后台菜单可见性**与权限位**（按钮级授权不再恒真）。**超管豁免**：`role_id='428007432736870400'`（ROLE_ADMIN，沿用 `AgentKnowledgeMapper.canReadKnowledgeSource` 既有口径）授予全部存活菜单。**数据前置（Q-P1 裁定）**：升级时为可登录用户补齐默认角色（普通角色）并重建 ACL 基线；若账号仍无任何授权菜单，登录后 SHALL 给出明确提示而非白屏（前端 `homePath` 默认 `/agent/list`）。

#### 验收场景

- **GIVEN** 用户 U 仅具「普通用户」角色
  **WHEN** 打开后台
  **THEN** 仅见其角色授权菜单（非全量 26 项）
- **GIVEN** 上述 U
  **WHEN** 手工访问未授权菜单 URL
  **THEN** 被拒绝（403 或跳首页），不渲染页面内容

### R-06 三方平台用户/组织同步能力下线

WHEN 新版本部署完成,
系统 SHALL 不再提供三方平台（钉钉 / 飞书 / 企业微信）的**用户与组织同步**能力——同步端点、同步策略、同步所需的组织维度读写、前端同步入口一并移除。

边界（**共享面保护，必须成立**）：

1. ~~第三方免登 SHALL 继续可用~~ **【已作废 v2.0.0】**：经用户 2026-10-07 口令与 Q-P5/Q-P6 裁定，第三方免登与平台配置**一并下线**，不再属于本条款的保留范围 ⇒ 见 **R-11**。（机制实测记录：免登为 `apps/mobile-ui` 在钉钉/企微/飞书客户端内经 JS-SDK 取免登授权码后 `POST /auth/thirdLogin`，**与"扫码"无关**；admin-ui 侧入口本就关闭：`layouts/basic.vue:156 :show-third-party-login="false"`。）
2. 后端全量构建 SHALL 通过，不得残留对已删同步类的引用。
3. `/platform/sync` 端点 SHALL 返回 404（不存在），不是 500。
4. **【v2.0.0 变更】** 前版此处要求「保留 3 个 `*SyncConstants` 常量文件」以保住免登编译——因 R-11 将三个登录策略一并删除，该约束**自动解除**：`service/sync/**`（含 `*SyncConstants`）SHALL **整包删除**，后端全量构建 SHALL 通过。

#### 验收场景

- **GIVEN** 新版本部署完成
  **WHEN** 调用 `/platform/sync`
  **THEN** 404，无残留路由
- **GIVEN** 同步模块已删除
  **WHEN** 全量构建后端
  **THEN** 编译通过（无对 `service/sync/**` 的悬空引用）
- **GIVEN** 同步模块已删除
  **WHEN** 在组织管理相关页面（部门/人员）上查找
  **THEN** 页面与菜单均已不存在（见 R-01），且**全站无任何「同步」按钮或入口**
- **GIVEN** 同步模块已删除
  **WHEN** 用账号密码登录
  **THEN** **仍可正常登录**（回归断言——证明移除同步未误伤密码登录）

### R-07 存量账号的角色与组归属补齐

WHEN 升级完成,
系统 SHALL 为存量可登录用户补齐至少一个角色（及必要的组归属），使升级后不存在「无任何角色的可登录用户」。

边界：升级后「可登录用户中角色关联数为 0」的计数 MUST 为 0；已有多个角色的用户，其角色关联 SHALL 不被覆盖或截断。

#### 验收场景

- **GIVEN** 升级后的库
  **WHEN** 统计可登录用户
  **THEN** 无角色关联的用户数为 0
- **GIVEN** 用户 U 升级前已有 2 个角色
  **WHEN** 升级完成
  **THEN** U 的角色关联仍为 2 条

### R-08 预设问题接口的角色限制

WHEN 非授权角色调用预设问题新增 / 删除接口,
系统 SHALL 拒绝该请求并返回统一错误响应。

边界：本条为 BL-05 备注中「预设问题 add/delete 接口角色限制随体系统一一并做」的遗留项（`unified-account-center` spec 未覆盖）；经 Q6 裁定**纳入本期**。

#### 验收场景

- **GIVEN** 以普通用户角色登录
  **WHEN** 调用预设问题新增接口
  **THEN** 返回权限拒绝（统一错误码），且数据未变更
- **GIVEN** 以管理员角色登录
  **WHEN** 调用同一接口
  **THEN** 正常成功（证明未误伤授权角色）

### R-09 组语义唯一化（两套组合并为一套）

WHEN 新版本部署完成,
系统 SHALL 只保留**一套**「组」——平台侧资源授权组（`tbl_platform_group_info` 及其四张资源关联表、用户关联表）；后台侧数据组（`tbl_privilege_group` 及其残余代码）SHALL 被移除。

边界：Q1 裁定「合并一套」；因后台数据组在全仓**仅有 1 个自声明 VO（`PrivilegeGroupVO`）、零 mapper/service/接口引用 = 死代码**，本次合并为**单向下线**（无数据合并、无映射规则）。

#### 验收场景

- **GIVEN** 新版本部署完成
  **WHEN** 检索库表
  **THEN** `tbl_privilege_group` 不存在，平台侧组表与其资源关联表均存在
- **GIVEN** 管理员打开组管理
  **WHEN** 查看组列表
  **THEN** 只有一套组（来源平台侧表），无重复概念或双入口
- **GIVEN** 合并完成
  **WHEN** 全量构建
  **THEN** 编译通过（无对 `PrivilegeGroupVO` 的悬空引用）

### R-10 角色↔菜单授权可维护

WHEN 管理员在后台为一个角色配置可见菜单,
系统 SHALL 能通过界面完成授权并保存生效（无需直接改数据库）。

边界：现状该界面**失效**（BUG-116：`role/index.vue:245-250` 在给 `currentRole` 赋值**之前** `return`，`assign-menu.vue:84` 的 `currentRole` 全文件只读不赋值 ⇒ `saveModuleAclApi`/`saveAllAclApi` 从不被调用，ACL 只能手工 SQL 维护）。本条款要求修复该链路，复用既有端点 `POST /api/privilege/acl/saveAll/{releaseId}/{checkStatus}` 与 `POST /api/privilege/acl/saveModule`，**不新增后端端点**。经 Q-P2 裁定纳入本 spec。

#### 验收场景
- **GIVEN** 管理员在「角色管理」对某角色点「分配菜单」
  **WHEN** 勾选若干菜单并保存
  **THEN** 保存接口被**真实调用**（断言业务码成功）且 `tbl_privilege_acl` 出现对应 `(release_id, module_id)` 行
- **GIVEN** 上述授权已保存
  **WHEN** 该角色下的用户重新登录
  **THEN** 其菜单集合发生对应变化
- **GIVEN** 管理员取消某角色对某菜单的授权并保存
  **WHEN** 该角色用户重新登录
  **THEN** 该菜单不再出现

### R-11 第三方免登与三方平台配置整体下线（仅保留账号密码登录）

WHEN 新版本部署完成,
系统 SHALL 只提供**账号密码登录**一种登录方式；第三方免登（钉钉 / 飞书 / 企业微信客户端内 SSO）与三方平台配置管理能力 SHALL 整体下线。

边界（下线清单，逐项可验；**v2.0.0 范围变更**，经 Q-P5 / Q-P6 裁定）：

1. **后台免登链路**：`ThirdPartyLoginFactory`、`ThirdPartyLoginStrategy`、`thirdparty/strategy/{DingTalk,Feishu,WeCom}ThirdPartyLoginStrategy`、`AccountInfoServiceImpl.thirdPartyLogin`（:346-378）与 `getByThirdPartyId`（:184-186）、`AccountInfoController` 的 `GET /third-party/{thirdPartyId}`（:69-71）、`AccountLoginController` 的 `POST /auth/thirdLogin`（:37-39）一并移除。
2. **平台配置面**：`PlatformInfoController`（`/platform/platform-info/**`）、`PlatformInfoService(+Impl)`、`PlatformInfoMapper`、`PlatformInfo` 实体、`tbl_platform_platform_info` 表、`PlatformTypeEnm`、三个 SDK 服务（`{DingTalk,Feishu,Weixin}SdkService+Impl`）一并移除；前端 `views/platform/platform-info/**`、`api/core/platform-info.ts` 与菜单行「三方平台」（`/basic/platform-info`，module id `37def68697b54109a57c18508fc4358c`）一并下线。
3. **账号侧残留**：`AccountInfo.thirdPartyId`（`AccountInfo.java:54`）与 `tbl_platform_account_info.third_party_id` 列随之移除。
4. **客户端**：`apps/mobile-ui` **保留**（Q-P6 裁定），但启动时的 `trySsoLogin()`（`main.ts:47-89`）、`getAuthCode` 与 `platformService.thirdPartyLogin` SHALL 移除，改由既有密码登录页（`LoginPage.vue` + `POST /auth/login`）承接。
5. **构建保护**：后端与前端全量构建 SHALL 通过，不得残留对已删类的悬空引用（`service/sync/**` 亦随本条款整包删除，见 R-06 边界 4）。

#### 验收场景

- **GIVEN** 新版本部署完成
  **WHEN** 调用 `POST /auth/thirdLogin`
  **THEN** 404（不存在），不是 500
- **GIVEN** 新版本部署完成
  **WHEN** 调用 `/platform/platform-info/**` 任一端点
  **THEN** 404
- **GIVEN** 管理员打开后台
  **WHEN** 查看导航
  **THEN** 无「三方平台」菜单项；直接访问 `/basic/platform-info` 落 404 或首页
- **GIVEN** 新版本部署完成
  **WHEN** 分别用后台账号与前台账号执行 `POST /auth/login`
  **THEN** **均登录成功**（回归断言：只保留密码登录，且密码链路未被误伤）
- **GIVEN** `apps/mobile-ui` 新版本在浏览器环境启动
  **WHEN** 观察启动流程
  **THEN** 不再触发 SSO / 免登请求，停留于密码登录页
- **GIVEN** 三方集成已删除
  **WHEN** 全量构建后端与前端
  **THEN** 均通过（无对已删类与已删 API 客户端的悬空引用）

## Non-goals（范围外）

- **不做**账号历史数据质量清理（孤儿账号 id 引用、共用 `employee_id`、`system` 哨兵 creator）→ 已登记 **BL-30**，独立排期。
- **不做**智能体执行面硬隔离（沙箱 / 独立执行身份）→ **BL-32**。
- **不做**知识库数据面收敛（去明文原件、剥离 DB 凭据）→ **BL-33**。
- **不做**存量向量重建与 embedding 指纹校验 → **BL-34**。
- **不移除**账号密码登录（`POST /auth/login`，后台与前台/移动端共用）——本期只下线三方集成（同步 + 免登 + 平台配置，见 R-06 / R-11）。
- **不新增**其他登录方式（短信 / OTP / 本地 SSO 等）。
- **不做**功能级/数据行级 ACL 的细粒度重设计——本期只把既有 ACL 行从「预置数据」转为「生效判定」。
- **不新增**组织架构双向同步能力（只删不建）。
- **不保留**「按部门/企业统计」类报表能力。
- **不处理** `tbl_privilege_user.del_flag`/`status` 语义不一致（BUG-101 已延期，另行处置）。

## 我正在做的假设

1. 「组」的存活方为**平台侧** `tbl_platform_group_info`（资源授权组）；后台侧 `tbl_privilege_group` 按 R-09 单向下线（依据：零业务引用的死代码）。若后台数据组实际另有用途而调研未覆盖，请现在纠正。
2. 企业/部门/员工的存量数据**直接删除**，不做归档表（Q3 已裁定，此处再确认一次口径）。
3. 外部 IDM/HR 系统**不存在**（Q5 已裁定）⇒ `idm_company_id`/`third_id`/`it_user_id`/`employee_id` 等外部对接列可安全移除。
4. 用户↔角色、用户↔组均为多对多。
5. 破坏性变更 → 挂 **v2.0.0**（MAJOR），按 MAJOR 语义写升级说明与回滚。
6. 本期**一次做完**「角色过滤生效」与「组↔资源纳管归一」（Q2 已裁定，不分期）。
7. `apps/mobile-ui` **保留**（Q-P6 裁定），仅移除其 SSO 自动登录；三方平台配置（表 / CRUD / 页面 / 菜单行）**整体下线**（Q-P5 裁定）。

现在纠正，否则按此执行。

## 待确认问题

**无**（Q1~Q6 与 Q-P1~Q-P6 已于 2026-10-07 全部裁定，见下表；裁定事实已同步 `changelog.md`）。

## 已裁定问题（2026-10-07，用户答）

| # | 问题 | 裁定 | 落点 |
|---|---|---|---|
| Q1 | 两套组是否合并 | **合并一套**（保留平台侧资源授权组，下线后台数据组死代码） | R-09 |
| Q2 | 是否分期 | **一次做完**（角色过滤 + 组↔资源归一） | 假设 6 |
| Q3 | 存量组织数据 | **删除**（不归档） | R-03 / 假设 2 |
| Q4 | 三方同步 | **移除**同步模块（注：原「免登保留」已被 Q-P5 推翻） | R-06 |
| Q5 | 外部 IDM/HR 对接 | **没有** ⇒ 外部对接列可移除 | R-02 边界 / 假设 3 |
| Q6 | R-08 是否纳入 | **纳入** | R-08 |
| Q-P1 | 零 ACL 角色 / 无角色用户的降级策略 | **自动补默认角色 + 超管豁免 + 提示兜底** | R-05 边界 / R-07 |
| Q-P2 | BUG-116（角色↔菜单授权界面失效）是否纳入 | **纳入** ⇒ 本次新增 R-10（v1.1.0 变更源） | R-10 |
| Q-P3 | 组织列范围（`tbl_privilege_role` / 前台账号表） | **三表都去组织列** | R-02 / R-03 边界 |
| Q-P4 | 无任何授权行的资源之可见性口径 | **保持兼容：无授权行＝公开** | R-04 边界 |
| Q-P5 | 三方平台配置（表/CRUD/页面/菜单）是否随免登下线 | **随免登一起下线**（全清，含 `third_party_id` 列与 `PlatformTypeEnm`） | R-11 边界 2/3 |
| Q-P6 | `apps/mobile-ui` 去留 | **保留 app，只删 SSO 自动登录** | R-11 边界 4 / 假设 7 |
