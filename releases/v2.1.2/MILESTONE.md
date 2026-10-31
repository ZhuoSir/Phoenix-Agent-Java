# 里程碑 v2.1.2

> 状态：**已发布（2026-10-31；M2 冻结 → M3 汇总 → M4 发布）**
> 冻结依据：spec 三文档 v1.0.0 已确认、tasks 14/16（T-16 延期）、本版无 P0/P1、账证初对与孤儿 spec 检查全绿
> 类型：**PATCH**（交付链修复 + 三处缺陷修复；无库表、无配置变更）
> 应用代码差异（**对 v2.1.1 的唯一差异**）：BUG-215 引入的前端 4 文件改动 + `public/imgs/logo.png`、`public/favicon.ico` 两个资源替换；
> 后端 Java 代码与 v2.1.1 一致。
> **版本分支**: `2.1.2`（基点 = `main` @ `6dab10b3`；v2.1.1 已发版并已合 main，无悬空）
> 交付包：`docker/dist/phoenix-2.1.2-amd64.tar.gz`（1.8G，sha256 `1a84f672d0e7f2a99240add4f0bf597eb43654070699875d22dd4166805dded0`）
> （含 BUG-215 前端修复的终版；此前 `ebecb5f6…` 不含该修复，已改名 `phoenix-2.1.2-amd64.superseded-ebecb5f6.tar.gz` 留档）

## 一、纳入需求

| spec 目录 | 内容 | 状态 | 备注 |
|---|---|---|---|
| `specs/20261031_delivery-upgrade-and-brand-icon/` | 一键升级入口 `upgrade.sh`（场景自判别 + 探测/拒绝 + dry-run 零副作用 + 入包根 + 门禁第五项）；品牌图标上传返回可访问 URL（含历史值兜底） | **已发布(v2.1.2)**：三文档 **v1.0.0 已确认**（陈卓 2026-10-31）；14/16 任务勾选，T-16 延期→v2.1.3，T-15 已收口 | 代码先行落 `main`（`8bce9a4d`/`e65760be`），文档补记提交 `28ea601c` 在版本分支；本 spec 为 v2.1.2 的账与验收依据 |

## 二、纳入缺陷

| BUG | 描述摘要 | 严重度 | 状态 |
|---|---|---|---|
| BUG-213 | 原仓库 `upgrade.sh` 对交付包升级不可用三硬伤：SQL 硬门槛挡死 PATCH 版 / `TAG` 死变量切版本不生效 / 不入交付包根客户机无入口 | P2 | 已发布(v2.1.2)（2026-10-31）（`8bce9a4d`；包根含 `upgrade.sh`） |
| BUG-214 | 外观设置上传 Logo/favicon 显示不出来：`storeFile()` 的**磁盘相对路径**被当 URL 返回 ⇒ 浏览器请求 `/data-agent/…` 被 SPA 兜底送回 `index.html`；连带 PUT 校验也拒（值不以 `/` 开头） | P2 | 已发布(v2.1.2)（2026-10-31）（`e65760be`；含读写两侧 `toAccessUrl()` 兜底） |
| BUG-215 | 后台「品牌设置」上传的 Logo 只改 favicon/标题/主题色，**左上角侧边栏徽标始终是内置旧图**：两条独立链路（前端构建期 `preferences.logo.source` vs 后端 `brand.logoUrl`），且该偏好默认值 `/public/imgs/logo.png` 在 nginx 下无 `/public` 映射 ⇒ 命中 SPA 兜底返回 HTML | P2 | 已发布(v2.1.2)（2026-10-31）（`47f78d5a`；包已按修复后代码重打，见 §五） |

> 说明：本版**不吸收** v2.1.0 遗留的 9 条 P1（`BL-75` 跟踪，口径 A 已随 v2.1.0 发布）。

## 三、升级件

**无**（无 SQL、无配置变更）。`phoenix.data.file.url-prefix`（默认 `/uploads`）为既有配置，本版不改语义。
升级方式：替换交付包/镜像即可；SQL 台账仍停在 `V2.1.0_01~04`（4 行）。

## 四、验证（真跑）

| 项 | 结果 |
|---|---|
| 自测 `t09_upgrade_selftest.sh` | **通过 22 / 失败 0 / 跳过 0**（bash 3.2 判别、探测、多栈拒绝、缺 `.env` 拒绝、零副作用、参数透传、PATCH 跳迁移、TAG 生效、打包与门禁接线） |
| 真包回归 `t07_gate_selftest.sh`（用 2.1.1 真实包载荷） | **通过 12 / 失败 0**；门禁五项 `通过=5 失败=0`；含反例「包根缺 `upgrade.sh` ⇒ FAIL item=upgrade-entry」；门禁栈零残留 |
| v2.1.2 包出货门禁（终版） | **结果=PASS 通过=5 失败=0 耗时=260s**，证据 `docker/dist/gate-2.1.2-amd64-20261031-134927/`（含 `upgrade-entry` 与 `fresh-db-install` 真装） |
| 包内容核验 | `shasum -a 256 -c` → OK；包根含 `upgrade.sh` 且与仓库 HEAD 同 sha 前缀；包内 `docs/INSTALL.md` 有升级节 |
| 品牌图标端到端（开发栈 9091） | 上传 → `/uploads/data-agent/brand/<uuid>.png`；**匿名** GET → `200 / image/png / 70 字节`；PUT 落库并匿名回显一致；旧格式裸路径 → 自动补前缀且日志留痕 |
| 共享面不回归 | 头像 `/api/upload/avatar` 返回可用 URL 且匿名 200 图；附件 VO 仍只给受鉴权端点；`DENIED_STATIC_PREFIXES` 未改动 |
| 表检 | `mdtable_check.py` 全部改动 md 全绿 |

## 五、打包记录（含两次失败，如实）

| 时间 | 结果 | 原因 |
|---|---|---|
| 2026-10-31 09:25 | ✅ 出包 sha `078c51f8…`（门禁五项 PASS） | —— 但**不含 BUG-214 修复**，随后作废 |
| 2026-10-31 10:58 | ❌ 步骤 4 失败 | `docker.1panel.live` 源 DNS 解析不到（10-26 时可用）→ 换 `--mirror https://docker.1ms.run` |
| 2026-10-31 12:59 | ❌ 步骤 4 中断 | Docker Desktop 进程退出（daemon 不可达）→ 重启后续跑 |
| 2026-10-31 13:07 | ✅ 步骤 8/8 完成，sha `ebecb5f6…` | 含 BUG-213 + BUG-214；**不含 BUG-215**，重打后作废（改名留存） |
| 2026-10-31 13:44 | ❌ 步骤 5 失败 | `docker save` 报 `no space left on device`（Docker Desktop 磁盘不足，Build Cache 占 16G）→ `docker builder prune -f` 回收 16G 后续跑 |
| 2026-10-31 13:53 | ✅ **步骤 8/8 完成，sha `1a84f672…`（当前交付物）** | 含 BUG-213/214/**215**；门禁五项 PASS（260s，`fresh-db-install` 真装一次） |

## 六、已知限制与待办

1. **未在真实 amd64 机器端到端复验**：构建机是 arm64，门禁 `fresh-db-install` 以 `PHX_ALLOW_ARCH_MISMATCH=1` 放行（模拟执行）。出货前须按 `UPGRADE.md` 在真实 x86 机复验（对应任务 T-16，L-12 纪律）。
2. **Windows 侧无一键升级**：`install.ps1` 只覆盖安装；升级仍需进 WSL 执行 `bash upgrade.sh`（未立项）。
3. 若现场把 `phoenix.data.file.url-prefix` 改成非 `/uploads`，需同步 nginx `location /uploads` —— 本版未加自动断言（列入 plan §5 风险）。
4. 发版动作现状（2026-10-31 用户口令「2.1.2 走发版流程，然后合并Main」）：M3 汇总四件套已落盘 → 四步核验 → tag `v2.1.2` → 合 main；**push 仍需单独口令**。
5. 待拍板：是否补打 **arm64** 包；作废包已按 Q2 建议改名移开（`…superseded-ebecb5f6.tar.gz`）。

## 七、审计记录（M3 步骤 0 / M4 步骤 0·4b）

| 项 | 判据 | 结果 |
|---|---|---|
| spec 挂接对账 | `git log v2.1.1..2.1.2 --grep "Spec:"` = {`28ea601c`,`47f78d5a`,`a094e68c`}；`grep 挂载: specs/*/changelog.md` 命中本 spec | 账平：1 spec，无漏挂无空行 |
| 进度重算 | tasks 勾选 15/16（T-16 延期已注明去向） | 无假勾 |
| 账证初对 | 本版 `Bug:` footer = BUG-213/214/215，均可在 bugs.md 定位 | 全绿 |
| CR 对账 | 本 spec 无 `changes/` 目录 | 0 未收口 |
| 升级件对账 | 无 `sql/`、无配置变更；`config/changes.md` 记「无变更」 | 与 artifacts 事实一致 |
| 四步核验 | ①逐条 grep 回验+表检 ②审计复跑 ③工作树干净 ④三条齐绿 | 见 checklist §四 |

## 八、发版后追加（2026-10-31，未回改 tag `v2.1.2`）

| 项 | 内容 |
|---|---|
| 作废包物理隔离 | 用户口令「作废包移走」→ `phoenix-2.1.2-amd64.superseded-ebecb5f6.tar.gz` 已从**出货目录** `docker/dist/` 移入 `docker/dist/_superseded/`，同目录落 `README.txt`（记 sha、作废原因、现行交付物路径）。此后 `docker/dist/` 内的 2.1.2 包**只剩一个**，不会误发 |
| 现行交付物复核 | `docker/dist/phoenix-2.1.2-amd64.tar.gz` 1.8G ｜ sha256 `1a84f672…05dded0` ｜ `shasum -a 256 -c` → **OK** ｜ manifest `git=v2.1.1-13-ga094e68c`、`arch=amd64`、`built_at=13:47` ｜ 包根含 `upgrade.sh`（1 条） |
| 门禁复核 | 证据 `docker/dist/gate-2.1.2-amd64-20261031-134927/`：五项 **全 PASS**（`sha256sums`/`jar-readable`/`prereq-match`/`upgrade-entry`/`fresh-db-install`） |
| 远端复验（受阻，如实） | 复核时刻 `github.com:443` **不可达**（`curl` → 000 超时；`git ls-remote` → Failed to connect），无法向远端取证。本地远端跟踪引用显示推送已在 13:54 成功：`origin/main=ac3fe90e`、`origin/2.1.2=43fabb7f`、tag `v2.1.2`→`43fabb7f`。**网络恢复后请复跑** `git -c http.version=HTTP/1.1 ls-remote origin refs/heads/main refs/heads/2.1.2 refs/tags/v2.1.2` 三条对齐即销 |
| arm64 包补打（2026-10-31 19:49，用户改口「3.补充」） | **`docker/dist/phoenix-2.1.2-arm64.tar.gz`** 1.7G ｜ sha256 `167a2c5c4ee1df9f485bc12bcee349577ab182303cdd640532031f68bae38220` ｜ `shasum -c` → **OK** ｜ manifest `arch=arm64`、`git=v2.1.2-2-g154cfd39`（**与 tag 代码一致**：`git diff v2.1.2..HEAD` 对 `*.java/*.ts/*.vue/*.sql/*.yml/docker/**` 为空，只多两条文档提交） ｜ 包根含 `upgrade.sh`+`install.sh` |
| arm64 包门禁 | 五项**全 PASS**，耗时 **57s**，证据 `docker/dist/gate-2.1.2-arm64-20261031-194803/`；**本次未用 `--allow-arch-mismatch`**（构建机即 arm64，`prereq-match` 与 `fresh-db-install` 是真实执行，不是模拟） |
| 出货目录现状 | `docker/dist/` 内 v2.1.2 两份、按机器二选一：x86_64 → `…-amd64.tar.gz`（`1a84f672…`）；ARM（鲲鹏/飞腾/Apple Silicon 虚机）→ `…-arm64.tar.gz`（`167a2c5c…`）。作废包仍在 `docker/dist/_superseded/` |
| ⚠️ 同名镜像标签被覆盖 | 为出 arm64 包，本地 `phoenix-backend:2.1.2` / `phoenix-frontend:2.1.2` 已被重打为 **arm64**。两份包各自内嵌本架构镜像、互不影响；但**若还要再出 amd64 包，需重新组装 amd64 镜像**（`--arch amd64` 重跑即可，约 20 分钟） |
| 仍未了结 | 真实 amd64 机器端到端复验（spec 任务 T-16）；本地台账提交 `154cfd39` 及本次追加提交受 GitHub 连通性影响待推 |
