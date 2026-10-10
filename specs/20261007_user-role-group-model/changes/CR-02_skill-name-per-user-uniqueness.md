# CR-02 · 技能上传重复判定改为「按用户 + 名称」（跨用户同名允许）

> 父 spec: `specs/20261007_user-role-group-model`（在途 **v2.7.0**，未打 tag ⇒ 允许 CR）
> 状态: **已合入 → 父 v2.8.0 | 2026-10-07 | 代码+SQL 收口见本文件 §I** | 档位提议: **major**（涉 SQL 唯一索引变更）| 提请人: 陈卓（2026-10-07 口令）
> 用户原话:「现在上传skill，判断重复是根据名称判断的，但是两个不同用户上传的同名skill，这个按道理是可以上传的，
> 所以重复判断校验要加上用户判断」

---

## §S 变更意图

### S-1 三张清单

**修改 R：**
- **R-02（技能上传/覆盖语义）**：重复判定由「全局 name 唯一」改为「**同一创建人内 name 唯一**」；
  `overwrite=true` 的覆盖对象由「任意同名技能」收窄为「**本人**的同名技能」。

**新增 R：**
- **R-19 技能名称唯一性按创建人隔离**：不同用户 SHALL 可上传同名技能；同一用户内同名 SHALL 仍互斥
  （未开 overwrite 时报错提示，开了 overwrite 则覆盖本人那条）。

**废除 R：** 无。

### S-2 现状实测（变更依据）

- 代码：`SkillAdminServiceImpl.upload:156` 判重为 `selectOneByQuery(where name = ?)` —— **全局**、不看 creator；
  且 `overwrite=true` 时**覆盖任意同名技能**（含他人创建的）⇒ 跨用户覆盖洞。
- DB：`tbl_harness_skills_name_key` 为 **UNIQUE(name)** 全局唯一索引 ⇒ 第二用户同名插入会被 DB 拒绝。
  ⇒ 仅改代码不够，**必须改唯一索引**（故档位 major）。
- 运行期/绑定：技能一律按 **id** 解析（`tbl_data_agent_skill_info.skill_id`）；全仓按 name 查 skill 的**仅上传判重一处**
  ⇒ 跨用户同名**不会**影响运行期。

### S-3 受影响既有 R 核查（必答）

| 既有 R | 是否受影响 | 说明 |
|---|---|---|
| R-02 技能上传/覆盖 | **是** | 判重与覆盖范围改按创建人 |
| R-18 资源归属（creator） | **是（依赖）** | 本 CR 以 R-18 的 creator 列为判重维度 |
| R-04 组授权 / R-17 智能体归属 | 否 | 不涉及 |

### S-4 Non-goals

- 不改运行期技能解析（仍按 id）。
- 不给存量技能改名/加后缀。
- 前端列表/选择器**暂不展示创建人**（同名跨用户时的区分展示另议，见 Q-D1）。
- 不动 MCP/知识库的同名策略（本次仅技能，因仅技能有全局唯一索引与按名覆盖语义）。

### S-5 为何不另立 spec

紧接 R-18（creator 列）的语义收口：判重维度从全局改按 creator 是 R-18 归属模型的直接延伸；
另立会割裂"归属"口径。复用同一 creator 列与 AdminRoleGuard 超管口径。

### S-6 范围自检问句

> 一句话说明对现有用户可见行为的影响？
> **能**：不同用户可上传同名技能；同一用户内同名仍互斥（overwrite 只覆盖自己的）。

### S-7 待确认问题

- **Q-D1** 跨用户同名后，管理页/选择器会出现同名条目。是否本次**顺带在技能列表与选择器显示创建人**以区分？
  （建议：本次不做 UI，仅后端语义；如要区分展示另开小 CR/BL。）
- **Q-D2** 覆盖语义确认：`overwrite=true` 时**只覆盖本人同名技能**；对他人的同名技能即使 overwrite=true 也**新建一条**（不覆盖）。确认？

---

## §P 影响面与已实现处置表

> §S 已确认（2026-10-07，陈卓）：档位 **major**；Q-D1 本次不做 UI 区分；Q-D2 overwrite 只覆盖本人的。

### P-1 影响面

| 层 | 位置 | 变更 |
|---|---|---|
| SQL | `V2.0.0_14`（+rollback） | ① `tbl_harness_skills.creator` 设 NOT NULL（V2.0.0_13 已回填全非空）；② **DROP** 全局唯一索引 `tbl_harness_skills_name_key(name)`；③ **CREATE UNIQUE** `(name, creator)` |
| 后端 | `SkillAdminServiceImpl.upload` | 判重 `where name=?` → `where name=? and creator=?`；overwrite 仅覆盖**本人**同名；他人同名 ⇒ 走新建分支 |
| 后端 | `SkillAdminServiceImpl.upload` insert | 捕获唯一键冲突（并发同名同用户）→ 返回友好 fail 而非 500 |
| 前端 | 无 | Q-D1 本次不做创建人展示 |

### P-2 已实现处置表

| 已实现 | 处置 | 说明 |
|---|---|---|
| R-18 / V2.0.0_13 的 `creator` 列与回填 | **保留（本 CR 基础）** | 判重维度即此列 |
| upload 的 overwrite 覆盖逻辑 | **改造** | 覆盖范围收窄为本人同名 |
| DB `UNIQUE(name)` | **改造** | → `UNIQUE(name, creator)` |
| 运行期/绑定按 skill **id** 解析 | **保留** | 跨用户同名不影响运行期（全仓按 name 查 skill 仅判重一处） |
| MCP / 知识库同名策略 | **保留** | Non-goal，本次不动 |

### P-3 被拒替代方案（含"另立新 spec"）

| 方案 | 拒绝理由 |
|---|---|
| **另立新 spec** | 是 R-18 creator 归属模型的直接语义收口，复用同一列与超管口径；另立割裂归属口径 |
| 只改代码判重、不改索引 | DB `UNIQUE(name)` 仍会拒绝跨用户同名插入 ⇒ 需求无法达成 |
| 同名自动加后缀（name-2） | 篡改用户可见名称，与"允许同名"诉求相悖 |
| 前端显示创建人区分 | Q-D1 裁定本次不做（管理页 own-only 已天然隔离） |

### P-4 风险与规避

| 风险 | 规避 |
|---|---|
| **rollback 前置条件**：若回滚时已存在跨用户同名行，重建 `UNIQUE(name)` 会失败 | rollback 文档注明前置；回滚前先清理跨用户同名或接受失败 |
| 并发同用户同名上传撞唯一键 → 500 | insert 捕获 DuplicateKey → 友好 fail「同名技能已存在」 |
| creator NOT NULL 对历史/未来插入 | V2.0.0_13 已回填全非空；upload 恒写 creator（R-18） |

### P-5 需拍板口径

无新增（Q-D1/Q-D2 已在 §S 拍板）。

---

## §T 增/改/废任务

> §P 已确认（2026-10-07，陈卓）。新增任务沿用父 spec 编号：**T-27 / T-28**，均 `关联: R-19`、`CR: CR-02`。

- [ ] T-27 技能名称唯一性改按创建人（V2.0.0_14：creator NOT NULL + UNIQUE(name,creator) 替换 UNIQUE(name)）
  关联: R-19 ｜ CR: CR-02 ｜ 处置: 改造（DDL 索引）
  依赖: 无
  验证方式: drill 正反向 + 洁净库重放（01~14）；活库 migrator 应用；
    核对索引定义变为 (name,creator)、creator 全非空；插入跨用户同名成功、同用户同名被拒
  验收标准: `tbl_harness_skills_name_key` 消失；存在 UNIQUE(name,creator)；creator NOT NULL；
    rollback 可复原（前置：无跨用户同名行）

- [ ] T-28 upload 判重/覆盖按创建人 + 并发撞键友好失败
  关联: R-19 ｜ CR: CR-02 ｜ 处置: 改造（upload 判重与覆盖范围）
  依赖: T-27
  验证方式: 活库 API：用户 A 上传 name=X 成功；用户 B 上传同名 X 成功（新建）；
    用户 A 再传 X（overwrite=false）→ fail 提示已存在；（overwrite=true）→ 覆盖**本人**那条且 B 的不受影响；
    并发同用户同名 → 友好 fail 非 500
  验收标准: 跨用户同名可共存；同用户同名互斥；overwrite 不跨用户；无 500

> 对既有任务处置：T-24（creator 列）保留不作废，本 CR 在其上收口语义。

---

## §I 执行与合入八动作（核对清单）

- [x] ① R-19 落 requirements（v2.8.0，已确认 陈卓）+ 修改 R-02 覆盖语义
- [x] ② 三文档版本头 bump（requirements v2.8.0 / plan v1.8.0 / tasks v1.8.0）
- [x] ③ changelog 记到编号级（v2.8.0 = CR-02 合入）
- [x] ④ 新 T-27/T-28 并入 tasks.md 并勾选；覆盖矩阵补 R-19
- [x] ⑤ 本 CR 文件盖「已合入 → 父 v2.8.0 | 日期」
- [x] ⑥ MILESTONE 计数 T-01~T-28 + artifacts 重跑（涉 SQL → 14/14，补第 14 件）
- [x] ⑦ 关联账联动：教训 L-66（约束 vs 索引 / drill 假通过）
- [x] ⑧ 合入登记独立 commit `docs(spec): CR-02 合入 …`

