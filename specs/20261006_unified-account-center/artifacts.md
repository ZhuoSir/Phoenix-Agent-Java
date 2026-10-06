> 版本: v1.1.0 | 生成: 2026-10-06 | 更新: 2026-10-06（v1.1.0：补 03/04 升级件 + 菜单数据与前端路由变更） | 挂载: v1.7.0

# 升级件登记：unified-account-center（统一账号中心 / 一期）

> 纪律：升级件先落 **spec 目录**，M3 汇总时才聚合进 `releases/v1.7.0/sql/`（禁止单需求完成时私自塞散件）。

## 一、SQL 升级件

| 序号 | 文件 | 类型 | 内容 | 回滚 | 状态 |
|---|---|---|---|---|---|
| 01 | `sql/V1.7.0_01__unified_account_map.sql` | DDL | 新建映射/审计表 `tbl_unified_account_map`（主键 `old_account_id` + 索引 `idx_uam_new_user`；append-only 不设逻辑删列） | `sql/rollback/V1.7.0_01__unified_account_map_rollback.sql`（先备份映射表再 drop） | **已演练**（`evidence/T-02_ddl-and-rollback-drill.txt`） |
| 02 | `sql/V1.7.0_02__unified_account_migration.sql` | DML | 分支 B 新建（沿用前台 id）+ 分支 A 8 类列重写（含行级审计表写入）+ 历史口令重算 + M8 thinktest 清理 | `sql/rollback/V1.7.0_02__unified_account_migration_rollback.sql`（**行级精确反向**，逐表 keyexpr） | **已演练 + 已应用生产**（T-09~T-12；`evidence/T-09-T-12_migration-drill.txt`、`evidence/T-12_rollback-drill-v2.txt`） |
| 03 | `sql/V1.7.0_03__agent_chat_center_menu.sql` | DML | 新增**一级菜单**「智能体中心」（`url=/agent/chat`、`component=#/views/front/chat.vue`、`order_no=-1` 置顶、`image=lucide:message-square`）+ 每角色 1 行 `tbl_privilege_acl` 授权 | `sql/rollback/V1.7.0_03__agent_chat_center_menu_rollback.sql` | **已演练 + 已应用生产**（T-18；v2 补图标后**重跑**，脚本幂等 —— `evidence/T-18_menu-mechanism.txt`、`evidence/T-18_prod-apply-verify.txt`） |
| 04 | `sql/V1.7.0_04__agent_chat_center_new_window.sql` | DML | 菜单行 `url`：`/agent/chat` → `#/front/chat`（改为**新窗口菜单**，见 plan 决策 8） | `sql/rollback/V1.7.0_04__agent_chat_center_new_window_rollback.sql`（改回 `/agent/chat`） | **已演练 + 已应用生产**（T-22；`evidence/T-22_new-window-menu.txt`） |

**执行顺序（M3/UPGRADE.md 必须写明）**：01 → 02 →（T-18）03 →（T-22）04。
**幂等性**：02 带 M2 门禁（无匹配即停）、03 先删后插、04 带 `url <> 目标` 条件 ⇒ 重复执行均安全。

## 二、配置与数据变更

| 项 | 变更 | 说明 |
|---|---|---|
| 菜单数据 | 新增一行一级菜单 + 2 行角色 ACL；`url` 改为外链形式 `#/front/chat` | 见 SQL 03/04；前端「外链菜单」约定见 plan 决策 8 |
| 前端路由 | `/front/chat` 恢复为**独立全屏 chat 页**；`/front/agent` 重定向 `/agent/list`；`/agent/chat` 不再是 admin 内路由 | R-12/T-22；纯前端代码，无 SQL |
| 前端登录 | 删除第二套鉴权分支（`userLoginApi`/双 tab/`guard` 的 `userType===1` 分支） | R-11/T-19；后端 `/auth/login` 端点**保留**（pc-ui/mobile-ui 仍调用） |
| sa-token 会话存储 | **不变**（进程内存） | 裁定 D2：不落 Redis；BUG-100 挂账 |
| 前端 token 键 | **不变**（`phoenix-token`） | loginId 语义统一为 `tbl_privilege_user.id` |
| 后端环境变量 | **无新增** | — |

## 三、本地/演练环境产物（不入库）

| 产物 | 位置 | 说明 |
|---|---|---|
| 演练库 | postgres 容器内 `phoenix_drill` | 由 `pg_dump phoenix \| psql phoenix_drill` 复制；DDL/迁移/回滚一律先在此演练 |
| 备份/导出 | 仓库根 `backups/`（已 gitignore） | `prod_pre_migration_20261006_142859.sql`（迁移前全量）、`pre_menu_20261006_145028.sql`、`pre_menu_acl_20261006_145936.sql`（sha256 `6190838f…`，菜单+ACL）、`pre_url_20261006_152136.sql`（sha256 `493575dd…`，菜单表） |

## 四、待汇总清单（M3 用）

- [ ] SQL 01~04 聚合进 `releases/v1.7.0/sql/`（含 rollback 配对，Flyway 风格编号；**保持 01→02→03→04 顺序**）
- [ ] `config/changes.md`：记「菜单数据 2 条 DML 变更 + 前端路由变更」，其余配置无变更
- [ ] RELEASE-NOTES（从 R 条款聚合：R-01~R-04、R-06~R-13、R-20/R-21；**R-03 已删除**；R-05 二期）
- [ ] UPGRADE.md（部署顺序 / SQL 顺序 / 验证步骤 / 回滚方式；**含强制重登告知**；
      **标注 02/03/04 已在生产执行过**——重跑前先按幂等性核对，且 03/04 依赖 02 的账号统一结果）
