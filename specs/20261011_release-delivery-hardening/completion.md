> 版本: v1.0.0 | 状态: 完成 | 更新: 2026-10-11 | 确认人: 陈卓（三重确认）

# 完成清单：交付链加固（release-delivery-hardening）

**挂载版本**：v2.1.0（`releases/v2.1.0/MILESTONE.md`）｜**施工分支**：`feature/release-delivery-hardening`（基点 `2.1.0`）
**任务完成度**：**10/10**（T-01 ~ T-10 全勾选）｜**变更性质**：仅脚本 / 构建链 / 交付文档（未改应用代码与库表结构）

## 一、已完成项（T-xx + 验证证据 + commit）

| 任务 | commit | 验证方式与结果（真实输出） |
|---|---|---|
| T-01 common.sh 通用基座 | `de3734a` | `docker/scripts/tests/t01_common_selftest.sh` **18/18 通过**（含字节不一致/文件缺失/OSType=windows/仅 Compose v1 四个反例）；+137/−0 行 ⇒ 4 个调用方结构性零回归 |
| T-02 前置能力闸（OSType/context/Compose v2） | `48261cf` | `t02_compose_gate_selftest.sh` **14/14**：仅 v1 时退出码 1、日志含检测结论与安装指引、未进入步骤 3、**真 docker 核对零残留容器**；严格默认拒绝架构不符；跨架构开关放行；重跑幂等 |
| T-03 离线引擎件入包 | `60ad24c` | **10/10**：真实 aarch64 静态件（77,323,501B）URL 入包且字节与 Content-Length 一致；**把真实件截到 1,000,000B 后入包被拒**（tar 完整性校验，fail-closed）；错期望字节被拒 |
| T-04 步骤 2 离线落地 | `5737118` | `t04_engine_static_selftest.sh` **9/9**：已有引擎 ⇒ 跳过；容器内（无 docker CLI）**8 个二进制落地 0755**、无 systemd ⇒ 显式降级告知；异构架构件 ⇒ 明确停止 |
| T-05 底座差异记录 | `54cda59` | **9/9**：未替代/替代两态 manifest 字段与六要素 note；`--base-image` 生效（**并修掉其原先无效的实现缺陷**）；既有参数零回归 |
| T-06 维度迁移独立脚本 | `0755b6c` | **13/13**：dry-run 事务回滚（维度仍 768）+ 事务内自检 1024；真跑两表 →1024 且**主键保留**、hnsw 重建；缺 PK ⇒ 打印 `ADD CONSTRAINT`；重复 id ⇒ 预检报组数 + 去重语句 |
| T-07 出货门禁四项 | `647567c` | `t07_gate_selftest.sh` **10/10**：反例 jar0600（uid=10001 实读不可读）⇒ FAIL；反例破坏 SHA ⇒ FAIL；**正例四项全 PASS（通过=4 失败=0）+ 全新空库安装产出收据 + 零残留容器** |
| T-08 出货强制门禁 + 哈希复核 | `bb3471c` | **10/10**：门禁失败即拒出货；**门禁期间产物被改动 ⇒ 哈希复核拒绝出货**；`--skip-gate` 打 WARN 且不调用门禁 |
| T-09 交付文档 | `d5c8829` | 三份文档（DELIVERY / OFFLINE-ENGINE / VECTOR-DIM-MIGRATE）；**每条外部 URL 逐条实测**（download.docker.com=000 超时已标注）；照做走查 rc=0 |
| T-10 全链回归 | 本次提交 | **重打 `phoenix-2.1.0-arm64.tar.gz`（1.7G，sha256 `7fbd3137…`）**：打包内**门禁四项全 PASS（52s）+ 包哈希复核一致**；载荷含 `engine/docker-29.7.2-arm64.tgz`(77,323,501B) 与 `base_images/equivalence_note`；**从交付件执行 `install.sh` → 收据「安装成功」（2.1.0/arm64）+ 4 容器 healthy** |

## 二、未完成 / 未验证项（如实标注，含原因与去向）

| 项 | 原因分类 | 处置去向 |
|---|---|---|
| 真实裸机上 `dockerd` 启动与 systemd 开机自启 | 环境限制（本机只能在特权容器内测，容器内 dockerd 无法起） | **目标机验证清单**（写入 `docs/OFFLINE-ENGINE.md` 已述；验收时在目标机跑一次无网安装） |
| amd64 包重打 + 真机安装 | 环境限制（本机为 arm64，amd64 需模拟；底座差异需 BL-55 决策） | 延后 → 随 v2.1.0 M3/M4 或目标机执行；BL-55 |
| `install.sh --env-from` / `--from-wsl` / `--offline` 完整身份回归 | 时间/环境（本轮回合聚焦交付链新增能力） | 延后 → v2.1.0 冻结前的端到端演练（BL-59 出货门禁可覆盖） |
| 平台"写入路径/检索路径"身份端到端（T-06 ⑤） | 需应用+库联动，本机现网库为 768 默认模型 | 现场执行（换模型后入库一篇文档验证命中） |
| BUG-196（`--with-engine-debs` 的 deb 组未入 SHA256SUMS） | 既有缺陷，本次**不顺手修**（登记为新建） | 转待办：随下次打包修复（静态二进制路径已规避） |
| `get.docker.com` 在线装引擎路径 | 未在本次核验 | 目标机验收时确认；失败信息已含处置 |

## 三、Bug 核对

- 本过程新登记：**BUG-196**（deb 组未入校验，新建）、**BUG-197**（交付包含安装残留，**已修复(v2.1.0)**：门禁增加残留巡检+移除，实测 package.sh 产物无残留）
- 关联既有：**BUG-193**（Compose v1 无友好提示）→ 由 R-03/T-02 收口；**BUG-195**（缺 id 唯一约束）→ 由 R-04/T-06 自检收口
- 本次实现中验证不过的问题（如 `--base-image` 失效、门禁输出抑制、项目名竞态）均属任务未完成，已当场修复并复验，不计入 BUG

## 四、升级件（artifacts）

**不创建** `artifacts.md`：本 spec 无库表结构变更、无配置项新增（R-04 只提供**运维脚本**，其 DDL 由运维主动执行；`docker/scripts/vector-dim-migrate.sh` 随包分发，不需要 Flyway 编号）。

## 五、挂接一致性核对

- `releases/v2.1.0/MILESTONE.md` 需求挂接表：`specs/20261011_release-delivery-hardening` 行**存在**，状态需同步为「**Implement 完成（10/10）**，待 M2 冻结」
- 缺陷表：BUG-189~195 已在表中；**新增 BUG-196/197 同步入表**
- backlog：BL-55~59 状态为「已立项(specs/20261011_release-delivery-hardening)」——**尚未发版，不得翻「已交付」**（按 §12：已合并未发版 ⇒ 停「已立项（随 v2.1.0 待发布）」）；BL-60 仍在待办（发布通道不在本版范围）
- 分支现状（M2 前保持）：HEAD 在 `feature/release-delivery-hardening`；**未 push、未合并、未 tag**（等用户口令）
