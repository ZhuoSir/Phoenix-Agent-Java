# v2.0.0 发版问题复盘（2026-10-10 ~ 10-11）

> 说明：只记录**因本次发版/交付引发或暴露**的问题；每条含 现象 → 根因 → 处置 → 现状。
> 涉及代码/脚本的改动**均在脚本/构建层**，未改数据库与应用代码（迁移 SQL 除外，属运维操作）。

## 一、打包链（发布物产出）——4 项，均已修

| # | 现象 | 根因 | 处置 | 现状 |
|---|---|---|---|---|
| 1 | `package.sh` 步骤 3 崩溃：`common.sh:143: img?: unbound variable` | 6 个脚本里 **31 处** `$var` **紧贴中文字符**（`$img（第…`），某些 locale 下被当成非法变量名 | 统一改 `${var}`（bootstrap/build/install/package/verify/lib 六个文件） | ✅ 已修，语法自检通过 |
| 2 | 每次构建白传 **3.5GB** 上下文（多花 3~5 分钟） | `.dockerignore` 未排除**打包产物自身**（`docker/dist`、`*.tar.gz`） | 已排除；**并撤回**我一度加入的 `docker/.stage/*.jar`（`Dockerfile.backend:52` 正需要它） | ✅ 已修（下次构建上下文降到几十 MB） |
| 3 | **amd64 包打不出来**：`docker.elastic.co/.../9.2.5` 拉 10 分钟仅 12 层、20 秒零增长 | 该官方 registry 在当前网络下基本拉不动；且无镜像站代理 | amd64 改用镜像站可得的 `eclipse-temurin:21-jre-jammy`（也正是 `.env` 声明值）后一次通过 | ✅ 包已产出；⚠️ **两架构底座因此不一致**（arm64=UBI9+ES JDK，amd64=temurin jammy） |
| 4 | 兜底包 `.sha256` 格式非法 | 我生成时只写哈希、缺文件名；`shasum -c` 失败，而交付脚本 `set -e` ⇒ 目标机第一步即退出 | 改为 `哈希␣␣文件名` 并自检通过 | ✅ 已修 |

## 二、交付包缺陷（装机阶段）——3 项，均已修

| # | 现象 | 根因 | 处置 | 现状 |
|---|---|---|---|---|
| 5 | **全新空库装不起来**：migrator `exit 3`，`V2.0.0_13` 报 `relation "tbl_harness_skills" does not exist` ⇒ backend 永不启动 | `tbl_harness_skills` 是**应用自建表**（AgentScope `PostgresSkillRepository.createIfNotExist`），而 compose 让 `backend` 等 `migrator` **成功完成** ⇒ 鸡生蛋死锁 | `docker/init/migrate.sh` 在跑发布件前**幂等预建**应用自建表（`to_regclass` 守卫，保证**重跑安全**） | ✅ 已修并实测（空库 9/9 步通过） |
| 6 | migrator `exit 2`：`connection to server at "postgres" … Connection refused` | compose 健康检查经 **unix socket** 提前通过，**TCP 5432** 尚未就绪 | `migrate.sh`/`post_start.sh` 增加"等 TCP 真就绪"重试循环 | ✅ 已修 |
| 7 | backend 无限重启：`Error: Unable to access jarfile /app/phoenix-admin.jar` | `Dockerfile.backend.multistage` 里 jar 进镜像后为 **root:root 0600**，而容器以非 root(10001) 运行 | `COPY --chown=app:app` + `RUN chmod 644`（thin 路径 `Dockerfile.backend` 同样加固） | ✅ 已修 |
| — | （过程中我拼的手工兜底包还漏了 `sql/` 目录） | 我手工组装，未对齐仓库同构布局（compose 挂载 `../sql`） | 已补齐 | ✅ 已修（该兜底包已废弃，改用官方包） |

## 三、目标机环境适配（Deepin）——3 项

| # | 现象 | 根因 | 处置 | 现状 |
|---|---|---|---|---|
| 8 | `install.sh` 步骤 6 失败：`The Compose file … is invalid because: 'name' does not match any of the regexes: '^x-'` | 目标机只有**旧版 `docker-compose` v1**；而 compose 文件用了 **Compose Spec(v2)** 语法：顶层 `name:` + `depends_on: service_completed_successfully` | 装 **`docker-compose-plugin`（v2）**；`install.sh` 会自动优先用 `docker compose` | ⚠️ **待现场确认**（用户已改用普通用户跑通安装） |
| 9 | root 下找不到 compose 插件，普通用户却可以 | Docker CLI 插件查找路径**分用户**（`~/.docker/cli-plugins` 只对当前用户生效） | 若要 root 可用：把插件放到 `/usr/libexec/docker/cli-plugins/`（deb 安装即系统级） | ⚠️ 建议固定用**普通用户 + docker 组**跑 install.sh |
| 10 | `common.sh:31` 报"权限不够"（写状态文件） | **root 跑过一次**，留下 root 属主的 `.phoenix-install.state`、`docker/.env`(600)、`RECEIPT` ⇒ 普通用户追写失败 | `sudo chown -R "$(id -un):$(id -gn)" .`；**固定同一用户**执行 | ✅ 已给处置；不影响功能（该步只是没记账，重跑会重复该步） |
| 11 | 交付包**不含离线 Docker 引擎** | 打包未加 `--with-engine-debs` | 已额外下载 Deepin 离线 deb 四组合（bookworm/bullseye/buster × amd64 + bookworm-arm64，各 5 个包 + `install-docker.sh` + SHA256SUMS，共 378MB） | ⚠️ **现状缺口**：目标机需预装 Docker；如需包内自带引擎，可重打 `--with-engine-debs` |

## 四、发版后站点功能问题——2 项

| # | 现象 | 根因 | 处置 | 现状 |
|---|---|---|---|---|
| 12 | 换 1024 维 embedding 模型后，只改一张向量表会**被拒写入** | **R-09 是动态全表校验**（扫描所有含 `embedding` 列的表），在入库入口 `addDocuments` 拦截 | 提供迁移 SQL（显式版/紧凑版/加固版），**4 张表一起改**；并揭示结构差异（harness 表**无 metadata 列**、**id 是 varchar**） | ✅ SQL 已给（真库 BEGIN→ROLLBACK 演练通过） |
| 13 | `tbl_vector_store_simple_data` 入库报 `there is no unique or exclusion constraint matching the ON CONFLICT specification` ⇒ 文档 `embedding_status=FAILED` | 该表 **`id` 上没有主键/唯一约束**；Spring AI `PgVectorStore` 的 upsert 是 `INSERT … ON CONFLICT (id) DO UPDATE`，PG 要求冲突列有唯一约束 | `ALTER TABLE tbl_vector_store_simple_data ADD CONSTRAINT …_pkey PRIMARY KEY (id);`（已给 4 张表自动补齐脚本） | ⚠️ **待现场执行验证** |

## 五、我方操作事故（我方责任，已记坑）——2 项

| # | 事故 | 影响 | 教训 |
|---|---|---|---|
| 14 | 部署重建容器，**打断用户正在跑的会话**（`AgentShuttingDownException`），并丢失老容器日志 | 用户以为"审查功能失败"，实际是进程被杀 | **L-87**：部署/重启/热替换前必须确认无在跑会话；重启会丢日志，排障前先 `docker logs > 文件` |
| 15 | Docker VM 磁盘被构建缓存/悬空镜像塞满 ⇒ Redis AOF 写失败 ⇒ **连登录都 500** | 平台短时不可用 | 清理 **24.17GB 构建缓存 + 1.5GB 悬空镜像**（此前另清 77GB）恢复；建议定期 `docker image prune -f`（不要 `-af` 清缓存） |
| 16 | 我**误将用户手动切的 CHAT 默认模型当作测试残留还原** | 与用户意图相反 | **L-86**：合并/冻结/push/回退等口令制动作，以及"改回用户配置"，都必须先确认 |

## 六、当前状态与未闭环项

**已交付**：`phoenix-2.0.0-arm64.tar.gz`(1.6G) / `phoenix-2.0.0-amd64.tar.gz`(1.3G)
- 两者均从重打包产物跑通 `install.sh`：**9/9 步、退出码 0、收据"安装成功"**，装出的栈 4 容器 healthy、首页 200、登录拿 token、台账 32 件
- amd64 在本机（arm64）靠 `PHOENIX_ALLOW_ARCH_MISMATCH=1` 模拟验证；真机 x86_64 不需要

**未闭环（需要跟进）**：
1. **仓库 13 个脚本文件未提交**（`.dockerignore`、`Dockerfile.backend(.multistage)`、`init/migrate.sh`、`init/post_start.sh`、`scripts/{bootstrap,build,install,package,verify}.sh`、`scripts/lib/common.sh`、`install.sh`）⇒ **下次重打会退回旧脚本、包又会坏**
2. **两架构底座不一致**（arm64=UBI9+ES JDK，amd64=temurin jammy）⇒ 若要统一身份，需能拉到 amd64 版 ES 镜像后重打
3. **交付包不含离线引擎 deb**；`install.sh` 步骤 2（装引擎）**从未被实测**
4. **`install.sh` 对 Compose v1 无友好提示**（会退回 v1 然后吐解析错误）⇒ 建议加"强制 v2 + 明确指引"
5. 现场待办：**补 `simple_data` 主键** → 重新入库 → 确认 `embedding_status=COMPLETED`；**4 张向量表维度统一到 1024**
6. 遗留台账项：BL-34/54（模型↔维度指纹校验、升级件缺维度对齐工具）、文档 id=37 需重导入
