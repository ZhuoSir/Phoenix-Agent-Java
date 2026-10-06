> 版本: v1.0.0 | 生成: 2026-10-06 | 挂载: v1.7.0

# 升级件登记：unified-account-center（统一账号中心 / 一期）

> 纪律：升级件先落 **spec 目录**，M3 汇总时才聚合进 `releases/v1.7.0/sql/`（禁止单需求完成时私自塞散件）。

## 一、SQL 升级件

| 序号 | 文件 | 类型 | 内容 | 回滚 | 状态 |
|---|---|---|---|---|---|
| 01 | `sql/V1.7.0_01__unified_account_map.sql` | DDL | 新建映射/审计表 `tbl_unified_account_map`（主键 `old_account_id` + 索引 `idx_uam_new_user`；append-only 不设逻辑删列） | `sql/rollback/V1.7.0_01__unified_account_map_rollback.sql`（先备份映射表再 drop） | **已演练**（证据 `evidence/T-02_ddl-and-rollback-drill.txt`） |
| 02 | （待 T-09/T-10 产出）`V1.7.0_02__unified_account_migration.sql` | DML | 分支 B 新建（沿用前台 id）+ 分支 A 8 类列重写 + 密码重算 + M8 thinktest 清理 | 同目录 rollback（T-12 补齐并演练） | 待实现 |

## 二、配置变更

| 项 | 变更 | 说明 |
|---|---|---|
| sa-token 会话存储 | **不变**（进程内存） | 裁定 D2：不落 Redis；BUG-100 挂账 |
| 前端 token 键 | **不变**（`phoenix-token`） | loginId 语义统一为 `tbl_privilege_user.id` |
| 后端环境变量 | **无新增** | — |

## 三、本地/演练环境产物（不入库）

| 产物 | 位置 | 说明 |
|---|---|---|
| 演练库 | postgres 容器内 `phoenix_drill` | 由 `pg_dump phoenix \| psql phoenix_drill` 复制；DDL/迁移/回滚一律先在此演练 |
| 备份/导出 | 仓库根 `backups/`（已 gitignore） | 迁移前全量快照、8 类列计数快照、映射表备份 |

## 四、待汇总清单（M3 用）

- [ ] SQL 01/02 聚合进 `releases/v1.7.0/sql/`（含 rollback 配对，Flyway 风格编号）
- [ ] `config/changes.md`（如无配置变更则记「无」）
- [ ] RELEASE-NOTES（从 R 条款聚合：R-01~R-04、R-06~R-10、R-20/R-21；R-05 二期）
- [ ] UPGRADE.md（部署顺序 / SQL 顺序 / 验证步骤 / 回滚方式；**含强制重登告知**）
