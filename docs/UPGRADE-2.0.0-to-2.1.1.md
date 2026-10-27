# 升级演练手册：v2.0.0 → v2.1.1（实测版）

> 本文是 **2026-10-26 真机演练** 的完整步骤与结果记录（不是纸面流程）。
> 演练形态：同机并行栈（不复用生产栈），项目名 `phxupg`、端口 `9191`、DB 卷 `phxupg_*`。
> 结论：**9 步安装器全通过 + 11 项验证全绿 + 回滚可逆**。

## 0.1 端口可改（本次已实测切换）

端口**唯一入口**是 `.env` 的 `PHOENIX_HTTP_PORT`（compose 里 `${PHOENIX_HTTP_PORT:-9080}:80`）。

```bash
# 方式 A：改 .env（推荐，升级/重装都沿用）
sed -i '' 's|^PHOENIX_HTTP_PORT=.*|PHOENIX_HTTP_PORT=8901|' docker/.env
docker compose -p <project> up -d --force-recreate nginx    # 只重建 nginx 即可换端口

# 方式 B：安装器首次生成 .env 时指定
bash install.sh --project <project> --port 8901
```

本次演练：`9191 → 8900 失败（本机 8900 被 Python 占用）→ 实际切到 8901`，
`0.0.0.0:8901->80/tcp`，登录 200、首页/品牌/图标/vendor 全 200。

> 提醒：改端口只需重建 **nginx**（backend 不用动）；若端口被占用，`docker compose` 会明确报
> `Ports are not available: ... address already in use`，换一个空闲端口即可。

## 0. 演练环境

| 项 | 值 |
|---|---|
| 宿主 | macOS / Docker Desktop 20.10.20 / Compose v2.12.0 / arm64 |
| 基线 | v2.0.0（镜像 `phoenix-backend:v2.0.0`、`phoenix-frontend:v2.0.0`） |
| 目标 | v2.1.1（交付包 `phoenix-2.1.1-arm64.tar.gz`，sha256 `3a08a1f9…`） |
| 项目名/端口 | `phxupg` / **8901**（演练中由 9191 切换而来；与主栈 `phx210`/`9091` 隔离） |

## 最简操作（照抄即可）

**路径不是固定的**：包解到哪儿就 `cd` 到哪儿；`tar` 会自带一层目录 `phoenix-2.1.1-arm64`。

```bash
# ① 解包（放哪都行，示例用当前目录）
tar -xzf phoenix-2.1.1-arm64.tar.gz
cd phoenix-2.1.1-arm64

# ② 升级 —— 一条命令，不用查任何东西
bash upgrade.sh
```

看到 **`Phoenix 安装收据 —— 安装成功`** 即完成（镜像、迁移、健康等待、自检都在这一步里）。

`upgrade.sh` 自己会去机器上找「装过的那一套」，找不到就说清缺什么：

| 情形 | 它的行为 |
|---|---|
| 机器上只有一套 Phoenix | 自动取项目名（从容器 label 反查，运行中/已停止都能取到） |
| 机器上有多套（如既有生产栈又有演练栈） | **拒绝猜**，列出「容器 → 项目名」让你挑：`bash upgrade.sh --project <名>` |
| 旧安装的 `docker/.env` | 先从容器 label 定位；不行再在 `/opt /srv /data ~` 找带 `PG_PASSWORD=` 的那个 |
| 就是找不到旧 `.env` | **中止**（不给它安装器会随机生成库密码 ⇒ 新容器连不上旧数据卷），并教你手动指定 |
| 想先看会干什么 | `bash upgrade.sh --dry-run`（零副作用，只打印将要执行的命令） |

### 老包（含手头的 `phoenix-2.1.1-arm64.tar.gz`）里还没有 `upgrade.sh`

一键入口是 2026-10-27 补的，**要重新打包才会进包**。拿到旧包时仍用 `install.sh`，两个参数必须填对：

```bash
# ① 查旧安装的项目名（输出形如 phoenix-backend-1 → 项目名 = phoenix）
docker ps -a --format '{{.Names}}' | grep backend

# ② 查旧安装的 .env 在哪
find / -name .env -path '*docker*' 2>/dev/null | head

# ③ 升级（把两个占位换成上面查到的结果）
bash install.sh --offline --timeout 900 \
  --project  <项目名>          \
  --env-from <旧安装目录>/docker/.env
```

| 参数 | 填什么 | 填错的后果 |
|---|---|---|
| `--project` | 旧安装的项目名（`docker ps` 容器名前缀） | 项目名不对 ⇒ 会新建一套空栈，旧栈还在跑旧版 |
| `--env-from` | 旧安装的 `docker/.env` 绝对路径 | 不给 ⇒ 随机生成库密码，新容器连不上旧数据卷 |

> 其余都可以默认：包目录位置、执行目录、`--timeout`（默认 300，建议给 900）。
> 装好后要改端口见 §0.1；日常启停用包内 `docker/scripts/phoenix-ctl.sh`。


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

## 5. 一键升级入口 `upgrade.sh`（2026-10-27 落地，实测）

上面 §2 那条命令要手填两个参数，容易看迷糊，故把「查项目名 + 找旧 .env + 调安装器 + 自检」收进一条：

```bash
cd phoenix-2.1.1-arm64 && bash upgrade.sh          # 交付包内（推荐）
bash upgrade.sh --dry-run                          # 只打印将执行什么，零副作用
bash docker/scripts/upgrade.sh v2.1.1              # 仓库开发栈（同一脚本自动判别场景）
```

### 5.1 它替人做的判断

| 情形 | 行为 |
|---|---|
| 一套栈 / 多套栈 | 单套自动取项目名；**多套拒绝猜测**，列出「容器 → 项目名」要求 `--project` 指定 |
| 旧 `.env` | 先按容器 label `com.docker.compose.project.config_files` 反查；查不到再在 `/opt /srv /data ~` 内找带 `PG_PASSWORD=` 的 `docker/.env`（**跳过 git 工作树内的**，防误用开发栈密码） |
| 仍找不到旧 `.env` | **中止**并说明后果（不给 ⇒ 安装器随机生成库密码 ⇒ 连不上旧数据卷），而不是带着新密码往下装 |
| 找到的 `.env` 不在旧 compose 目录同层 | 继续但 ⚠️ 提示核对库密码 |
| 其它未识别参数 | 连同取值原样转给 `install.sh`（不吞参数、不假装是版本号） |
| 升级完成 | 打印地址 / 登录状态码 / 容器与镜像 / 迁移台账查询命令 |

脚本**不另造安装流程**：包根有 `install.sh` + `manifest.json` 即判为交付包场景，内部就是
`bash install.sh --offline --timeout 900 --project … --env-from …`（九步状态机、幂等续跑都在安装器里）。

### 5.2 顺手修掉的三个真缺陷（仓库原 `upgrade.sh`）

| # | 原状 | 后果 | 现状 |
|---|---|---|---|
| 1 | 硬门槛 `releases/<VER>/sql` 不存在即「拒绝执行」 | **v2.1.1 这类 PATCH 版（无 SQL）根本升不了** | 有 SQL 才迁移，无则跳过并说明 |
| 2 | `TAG="${IMAGE_TAG:-v1.2.0}"` 赋值后**从未使用**（死变量） | 切版本不生效，容器仍跑旧镜像 | tag 真正写入 `docker/.env` 的 `IMAGE_TAG` 或本次注入 |
| 3 | 只存在于 `docker/scripts/`，**不入交付包根** | 客户机没有 `bash upgrade.sh` 这个入口 | `package.sh` 与 `install.sh` 同级入包根，并由出货门禁断言 |

### 5.3 实测证据（全部真跑）

```
bash docker/scripts/tests/t09_upgrade_selftest.sh  → 通过 22 / 失败 0 / 跳过 0
bash docker/scripts/tests/t07_gate_selftest.sh     → 通过 12 / 失败 0（门禁由四项升为五项，含 upgrade-entry）
```

t09 关键断言：场景判别、命令组装与 `install.sh` 参数契约一致、dry-run 零副作用（不写状态/不生成 `.env`）、
多栈并存拒绝猜测（本机实测 5 套栈）、缺旧 `.env` 不放行、`--env-from` 文件不存在早退、
未知参数透传、PATCH 版跳过迁移、tag 真正落到命令里、打包与门禁接线。

t07 在**真实 2.1.1 交付包**上跑通五项门禁：`sha256sums` / `jar-readable` / `prereq-match` /
**`upgrade-entry`** / `fresh-db-install`；并验反例「包根缺 `upgrade.sh` ⇒ 门禁 FAIL 拦下」。

真实探测也验过：`--project phx210` 从容器 label 正确反查到
`…/Phoenix-Agent-Java/docker/.env`；`--project phxupg`（其 compose 目录已被系统清掉）
**拒绝执行**而非随机生成密码。

### 5.4 现有包与重打包

手头的 `docker/dist/phoenix-2.1.1-arm64.tar.gz` 打于 2026-10-26，**包根没有 `upgrade.sh`**；
拿旧包仍按 §2 用 `install.sh` 两条参数。下次打包（`package.sh`）起自动入包并被门禁把关。

## 6. 结论与注意事项

1. **升级流程完整**：安装器一条命令完成「校验→载镜像→承接 env→重建容器→迁移→健康等待→verify→收据」；
   现在对外只暴露 `bash upgrade.sh` 一个动作。
2. **迁移幂等**：`tbl_phoenix_release` 台账保证重复执行跳过；四件 SQL 均可重跑。
3. **数据不丢**：升级不动业务数据（本次演练用户/菜单/模型配置均保留）。
4. **回滚可逆**：换镜像即可回旧版；结构回退只在必要时用 rollback + 备份。
5. **易踩坑清单**：
   - 手工搭基线时**必须**带 `releases/`（否则缺 `is_default` 等增量列，后端起不来）
   - 内网/镜像源：`docker.m.daocloud.io` 拉 `postgres:16-alpine` 会卡死，建议 `--mirror https://docker.1panel.live`
   - 端口/项目名：升级演练务必用独立 `-p`/端口，避免影响生产栈
6. **已知限制**：v2.1.1 与 v2.1.0 行为一致，9 条 P1 以「已延期」随版（`BL-75` 跟进），见
   `releases/v2.1.0/RELEASE-NOTES.md` §四之三。
