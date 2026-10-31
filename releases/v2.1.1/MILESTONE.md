# 里程碑 v2.1.1

> 状态：**已发布（2026-10-26）**
> 类型：**PATCH**（打包链修复，不含功能变更、不含 SQL、不含配置变更）
> **版本分支**: `2.1.1`（基点 = `main` @ `b99a3db5`，即 v2.1.0 发版后的 main）
> 交付包：`docker/dist/phoenix-2.1.1-arm64.tar.gz`（1.7G，sha256 `3a08a1f94a14eac820b324da258cfca89160229c6a4c7978d10c2e67801fa37d`）

## 一、修复项

| BUG | 描述 | 状态 |
|---|---|---|
| BUG-212 | `apps/admin-ui/package.json` 残留 `vxe-table` 依赖行，锁文件未收录 ⇒ 容器内 `pnpm install --frozen-lockfile` 失败，`package.sh` 第 3/8 步中断（BUG-149 的一个具体成因） | 已修复(v2.1.1) |

## 二、升级件

**无**（无库表、无配置变更）。升级方式：替换镜像/交付包即可。

## 三、验证

- `docker/scripts/package.sh --arch arm64 --version 2.1.1` → 8/8 步通过
- 出货门禁 4/4 PASS（`sha256sums` / `jar-readable` / `prereq-match` / `fresh-db-install`）
- 运行栈冒烟：登录 / 首页 / 品牌 / 图标 / vendor 全 200

## 四、已知限制

沿用 v2.1.0 的已知限制（9 条 P1 以「已延期」随版，随 `BL-75` 排期），本版未新增。

## 五、发版记录（2026-10-26）

| 项 | 值 |
|---|---|
| 打包 | `package.sh --arch arm64 --version 2.1.1` → **8/8 步通过** |
| 出货门禁 | **PASS 4/4**（`sha256sums` / `jar-readable` / `prereq-match` / `fresh-db-install` 真装一次，79s） |
| 包 | `docker/dist/phoenix-2.1.1-arm64.tar.gz` 1.7G ｜ sha256 `3a08a1f94a14eac820b324da258cfca89160229c6a4c7978d10c2e67801fa37d` |
| 证据 | `docker/dist/gate-2.1.1-arm64-20261026-224710/` |
| 镜像源注意 | `docker.m.daocloud.io` 拉 `postgres:16-alpine` 会卡死；本次用 `--mirror https://docker.1panel.live` 通过 |

## 六、升级演练（2026-10-26 实测 · 2.0.0 → 2.1.1）

| 项 | 结果 |
|---|---|
| 形态 | 同机并行栈（`phxupg` / 端口 9191），基线 v2.0.0 → 官方 `install.sh --offline --env-from` 一条命令升级 |
| 安装器 | **9/9 步通过**；镜像一致性核验 `backend←phoenix-backend:2.1.1`、`nginx←phoenix-frontend:2.1.1` |
| 迁移 | `V2.1.0_01~04` 自动写入 `tbl_phoenix_release`（4 条齐） |
| 验证 | **11/11**：登录/首页/品牌/图标(200)/图标缺失(404)/vendor/附件上限 50/品牌权限/迁移台账/结构(品牌表+KB绑定列+重建表)/数据保留(用户2·菜单21·模型2) |
| 回滚 | 换回 `IMAGE_TAG=v2.0.0` 重建 → 登录 200、数据完好；再切回 2.1.1 → 200（可逆） |
| 手册 | `docs/UPGRADE-2.0.0-to-2.1.1.md`（含实测命令、踩坑与结论） |

**演练中发现的唯一阻塞坑**：手工搭基线时未挂载 `releases/` ⇒ migrator 只跑基线 `all_schema.sql`，
缺 `V1.2.0_05` 的 `is_default` 列 ⇒ v2.0.0 后端 `Application run failed`；补齐 `releases/` 后恢复。
该坑已写入手册「必踩坑」段（安装器标准流程本身不受影响，因为它自带 releases）。

## 七、发版后追加（2026-10-27，未回改 tag）

| 项 | 内容 |
|---|---|
| 触发 | 用户「补充到 upgrade 中」+「太复杂看不明白」⇒ 要一条命令的升级入口 |
| 缺陷登记 | **BUG-213**（原 `upgrade.sh` 三处硬伤：SQL 硬门槛致 PATCH 版升不了 / `TAG` 死变量切版本不生效 / 不入交付包根故客户机没有该入口） |
| 改动（main） | `docker/scripts/upgrade.sh` 重写为「场景自动判别」统一入口；`package.sh` 将其拷入**包根**；`gate.sh` 门禁四项 → **五项**（新增 `upgrade-entry`）；`tests/t07` 加反例、新增 `tests/t09_upgrade_selftest.sh` |
| 证据 | `t09` 通过 22/22；`t07` 通过 12/12（真实 2.1.1 包载荷上跑通五项，含「包根缺 upgrade.sh ⇒ FAIL」反例；门禁栈零残留） |
| 文档 | `docs/UPGRADE-2.0.0-to-2.1.1.md` §5、`releases/v2.1.1/UPGRADE.md` 最简操作、包内 `docker/payload-docs/INSTALL.md` 升级节、`docker/scripts/README.md` |
| 教训 | **L-114**（仓库里有脚本 ≠ 客户机能用：必须「入包清单 + 门禁断言 + 真包载荷实测」三件齐全） |
| **交付状态** | 手头的 `phoenix-2.1.1-arm64.tar.gz`（2026-10-26 打）**包根内没有 upgrade.sh**——须重打包才随包交付；tag `v2.1.1` 未动、未 push |
| 重打包（2026-10-31） | 09:25 首打 `sha256=078c51f8…e3122`——**已作废**（不含同日 BUG-214 图标修复）；13:07 重打为 **`sha256=ebecb5f69ce0937a09af515318f063abb38bc4fe834cfbff9fc6036abc58bcc6`**（当前交付物，门禁五项 PASS，见 `releases/v2.1.2/MILESTONE.md` §五）；应用代码与 v2.1.1 一致（`git diff v2.1.1..HEAD` 对 `*.java/*.ts/*.vue/*.sql/*.yml` 为空），差异仅交付脚本与文档，故取 PATCH 号 2.1.2 |
| 该包门禁 | **五项全 PASS**（`sha256sums`/`jar-readable`/`prereq-match`/**`upgrade-entry`**/`fresh-db-install`），耗时 251s；证据 `docker/dist/gate-2.1.2-amd64-20261031-092543/`；门禁栈零残留 |
| 2.1.2 台账 | **未建版支/未建 releases/v2.1.2/、未打 tag、未 push**（等口令）|
| 镜像源现状（重要） | 10-26 记的 `docker.1panel.live` **现已超时不可达**；本次可用的是 `https://docker.1ms.run`（`docker.m.daocloud.io` 仍拉 `postgres:16-alpine` 卡死史）。以后打包先探活再带 `--mirror` |
| 沙箱注意 | `~/.phoenix/*.log` 在受限环境下写入被拒 ⇒ `phx_prefetch_images` 的重定向失败会被误报成「镜像源传输质量差」；把 `PHX_LOG_FILE` 指到工作区内即正常 |
