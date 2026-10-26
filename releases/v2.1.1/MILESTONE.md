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
