# CR-01 · 资源归属可见性（知识库/技能/插件MCP 对齐智能体）+ 智能体编辑选择器按组可见

> 父 spec: `specs/20261007_user-role-group-model`（在途 **v2.6.0**，未打 tag ⇒ 允许 CR）
> 状态: **已合入 → 父 v2.7.0 | 2026-10-07 | 代码收口 9324bf8 + 合入登记见本文件 §I** | 档位提议: **major**（跨 phoenix-data / phoenix-agent / phoenix-platform / 前端 ≥3 模块；涉 SQL 加列）| 提请人: 陈卓（2026-10-07 口令）
> 用户原话:「知识库，skill，插件MCP这些，都得和智能体一样，只展示用户自己发布的，编辑删除查询也是只有自己的，
> 但是在智能体编辑中，选择skill，知识库，插件MCP这些是根据组别进行查询，可以看到自己的，关联组的，还有公共的。」

---

## §S 变更意图

### S-1 三张清单

**新增 R：**
- **R-18 资源归属可见性（知识库 / 技能 / 插件MCP 对齐智能体 R-17 模式）**
  - **管理页列表**（`/api/knowledge-base/query/page`、`/api/skill`、`/api/mcp`）：仅返回**当前用户自己创建的**；**超管看全部**（口径同 R-17）。
  - **单对象端点**（详情/编辑/删除/查询）：加归属校验，非本人且非超管 ⇒ 403（同 R-17 `checkAgentExists` 模式）。
  - **智能体编辑选择器**（`/api/skill/options`、`/api/mcp/options`、`/platform/agent-kbase/agent/{id}/bindable`）：返回 **自己的 ∪ 关联组的 ∪ 公共的**（组可见性口径沿用 R-04 / Q-P4）。

**修改 R：**
- **R-04**（组直接关联四类资源 / 可见性口径）：把"选择器可见集合"从现状的**全部已发布池**收紧/明确为 **own ∪ group ∪ public**；管理页可见集合从"全部"改为"own（超管 all）"。
  （R-04 原文只定义了组授权与"无授权行=公开"，未定义管理页与选择器两种上下文的可见集合 ⇒ 本次补口径。）

**废除 R：** 无。

### S-2 受影响既有 R 核查（必答）

| 既有 R | 是否受影响 | 说明 |
|---|---|---|
| R-04 组关联资源 / 可见性口径 | **是** | 选择器可见集合改 own∪group∪public；管理页改 own-only |
| R-05 角色过滤菜单 | 否 | 菜单/ACL 模型不动，仅资源数据可见性 |
| R-17 智能体归属 | **是（复用）** | 复用其超管豁免 + 单对象归属校验模式；不改变智能体本身口径 |
| R-10 角色↔菜单授权 | 否 | 不涉及 |
| R-13 账号收敛 | 否 | 不涉及 |

### S-3 Non-goals（本次不做）

- 不改菜单/角色 ACL 模型；不改前台（mobile / front）资源可见性。
- 不做资源"归属转让"能力。
- 不改 agent↔资源绑定表结构（`tbl_data_agent_{knowledge,skill,mcp_info}`）。
- 不处理历史 `creator='system'` 的语义重构（仅按裁定归口，见 Q-C3）。

### S-4 为何不另立 spec

同属"用户体系三维度化 + 资源归属"主线；复用 R-04 组授权表（`tbl_platform_group_{kbase,skill,mcp}_info`）与 R-17 归属/超管判定模式。另立会产生**第二套归属口径与第二套超管判定**，违背单一口径原则（L-57/L-63 同源教训）。

### S-5 范围自检问句

> 「这条变更能否用一句话说明对现有用户可见行为的影响？」
> **能**：管理页只看自己的资源（超管看全部）；智能体编辑选资源时看到 自己的 + 关联组的 + 公共的。

### S-6 现状实测（变更依据）

| 对象 | 归属列 | 管理页列表现状 | 选择器现状 |
|---|---|---|---|
| 知识库 `tbl_data_knowledge_base` | `creator`（用户 id；含 1 条历史 `'system'`） | **无归属过滤**（人人看全部） | `bindable` = 全量/组？待 §P 核 |
| 技能 `tbl_harness_skills` | **无 creator 列**（需加列 + 回填） | **无归属过滤** | `options` = 全部已发布池 |
| MCP `tbl_mcp_server` | `creator`（用户 id） | **无归属过滤** | `options` = 全部已发布池 |

### S-7 待确认问题（§S 门控必答）

- **Q-C1** 管理页"只展示自己发布的"：是"自己创建的（含 draft/published 各状态）"还是"仅 published"？
  （建议：自己的**全部状态**都可见，否则草稿无法管理；"发布"仅作为对他人/组可见的门槛。）
- **Q-C2** 技能表无 `creator` 列 ⇒ 需 **DDL 加列 + 存量回填**。存量 7 条回填给谁？（建议：admin，同 R-17 智能体回填口径。）
- **Q-C3** 知识库历史 `creator='system'` 的行归谁？（建议：视为**公共/超管可见**，普通用户管理页不可见。）
- **Q-C4** 选择器"公共"口径：确认 = **无组授权行**（沿用 Q-P4"无授权行=公开"），还是另有 `is_public` 列？（实测无 is_public 列 ⇒ 沿用 Q-P4。）

---

## §P 影响面与已实现处置表

> §S 已确认（2026-10-07，陈卓）：档位 **major**；Q-C1 自己全部状态 / Q-C2 skill 回填 admin /
> Q-C3 kbase `'system'` 改归 admin / Q-C4 公共 = 无组授权行（沿用 Q-P4）。

### P-1 影响面（模块 × 端点 × 文件）

| 模块 | 端点 / 位置 | 变更 |
|---|---|---|
| phoenix-data | `KnowledgeBaseController.queryByPage`（POST /api/knowledge-base/query/page）+ `KnowledgeBaseServiceImpl` | 列表加 owner 过滤（超管豁免） |
| phoenix-data | 知识库单对象端点（GET /{id}、PUT、DELETE、re-embed、recall） | 加归属校验（非本人且非超管 → 403） |
| phoenix-agent | `SkillController.page` + `SkillAdminServiceImpl.page` | 列表加 owner 过滤（超管豁免） |
| phoenix-agent | `SkillController` 单对象（GET /{id}、publish、offline、PUT /{id}/groups、refs） | 加归属校验 |
| phoenix-agent | `SkillController.upload`（create） | 写入 creator = 当前用户 |
| phoenix-agent | `SkillController.options` + `SkillAdminServiceImpl.options` | 全量已发布池 → **own ∪ group ∪ public** |
| phoenix-agent | `McpAdminController.page` + `McpAdminServiceImpl.page` | 列表加 owner 过滤（超管豁免） |
| phoenix-agent | `McpAdminController` 单对象（GET /{id}、PUT /{id}/status、DELETE、PUT /{id}/groups） | 加归属校验 |
| phoenix-agent | `McpAdminController.options` + `McpAdminServiceImpl.options` | 全量池 → **own ∪ group ∪ public** |
| phoenix-platform | `AgentKbaseServiceImpl.bindable`（/platform/agent-kbase/agent/{id}/bindable） | 全量+selectable 灰显 → 可见集合 **own ∪ group ∪ public**（selectable 仍按组） |
| SQL | `V2.0.0_13`（+rollback） | `tbl_harness_skills` **ADD COLUMN creator**；存量 7 条回填 admin；`tbl_data_knowledge_base.creator='system'` 改归 admin id |
| 前端 admin-ui | 知识库/技能/MCP 管理页 | 列表字段无需改（后端过滤）；超管看他人资源时编辑/删除仍可用（同 R-17 超管豁免） |

### P-2 已实现处置表（逐条交代，禁"其他不变"）

| 已实现 | 处置 | 说明 |
|---|---|---|
| R-04 组授权表 `tbl_platform_group_{kbase,skill,mcp}_info` | **保留** | 选择器 group 可见性的数据源 |
| R-04/T-09 `GroupSkillController`/`GroupMcpController` 组侧端点 | **保留** | 组↔资源授权维护不变 |
| R-17 `AgentController.checkAgentExists` + `AdminRoleGuard` 归属/超管模式 | **保留 + 复用** | 作为三类资源单对象归属校验的范式（同口径：超管 id 或 upper(sn)=ROLE_ADMIN） |
| T-07 `skill options` / `mcp options`（全量已发布池） | **改造** | 可见集合收紧为 own ∪ group ∪ public |
| `kbase bindable`（全量 + selectable 灰显） | **改造** | 可见集合收紧为 own ∪ group ∪ public；**已绑定但不再可见者保留灰显**（R-04 场景2，不丢已绑关系） |
| 管理页 `page`/`queryByPage`（无过滤） | **改造** | 加 owner 过滤 + 超管豁免 |
| `tbl_harness_skills`（无 creator 列） | **改造（DDL）** | 加列 + 回填 admin（Q-C2） |
| `tbl_data_knowledge_base.creator='system'` | **改造（数据）** | 归 admin id（Q-C3） |
| BUG-135 `state` 补齐 / BUG-138 `release_sn` 归正 | **保留** | 与本 CR 正交，不回退 |

### P-3 被拒替代方案（含"另立新 spec"）

| 方案 | 拒绝理由 |
|---|---|
| **另立新 spec** | 同属"用户体系+资源归属"主线，复用 R-04 组授权表与 R-17 归属/超管口径；另立将产生第二套归属判定与第二套超管判定（违 L-57/L-63 单一口径教训） |
| 前端裁剪（后端返全量、前端隐藏他人资源） | 越权面：前端可绕过，单对象端点仍可读/改/删他人资源 |
| skill 归属存 `metadata_json` 而非加列 | 不可索引/不可过滤/语义脏；后续每个查询都要解析 JSON |
| 选择器维持"全量 + 灰显" | 用户明确要求"看到自己的、关联组的、公共的"（可见集合），非全量灰显 |

### P-4 风险与规避

| 风险 | 规避 |
|---|---|
| skill 加列回填后，普通用户技能管理列表变空（原能看到全部） | 属需求预期行为；超管仍见全部；回填 admin 保证存量不丢 |
| bindable 收紧后 agent 编辑可选集合变小，已绑资源可能"不可见但已绑" | 保留 bound 项灰显（R-04 场景2），不丢已绑关系 |
| 超管豁免口径与 R-17 不一致 | 复用同一 `AdminRoleGuard.isAdmin`，不新造判定 |
| DDL 加列对存量部署 | `ADD COLUMN IF NOT EXISTS` + 幂等回填；rollback 可 DROP COLUMN |

### P-5 需拍板口径（§P 门控）

- **Q-P1** 选择器"关联组的"= **我所在的组**（`account_group_info.account_id=me`），还是**智能体所在的组**？
  建议：**可见集合用"我所在的组"**（我能看到哪些资源），**可绑定性 selectable 用"智能体所在的组"**（沿用现有 bindable 逻辑）——两者职责分离，与 R-04 一致。

## §T 增/改/废任务

> §P 已确认（2026-10-07，陈卓）。新增任务沿用父 spec 编号序列：**T-24 / T-25 / T-26**，均 `关联: R-18`、`CR: CR-01`。
> 说明：bug 修复证据文件已占用 `T-24_result.txt`/`T-25_*.txt`/`T-26_result.txt` 之名（BUG-135/138），
> 本 CR 任务证据改用 `T-24_r18_*.txt` 等区分命名，避免混淆。

- [ ] T-24 skill 加 creator 列 + 存量回填 + kbase 'system' 归 admin（V2.0.0_13）
  关联: R-18 ｜ CR: CR-01 ｜ 处置: 改造（DDL + 数据）
  依赖: 无
  验证方式: `V2.0.0_13`（+rollback）drill 正反向 + 洁净库重放；活库 migrator 应用；
    核对 skill 7 条 creator=admin、kbase 'system' 行 creator=admin id
  验收标准: `tbl_harness_skills.creator` 存在且新建 skill 写入当前用户；存量 7 条 = admin；
    kbase 无 'system' creator；rollback 可复原

- [ ] T-25 三类资源管理页归属过滤 + 单对象归属校验（知识库/技能/MCP）
  关联: R-18 ｜ CR: CR-01 ｜ 处置: 改造（page/queryByPage + 单对象端点）
  依赖: T-24（skill 需先有 creator 列才能过滤）
  验证方式: 列表接口对普通用户仅返本人 creator 行、超管返全部；单对象端点非本人且非超管 → 403；
    drill + 活库 API 实测（chenzhuo vs admin 对照）
  验收标准: chenzhuo 技能/知识库/MCP 管理页只见自己的；admin 见全部；
    chenzhuo 直连他人资源 详情/编辑/删除 → 403；admin 可操作他人资源（超管豁免，同 R-17）

- [ ] T-26 智能体编辑选择器改 own ∪ myGroups ∪ public（skill options / mcp options / kbase bindable）
  关联: R-18 ｜ CR: CR-01 ｜ 处置: 改造（options/bindable 可见集合；selectable 仍按 agent 组，Q-P1）
  依赖: T-24
  验证方式: 三选择器对普通用户返回 = 自己的 ∪ 本人所在组关联的 ∪ 无组授权行的（公共）；超管 = 全部；
    已绑定但不再可见者保留灰显（bound=true, selectable=false）
  验收标准: chenzhuo 在智能体编辑里看不到"他人私有且未授权给其组"的 skill/mcp/kbase；
    能看到自己的 + 其组关联的 + 公共的；admin 看到全部

> **对既有任务的处置说明**：T-07（skill/mcp options 全量池）与 T-09（组侧端点）保持 `[x]` 不作废——
> 其"组授权维护"能力保留；仅 options 的**可见集合语义**被 T-26 取代（见 §P-2 处置表）。

## §I 执行与合入八动作（待 §T 确认后执行）

（占位）

## §I 执行与合入八动作（核对清单）

- [x] ① R-18 落 requirements（v2.7.0，已确认 陈卓）
- [x] ② 三文档版本头 bump（requirements v2.7.0 / plan v1.7.0 / tasks v1.7.0）
- [x] ③ changelog 记到编号级（v2.7.0 = CR-01 合入）
- [x] ④ 新 T-24/T-25/T-26 并入 tasks.md 并勾选；覆盖矩阵补 R-18
- [x] ⑤ 本 CR 文件盖「已合入 → 父 v2.7.0 | 日期 | 收口 commit」
- [x] ⑥ MILESTONE 计数同步（T-01~T-26）+ artifacts 重跑（涉 SQL → 13/13，补第 13 件）
- [x] ⑦ 关联账联动：BUG-139 登记；教训 L-64
- [x] ⑧ 合入登记独立 commit `docs(spec): CR-01 合入 …`

## 合入后追加（2026-10-07）

- **BUG-140（CR-01 回归）**：upload 的 `me()` 误入 reactive 异步段 ⇒ NotLoginException。已修（operator 同步段取值），
  端到端验证通过；教训 L-65。该修复为 CR-01 收口后的**修正 commit**，不改变 R-18 语义。
