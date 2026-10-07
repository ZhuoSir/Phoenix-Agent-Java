> 版本: v1.6.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-07 | 变更源: R-17（智能体按创建人可见 + 系统管理员角色不可删）|
>
> **v1.1.0 变更（铁律 4：已确认文档被改动 ⇒ 回退待重确认）**：**仅订正事实数字**，无设计变更 —— T-01 活库门禁统计（`evidence/T-01_gate-stats.txt`）推翻了两处口径：普通角色 ACL 由「0 行」订正为「**1 行**（智能体中心）」、名下用户由「10」订正为「**5 个存活用户**」；并在 §数据模型一 追加「活库现值复核」表（dump 为迁移前快照）。**决策 1~6、任务拆解 T-01~T-17、需求条款均不受影响。**

# 技术方案：user-role-group-model

> 本轮为 Phase 2 稿：**坑核对 / 方案概述 / 迁移与回滚 / 接口设计 / 数据模型变更 / 共享面身份矩阵（A 路已落证）/ 关键决策 / 风险 / 依赖** 已成文；
> 全部章节已成文（三路只读调研 A/B/C 已回填 + v2.0.0 范围变更已同步）；坑核对覆盖 **L-01~L-44 共 44 条**（相交 15 条）；Q-P1~Q-P6 六项裁定全部落账，待确认问题已归零。**本文件已于 2026-10-07 经陈卓确认②。**

## 坑核对（必填，确认②审这一节）

已核对 `specs/_project/lessons.md` **L-01~L-44 共 44 条**（该台账头部规则：active 条目每次 Plan 强制核对；其中 L-15~L-44 无 `- 状态:` 行，按默认 active 处理）。**相交并已避开（15 条）**：
其中 L-15~L-42 无 `- 状态:` 行，按默认 active 处理）。**相交并已避开**：

| 坑 | 本方案如何避开 |
|---|---|
| **L-01** 文本补丁静默脱靶（头号惯犯） | 本方案是**大规模删除/改名**（Java 引用 227 处 + 前端整目录），正确性**只认编译器**：删改后以 `mvn clean install -Dspring-javaformat.skip=true` 全量构建为裁判，禁止用文本批量替换删代码；文档/台账编辑一律走带 `assert count==1` 的 edit 助手 |
| **L-06** 多入口枚举缺失（含"删共享资源前枚举消费方"） | 组织维度有 **6 类入口**：DDL 表 → Java 实体/服务 → REST 端点 → 菜单记录 → 前端路由/页面 → API 客户端；第 7 类是**同步模块消费方**。方案要求逐类清单化（§涉及模块与数据流），删表/删模块前先列全消费方 |
| **L-13** `git add` 面过宽吞大产物 | 本 spec 删除大量文件，`git add -A` 会把 `backups/*.sql`（40MB 级 dump）一起吞。**提交一律显式列路径**，提交前用 `git status --porcelain` 核对清单 |
| **L-14** md 表格错乱（台账类系统性暗伤） | 每次改文档回验 `mdtable_check.py`（铁律 8） |
| **L-21** MyBatis-Flex 逻辑删列让墓碑行隐形 | `tbl_privilege_user_role`、平台组三张关联表均带 `del_flag` 逻辑删列 ⇒ 凡需统计**含已删行**的判定（如 R-07 的存量角色盘点要用"活跃角色"口径）必须走**原生 SQL**，不得用 QueryChain（会自动追加 `del_flag=0`） |
| **L-26** 项目"业务失败"也返回 HTTP 200 | R-08 的"权限拒绝"断言必须解析响应体 `code`（本项目 100/200=成功），**禁止**用 `-w '%{http_code}'` 单独下结论 |
| **L-27** 改身份源后必须对迁移后状态回归 | R-07 是"存量用户补角色"，验证必须打在**迁移后的真实账号**上（含经 `tbl_unified_account_map` 映射过的账号），一次性新建账号只能证明新路径 |
| **L-29** 判断"某列指向哪张表"必须实测 join | R-05 恰好踩这条的正解：`tbl_privilege_acl.module_id` **就是菜单 id**（实测 join 已证），`module_sn` 是**过期冗余标签**（4 行全写 `agentManagerIndex`）⇒ 过滤逻辑必须用 `module_id`/`url`，**禁用** `module_sn` |
| **L-31** 数据驱动"抄一行"必须 dump 整行逐列核对 | 若需新增/复制菜单行（如组管理入口），先 dump 母版行逐列核对三态（继承/覆盖/置空）；现网菜单 27 行中 24 行带 `image` 图标，图标属验收面 |
| **L-38** 框架权限规则语义不能凭类名推断 | R-05 若复用框架声明式权限机制（Sa-Token 注解/拦截器），必须先读其匹配语义（`javap` 或一次可控实验）证明规则真生效，再写进 tasks 验证方式 |
| **L-40** 手工执行 SQL 必须补登迁移台账 | 项目迁移器 = `docker/init/migrate.sh`（扫 `/releases/v*/sql/V*.sql`，`sort -V` 排序，逐文件开事务 `-1`，成功后写 `tbl_phoenix_release(seq,file)`）；本版是**破坏性 DDL**，任何手工 `psql` 执行必须同批补登台账，且 SQL 仍须自幂等（BUG-115 已证台账可能缺行） |
| **L-41** 同名分支与 tag 需显式 refspec | `v2.0.0` 分支与将来的 tag 同名 ⇒ 发布/推送一律写 `refs/heads/x:refs/heads/x` / `refs/tags/x:refs/tags/x`，状态判断用显式 ref |
| **L-42** 发布后补账提交悬在版本分支 | 本版收尾补账提交必须**同轮合回版本分支**；M4 前自检 `git log --oneline main..refs/heads/<分支>` |
| **L-43** 解析 psql COPY dump 转义差一字符 ⇒ 计数全 0（本轮自产） | 本 spec 的范围决策**大量依赖生产 dump 统计**（组织三表行数、组表是否死代码、ACL 失真面）⇒ 已按该坑纪律执行：**解析块数必须等于 COPY 行数**才采信（本次 63/63 一致），单表出现「全 0/异常巨大」先怀疑解析器，删/留数据的结论必须**代码引用面 + 数据行数**双证据（组表 606→0 的教训即此） |
| **L-44** 能力命名 ≠ 业务形态（本轮自产） | 本文件曾把 `ThirdPartyLoginStrategy` 从类名误命名为「扫码登录」并扩散 26 处 ⇒ 已全库订正为「第三方免登（应用内 SSO）」；纪律：给能力起中文名前必须齐 **调用方 / 入参来源 / 端点** 三件证据，类名只能生成「假设」而非事实 |

**其余 29 条**（L-02~L-05、L-07~L-12、L-15~L-20、L-22~L-25、L-28、L-30、L-32~L-37、L-39 —— 即 44 条减去上表 15 条）按标题与触发场景逐条核对，与「用户体系改造 + 破坏性迁移」**无直接相交**；其中 **L-07（假阳性验证）/ L-30（管道吞掉退出码）/ L-05（shell 方言）/ L-11（用户环境 ≠ 我以为的环境）** 作为通用纪律适用于本 spec 全部验证步骤。

## 方案概述

三条主线，一次版本完成（Q2 裁定不分期）：

1. **下线**（R-01/R-03/R-06/R-09/**R-11**）——删除组织维度（三表 + 用户模型三表组织列 + 6 类入口）、下线**三方平台集成三层**（同步 / 客户端内免登 / 平台配置，含 SDK、`PlatformTypeEnm`、前端「三方平台」页面与菜单行）、下线后台数据组死代码。**只保留账号密码登录**。
2. **收敛**（R-02/R-04/R-09）——用户模型收敛为 用户 / 角色 / 组；组唯一化到平台侧资源授权组，组直接关联智能体 / 技能 / 知识库 / MCP。
3. **生效**（R-05/R-07/R-08）——角色过滤菜单生效（ACL 预置数据转判定源）、存量账号补角色与组、预设问题接口加角色校验。

配套：破坏性迁移走「备份 → 结构变更 → 数据订正 → 应用校验 → 回滚配对」五段；SQL 在 Implement 期落 `specs/20261007_user-role-group-model/sql/`，M3 汇总时聚合成 `releases/v2.0.0/sql/V2.0.0_0x__*.sql`。

## 迁移与回滚策略（破坏性变更的五条纪律）

1. **备份先于删除**：全量 `pg_dump` + **组织维度专项导出**（三张表 + 用户表组织列相关数据）→ `backups/pre_v2.0.0_orgdim_<ts>.sql`。
   ⚠️ 这是 R-03「回滚后行数与升级前一致」**唯一可行基础**——表删掉后，结构回滚脚本无法凭空恢复数据。此约束必须写进 `UPGRADE.md`，不得让运维误以为 rollback 脚本自带数据。
2. **执行顺序**：① 台账在册核对 → ② 全量 dump + 专项导出 → ③ DDL（**先去约束/索引依赖 → 再 DROP COLUMN → 最后 DROP TABLE**）→ ④ DML（R-07 补角色、菜单与 ACL 清理、组相关订正）→ ⑤ 应用启动 + 接口校验。
3. **回滚配对**：每个正向 SQL 一份 `sql/rollback/` 同名逆序脚本（结构回滚 + 依赖备份的数据回填说明）；M3 汇总时进 `releases/v2.0.0/sql/rollback/`。
4. **幂等优先**：DDL 用 `DROP ... IF EXISTS` / 存在性判断；DML 用 `WHERE NOT EXISTS` / `ON CONFLICT DO NOTHING`。理由：`migrate.sh` 靠 `tbl_phoenix_release` 判重，而 **BUG-115 已证台账可能缺行**（手工执行未登记）⇒ 不能假设"台账在册=不会重跑"。
5. **命名沿用项目既成惯例**：`V2.0.0_<序号>__<snake_case描述>.sql` + `rollback/…_rollback.sql`（v1.6.0/v1.7.0 实际用法）。注：`references/milestone.md` 模板写的是 `R<版本>_<序号>__…` 前缀，与项目实践不一致 ⇒ 本 spec 不擅自定，**M3 汇总时统一裁决**并在 UPGRADE.md 注明。

## 涉及模块与数据流

### A. 删除面：组织三维度（Java **227 处 / 26 文件**，实测口径）

| 分类 | 数量 | 文件 |
|---|---|---|
| **整文件可删** | 22 | `phoenix-privilege-api`：`entity/{PrivilegeCompany,PrivilegeDepartment,PrivilegeEmployee}.java`、`dto/{PrivilegeCompanyDTO,PrivilegeDepartmentDTO,PrivilegeEmployeeDTO}.java`、`dto/query/PrivilegeCompanyQuery.java`、`vo/{PrivilegeCompanyVO,PrivilegeDepartmentVO,PrivilegeEmployeeVO}.java`（10）；`phoenix-privilege-core`：三个 `*Mapper.java`、`I*Service.java`、`*ServiceImpl.java`（8）；`phoenix-privilege-rest`：三个 `*Controller.java`（3）；`phoenix-common-core`：`service/sync/AbstractPlatformSyncStrategy.java`（1，随同步包整删） |
| **文件内部分改** | 3 | `AccountInfoServiceImpl.java`（import:38、字段:67、唯一调用 401-404）、`PrivilegeUserServiceImpl.java`（import:12-13、`pageByQuery` select:136 + leftJoin:137-140 + eq:141-142）、`PrivilegeUserController.java`（import:9-14、字段:27-28、`toVo` 54-72 中公司/部门回填 59-70） |
| **227 之外必删的 VO**（易漏） | 2 | `vo/DepartmentTreeVO.java`、`vo/OrganizationTreeVO.java`（仅被部门 Controller/ServiceImpl 使用） |
| **删后成孤儿（不报错但须清）** | 6 | `service/platform/{DingTalk,Feishu,Weixin}SdkService(+Impl)`——引用于三个 `*SyncStrategy` 与被删的两个组织 Service；**`PlatformInfoService` 必须保留**（第三方免登依赖） |

### B. 字段级：组织列横跨 4 处（Q-P3 已裁定「全删」）

| 对象 | 列（实体行号） |
|---|---|
| `tbl_privilege_user` | `employeeId`:23、`companyId`:53、`deptId`:56、`itUserId`:66、`itUserName`:69、`isLeader`:72；另 `companyName`/`deptName` 是 `@Column(ignore=true)` 回填列（59/63） |
| `tbl_privilege_role` | `companyId`（**`Long`**，`PrivilegeRole.java:36`；DTO:32 / Query:19 / VO:41；服务签名 `getByCompanyId(Long)`） |
| `tbl_platform_account_info` | `employeeId`（`AccountInfo.java:56`）、`deptId`:58、`deptName`:60 |
| `LoginVO`（phoenix-common-api） | `deptIds:21` —— **只写不读的死载荷**，随组织维度一并删 |

### C. 三方集成三层删除面（**v2.0.0 范围变更：三层同批下线**）

**第 1 层 · 同步**：`service/sync/{PlatformSyncStrategy,AbstractPlatformSyncStrategy,PlatformSyncFactory}.java`、`sync/{dingtalk,feishu,weixin}/*SyncStrategy.java` 与 `*SyncConstants.java`、`model/dto/{SyncUserDTO,SyncDeptDTO}.java`、`controller/sync/PlatformSyncController.java`、前端 `api/core/platform-sync.ts` + `api/core/index.ts` 再导出。
**第 2 层 · 免登**：`service/thirdparty/**`（`ThirdPartyLoginStrategy`、`ThirdPartyLoginFactory`、三个 `*ThirdPartyLoginStrategy`）、`AccountInfoServiceImpl.thirdPartyLogin`(:346-378) 与 `getByThirdPartyId`(:184-186)、`AccountInfoController GET /third-party/{thirdPartyId}`(:69-71)、`AccountLoginController POST /auth/thirdLogin`(:37-39)、`AccountInfo.thirdPartyId`(:54) 与 `tbl_platform_account_info.third_party_id` 列、`PlatformTypeEnm`；mobile-ui 的 `trySsoLogin()`/`getAuthCode`/`platformService.thirdPartyLogin`（**app 保留**，Q-P6）。
**第 3 层 · 平台配置**：`PlatformInfoController`(`/platform/platform-info/**`)、`PlatformInfoService(+Impl)`、`PlatformInfoMapper`、`PlatformInfo` 实体、`tbl_platform_platform_info` 表、`service/platform/{DingTalk,Feishu,Weixin}SdkService(+Impl)`、前端 `views/platform/platform-info/**` + `api/core/platform-info.ts` + 菜单行「三方平台」（`/basic/platform-info`，module id `37def68697b54109a57c18508fc4358c`）。
**原 R-06 边界 4 约束已自动解除**：前版为保住免登编译要求「保留 3 个 `*SyncConstants`」；第 2 层删掉三个登录策略后无此需要 ⇒ `service/sync/**` **整包删除、不留保留项**。
**两条平行死端点**（phoenix-privilege，非 sync 包，全仓零调用）：`POST /api/privilege/department/sync`、`/sync-children/{deptId}`、`POST /api/privilege/employee/sync`、`/sync-by-dept/{deptId}` ⇒ 随组织 Controller 整删。

### D. 前端删除面（`apps/admin-ui`）

**整删 18 个**：`views/organization/{company,department,employee}/{index.vue,form.vue,data.ts}`（9）+ `api/core/{privilege-company,privilege-department,privilege-employee,platform-sync}.ts`（4）+ `components/dept/{DepartmentSelector,EmployeeSelector}.vue`（2）+ **R-11 追加** `views/platform/platform-info/{index.vue,form.vue}`（2）+ `api/core/platform-info.ts`（1）。**mobile-ui 侧**：删 `services/platformService.ts` 的 `getEnabledPlatform`/`thirdPartyLogin` 与 `main.ts:47-89` 的 `trySsoLogin()`（app 保留，登录改走 `LoginPage.vue` + `POST /auth/login`）。
**部分改 10 个**：`api/core/index.ts:16-19`（删 4 行导出）；`views/system-management/account/{index.vue,form.vue,data.ts}`；`views/account/account-info/{index.vue,form.vue,data.ts}`；`api/core/{platform-account-info.ts,privilege-user.ts,privilege-role.ts}`。
`components/dept/DeptTreeSidebar.vue` 被 **3 处**引用（两个账号页 + 员工页）⇒ **不是 organization 专用**，须先解除引用再删。
**路由零改动**（`router/access.ts` 用 `import.meta.glob` + 后端 `component` 字段动态解析）；`apps/admin-ui/dist/**`、`docker/.stage/dist/**` 是构建产物 ⇒ **重新构建覆盖，不手工改**；`pc-ui`/`mobile-ui` 对上述关键词**零命中**。

### E. 改造后数据流

```
登录 POST /api/privilege/auth/login
  └─ 写 session LOGIN_USER_INFO（不再带组织归属；不再写 LOGIN_ACLS 快照）
前端 generateAccess → GET /api/privilege/auth/menus
  └─ LoginServiceImpl.getUserMenus()          ← 【唯一改动点】
       ├─ 超管 (role_id=428007432736870400) → 全量存活菜单 + 全权限位
       └─ 其他角色 → ACL(release_id=roleId ∩ module_id) 命中菜单，权限位按角色授予
组授权 → tbl_platform_group_{agent,kbase,skill,mcp}_info
  └─ 前台可见性：getMyAgents（组∩已发布）/ FrontSkillAccessServiceImpl.MY_SKILLS_SQL / FrontMcpAccessServiceImpl.EFFECTIVE_FRONT_SQL
删除面：组织三表 + 用户模型三处组织列 + 后台数据组 + 三方集成三层（同步 / 免登 / 平台配置：含 3 个 SdkService、PlatformTypeEnm、third_party_id、前端 platform-info 页与「三方平台」菜单行）
```

## 接口设计

遵守 `.specrc.yml` 路由（api-design = global）与**本项目既有信封约定**：HTTP 层恒 200，成败在响应体 `code`（100/200 成功）——验证一律断言 `code`（L-26）。

**A. 随组织维度一并删除的端点**

| 端点族 | 载体 | 备注 |
|---|---|---|
| `/api/privilege/company/**` | `PrivilegeCompanyController`（`@RequestMapping` :20） | 随文件整删 |
| `/api/privilege/department/**` | `PrivilegeDepartmentController` | 含两条**死端点** `POST /sync`(:156)、`/sync-children/{deptId}`(:165)（全仓零调用） |
| `/api/privilege/employee/**` | `PrivilegeEmployeeController` | 含两条**死端点** `POST /sync`(:76)、`/sync-by-dept/{deptId}`(:85) |
| `/platform/sync/**`（6 条） | `PlatformSyncController`：`/all`(:19)、`/departments`(:25)、`/users`(:31)、`/depts/{deptId}`(:37)、`/depts/users/{deptId}`(:43)、`/users/{userId}`(:49) | 前端唯一消费方 `api/core/platform-sync.ts` 与 `views/organization/{department,employee}/index.vue` 一并删 |
| `/api/privilege/role/company/{companyId}` | `PrivilegeRoleController:34-36` + `getByCompanyId(Long)` | 随 `PrivilegeRole.companyId`（Q-P3 全删）一并移除 |
| `/auth/thirdLogin` | `AccountLoginController:37-39` + `AccountInfoServiceImpl.thirdPartyLogin:346-378` | **R-11**：端点随免登整删 ⇒ 404 |
| `/platform/platform-info/**`（含 `/getEnabledPlatform`、`/getEnabled`） | `PlatformInfoController` 全量 CRUD | **R-11**：随平台配置整层删除 ⇒ 404；前端「三方平台」菜单行与页面一并下线 |

删除后 SHALL 返回 404（路由不存在），**不是 500**。

**B. 行为变更（不新增端点，但语义破坏性）**

| 端点 | 现状 | 变更后 |
|---|---|---|
| `GET /api/privilege/auth/menus` | 返回**全表**存活菜单（22 条，含 4 条 `is_show=0`），每个菜单 `state` 为**满权限位** | 返回**按当前用户角色过滤**后的菜单树；`state`/`pvalues` 按角色授予计算；超管豁免（`role_id='428007432736870400'`） |
| `GET /api/privilege/auth/getLoginUserInfo` | 由 `PrivilegeUser` 实体拷贝（无 roles） | **不改**（`accessMode='backend'` 下前端不依赖 roles；改动会扩大面） |

响应**结构不变**（仍 `{code,msg,data:{menus,pvalues}}`）⇒ 前端零改动；但"同一账号可见菜单集合变小"是用户可感知的破坏性行为变化，须写入 `RELEASE-NOTES.md`。

**C. 新增端点**：**后端零新增**。R-04 四类资源中，**组侧已有端点仅 2 类**——组↔智能体（`POST /platform/group-agent-info`、`DELETE /group/{gid}/agent/{aid}`）、组↔知识库（`PUT /platform/group-kbase/{gid}/assign`，差集幂等）；**组↔技能 / 组↔MCP 只有「资源侧」单向授权**（`PUT /api/skill/{id}/groups`、`PUT /api/mcp/{id}/groups`），组管理页无对应操作 ⇒ **R-04 需补齐这两类的组侧读写（工作量计入本 spec）**。R-08 落点：`AgentPresetQuestionController` 的 `POST /{agentId}/preset-questions`(:53-76) 与 `DELETE /{agentId}/preset-questions/{questionId}`(:81-91) 加角色校验（现状零校验，见 BUG-122）。

**D. 若 Q-P2 裁定纳入 BUG-116**：修复前端 `assign-menu.vue` 的 `currentRole` 赋值链，复用既有端点 `POST /api/privilege/acl/saveAll/{releaseId}/{checkStatus}`、`POST /api/privilege/acl/saveModule`；**不新增后端端点**。

**E. 错误码**：R-08 的权限拒绝复用项目既有权限错误码——**具体码值待 Implement 时从常量类核对，禁止臆造**（api-design § 6：错误码集中维护）。

## 数据模型变更

### 一、事实基线（生产 dump 实测，2026-10-07）

| 对象 | 生产行数 | 组织列非空率 | 备注 |
|---|---|---|---|
| `tbl_privilege_company` | **4** | `idm_company_id` 0/4、`third_id` 0/4 | 唯一索引 `idx_tbl_privilege_company_code` |
| `tbl_privilege_department` | **17** | — | `company_id` NOT NULL |
| `tbl_privilege_employee` | **14** | `third_user_id`/`third_open_id`/`third_union_id` 均 **0/14** | 外部对接列全空 |
| `tbl_privilege_group` | **0** | — | 代码 0 引用 + 数据 0 行 ⇒ **R-09「死代码」定性双重成立** |
| `tbl_privilege_user` | 7（存活 5） | `company_id` 7/7、`dept_id` 7/7、`employee_id` 6/7、`is_leader` 7/7、`it_user_id`/`it_user_name` **0/7** | **NOT NULL 强约束实证** |
| `tbl_privilege_role` | 2 | `company_id` 1/2 | **角色表也带组织列**（原需求未点名，属 R-02「用户模型」范畴） |
| `tbl_platform_platform_info` + `PlatformInfoController/Service` + 3 个 SdkService | ① 同步模块读（corpId/secret）② 免登读（`AccountInfoServiceImpl:351 getEnabledByType` → `ThirdPartyLoginFactory`）③ 前端「三方平台」页 CRUD ④ **无其他业务消费方** | **R-11：整体删除**（表 + CRUD + 页面 + 菜单行）——三层同批下线的收口；删表前专项导出留档 | T-xx |
| `tbl_platform_account_info`（前台账号） | 2 | `dept_id` 2/2、`employee_id` 1/2、`dept_name` 1/2、`third_party_id` 0/2 | **前台账号表也带组织列** |
| `tbl_privilege_acl` | 73 | — | 见 BUG-117（50 行挂非角色 id、44 行孤儿 module_id、普通角色 0 行） |
| `tbl_privilege_module` | 27 | — | 其中 4 行为组织菜单 |
| `平台组四表` | 1 / 4 / 4 / 11 / 6；account_group 2 | — | 组语义唯一化的存活方 |

**范围澄清（请在确认②时确认或否决）**：R-02/R-03 原文说"用户表上的组织列"。数据模型上"用户模型"实际横跨**三张表**：`tbl_privilege_user`、`tbl_privilege_role`（`company_id`）、`tbl_platform_account_info`（前台账号）。本方案按"**用户模型相关表一律去组织列**"执行；若你要求只动 `tbl_privilege_user`，请否决并说明保留范围。

**索引/视图/外键依赖核对（已实测）**：三张组织表**无任何外键被引用**（仅自身 PK）、**无视图/触发器/函数**依赖、组织列上**无索引**（唯一例外是 company 表自身的 `code` 唯一索引，随表删除）⇒ DDL 可安全 `DROP`，无需 `CASCADE`。

**活库现值复核（T-01 门禁统计，2026-10-07；容器 `phoenix-release-postgres-1`／DB `phoenix`，已应用 `V1.7.0_01~04`）**——上表为**迁移前 dump 快照**，下表为**活库现值**，冲突时以本表为准：

| 指标 | 迁移前 dump | 活库现值 | 影响 |
|---|---|---|---|
| 组织三表 | 4 / 17 / 14 | **4 / 17 / 14**（一致） | 无 |
| `tbl_privilege_group` | 0 | **0** | R-09「死代码」定性不变 |
| `tbl_platform_platform_info` | — | **0 行（空表）** | R-11 删表**零数据损失**（仅结构） |
| ACL 存活行 | 73（含墓碑） | **61** | 决策 6 清理面以 61 为准（含 **30 行孤儿 `module_id`**、**36 行挂非角色 id**） |
| 超管可用菜单 | 23 | **24 / 27**（缺「三方平台」「请求管理」「基础管理」） | 与决策 6 一致 |
| **普通角色 ACL 行** | 0 | **1**（智能体中心 `/agent/chat`） | ⚠️ **订正** plan 前文「0 行」 |
| **普通角色名下存活用户** | —（seed 口径 10） | **5** | ⚠️ **订正** plan 前文「10 用户」 |
| 零角色存活用户 | — | **2 / 5** | R-07 补齐对象 |
| 零组前台账号 | — | **1 / 1** | R-04 口径影响面 |
| 组授权（del=0 / del=1） | 4/4/11/6 | agent 4/0、kbase 3/2、skill 5/6、mcp 1/5 | BUG-121 墓碑行**实证存在** |

证据：`evidence/T-01_gate-stats.txt`、`evidence/T-01_role-menu-facts.txt`、`evidence/T-01_compile-baseline.raw.txt`（基线构建）、`evidence/T-01_backup-manifest.txt`（备份清单）。
**关键推论**：启用菜单过滤后，普通角色 5 个用户**只剩「智能体中心」1 个菜单**，而前端 `homePath` 默认 `/agent/list` 未授权 ⇒ **落地即 404/白屏**，T-07 必须在该账号上验证。

### 二、DDL 草案（Implement 期落 `specs/{本目录}/sql/`，M3 汇总为 `V2.0.0_0x__*.sql`）

```sql
-- V2.0.0_01__org_dimension_drop_ddl.sql  （暂定序号，M3 汇总时复核重排）
BEGIN;
-- ① 用户模型相关表：去组织列（无索引/约束依赖，可直接删列）
ALTER TABLE tbl_privilege_user
  DROP COLUMN IF EXISTS company_id,
  DROP COLUMN IF EXISTS dept_id,
  DROP COLUMN IF EXISTS employee_id,
  DROP COLUMN IF EXISTS it_user_id,
  DROP COLUMN IF EXISTS it_user_name,
  DROP COLUMN IF EXISTS is_leader;
ALTER TABLE tbl_privilege_role          DROP COLUMN IF EXISTS company_id;
ALTER TABLE tbl_platform_account_info
  DROP COLUMN IF EXISTS employee_id,
  DROP COLUMN IF EXISTS dept_id,
  DROP COLUMN IF EXISTS dept_name,
  DROP COLUMN IF EXISTS third_party_id;  -- R-11：免登标识
-- ② 组织三表（无外键引用，直接删；company 的 code 唯一索引随表消亡）
DROP TABLE IF EXISTS tbl_privilege_employee;
DROP TABLE IF EXISTS tbl_privilege_department;
DROP TABLE IF EXISTS tbl_privilege_company;
-- ③ 后台数据组（R-09 单向下线：代码 0 引用 + 数据 0 行）
DROP TABLE IF EXISTS tbl_privilege_group;
-- ④ 三方平台配置表（R-11：免登与同步是它的仅有消费方；删前随全量 dump 留档）
DROP TABLE IF EXISTS tbl_platform_platform_info;
COMMIT;
```

**幂等性**：DDL 全部 `IF EXISTS` ⇒ 重跑安全（不依赖迁移台账，因 BUG-115 已证台账可能缺行）。

### 三、回滚策略（结构可回滚，数据依赖备份——必须写进 UPGRADE.md）

```sql
-- rollback/V2.0.0_01__org_dimension_drop_ddl_rollback.sql
BEGIN;
CREATE TABLE IF NOT EXISTS tbl_privilege_company ( ... );   -- 结构照 all_schema.sql:3287 复制
CREATE TABLE IF NOT EXISTS tbl_privilege_department ( ... ); -- :3338
CREATE TABLE IF NOT EXISTS tbl_privilege_employee ( ... );   -- :3444
CREATE TABLE IF NOT EXISTS tbl_privilege_group ( ... );      -- :3531（0 行，无数据可回填）
ALTER TABLE tbl_privilege_user
  ADD COLUMN IF NOT EXISTS company_id varchar(255),
  ADD COLUMN IF NOT EXISTS dept_id    varchar(255),
  ADD COLUMN IF NOT EXISTS employee_id varchar(65),
  ADD COLUMN IF NOT EXISTS it_user_id  varchar(255),
  ADD COLUMN IF NOT EXISTS it_user_name varchar(32),
  ADD COLUMN IF NOT EXISTS is_leader   int4 DEFAULT 0;
ALTER TABLE tbl_privilege_role          ADD COLUMN IF NOT EXISTS company_id varchar(255);
ALTER TABLE tbl_platform_account_info
  ADD COLUMN IF NOT EXISTS employee_id varchar(64),
  ADD COLUMN IF NOT EXISTS dept_id     varchar(64),
  ADD COLUMN IF NOT EXISTS dept_name   varchar(128);
-- ⚠️ 回滚只恢复【结构】：三表 35 行数据与用户/角色/账号的组织列取值
--    必须从 backups/pre_v2.0.0_orgdim_<ts>.sql 专项导出回填；回填后如需恢复
--    NOT NULL 语义，须先确认回填完成再 SET NOT NULL（否则 DDL 直接失败）
COMMIT;
```

### 四、DML（R-05 / R-07 / R-08 的数据侧）

| 序号 | 内容 | 幂等手法 |
|---|---|---|
| `V2.0.0_02__role_backfill_dml.sql` | R-07 存量可登录用户补 ≥1 角色；孤儿 `user_role` 行按"保留+记账"处置（BUG-118） | `INSERT ... WHERE NOT EXISTS` |
| `V2.0.0_03__org_menu_cleanup_dml.sql` | 删 4 条组织菜单行（`2bafd881…`、`5db7285…`、`8ec79aa…`、`90511f3…`）及其 ACL 引用行（4 行，挂 `428007432736870400`） | 按固定 id `DELETE`，可重跑 |
| `V2.0.0_04__acl_baseline_rebuild_dml.sql` | 决策 6 的 ACL 基线重建（超管全量 + 普通角色基线集合）＋ 清理 50 行非角色 id 与 44 行孤儿 module_id | `WHERE NOT EXISTS` + 先导出留档再 `DELETE` |
| `V2.0.0_05__preset_question_acl_dml.sql` | R-08 若需补权限数据（无则免） | 待 Implement 定 |

**执行顺序**：`DROP DDL(01)` → `角色补齐(02)` → `菜单清理(03)` → `ACL 重建(04)` → `R-08(05)`。理由：ACL 依赖角色存在；菜单清理先于 ACL 重建（否则清理会把刚建的基线删掉）。**序号在 M3 汇总时统一分配，本表仅为依赖顺序声明。**

## 共享面身份矩阵

已由权限菜单链路调研（A 路）落证；组织维度/同步链路（C 路）与用户·组链路（B 路）行补完后**本表即 Tasks 断言清单源**。

| 对象 | 既有身份（方法 × 调用方 × 端点/版本） | 变更后预期行为 | 断言归属 |
|---|---|---|---|
| `GET /api/privilege/auth/menus` | ① 后台导航渲染（`router/access.ts:116-129` → `convertModuleTreeToRoute`）② 直接 URL 访问任意菜单路由 | ① 返回**按角色过滤后**的菜单树（`type='1'` 才带 component；目录 `type='0'` 无 component）② 未授权路由不再出现在动态路由表 ⇒ 落 404 | T-xx |
| `LoginServiceImpl.getUserMenus()`（`:118-135`） | ① 上述菜单接口唯一实现 ② 未登录直调（`/api/privilege/auth/*` 被全局过滤器 exclude，靠 session 判空） | ① 改为按角色 ACL 过滤 + 超管豁免 ② 未登录仍抛 NotLoginException（行为不变） | T-xx |
| `buildAdminAclMap()`（`:173-183`） | ① `getUserMenus` 的 state 填充（当前给每个菜单满权限位） ② 前端 `accessStore.setAccessCodes` 的权限码来源 | ① 改为按角色授予的权限位 ② 按钮级权限**由恒真变为按角色**（否则 R-05「操作」一半落空） | T-xx |
| `tbl_privilege_acl` | ① 维护写入方（`PrivilegeAclController` PUT/DELETE/`saveAll`/`saveModule`）② **判定读取方（本版新增）** | ① 写入路径不变（但界面写路径坏 → BUG-116）② 成为菜单可见性判定源 | T-xx |
| `tbl_privilege_user_role` | ① 角色分配维护（`/api/privilege/user-role/batch-save|batch-remove`，界面正常）② `LoginServiceImpl:80` 算 `hasAdminRole` ③ `PrivilegeAclServiceImpl:43` | ①②③ 行为不变；但**孤儿行（BUG-118）须先订正**，否则过滤结果不可预期 | T-xx |
| `AgentKnowledgeMapper:32-42` | 硬编码 `role_id='428007432736870400'` 判超管（既有先例） | 保持兼容：超管定义沿用该 role_id（新增豁免逻辑须与之一致，禁造第二套超管口径） | T-xx |
| `tbl_platform_platform_info` | ① 同步模块读（三个 `*SyncStrategy` 取 corpId/secret）② **第三方免登（应用内 SSO）读**（`AccountInfoServiceImpl:351 getEnabledByType` → `ThirdPartyLoginFactory`） | ① 随同步模块删除而失去消费方，**表与 `PlatformInfoService` 保留** ② 第三方免登**必须仍可用**（唯一硬耦合是 3 个 `*SyncConstants`，决策 3 方案 1 保留之） | T-xx |
| `POST /platform/sync` 等 6 条 | ① 前端 `platform-sync.ts`（唯一调用方）② 无任何后端内部调用方 | ① 前端随 organization 页面整删 ② 端点 404（不存在），非 500 | T-xx |
| `POST /auth/thirdLogin` | ① `apps/mobile-ui` 启动 `trySsoLogin()`（唯一调用方）② admin-ui 侧入口本就关闭（`basic.vue:156`） | **R-11：端点与策略整删** ⇒ 404；mobile-ui 保留但改走密码登录 | T-xx |
| `POST /auth/login`（密码登录） | ① 后台/前台/移动端共用 ② 唯一保留的登录方式 | **行为不变**（回归断言：后台账号与前台账号均登录成功） | T-xx |
| `tbl_privilege_user` | 登录 / 鉴权（Sa-Token `StpUtil`）/ 账号管理 / 前台 chat / `getLoginUserInfo`（`BeanUtils.copyProperties` 从**实体**拷贝）/ 原同步的原始 SQL 写入 | 组织列移除后各身份行为不变，唯一变化=字段集；注意 `PrivilegeUserVO.roles` 永不填充（前端 `userRoles` 恒空，`accessMode='backend'` 下不影响路由） | T-xx |
| `tbl_platform_group_*` 五表 | ① 组管理页维护（agent/kbase 有组侧端点；skill/mcp 仅资源侧）② 前台可见性解析：`getMyAgents`（`GroupAgentInfoServiceImpl.getByGroupIds:21-25`）、`FrontSkillAccessServiceImpl.MY_SKILLS_SQL:27-37`、`FrontMcpAccessServiceImpl.EFFECTIVE_FRONT_SQL:24-34` | 成为唯一组语义；授权即时生效——**但读侧 `del_flag` 口径不统一（BUG-121），须先统一再验收** | T-xx |

## 关键决策

### 决策 1：一次性破坏性删除 vs 数据库规范的「先加后删、跨版本兼容」

- **采用**：一次性删除（MAJOR 版本内完成）。
- **理由**：`standards/database-design.md` §22 的"先加后删/双写/跨多版本"是为**在线灰度**服务；本次是用户明确裁定的破坏性重构（Q3 = 删除），且 `company_id`/`dept_id` 是 **NOT NULL 强约束**——任何"保留期"都会让"新建用户必须选部门"的老问题继续存在，直接违背 R-02。保留期成本 > 收益，且本项目无灰度环境。
- **被拒绝**：保留列 + 双写过渡两版再删 —— 理由：过渡期 UI 与 API 仍需维护组织字段与两套写入路径，违背"删除"的需求实质；无灰度环境无法验证中间态。
- **例外声明**：规范条款不被豁免，而是**显式记例外 + 补偿措施**（pre-migration 备份 + 回滚配对 + UPGRADE 风险告知 + 本决策留档）。重新评估条件：若未来出现不可停机窗口，则改为先加后删。

### 决策 2：组唯一化 = 单向下线 vs 数据合并迁移

- **采用**：单向下线（删 `tbl_privilege_group` 及其 `PrivilegeGroupVO`）。
- **理由**：**双面取证**——① 代码面：全仓检索零 mapper/service/controller/前端引用（仅 1 个自声明 VO `PrivilegeGroupVO`），4 个 `ExceptionEnum`「数据组」错误码亦零引用；② **数据面：seed（`all_data.sql`/`all_schema.sql`）与生产 dump 均 0 行**（本次曾因解析转义错误一度误得「606 行」，已记 L-43）。无消费方、无数据、无映射规则可写。
- **被拒绝**：把 `tbl_privilege_group` 的 `super_id`/`type`/`state` 迁进平台组表 —— 理由：平台组表无对应语义列，且无任何消费方，迁移=凭空造需求。
- 重新评估条件：若调研发现遗漏引用，或生产表存在非空业务行且能证明用途。

### 决策 3：三方集成三层的下线方式（**v2.0.0 范围变更后重写**）

- **采用**：**三层同批整删，不留保留项**——① 同步（`service/sync/**` 含 `*SyncConstants` + `PlatformSyncController` + 前端 `platform-sync.ts`）；② 免登（`service/thirdparty/**` + `POST /auth/thirdLogin` + `third_party_id` + `PlatformTypeEnm` + mobile-ui 的 SSO 代码）；③ 平台配置（`PlatformInfo` 相关四件 + `tbl_platform_platform_info` + 3 个 SdkService + 前端页面与「三方平台」菜单行）。
- **理由**：三层的**仅存消费方就是彼此**（同步与免登是平台配置的唯一读者；SDK 只被同步与被删的组织 Service 使用）⇒ 单独保留任一层都会留下**无消费方的死表 / 死页面**；且用户 2026-10-07 明确「只保留现在的密码登录」（Q-P5 / Q-P6 裁定）。
- **被拒绝（前版方案 1，已作废）**：保留 3 个 `*SyncConstants` 常量文件以保住免登编译 —— 其前提是「免登保留」，该前提已被用户推翻 ⇒ 无此需要，`service/sync/**` 整包删除。
- **被拒绝（前版 Q4 口径）**：只删免登、保留平台配置页 —— 留死配置页无业务意义；用户已在 Q-P5 选「一起下线」。
- **被拒绝**：连 `apps/mobile-ui` 一起下线 —— 用户 Q-P6 裁定 app 保留（自带密码登录页与 `POST /auth/login` 调用）。
- **代价与补偿**：移动端用户在钉钉/企微/飞书客户端内**失去免登**，须手动输入账号密码（功能降级）⇒ 已写入 R-11 与 `RELEASE-NOTES` 的「变更 / 已知影响」，并配密码登录回归断言；三方平台配置数据（corpid/secret 等）删表前**随全量 dump + 专项导出留档**。
- 重新评估条件：若将来要重新引入第三方登录或同步，须**另立 spec**（本次只做下线，不做数据迁移）。

### 决策 4：权限过滤的实现落点（后端一处 + 数据订正，前端零改动）

- **采用**：改**后端唯一入口** `LoginServiceImpl.getUserMenus()`（`:118-135`）——把 `:131 privilegeModuleService.list()`（全表）与 `:132 buildAdminAclMap(...)`（满权限位）替换为「按当前用户角色 ACL 过滤的菜单树 + 按角色授予的权限位」。
  - 前端**无需改动**：`preferences.ts:13 accessMode='backend'` ⇒ 路由完全由该接口驱动，`generate-routes-backend.ts:22-60` 不做 roles 过滤（按 roles 过滤的 `generateRoutesByFrontend` 在该模式下根本不执行）。
  - 超管豁免**沿用既有先例口径**：`AgentKnowledgeMapper:32-42` 硬编码 `role_id='428007432736870400'`（ROLE_ADMIN）——禁止另造第二套超管定义。
  - 未授权菜单的兜底：动态路由不注册 ⇒ 直接 URL 访问落 404（需注意 `coreRoutes` 恒注册，业务菜单不在其中）。
- **被拒绝 A**：直接启用既有 `PrivilegeModuleServiceImpl.getModelTreeByUserId()`（`:37-58`）—— 理由：① 用 `acl.getPermission(3)`（**「删除」权限位**）判"菜单可见"，语义错位（BUG-120）；② 返回扁平列表不建树、不填 pvalues；③ 当前是死代码，无任何测试覆盖。重新评估条件：若其语义被修正并补建树/填 pvalues，可作为实现基础。
- **被拒绝 B**：只在前端按 `roles` 过滤 —— 理由：`userInfo.roles` 恒为空数组（`PrivilegeUserVO.roles` 永不填充），前端没有角色数据可依；且后端仍返回全量菜单 ⇒ 越权面未收敛（仅"看不见"而非"拿不到"）。
- **被拒绝 C**：只过滤菜单、不动按钮级权限 —— 理由：R-05 覆盖「菜单**与操作**」；`buildAdminAclMap` 满权限位 + `access.ts:123-126` 全量 `setAccessCodes` ⇒ `hasAccessByCodes` 恒真，按钮级授权形同虚设。**必须同批处理**。
- **依赖的数据订正（阻塞前置）**：见决策 6。

### 决策 5：是否把 BUG-116（角色↔菜单授权界面失效）纳入本 spec

- **建议纳入**，但**属范围决策、须用户裁定**。
- **理由**：R-05 生效后，角色→菜单的唯一授权手段就是 ACL 数据；而界面写路径已坏（`role/index.vue:249` 提前 `return` + `assign-menu.vue:84 currentRole` 恒 null ⇒ `saveModuleAclApi`/`saveAllAclApi` 从不被调用，证据见 BUG-116）⇒ **不修则 R-05 不可运营**（管理员无法通过界面为新角色配菜单，只能手工 SQL）。
- **被拒绝**：不修，只给 DBA SQL 脚本 —— 理由：把"改权限"降级为"改数据库"，运维不可持续，且与 R-05 的目标（让角色真正驱动可见性）相悖。
- **流程后果（必须先说清）**：R-05 已在 requirements **v1.0.0 已确认**；纳入 BUG-116 修复 = 新增可观察能力（授权界面可用）⇒ 按铁律 4/6 **须 requirements 新增 R-10 → bump v1.1.0 → 重走确认①**，之后才继续 Phase 2/3。若裁定不纳入，则 BUG-116 保持「新建/已延期」并写入 Non-goals 说明（R-05 交付的是过滤能力，界面修复另立）。
- 重新评估条件：用户裁定；或 R-05 实施中发现无界面手段会导致验收场景无法演示。

### 决策 6：ACL / 角色数据订正的策略（R-05 的阻塞前置，源 BUG-117/118）

- **采用**：以「菜单全集 × 角色」为口径**重建** ACL 基线，并清理孤儿：
  1. 超管（`428007432736870400`）：授予全部存活菜单（含当前缺的「三方平台 / 基础管理 / 请求管理」），`acl_state='31'`。
  2. 普通角色（`431285032083144704`，活库现 **1 行**（智能体中心 `/agent/chat`）、名下 **5 个存活用户**）：授予**非管理类基线菜单集合**（Q-P1 已裁定：自动补默认角色 + 兜底）。〔v1.1.0 订正：原文「0 行 / 10 用户」系 seed 口径，已按活库更正〕
  3. 清理 `71f2934c-…` 名下 50 行与 44 行孤儿 `module_id`（先导出留档再删，遵守 L-40 台账纪律）。
  4. 孤儿 `user_role` 行（BUG-118）：**不擅自删**，按"保留 + 记账"处置（与 BL-05/D6 的孤儿账号口径一致），并在 UPGRADE.md 列为已知项。
- **被拒绝 A**：直接切 ACL 过滤、数据原样不动 —— 理由：普通角色 0 行 ⇒ 10 个用户零菜单、前端 `homePath` 默认 `/agent/list` ⇒ 白屏/404（实测级后果）；超管缺 3 个菜单。
- **被拒绝 B**：按现有 ACL 反推"应该给普通角色什么" —— 理由：现有数据本身失真（50 行挂在非角色 id），从脏数据推口径 = 把错误固化（L-29 教训）。
- 重新评估条件：用户对 Q-P1（基线菜单集合）给出裁定后定稿。

## 待确认问题（确认②门控）

**无——全部开口已裁定（2026-10-07）**，逐条落账如下：

| 问题 | 裁定 | 落点 |
|---|---|---|
| Q-P1 零 ACL / 无角色用户的降级策略 | **自动补默认角色 + 超管豁免 + 提示兜底**（选项③+②） | R-05 边界 / R-07 / 决策 6 |
| Q-P2 BUG-116（角色↔菜单授权界面失效）是否纳入 | **纳入** ⇒ requirements 新增 **R-10** | R-10 / 决策 5 |
| Q-P3 组织列范围（`tbl_privilege_role` / 前台账号表） | **三表都去组织列** | R-02 / R-03 边界 / §数据模型 |
| Q-P4 无任何授权行的资源之可见性口径 | **保持兼容：无授权行＝公开** | R-04 边界 |
| Q-P5 三方平台配置是否随免登下线 | **随免登一起下线**（全清） | R-11 边界 2/3 / 决策 3 |
| Q-P6 `apps/mobile-ui` 去留 | **保留 app，只删 SSO 自动登录** | R-11 边界 4 / 决策 3 |

## 风险与规避

- **[风险] 227 处引用一次性删除导致编译面失控** → 规避：以编译器为裁判、按模块分批提交、禁文本批量替换；每批全量 `mvn clean install -Dspring-javaformat.skip=true`。
- **[风险] NOT NULL 列 / 索引 / 外键依赖删除顺序** → 已实测核对（C 路）：`tbl_privilege_user.company_id`(:3772)/`dept_id`(:3773) 是 **NOT NULL 且无 DEFAULT**、两列**无索引**；全库 **零外键**（grep `FOREIGN KEY`/`REFERENCES` 零命中）、**零视图/触发器/生成列**依赖；`tbl_privilege_user` 仅有的索引是 `code`/`username`。⇒ DDL 可安全 `DROP COLUMN`（自动带走 NOT NULL）。**但必须先清掉写这两列的代码**：`AbstractPlatformSyncStrategy:356`（原始 SQL）与 `PrivilegeUser` 全字段插入链路。
- **[风险] 存量组织数据不可逆** → 规避：pre-migration 全量 dump + 组织维度专项导出；UPGRADE.md 明确"数据恢复依赖备份"。
- **[风险] 资源可见性口径已定但**与现状代码语义耦合**（Q-P4 裁定：无授权行＝公开） → 现状代码有三处「**无任何授权行 = 全公开**」分支：`AccountInfoServiceImpl.getMyAgents:96-116`（`published ∧ 无授权行 ⇒ 全员可见`）、`FrontSkillAccessServiceImpl.validateVisible:75-86`、`FrontMcpAccessServiceImpl` 同族；而生产授权行极少（组↔智能体 4、组↔知识库 4、组↔技能 11、组↔MCP 6）⇒ **若解释为"无授权行即不可见"，绝大多数资源会对所有普通用户消失**。规避：Q-P4 定口径（建议保留兼容语义=「有授权行的资源仅授权组可见，无授权行者维持公开」），并在部署前统计「零组用户数 / 各资源授权行数」作为门禁。
- **[风险] 菜单过滤过严致全员看不到菜单（ACL 数据不完整/过期，L-29 已证 `module_sn` 是过期标签）** → 规避：过滤只用 `module_id`/`url`；超管豁免；上线前统计"每个角色可用菜单数"，出现 0 即拦截发布。
- **[风险] 最严重的一条：普通角色名下 5 个存活用户仅 1 行 ACL（智能体中心），且其落地页 `/agent/list` 未授权** —— 启用过滤后这 5 人**只看到 1 个菜单且落地 404/白屏**（比"零菜单"更隐蔽）〔v1.1.0 订正：原文「0 行 ACL / 10 用户」为 seed 口径〕→ 规避：实施决策 6 的 ACL 基线重建 + Q-P1 降级口径；上线前用 SQL 统计"每个角色的可用菜单数"与"无角色用户数"双门禁，任一为 0 即阻断发布；T-07 的"不白屏"断言必须**打在该普通角色账号**上
- **[风险] 过滤菜单但漏了按钮级权限 ⇒ `hasAccessByCodes` 恒真** → 规避：`buildAdminAclMap` 与 `access.ts:123-126` 的 `setAccessCodes` 同批改造（决策 4）；验收须含"未授权按钮不出现/点击被拒"的断言，不能只验菜单。
- **[风险] 无菜单用户落地白屏/404**（前端 `homePath` 默认 `/agent/list`，`store/auth.ts:49`）→ 规避：Q-P1 口径落定 + 提示页兜底；验收含"零授权账号登录后不白屏"。
- **[风险] session 里的 ACL 是登录时刻快照**（`LoginServiceImpl:76` 写 `LOGIN_ACLS`）→ 规避：新实现**不得**继续读 session 快照（否则改角色须重登才生效，与 R-04「即时生效」精神相悖）；改为按需查库并说明缓存口径。
- **[风险] 免登下线后移动端登录方式降级**（用户在钉钉/企微/飞书客户端内不再免登）→ 规避：`apps/mobile-ui` 保留并**回归验证密码登录**（R-11 断言：后台账号与前台账号 `POST /auth/login` 均成功）；`RELEASE-NOTES` 明示该行为变更与操作指引（改为手动输入账号密码）；删除平台配置表前专项导出留档。
- **[风险] 迁移器重跑（台账缺行，L-40/BUG-115）** → 规避：SQL 全部自幂等，不依赖台账判重。
- **[风险] 提交面过宽吞入 40MB 级 backups dump（L-13）** → 规避：显式列路径提交，提交前核对 `git status --porcelain`。
- **[风险] 平台侧组授权表读侧不过滤逻辑删 ⇒ 已撤销授权仍可能可见（BUG-121）** → 规避：统一读侧口径（`BaseModel` 补 `isLogicDelete` 或显式补 `del_flag=0`），并以 R-04「撤销即时生效」的断言覆盖；生产已有墓碑行（kbase 2 / skill 6 / mcp 5）。
- **[风险] R-04 的组侧能力只完成一半** → 组↔技能 / 组↔MCP 现无组侧端点（仅资源侧单向授权）⇒ 规避：把这两类的组侧读写列为独立任务，验收断言含「组管理页可为组勾选技能与 MCP 并即时生效」。
- **[风险] 预设问题接口零校验（BUG-122）被漏改** → 规避：R-08 的验证必须覆盖 `POST`（整批覆盖他人 agent）与 `DELETE`（按 questionId 删、不校验归属）**两条越权路径**，断言"普通角色被拒 + 管理员成功"（业务码，L-26）。
- **[风险] 删除组织维度后 `tbl_privilege_role.company_id` 的存量值悬空**（`ROLE_ADMIN.company_id=428001009954172928` = 初彩科技）→ 规避：Q-P3 已裁定删列 ⇒ 悬空随列消失；**但删列前须导出留档**（决策 2/迁移策略的备份动作覆盖）。

## 依赖与前置

- 三路只读调研结果（A 权限菜单链路 / B 用户·组·预设问题链路 / C 组织维度·同步·前端 —— 已全部回填本文件）。
- **`apps/mobile-ui` 的构建/发布链路确认**（是否在交付物打包范围内）——R-11 改动其登录入口与 `main.ts` 启动流程。
 - **编译基线**：实施前先跑 `mvn -q clean compile -Dspring-javaformat.skip=true` 取基线（C 路为纯静态调研，未跑构建，编译失败点为引用推导）。
 - **Q-P4 裁定**（无授权行资源的可见性口径）—— 影响 R-04 边界与 tasks 断言设计。
- 生产库真实数据统计（部署前门禁）：零角色用户数、零组用户数、各角色可用菜单数、`tbl_privilege_group` 行数与非空业务列比例、组织三表行数。
- `tbl_phoenix_release` 台账现状核对（BUG-115 补登后是否完整）。
- 迁移器事实（已确认，供 Implement 参照）：`docker/init/migrate.sh` 扫 `/releases/v*/sql/V*.sql`，`sort -V` 顺序，逐文件 `-1` 事务，成功写 `tbl_phoenix_release`；`rollback/` 子目录**不被扫描**（回滚靠人工执行）。

## R-12 实施方案（v1.2.0 追加）

> 触发：requirements **v2.1.0** 新增 R-12（权限管理 + 前台管理 → 「系统管理」；账号管理唯一化）。
> 本方案**不新增表/列**，只调整菜单数据与前端页面，属**数据 + 前端**变更。

### 一、数据库（新升级件 `V2.0.0_06__menu_merge_system_management_dml.sql` + rollback）

| # | 操作 | 对象 | 说明 |
|---|---|---|---|
| 1 | `UPDATE name/url/sn` | 权限管理 `02b733aa08774219a23c2f21f1b3f6b5` | 更名 **系统管理**；建议 `url` → `/system-management`、`sn` → `SystemManagement`；**id 不变**（保 ACL） |
| 2 | `UPDATE pid/order_no` | 组管理 `8b1a156184cf49fba34389e8caea4269` | 父 → 系统管理；次序与 账号管理 相邻 |
| 3 | `UPDATE order_no` | 菜单管理 / 权限值管理 / 日志管理 | 依次后移一位，保持 角色→账号→组→菜单→权限值→日志 |
| 4 | `DELETE` + ACL | 前台 账号管理 `6752c28a59c048cb9d08179ccadb38b4` | 连同 `tbl_privilege_acl` 中 `module_id` 指向它的行 |
| 5 | `DELETE` + ACL | 前台管理 `638d2319c2d54f3ea36b2a519c9a1d0f` | 空目录清理 |

- **幂等**：全部按固定 id 操作；重复执行影响 0 行。**自检**：存活菜单 = 19；超管 ACL 行数 = 存活菜单数；普通角色 ACL 仍 7。
- **回滚**：`rollback/V2.0.0_06__..._rollback.sql` —— 保真重建被删 2 行（原文取自 `sql/all_data.sql` 删除前副本或线上 dump）
  并把改名/搬迁/次序改回原值。
- **聚合**：与 01~05 同批放入 `releases/v2.0.0/sql/`（部署台账按 seq 自动应用，已部署环境补跑该件即可）。

### 二、前端（admin-ui）

| 操作 | 对象 |
|---|---|
| 删除 4 文件 | `views/account/account-info/{index.vue,form.vue,data.ts,group-form.vue}` |
| **保留** | 全部 3 个 API 模块（`platform-account-info.ts` 的 `getAccountInfoApi` 被 `api/core/user.ts` 使用；<br>`getAccountGroupInfoByAccountInfoApi` 被 `views/system-management/account/form.vue`（T-13）使用；<br>`platform-account-tenant-info.ts` 的 `getGroupAgentInfoListApi` 被组管理页使用） |
| 连带核对 | 删除后 `pnpm typecheck` 不新增错误（基线 207）、`pnpm build` 通过、无悬空 import |

### 三、验证

1. **drill 库**：正向 → 菜单 19、两目录消失、组管理在系统管理下；反向 → 复原；各跑两次验幂等。
2. **洁净库重放**：`all_schema` + `all_data` → 01~06 两轮全 exit=0；终态菜单 19。
3. **部署栈**：重建前端镜像 + `docker compose up -d` → 台账应用 `V2.0.0_06`；超管导航只见「系统管理」；
   `/platform-account/account-info` 404；普通角色导航仍 7 条。

## R-13 实施方案（v1.3.0 追加）

> 触发：requirements **v2.2.0** 新增 R-13（账号集合收敛为 admin + chenzhuo；初始化数据同步）。
> 含**存量清理（DML）**与**种子重写（基线）**，不新增表/列。

### 一、存量清理升级件（`V2.0.0_07__account_prune_dml.sql` + rollback）

| # | 操作 | 对象 |
|---|---|---|
| 1 | DELETE | `tbl_privilege_login_log`：`operation_id` 或 `create_by` ∈ 待删账号 id |
| 2 | DELETE | `tbl_privilege_user_role`：`user_id` ∈ 待删账号 id |
| 3 | DELETE | `tbl_agent_user_agent_info`：`user_id` ∈ 待删账号 id |
| 4 | DELETE | `tbl_privilege_user`：liufang / lwj / xtj（存活）+ maliu / wangwu（已软删，物理删） |
| 5 | 自检 | 存活账号恰为 admin/chenzhuo；无残留账号域行；chenzhuo 角色绑定仍在（L-51：不硬编码环境绝对数） |

- **待删 id（实测）**：liufang `428011841386577920`、lwj `431678413494018048`、xtj `428011841386577921`、
  maliu `432101006843711488`、wangwu `432061200055025664`
- **不删**：聊天会话/消息（业务数据；按用户裁定处理）
- **回滚**：逐行保真重建 5 行用户 + 其角色绑定/登录日志/智能体绑定
- **审计列**：其他行的 `create_by`/`update_by` 保留原值

### 二、种子/基线重写（全新库 = admin + chenzhuo）

| 表 | 现状 | 改为 |
|---|---|---|
| `tbl_privilege_user` | 5 行演示账号 | **1 行 chenzhuo** |
| `tbl_privilege_user_role` | 19 行（含孤儿） | **仅 chenzhuo 的 1 行** |
| `tbl_platform_account_info` | lwj | **chenzhuo** |
| `tbl_platform_account_group_info` | lwj→通用组 | **chenzhuo→通用组** |
| `tbl_agent_user_agent_info` | 3 行（liufang×2/lwj×1） | **仅 chenzhuo 的 1 行** |
| `docker/init/10_seed_admin.sql` | 只种 admin | 不变 |

### 三、验证

1. drill：部署时备份重放 01~07 两轮 + 反向 07 + 再正向；存活账号 = 2
2. 洁净库重放：all_schema/all_data → 01~07 两轮，终态恰为 admin + chenzhuo
3. 部署：migrator 应用 07 → 存活账号 2；5 旧账号登录失败；admin/chenzhuo 登录成功且菜单 19 / 7

## R-15 实施方案（v1.4.0 追加）

> 触发：requirements **v2.4.0** 新增 R-15。**无表结构变更** ⇒ 不新增迁移件（台账仍 8 件）。

### 一、后端

| 项 | 内容 |
|---|---|
| 检索修正 | `pageByQuery` keyword 子句补 **`mobile`**（手机号；BUG-130），保留 `phone`/`tel` |
| 新增端点 | `PUT /api/privilege/user/status`（{id,status}）、`PUT /api/privilege/user/status/batch`（{ids[],status}→更新行数） |
| 新增 DTO | `PrivilegeUserStatusDTO`、`PrivilegeUserBatchStatusDTO` |
| 服务层 | `isSuperAdmin`（**统一口径**，LoginServiceImpl 委托到此）、`updateStatus`、`updateStatusBatch`、`canDisable` |
| 安全保护 | 控制器：① 禁止操作当前登录账号本人（单/批）；② 禁用后须仍存在 ≥1 启用的超管 |

### 二、前端（admin-ui 账号管理）

| 项 | 内容 |
|---|---|
| 搜索 | 提示改为「用户名 / 姓名 / 手机号」 |
| 勾选 | `useColumns` 头部加 `type: 'checkbox'`；grid `checkboxConfig`；`@checkbox-change/@checkbox-all` 同步已选数 |
| 批量 | 工具栏「批量启用 / 批量禁用」（未勾选禁用、显示已选数、二次确认、成功后清勾选并刷新） |
| 行级 | 操作列「启用/禁用」按 `status` 显示对应动作（二次确认） |
| 收敛 | 删「分配权限」按钮、`RoleModal` 与 `role-form.vue`（角色/组分配统一走编辑，R-03） |

### 三、验证

1. API 级：手机号（全量/片段）、姓名、用户名检索；单/批启停；禁用后登录被拒；两条保护被拒。
2. 产物级：`account-*.js` 含批量按钮且无「分配权限」；`form-*.js` 含新提示、旧提示 0 命中。
3. 质量门：后端编译 0 错误、`typecheck` 零新增、`build` 通过、部署后后端 0 ERROR。

## R-16 实施方案（v1.5.0 追加）

> 触发：requirements **v2.5.0** 新增 R-16（用户口径：「admin 超管账号，不能被删除和禁用，这个是基础」）。
> **无表结构变更** ⇒ 不新增迁移件（台账仍 8 件）。

### 一、后端

| 项 | 内容 |
|---|---|
| 保护判定 | `PrivilegeUserServiceImpl.isProtectedAdmin(userId)`：取该行 `username`，与 `admin` **忽略大小写**比较（按用户名而非 id —— id 逐环境不同） |
| 禁用保护 | 控制器 `PUT /status`、`/status/batch` 先查保护账号（优先于自锁与最后超管），命中即拒；服务层 `updateStatus` 再兜底拒绝、`updateStatusBatch` 剔除保护账号 |
| 删除保护 | 控制器 `DELETE /{id}` 补三条：① 内置超管不可删 ② 不能删当前登录账号 ③ 须保留≥1 启用超管（**原实现零保护**，见 BUG-132）；服务层 `deleteUser` 兜底拒绝保护账号 |
| 改名保护 | 控制器 `PUT`（编辑）：保护账号 `username` 不得改为非 `admin`；服务层 `updateUser` 亦强制回填原用户名。注：`PrivilegeUserDTO` **不含 status**，编辑路径本就无法改状态（实测确认） |

### 二、前端

| 项 | 内容 |
|---|---|
| 置灰 | 操作列对保护账号的「启用/禁用」「删除」设 `disabled: true` + `tooltip`（VbenTableAction 支持） |
| 批量预检 | 勾选含保护账号且要禁用时，前端先提示并中止（服务端仍会拒） |

### 三、验证

1. API 级：单个/批量禁用 admin（admin 本人 + chenzhuo 发起）均拒；批量含 admin 整体拒绝且不部分执行；
   删除 admin（两种身份）均拒；改名 admin 被拒；admin 其他字段可编辑；chenzhuo 删除自己被拒。
2. 终态：三账号齐全且均启用；admin 可登录且菜单 19。
3. 质量门：后端编译 0 错误、`typecheck` 零新增、构建通过、部署后 0 ERROR。

## R-17 实施方案（v1.6.0 追加）

> 触发：requirements **v2.6.0** 新增 R-17。**含一条数据回填迁移件**（V2.0.0_09）。

### 一、智能体按创建人可见（phoenix-data）

| 项 | 内容 |
|---|---|
| 记录创建人 | `AgentController.create`：`admin_id = 当前登录用户 id`（**服务端取会话，覆盖入参**） |
| 列表过滤 | `GET /api/agent/list`：超管（`AdminRoleGuard.isAdmin`，口径同 R-08/R-15）→ 不过滤；其余 → `ownerId=本人` |
| 服务层 | `AgentService.listCreatedInPlatform(status, keyword, Long ownerId)`：`ownerId != null` 时按 `admin_id` 过滤（null=全部） |
| 越权防线 | **`checkAgentExists(id)` 内统一加归属校验** —— 它是详情/编辑/删除/发布/下线/授权/API Key 等所有单对象端点的共同入口 ⇒ 一处改动成组生效；非本人且非超管 ⇒ 403「无权访问他人创建的智能体」 |
| 存量回填 | `V2.0.0_09__agent_owner_backfill_dml.sql`：`admin_id IS NULL` 的历史智能体 → 内置 admin 的 id（**子查询动态解析**，跨环境；同 L-51/L-57）。回滚件按**记录的 10 个 id** 复位置空 |
| 说明 | 列表沿用既有「只列 sn 为空（平台内创建）」口径，Java 自注册智能体（sn 非空）不出现 |

### 二、角色删除保护（phoenix-privilege）

| 项 | 内容 |
|---|---|
| 服务层 | `deleteRoleById` 返回 `boolean`：`sn=ROLE_ADMIN` ⇒ false；有持有者 ⇒ false；否则真删（原实现 void 且无校验） |
| 新增 | `countHolders(roleId)`、`isBuiltInAdminRole(roleId)`（按 sn 判定） |
| 控制器 | 先判内置角色 → 再判持有者数 → 提示语可区分 |

### 三、验证

1. 列表可见性：超管 5 条（sn 为空的平台内智能体）/ 普通角色 0 条（未创建时）。
2. 越权：普通角色直连他人智能体的 详情/编辑/发布/删除/下线/授权 → 全部 403；本人与超管放行。
3. 创建人：普通角色新建后 `admin_id` = 本人；双方列表随之变化。
4. 角色：`ROLE_ADMIN` 拒绝、`COMMON`（有持有者）拒绝、无持有者角色可删且行消失。
