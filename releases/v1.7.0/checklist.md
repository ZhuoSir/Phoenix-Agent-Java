# v1.7.0 发布检查单（M3 汇总 / M4 发布）

> 冻结批次（2026-10-06）；**发版批次待用户口令**（tag / CHANGELOG / push / 合并 main）

## 一、M3 汇总（冻结批次）——已完成

- [x] **需求挂接核对**：3 个 spec 全部并入（`kb-access-isolation` **12/15**、`unified-account-center` **20/23**、`visibility-filetree-hygiene` **12/12**）；MILESTONE 需求行与 `version.md` 一致
- [x] **SQL 汇总与 Flyway 风格校对**：`sql/V1.7.0_01~04` 齐（4 件 + `rollback/` 4 件，已聚合到本目录）
- [x] **零 DDL 声明核对**：`kb-access-isolation`、`visibility-filetree-hygiene` 均**零 DDL**（与各自 `artifacts.md` 一致）
- [x] **文档落盘**：`MILESTONE.md`（状态已冻结）/`RELEASE-NOTES.md`/`UPGRADE.md`/`config/changes.md`/`checklist.md`
- [x] **迁移台账在册核对（原未通过 → 2026-10-06 已补齐）**：`tbl_phoenix_release` 最新为 **V1.6.0_03（2026-10-05）**，**没有 V1.7.0_01~04 行**；
  但实测**变更确已生效**：`tbl_unified_account_map`、`tbl_unified_account_migration_rows` 存在，`V1.7.0_03/04` 的菜单行在（`AgentRun` 等命中 2 行）
  ⇒ 判定为**"执行方式未登记"**（疑似手工 `psql` 执行）。**风险**：后续迁移器可能重复执行或顺序错乱。→ 已登记 **BUG-115** 并**已补齐 4 行**（备份 `tbl_phoenix_release_bak_20261006`；证据 `evidence/BUG-115_ledger-backfill.txt`，含回滚语句）

## 二、验证（冻结批次，实测留档）

- [x] `sh docker/scripts/verify.sh` → **13 PASS / 0 FAIL**
- [x] 静态面：KB 原件与工作区文件 **404 且正文无泄漏**（修复前 200 + 155,827B 正文）；头像 **200**
- [x] 受控接口：未登录=**业务码 401**、无权=**403 无正文**、属主/管理员=**200+正文**
- [x] 部署三段：jar → 镜像重建 → `backend started=11:43:18Z health=healthy` + `/echo/ok=200`
- [x] 前端门禁：`typecheck` 212（基线不变）、`build` ✓
- [x] 行为取证（真实对话）：问"北科软的业务报告"→ **直接调检索、未翻工作区**；要求直读原件 → **拒绝且未输出正文**

## 三、M4 发布（**待用户口令**）

- [ ] 发布演练（在演练库/演练环境跑 `UPGRADE.md` 全流程）
- [ ] 打 tag：`v1.7.0`
- [ ] 生成/更新根 `CHANGELOG.md`
- [ ] 合并 `v1.7.0` → `main`
- [ ] 推送 tag 与 `main`
- [ ] 通知使用方：**升级将导致全员重登一次**；回滚会恢复 BUG-108 暴露面

## 四、遗留与去向（冻结时登记）

| 项 | 去向 |
|---|---|
| `kb-access-isolation` T-08/T-09/T-11 三项待收口 | 该 spec `completion.md`（已如实登记） |
| **BUG-115** 迁移台账漂移 | 待办（下次 DB 变更前处理） |
| BUG-105 / 112 / 113 / 114 / 107 / 102 / 109 | `bugs.md`（部分已延期/部分待办） |
| BL-31（已吸收 BL-35）/ BL-32 / BL-33 / BL-34 | `backlog.md` |
