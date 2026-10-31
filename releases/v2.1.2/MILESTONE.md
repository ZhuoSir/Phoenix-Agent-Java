# 里程碑 v2.1.2

> 状态：**进行中（立项 2026-10-31；未冻结、未 tag、未 push）**
> 类型：**PATCH**（交付链修复 + 三处缺陷修复；无库表、无配置变更）
> 应用代码差异（**对 v2.1.1 的唯一差异**）：BUG-215 引入的前端 4 文件改动 + `public/imgs/logo.png`、`public/favicon.ico` 两个资源替换；
> 后端 Java 代码与 v2.1.1 一致。
> **版本分支**: `2.1.2`（基点 = `main` @ `6dab10b3`；v2.1.1 已发版并已合 main，无悬空）
> 交付包：`docker/dist/phoenix-2.1.2-amd64.tar.gz`（1.8G，sha256 `ebecb5f69ce0937a09af515318f063abb38bc4fe834cfbff9fc6036abc58bcc6`）

## 一、纳入需求

| spec 目录 | 内容 | 状态 | 备注 |
|---|---|---|---|
| `specs/20261031_delivery-upgrade-and-brand-icon/` | 一键升级入口 `upgrade.sh`（场景自判别 + 探测/拒绝 + dry-run 零副作用 + 入包根 + 门禁第五项）；品牌图标上传返回可访问 URL（含历史值兜底） | **已合并**：三文档 **v1.0.0 已确认**（陈卓 2026-10-31）；14/16 任务勾选，T-16 延期（真实 amd64 复验），T-15 本轮收口 | 代码先行落 `main`（`8bce9a4d`/`e65760be`），文档补记提交 `28ea601c` 在版本分支；本 spec 为 v2.1.2 的账与验收依据 |

## 二、纳入缺陷

| BUG | 描述摘要 | 严重度 | 状态 |
|---|---|---|---|
| BUG-213 | 原仓库 `upgrade.sh` 对交付包升级不可用三硬伤：SQL 硬门槛挡死 PATCH 版 / `TAG` 死变量切版本不生效 / 不入交付包根客户机无入口 | P2 | 已修复(v2.1.2)（`8bce9a4d`；需随包交付，本版包已含） |
| BUG-214 | 外观设置上传 Logo/favicon 显示不出来：`storeFile()` 的**磁盘相对路径**被当 URL 返回 ⇒ 浏览器请求 `/data-agent/…` 被 SPA 兜底送回 `index.html`；连带 PUT 校验也拒（值不以 `/` 开头） | P2 | 已修复(v2.1.2)（`e65760be`；含读写两侧 `toAccessUrl()` 兜底） |
| BUG-215 | 后台「品牌设置」上传的 Logo 只改 favicon/标题/主题色，**左上角侧边栏徽标始终是内置旧图**：两条独立链路（前端构建期 `preferences.logo.source` vs 后端 `brand.logoUrl`），且该偏好默认值 `/public/imgs/logo.png` 在 nginx 下无 `/public` 映射 ⇒ 命中 SPA 兜底返回 HTML | P2 | 已修复(v2.1.2)（本轮前端提交；包已按修复后代码重打，记录见 §五） |

> 说明：本版**不吸收** v2.1.0 遗留的 9 条 P1（`BL-75` 跟踪，口径 A 已随 v2.1.0 发布）。

## 三、升级件

**无**（无 SQL、无配置变更）。`phoenix.data.file.url-prefix`（默认 `/uploads`）为既有配置，本版不改语义。
升级方式：替换交付包/镜像即可；SQL 台账仍停在 `V2.1.0_01~04`（4 行）。

## 四、验证（真跑）

| 项 | 结果 |
|---|---|
| 自测 `t09_upgrade_selftest.sh` | **通过 22 / 失败 0 / 跳过 0**（bash 3.2 判别、探测、多栈拒绝、缺 `.env` 拒绝、零副作用、参数透传、PATCH 跳迁移、TAG 生效、打包与门禁接线） |
| 真包回归 `t07_gate_selftest.sh`（用 2.1.1 真实包载荷） | **通过 12 / 失败 0**；门禁五项 `通过=5 失败=0`；含反例「包根缺 `upgrade.sh` ⇒ FAIL item=upgrade-entry」；门禁栈零残留 |
| v2.1.2 包出货门禁 | **结果=PASS 通过=5 失败=0 耗时=247s**，证据 `docker/dist/gate-2.1.2-amd64-20261031-130348/` |
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
| 2026-10-31 13:07 | ✅ **步骤 8/8 完成，sha `ebecb5f6…`（当前交付物）** | 含 BUG-213 + BUG-214；`t09`/`t07` 全绿后重打 |

## 六、已知限制与待办

1. **未在真实 amd64 机器端到端复验**：构建机是 arm64，门禁 `fresh-db-install` 以 `PHX_ALLOW_ARCH_MISMATCH=1` 放行（模拟执行）。出货前须按 `UPGRADE.md` 在真实 x86 机复验（对应任务 T-16，L-12 纪律）。
2. **Windows 侧无一键升级**：`install.ps1` 只覆盖安装；升级仍需进 WSL 执行 `bash upgrade.sh`（未立项）。
3. 若现场把 `phoenix.data.file.url-prefix` 改成非 `/uploads`，需同步 nginx `location /uploads` —— 本版未加自动断言（列入 plan §5 风险）。
4. 待发版动作（均需口令）：M3 汇总 `RELEASE-NOTES.md`/`UPGRADE.md` → 四步核验 → tag `v2.1.2` → 合 main → push。
5. 待拍板：是否补打 **arm64** 包；作废包 `078c51f8…` 是否从 `docker/dist` 移走防误发。
