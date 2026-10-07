> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-07

# 技术方案：user-role-group-model

> 本轮为 Phase 2 稿：**坑核对 / 方案概述 / 迁移与回滚 / 接口设计 / 数据模型变更 / 共享面身份矩阵（A 路已落证）/ 关键决策 / 风险 / 依赖** 已成文；
> 仅 **§涉及模块与数据流**（逐文件删除/改动清单）与少量标〔B/C 路回填〕的条目待只读调研补齐 —— **补齐前不提交确认②**（缺节 = 方案不完整）。

## 坑核对（必填，确认②审这一节）

已核对 `specs/_project/lessons.md` **L-01~L-42 共 42 条**（该台账头部规则：active 条目每次 Plan 强制核对；
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

**其余 27 条**（L-02/03/04/05/07/08/09/10/11/12/16/17/18/19/20/22/23/24/25/28/30/32/33/34/35/36/37/39）按标题与触发场景逐条核对，与"用户体系改造 + 破坏性迁移"**无直接相交**；其中 **L-07（假阳性验证）/L-30（管道吞掉退出码）/L-05（shell 方言）/L-11（用户环境 ≠ 我以为的环境）** 作为通用纪律适用于本 spec 全部验证步骤。

## 方案概述

三条主线，一次版本完成（Q2 裁定不分期）：

1. **下线**（R-01/R-03/R-06/R-09）——删除组织维度（三表 + 用户表组织列 + 6 类入口）、下线三方平台用户/组织同步模块（保留第三方扫码登录）、下线后台数据组死代码。
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

〔调研回填〕——三路只读调研（权限菜单链路 / 用户·组·预设问题链路 / 组织维度·同步·前端）结果回来后逐文件清单化：每个文件标注「整文件可删 / 文件内部分改」，并给出模块级数据流图。

## 接口设计

遵守 `.specrc.yml` 路由（api-design = global）与**本项目既有信封约定**：HTTP 层恒 200，成败在响应体 `code`（100/200 成功）——验证一律断言 `code`（L-26）。

**A. 随组织维度一并删除的端点**（〔C 路回填完整清单〕，控制面已知）

| 端点族 | 载体 |
|---|---|
| `/api/privilege/company/**` | `PrivilegeCompanyController` |
| `/api/privilege/department/**` | `PrivilegeDepartmentController` |
| `/api/privilege/employee/**` | `PrivilegeEmployeeController` |
| `POST /platform/sync` | `PlatformSyncController`（R-06） |

删除后 SHALL 返回 404（路由不存在），**不是 500**。

**B. 行为变更（不新增端点，但语义破坏性）**

| 端点 | 现状 | 变更后 |
|---|---|---|
| `GET /api/privilege/auth/menus` | 返回**全表**存活菜单（22 条，含 4 条 `is_show=0`），每个菜单 `state` 为**满权限位** | 返回**按当前用户角色过滤**后的菜单树；`state`/`pvalues` 按角色授予计算；超管豁免（`role_id='428007432736870400'`） |
| `GET /api/privilege/auth/getLoginUserInfo` | 由 `PrivilegeUser` 实体拷贝（无 roles） | **不改**（`accessMode='backend'` 下前端不依赖 roles；改动会扩大面） |

响应**结构不变**（仍 `{code,msg,data:{menus,pvalues}}`）⇒ 前端零改动；但"同一账号可见菜单集合变小"是用户可感知的破坏性行为变化，须写入 `RELEASE-NOTES.md`。

**C. 新增端点**：**无**。R-04（组关联四类资源）沿用既有 `tbl_platform_group_*` 相关端点；R-08 在既有预设问题端点上**加角色校验**〔B 路回填：控制器与方法名〕。

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
| `tbl_platform_account_info`（前台账号） | 2 | `dept_id` 2/2、`employee_id` 1/2、`dept_name` 1/2、`third_party_id` 0/2 | **前台账号表也带组织列** |
| `tbl_privilege_acl` | 73 | — | 见 BUG-117（50 行挂非角色 id、44 行孤儿 module_id、普通角色 0 行） |
| `tbl_privilege_module` | 27 | — | 其中 4 行为组织菜单 |
| `平台组四表` | 1 / 4 / 4 / 11 / 6；account_group 2 | — | 组语义唯一化的存活方 |

**范围澄清（请在确认②时确认或否决）**：R-02/R-03 原文说"用户表上的组织列"。数据模型上"用户模型"实际横跨**三张表**：`tbl_privilege_user`、`tbl_privilege_role`（`company_id`）、`tbl_platform_account_info`（前台账号）。本方案按"**用户模型相关表一律去组织列**"执行；若你要求只动 `tbl_privilege_user`，请否决并说明保留范围。

**索引/视图/外键依赖核对（已实测）**：三张组织表**无任何外键被引用**（仅自身 PK）、**无视图/触发器/函数**依赖、组织列上**无索引**（唯一例外是 company 表自身的 `code` 唯一索引，随表删除）⇒ DDL 可安全 `DROP`，无需 `CASCADE`。

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
  DROP COLUMN IF EXISTS dept_name;
-- ② 组织三表（无外键引用，直接删；company 的 code 唯一索引随表消亡）
DROP TABLE IF EXISTS tbl_privilege_employee;
DROP TABLE IF EXISTS tbl_privilege_department;
DROP TABLE IF EXISTS tbl_privilege_company;
-- ③ 后台数据组（R-09 单向下线：代码 0 引用 + 数据 0 行）
DROP TABLE IF EXISTS tbl_privilege_group;
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
| `tbl_platform_platform_info` | ① 同步模块读（企微/钉钉/飞书 corpId/secret）② **第三方扫码登录读** | ① 随同步模块删除而失去消费方，**表保留** ② 扫码登录**必须仍可用** | T-xx〔C 路回填证据〕 |
| `POST /platform/sync` | 同步入口（`PlatformSyncController`） | 404（端点不存在），非 500 | T-xx〔C 路回填〕 |
| `tbl_privilege_user` | 登录 / 鉴权（Sa-Token `StpUtil`）/ 账号管理 / 前台 chat / `getLoginUserInfo`（`BeanUtils.copyProperties` 从**实体**拷贝） | 组织列移除后各身份行为不变；注意 VO 侧 `PrivilegeUserVO.roles` 永不填充（前端 `userRoles` 恒空，`accessMode='backend'` 下不影响路由） | T-xx〔C 路回填字段清单〕 |
| `tbl_platform_group_*` 四表 | 组管理维护 + 前台可见性解析（`getMyAgents` 等） | 成为唯一组语义；授权即时生效 | T-xx〔B 路回填〕 |

## 关键决策

### 决策 1：一次性破坏性删除 vs 数据库规范的「先加后删、跨版本兼容」

- **采用**：一次性删除（MAJOR 版本内完成）。
- **理由**：`standards/database-design.md` §22 的"先加后删/双写/跨多版本"是为**在线灰度**服务；本次是用户明确裁定的破坏性重构（Q3 = 删除），且 `company_id`/`dept_id` 是 **NOT NULL 强约束**——任何"保留期"都会让"新建用户必须选部门"的老问题继续存在，直接违背 R-02。保留期成本 > 收益，且本项目无灰度环境。
- **被拒绝**：保留列 + 双写过渡两版再删 —— 理由：过渡期 UI 与 API 仍需维护组织字段与两套写入路径，违背"删除"的需求实质；无灰度环境无法验证中间态。
- **例外声明**：规范条款不被豁免，而是**显式记例外 + 补偿措施**（pre-migration 备份 + 回滚配对 + UPGRADE 风险告知 + 本决策留档）。重新评估条件：若未来出现不可停机窗口，则改为先加后删。

### 决策 2：组唯一化 = 单向下线 vs 数据合并迁移

- **采用**：单向下线（删 `tbl_privilege_group` 及其 `PrivilegeGroupVO`）。
- **理由**：全仓检索确认**零 mapper/service/接口引用**（仅 1 个自声明 VO）= 死代码；无消费方、无映射规则可写。〔调研回填：补生产行数核对〕
- **被拒绝**：把 `tbl_privilege_group` 的 `super_id`/`type`/`state` 迁进平台组表 —— 理由：平台组表无对应语义列，且无任何消费方，迁移=凭空造需求。
- 重新评估条件：若调研发现遗漏引用，或生产表存在非空业务行且能证明用途。

### 决策 3：同步模块移除的边界（只删 sync，保留 login 与平台配置）

- **采用**：仅删 `service/sync/**` + `PlatformSyncController`（`/platform/sync`）+ 前端同步 API/入口；**保留** `service/platform/**`（SDK 封装）、`thirdparty/strategy/**`（扫码登录）、`tbl_platform_platform_info`。
- **理由**：扫码登录与同步**共用**平台配置表与 SDK；删配置表会直接打死第三方登录，超出用户"移除同步"的授权范围（R-06 边界 1）。
- **被拒绝**：连 `platform` 包与配置表一起删 —— 理由：破坏扫码登录，属超授权范围的连带破坏。
- 重新评估条件：若用户后续明确"第三方登录也不用"，另立 spec。

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
  2. 普通角色（`431285032083144704`，现 **0 行**、名下 10 用户）：授予**非管理类基线菜单集合**（具体集合待用户确认——见「待确认问题 Q-P1」）。
  3. 清理 `71f2934c-…` 名下 50 行与 44 行孤儿 `module_id`（先导出留档再删，遵守 L-40 台账纪律）。
  4. 孤儿 `user_role` 行（BUG-118）：**不擅自删**，按"保留 + 记账"处置（与 BL-05/D6 的孤儿账号口径一致），并在 UPGRADE.md 列为已知项。
- **被拒绝 A**：直接切 ACL 过滤、数据原样不动 —— 理由：普通角色 0 行 ⇒ 10 个用户零菜单、前端 `homePath` 默认 `/agent/list` ⇒ 白屏/404（实测级后果）；超管缺 3 个菜单。
- **被拒绝 B**：按现有 ACL 反推"应该给普通角色什么" —— 理由：现有数据本身失真（50 行挂在非角色 id），从脏数据推口径 = 把错误固化（L-29 教训）。
- 重新评估条件：用户对 Q-P1（基线菜单集合）给出裁定后定稿。

## 待确认问题（确认②门控，需用户裁定）

- **Q-P1 零 ACL / 无角色用户的降级策略**：普通角色现 0 行 ACL，5 个存活用户中 2 个无任何角色。选项：① 严格（无授权=零菜单，仅剩 coreRoutes）② 无角色用户登录后给显式提示页（不白屏）③ 自动补默认角色（推荐：③ + 超管豁免 + ② 的提示兜底）。**需你给出口径**，否则 R-05 上线即"部分用户白屏"。
- **Q-P2 BUG-116 是否纳入本 spec**（决策 5）——纳入则 requirements 需新增 R-10 并 bump v1.1.0 **重走确认①**。

## 风险与规避

- **[风险] 227 处引用一次性删除导致编译面失控** → 规避：以编译器为裁判、按模块分批提交、禁文本批量替换；每批全量 `mvn clean install -Dspring-javaformat.skip=true`。
- **[风险] NOT NULL 列/索引/外键依赖删除顺序** → 规避：先核 `all_schema.sql` 的约束与索引（〔调研回填〕），DDL 内先解依赖再删列。
- **[风险] 存量组织数据不可逆** → 规避：pre-migration 全量 dump + 组织维度专项导出；UPGRADE.md 明确"数据恢复依赖备份"。
- **[风险] 组过滤生效后"未入组用户看不到任何资源"（前台可见性骤变）** → 规避：R-07 补角色与组；「未入组」的可见性口径必须在 plan 定稿（〔调研回填〕），并在部署前用 SQL 统计"零组用户数"作为上线前门禁。
- **[风险] 菜单过滤过严致全员看不到菜单（ACL 数据不完整/过期，L-29 已证 `module_sn` 是过期标签）** → 规避：过滤只用 `module_id`/`url`；超管豁免；上线前统计"每个角色可用菜单数"，出现 0 即拦截发布。
- **[风险] 最严重的一条：普通角色现 0 行 ACL、名下 10 用户**（BUG-117 实测）→ 规避：实施决策 6 的 ACL 基线重建 + Q-P1 降级口径；上线前用 SQL 统计"每个角色的可用菜单数"与"无角色用户数"双门禁，任一为 0 即阻断发布。
- **[风险] 过滤菜单但漏了按钮级权限 ⇒ `hasAccessByCodes` 恒真** → 规避：`buildAdminAclMap` 与 `access.ts:123-126` 的 `setAccessCodes` 同批改造（决策 4）；验收须含"未授权按钮不出现/点击被拒"的断言，不能只验菜单。
- **[风险] 无菜单用户落地白屏/404**（前端 `homePath` 默认 `/agent/list`，`store/auth.ts:49`）→ 规避：Q-P1 口径落定 + 提示页兜底；验收含"零授权账号登录后不白屏"。
- **[风险] session 里的 ACL 是登录时刻快照**（`LoginServiceImpl:76` 写 `LOGIN_ACLS`）→ 规避：新实现**不得**继续读 session 快照（否则改角色须重登才生效，与 R-04「即时生效」精神相悖）；改为按需查库并说明缓存口径。
- **[风险] 移除同步波及扫码登录** → 规避：R-06 已写成共享面边界 + 回归断言（删同步后实测扫码登录仍成功）。
- **[风险] 迁移器重跑（台账缺行，L-40/BUG-115）** → 规避：SQL 全部自幂等，不依赖台账判重。
- **[风险] 提交面过宽吞入 40MB 级 backups dump（L-13）** → 规避：显式列路径提交，提交前核对 `git status --porcelain`。

## 依赖与前置

- 三路只读调研结果（§涉及模块 / §接口 / §数据模型 / §共享面矩阵 / 决策 4 的回填依据）。
- 生产库真实数据统计（部署前门禁）：零角色用户数、零组用户数、各角色可用菜单数、`tbl_privilege_group` 行数与非空业务列比例、组织三表行数。
- `tbl_phoenix_release` 台账现状核对（BUG-115 补登后是否完整）。
- 迁移器事实（已确认，供 Implement 参照）：`docker/init/migrate.sh` 扫 `/releases/v*/sql/V*.sql`，`sort -V` 顺序，逐文件 `-1` 事务，成功写 `tbl_phoenix_release`；`rollback/` 子目录**不被扫描**（回滚靠人工执行）。
