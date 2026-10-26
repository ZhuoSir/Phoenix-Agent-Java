# 升级演练手册：v2.0.0 → v2.1.1（实测版）

> 本文是 **2026-10-26 真机演练** 的完整步骤与结果记录（不是纸面流程）。
> 演练形态：同机并行栈（不复用生产栈），项目名 `phxupg`、端口 `9191`、DB 卷 `phxupg_*`。
> 结论：**9 步安装器全通过 + 11 项验证全绿 + 回滚可逆**。

## 0. 演练环境

| 项 | 值 |
|---|---|
| 宿主 | macOS / Docker Desktop 20.10.20 / Compose v2.12.0 / arm64 |
| 基线 | v2.0.0（镜像 `phoenix-backend:v2.0.0`、`phoenix-frontend:v2.0.0`） |
| 目标 | v2.1.1（交付包 `phoenix-2.1.1-arm64.tar.gz`，sha256 `3a08a1f9…`） |
| 项目名/端口 | `phxupg` / `9191`（与主栈 `phx210`/`9091` 隔离） |

## 1. 准备 v2.0.0 基线（模拟客户现网）

```bash
# 1.1 取 v2.0.0 的 docker 与 sql 资产（真实交付包结构同构：docker/ + sql/ + releases/）
cd /tmp && rm -rf upg200 && mkdir upg200 && cd upg200
git -C <repo> archive v2.0.0 docker/ sql/ releases/ | tar -x

# 1.2 准备 .env（升级时会被 --env-from 承接）
cat > docker/.env <<'EOF'
IMAGE_TAG=v2.0.0
PHOENIX_HTTP_PORT=9191
PG_PASSWORD=phoenix
EOF

# 1.3 启动基线
cd docker && docker compose -p phxupg up -d
```

> ⚠️ **必踩坑（本次真实遇到）**：`releases/` 必须一起挂载/解包。只挂 `sql/` 时 migrator 只做基线
> （`all_schema.sql`），**不会**执行 `releases/v1.2.0/sql/V1.2.0_05__model_default_column_index_and_backfill.sql`，
> 结果 `tbl_data_model_config` 缺 `is_default` 列，v2.0.0 后端启动即 `Application run failed`
> （`ERROR: column "is_default" does not exist`）。
> 补齐 releases 后重跑 migrator（`--force-recreate migrator`）即恢复。

基线验收：`POST /api/privilege/auth/login` → **200**。

## 2. 升级到 v2.1.1（官方安装器，离线）

```bash
# 2.1 解包交付包（v2.1.1 包已含 images/postgres等离线镜像 tar）
tar -xzf phoenix-2.1.1-arm64.tar.gz -C /tmp/pkg211b
cd /tmp/pkg211b/phoenix-2.1.1-arm64

# 2.2 执行升级（--env-from 承接旧密码/端口；--offline 走包内镜像）
bash install.sh --project phxupg --offline --env-from /tmp/upg200/docker/.env --timeout 900
```

安装器 9 步（实测输出）：

| 步 | 内容 | 结果 |
|---|---|---|
| 1 | 环境探测（引擎类型/Compose v2/架构） | ✅ linux 引擎 + Compose 2.12.0 |
| 2 | 引擎就位 | ✅ 已在，跳过安装 |
| 3 | SHA256SUMS 全量校验 | ✅ |
| 4 | 镜像 load | ✅ 同 tag 已在，跳过 |
| 5 | `.env` 生成/保留 | ✅ 承接旧 `.env`，**IMAGE_TAG v2.0.0→2.1.1**，密码/端口不动 |
| 6 | compose up（每次必跑） | ✅ 镜像一致性核验：`nginx-1 ← phoenix-frontend:2.1.1`、`backend-1 ← phoenix-backend:2.1.1` |
| 7 | 等 migrator + backend healthy | ✅ |
| 8 | verify 全断言 | ✅ 通过（失败即判安装失败） |
| 9 | 收据卡 | ✅ 版本 2.1.1 / 地址 9191 |

**迁移自动执行**（migrator 读取 `releases/v*/sql`，写入台账 `tbl_phoenix_release`）：
```
V2.1.0_01__kb_embedding_binding_ddl   23:03:28
V2.1.0_02__kb_rebuild_job_ddl         23:03:28
V2.1.0_03__vector_tables_pk_ddl       23:03:28
V2.1.0_04__brand_config               23:03:28
```
> 手工执行等价命令（安装器不可用时的兜底）：
> ```bash
> for f in sql/V2.1.0_01__kb_embedding_binding_ddl.sql sql/V2.1.0_02__kb_rebuild_job_ddl.sql \
>          sql/V2.1.0_03__vector_tables_pk_ddl.sql sql/V2.1.0_04__brand_config.sql; do
>   docker exec -i phxupg-postgres-1 psql -U phoenix -d phoenix < "$f"
> done
> ```

## 3. 升级后验证（11 项，全部实测）

| # | 验证 | 期望 | 实测 |
|---|---|---|---|
| 1 | 登录 `POST /api/privilege/auth/login` | 200 | **200** |
| 2 | 首页 `GET /` | 200 | **200** |
| 3 | 品牌匿名 `GET /platform/brand-config` | 200 + 4 字段 | **200**（`Phoenix Agent / #006BE6`） |
| 4 | 图标 `GET /icons/lucide.json` | 200 JSON | **200 application/json** |
| 5 | 缺失图标 `GET /icons/nope.json` | **404**（不回落 index.html） | **404** |
| 6 | 报表 vendor `GET /js/vendor/echarts.min.js` | 200 | **200** |
| 7 | 附件上限 `GET /api/chat/attachment/limit` | `{maxCount:50}` | **maxCount=50** |
| 8 | 品牌权限 `GET /platform/brand-config/permission` | `canEdit:true`（超管） | **true** |
| 9 | 迁移台账 | 含 V2.1.0_01~04 | **4 条齐** |
| 10 | 结构到位 | 品牌表 / KB 绑定列 / 重建表 | **1 / 2 / 1** |
| 11 | 数据保留 | 用户/菜单/模型配置不丢 | **用户 2 / 菜单 21 / 模型 2** |

容器最终态：
```
phxupg-nginx-1    phoenix-frontend:2.1.1  healthy
phxupg-backend-1  phoenix-backend:2.1.1   healthy
phxupg-redis-1    redis:7-alpine          healthy
phxupg-postgres-1 pgvector/pgvector:pg16  healthy
```

## 4. 回滚演练（实测可逆）

SQL 均为**增量**（新增表/列），回滚只需换回旧镜像：
```bash
cd /tmp/upg200/docker
docker compose -p phxupg --env-file .env -e IMAGE_TAG 2>/dev/null || true
IMAGE_TAG=v2.0.0 PHOENIX_HTTP_PORT=9191 docker compose -p phxupg up -d --force-recreate backend nginx
```
实测：回滚后 `phxupg-backend-1 ← phoenix-backend:v2.0.0`，**登录仍 200**，数据（用户 2 / 菜单 21）完好。
再切回 `IMAGE_TAG=2.1.1` 即回到升级态（登录 200）。

> 若需**彻底回退结构**（不建议，通常无需）：
> `releases/v2.1.0/sql/rollback/V2.1.0_04→03→02→01`（逆序）+ 用升级前 `pg_dump` 恢复。

## 5. 结论与注意事项

1. **升级流程完整**：安装器一条命令完成「校验→载镜像→承接 env→重建容器→迁移→健康等待→verify→收据」。
2. **迁移幂等**：`tbl_phoenix_release` 台账保证重复执行跳过；四件 SQL 均可重跑。
3. **数据不丢**：升级不动业务数据（本次演练用户/菜单/模型配置均保留）。
4. **回滚可逆**：换镜像即可回旧版；结构回退只在必要时用 rollback + 备份。
5. **易踩坑清单**：
   - 手工搭基线时**必须**带 `releases/`（否则缺 `is_default` 等增量列，后端起不来）
   - 内网/镜像源：`docker.m.daocloud.io` 拉 `postgres:16-alpine` 会卡死，建议 `--mirror https://docker.1panel.live`
   - 端口/项目名：升级演练务必用独立 `-p`/端口，避免影响生产栈
6. **已知限制**：v2.1.1 与 v2.1.0 行为一致，9 条 P1 以「已延期」随版（`BL-75` 跟进），见
   `releases/v2.1.0/RELEASE-NOTES.md` §四之三。
