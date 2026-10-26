> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-11 | 确认人: 陈卓 | 确认日期: 2026-10-11

# 技术方案：交付链加固（release-delivery-hardening）

## 坑核对（必填，确认②审这一节）

**核对口径（如实说明）**：对 `specs/_project/lessons.md` **全部 101 条 active 坑的标题 + 关键词**做了全量扫描，
筛出 14 条与本方案（打包链/安装器/交付件/运维脚本）相交；**这 14 条已逐条读取正文**并给出规避（下表），
其余 87 条按标题/触发场景判定无相交。相交的 14 条：L-04/12/22/31/41/71/73/89/96/97/98/99/100/101。

| 坑 | 与本方案的相交点 | 本方案如何避开 |
|---|---|---|
| **L-04** Docker VM 盘满级联 + `prune` 误删在跑容器 | R-05 门禁要反复"清空卷→全新空库安装" | 门禁一律用**独立项目名**（`phxgate-<ts>`）与独立端口，只 `down -v` 本项目；**禁止 `docker system prune -a`** 类全局清理 |
| **L-12** 交付目标机架构/引擎/网络三查缺失 | R-01/R-02/R-03 正是这三查 | 三查在步骤 1 一次性做齐：架构匹配、引擎能力、Compose 能力；任一不满足给明确处置 |
| **L-22** 交付物构建严禁并发（同 stage 文件/同 tag） | 打包链要新增底座选择与门禁 | 打包与门禁**全程串行**；门禁使用与出货包**不同的镜像 tag**（`…:2.1.0-gate`），避免与出货 tag 抢占 |
| **L-31** 数据驱动"抄一行"必须 dump 整行逐列核对 | manifest / `SHA256SUMS` / 差异记录表都是"逐列"数据 | 差异记录与校验清单生成后**逐行 dump 复核**（列数、行尾 `\|`），并用 `mdtable_check`/`shasum -c` 双验 |
| **L-41** 发布/推送类操作需显式 refspec；网络抖动走后台重试 | 重打后可能涉及 push（本版 R-06 已删，仅提示） | 本版**不做发布通道**；若后续动作涉及 push，按显式 refspec + 后台作业重试执行（另立 spec/BL-60） |
| **L-71** "国内源"必须核到每条外部 URL | R-02 离线件、镜像站说明文档 | 文档与脚本中每条外部 URL 逐条列出并标注可用性；离线件**必须随包**，不依赖运行时下载 |
| **L-73** `docker pull` 报 DONE ≠ blob 完整，先做字节对账 | R-01 替代底座流程 | 底座镜像落地后做**摘要/字节对账**（`docker image inspect` 的 Id/Size + 拉取日志 grep），不看"DONE"字样 |
| **L-89** 本机直连 github 不通 ⇒ push 必须走代理 | 交付/重打流程（本版不含 push） | 本版无 push 动作；文档注明"如需 push 需代理"（避免误判为权限问题） |
| **L-96** 「继续/做到结束」不是口令 | 本方案会触碰冻结/tag/合并（发版流程） | Implement 与后续发版中，**合并/push/tag/回退四类动作只问不做** |
| **L-97** 部署/重启打断在线会话 | 门禁的"全新空库安装"会起容器 | 门禁用独立项目名+独立端口，**不重启、不删除现网栈**；如需重启先确认无在跑会话 |
| **L-98** 构建上下文须排除产物目录，且排除规则要与 Dockerfile `COPY` 交叉核对 | R-05 门禁与打包链改造要动 `.dockerignore` | 修改 `.dockerignore` 前**强制 grep 所有 Dockerfile 的 COPY/ADD 目标**，并保留产物排除（`docker/dist`、`*.tar.gz`） |
| **L-99** 交付包验收必须含"全新空库安装"；顺序耦合要在迁移前幂等解开 | R-05 门禁① 就是这条的固化 | 门禁①=清空卷后全新库 `install.sh` 到收据；迁移预建块保持 `to_regclass` 幂等守卫 |
| **L-100** Docker CLI 插件按用户查找；安装脚本严禁 root/普通用户混跑 | R-03 检测与指引 | 检测同时覆盖 `docker compose version` 与插件**系统级/用户级路径**；指引明确"统一普通用户 + docker 组"，并给出系统级落地路径 |
| **L-101** 非 root 运行的应用，COPY 产物必须 chown/chmod 644 | R-05 门禁② 的检测原理 | 门禁②以"容器内以运行用户身份读 jar"为判据（`id` + `ls -l` 对照），而非仅看宿主文件权限 |

## 方案概述

五个工作流（W1~W5），全部落在**脚本 / 构建链 / 交付件**层，不改应用代码与库表结构：

| 流 | 需求 | 落点（拟） |
|---|---|---|
| **W1 底座与差异记录** | R-01 | `docker/scripts/package.sh`（底座选择 + 失败/替代分支）、`manifest.json` 生成器（新增 `base_images` 逐架构字段 + `equivalence_note`）、交付说明模板 |
| **W2 离线引擎件** | R-02 | `docker/scripts/bootstrap.sh` + `install.sh` 步骤 2：包内新增 `engine/docker-<ver>.tgz`（按架构），落地 `dockerd` + systemd unit（无 systemd 时降级为 `nohup` 并显式告知）；打包时预下载入包 |
| **W3 前置能力闸** | R-03 | `install.sh` 步骤 1 的"三查"扩展：①`docker info` 的 **`OSType` 必须为 `linux`**（显式拒绝 Windows 容器引擎，L-12 原场景）②`docker context ls` 记录当前上下文 ③`docker compose version` + 插件路径（系统级/用户级）探测；任一不满足即 `phx_fail`（早于步骤 6），并**删除现有的 v1 回退分支** |
| **W4 维度迁移独立脚本** | R-04 | 新脚本 `docker/scripts/vector-dim-migrate.sh`（随包分发）：入参 `--dims N [--dry-run]`；生成并执行安全 DDL（删索引时排除 `pg_constraint.conindid`/`*_pkey`）；迁移后自检维度与 id 唯一约束，输出修复语句 |
| **W5 出货门禁** | R-05 | 新脚本 `docker/scripts/gate.sh`（四项自检）+ `package.sh` 出货步骤强制调用；**门禁开始前先跑环境体检三件套**（`docker system df` + `docker ps` + 目标库连通，L-04），失败输出附体检结果以免把环境问题误判为门禁失败；证据落 `docker/dist/gate-<ver>-<ts>/` |

复用既有资产：R-04 的安全迁移逻辑以本次已演练过的 `alter_vector_dim_v2_safe.sql` 为基线（真库 `BEGIN→ROLLBACK` 演练通过）；
R-05 门禁①复用本次"清空卷 + 全新库 install.sh"验证路径（两包各跑通 9/9 步）。

## 涉及模块与数据流

```
开发者 ──► bootstrap.sh ──► package.sh ──┬─► 构建镜像（底座按架构选择）
                                         ├─► 组装 payload（docker/ sql/ releases/ images/ engine/）
                                         ├─► 生成 manifest.json + SHA256SUMS
                                         └─► gate.sh（四项自检；任一失败即拒发）
运维 ──► 交付包 ──► install.sh 步骤1(三查+Compose v2 闸) → 2(引擎: 已有则跳过/无则静态二进制落地)
                 → 3(SHA256 全量校验) → 4(镜像 load) → 5(.env) → 6(compose up)
                 → 7(等 migrator/backend) → 8(verify 全断言) → 9(收据)
迁移 ──► vector-dim-migrate.sh --dims 1024 ──► 目标库(ALTER) ──► 自检报告(维度/约束)
```
**改动面**：`docker/scripts/{package,bootstrap,install,verify}.sh`、`docker/scripts/lib/common.sh`、
新增 `docker/scripts/{gate,vector-dim-migrate}.sh`、`.dockerignore`、`manifest` 生成逻辑、交付文档模板。
**不动**：应用 Java 代码、`sql/` 与 `releases/**` 迁移件内容、`docker-compose.yaml` 的服务定义。

## 接口设计

非 HTTP；定义脚本 CLI 契约（供 Tasks 断言）：

| 脚本 | 接口 | 约定 |
|---|---|---|
| `package.sh` | `--version X.Y.Z --arch {arm64,amd64} [--mirror M] [--base-image IMG] [--skip-gate]` | 底座不可得且未显式给 `--base-image` → 记入差异并继续；`--skip-gate` 仅调试用，出货禁用（CI/出货命令不带） |
| `install.sh` | 既有参数不变 | 新增行为：步骤 1 检测 Compose v2 失败 → 退出码 1 且**不创建任何容器**；步骤 2 离线件缺失/架构不匹配 → 退出码 2 |
| `gate.sh` | `--payload DIR --arch A [--out DIR]` | 四项逐项 PASS/FAIL；全部 PASS 退出码 0，任一 FAIL 非 0 且打印失败项与复现命令 |
| `vector-dim-migrate.sh` | `--dims N [--dry-run] [--pg-container NAME] [--table T]` | 迁移前打印快照；迁移中逐表操作日志；迁移后自检并给修复语句；`--dry-run` 走事务回滚 |

## 数据模型变更

**无表结构变更**。R-04 仅在运维主动执行时对 `embedding` 列做 `ALTER TYPE`，并对既有约束做**只读自检**；
自检依赖元数据视图（`pg_attribute`/`pg_constraint`/`pg_index`/`pg_indexes`），不新增表、不改列。

## 共享面身份矩阵（触碰共享面时必填，判据见 SKILL.md §3）

自检问句「还有谁依赖这个路径/列/配置？」——以下是**确认的共享面**及其既有身份：

| 共享对象 | 既有身份 | 变更后预期行为 |
|---|---|---|
| `install.sh` | ① 全新安装 ② 重跑（幂等/断点续传） ③ `--env-from` 升级 ④ `--offline` ⑤ `--from-wsl` ⑥ 跨架构模拟（`PHOENIX_ALLOW_ARCH_MISMATCH=1`） | ①~⑥ 全部保持可用；新增"Compose v2 不满足即早退"仅在第 ① ② ⑥ 身份**能力不足时**触发，能力足够时行为不变；退出码语义不变（0 成功/非 0 失败） |
| `docker-compose.yaml` | ① install.sh 调用 ② `phoenix-ctl.sh` 运维 ③ 本机开发栈 | **服务定义不改**；W1/W5 不改 compose 内容 ⇒ 三身份行为完全不变 |
| `package.sh` | ① arm64 出货 ② amd64 出货 ③ `--mirror` ④ `--version` ⑤ `--out` ⑥ 断点续跑（state 文件） | ①~⑥ 保持；新增底座选择与门禁调用不改变既有参数语义；`--skip-gate` 为新增可选，默认关闭 |
| 向量表 `embedding` 列（4 张表） | ① 平台写入路径（R-09 全表校验） ② 平台检索路径 ③ 运维迁移脚本 | ① ② 不受影响（本版不改维度，仅提供脚本）；③ 新增后主键/唯一约束**零丢失**（迁移前后约束数对账） |
| `lib/common.sh`（日志/步骤机） | ① install.sh ② bootstrap.sh ③ package.sh ④ verify.sh | 新增函数为**纯增量**；既有函数签名与输出格式不变（既有调用方零改动） |

## 关键日志点清单（涉及后台服务/异步/定时任务/外部调用时必填）

本版产物是**脚本流水线**（含外部调用：`docker pull/load`、包下载），按 logging 规范的自检精神列出脚本级日志点：

| 场景 | 级别 | 触发点 | 必带字段 | traceId/runId |
|---|---|---|---|---|
| 出货门禁开始/结束 | INFO | `gate.sh` 进入与退出 | 版本、架构、四项结果、耗时、退出码 | `runId=<ver>-<arch>-<ts>` |
| 门禁单项失败 | ERROR | 任一项 FAIL | 项名、实测值、期望值、复现命令 | 同 runId |
| 引擎落地（离线） | INFO | `install.sh` 步骤 2 | 来源（已有/离线件）、架构、二进制版本、是否配自启 | `runId=install-<ts>` |
| 引擎落地失败 | ERROR | 同上 | 缺失项（文件/架构/权限）、手工安装指引 | 同 runId |
| Compose 能力闸拒绝 | ERROR | `install.sh` 步骤 1 | 检测到的能力、所需能力、指引、**已创建容器数（应为 0）** | 同 runId |
| 迁移脚本逐表操作 | INFO | `vector-dim-migrate.sh` | 表名、原维度、目标维度、索引处理动作、行数 | `runId=migrate-<ts>` |
| 迁移自检结果 | INFO/ERROR | 迁移结束 | 各表维度、约束是否齐、修复语句（不通过时） | 同 runId |

## 关键决策

### 决策 1：底座不可得时"允许替代 + 强制记录差异"
- **采用**：替代底座出货，但 manifest/交付说明逐架构记录（架构↔底座↔`JAVA_BIN`↔影响），且两架构各自端到端验证
- **拒绝 A**：直接失败不出货（`--fail-on-missing-base`）——本次真实场景（amd64 版 ES 镜像拉不动）已证明会导致**发不出包**
- **拒绝 B**：静默替代——会把"两架构不一致"藏起来，正是 BUG-191 同类隐患的温床

### 决策 2：离线引擎件用**通用静态二进制**
- **采用**：`docker-<ver>.tgz`（x86_64/aarch64），落 `/usr/local/bin` + systemd unit（无 systemd 降级 `nohup` 并显式告知）
- **拒绝 A**：发行版专属 `deb/rpm`——目标机可能非 Debian 系，且 Deepin 代目判定本身易错（本次现场踩过）
- **拒绝 B**：deb + 静态件双份——包体积翻倍（当前包已 1.3~1.6GB）

### 决策 3：前置能力检测（OSType/context/Compose v2）全部放在**步骤 1**（最早），而非步骤 6 前
- **采用**：与"三查"合并，能力不足最早期失败，零副作用；`OSType != linux` 直接拒绝（Windows 容器引擎场景，L-12）
- **拒绝**：放在步骤 6——会先做完 load/写 `.env` 等动作才失败，浪费 10+ 分钟并留下中间态

### 决策 4：维度迁移做成**随包独立脚本**
- **采用**：`vector-dim-migrate.sh`，`--dry-run` 走事务回滚
- **拒绝 A**：平台内运维入口（页面/接口）——本版不改应用代码
- **拒绝 B**：只发 SQL 片段让运维手改——本次现场 BUG-195 正是手工执行的后果

### 决策 5：门禁内嵌 `package.sh` 出货步骤
- **采用**：出货路径强制调用 `gate.sh`，证据落盘
- **拒绝 A**：独立 CI 流水线——本项目无 CI，且交付常在无外网机器上进行
- **拒绝 B**：人工 checklist——本次四项缺陷全都"人工以为没问题"

### 决策 6：门禁的"全新空库安装"用独立项目名与端口
- **采用**：`phxgate-<ts>` + 独立端口；结束 `down -v` 只清本项目
- **拒绝**：复用 `phoenix` 项目名——会清掉现网数据卷（L-04/L-97 双重风险）

## 风险与规避

| 风险 | 影响 | 规避 |
|---|---|---|
| 门禁使打包耗时显著增加（空库安装 3~5 分钟/架构） | 打包变慢 | 门禁四项并行化不可行（L-22），改为**两架构串行但复用同一镜像层**；提供 `--skip-gate` 仅供开发调试，出货命令强制带门禁 |
| 静态二进制在非 systemd 环境无法自启 | 目标机重启后引擎不自启 | 显式降级路径 + 收据/日志里写明"需手工启动"，并保留 `--from-wsl` 类场景说明 |
| 替代底座导致 `JAVA_BIN` 语义差异（如 ES 自带 JDK 路径） | 容器启动失败 | manifest 记录 + **两架构各自 install.sh 验证**为出货前置（R-01 场景 3） |
| 迁移脚本在超大表上 `TRUNCATE`+`ALTER` 锁表 | 现场停机时间超预期 | 脚本先报行数与预估、要求显式 `--yes`（非交互场景例外）；`--dry-run` 可预演 |
| 门禁证据与出货包不同源（跑完门禁又改了文件） | 证据失真 | 门禁后**复核包哈希**并与出货记录绑定（哈希不一致即拒发） |
| **离线引擎件来源在境外**（`download.docker.com` 静态二进制；L-71） | 打包机无代理时取不到离线件 ⇒ W2 落空 | 打包时**逐条列出外部 URL 并核对可达性**；支持 `--engine-tgz <本地路径/内网地址>` 外部供件；入包后按 **sha256 + 字节数对账**（L-73） |

## 依赖与前置

1. 打包机：Docker Engine + **Compose v2**（本次已具备）+ 可访问镜像站/Docker Hub（或预置底座镜像）
2. 目标机：Linux（arm64/amd64 任一）；离线场景需包内 `engine/*.tgz`
3. 既有资产：`alter_vector_dim_v2_safe.sql`（真库演练过）、本次两个包的 `install.sh` 9/9 步验证路径
4. 版本账：v2.1.0 为唯一在途版；v2.0.1 为过渡版（不 tag/不发版）
5. 前置需求：requirements **v1.0.0 已确认（陈卓 2026-10-11）**
