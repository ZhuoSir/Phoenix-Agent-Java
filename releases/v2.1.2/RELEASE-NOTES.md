# v2.1.2 发布说明

> 状态：**已发布（2026-10-31）** ｜ 类型：PATCH ｜ 部署基线：tag `v2.1.2`

## 一、新增能力

- **交付包一键升级入口 `upgrade.sh`**（R-01~R-05）
  解包后在包根执行 `bash upgrade.sh` 即可完成升级，自动探测旧安装的 compose 项目名与旧 `docker/.env`：
  - 自动判别「交付包场景 / 仓库开发场景」，包根场景转调 `install.sh --offline`，不造第二套安装流程；
  - 多套栈并存或缺旧 `.env` 时**拒绝猜测并给出下一步**（不新建空栈、不随机生成库密码）；
  - `--dry-run` 零副作用（不产状态文件、不改 `.env`、不动容器与镜像）；
  - 随包交付（包根与 `install.sh` 同级），并由出货门禁第五项 `upgrade-entry` 把关：缺脚本 / 组装不出命令 / dry-run 有副作用 ⇒ 整包不可出货。

## 二、修复

- **BUG-213**：仓库 `upgrade.sh` 对交付包升级不可用（SQL 硬门槛挡死 PATCH 版 / `TAG` 是赋值后未使用的死变量 / 脚本不入包根）。
  改为场景自判别统一入口；去掉 SQL 硬门槛（无 SQL 的 PATCH 版不再被拒）；`TAG` 真正生效；`package.sh` 拷入包根 + 门禁新增第五项。
- **BUG-214**：外观设置上传 Logo/favicon 后显示不出来。根因是上传接口把 `storeFile()` 返回的**磁盘相对路径**当 URL 回给前端
  （浏览器请求 `/data-agent/…` 被 nginx 的 SPA 兜底送回 `index.html`）。改为返回 `getFileUrl()`（本地为 `/uploads/…`，OSS 为其对象地址），
  并对历史裸路径加 `toAccessUrl()` 兜底（自动补前缀 + INFO 日志）。
- **BUG-215**：后台「品牌设置」换的 Logo 只改 favicon / 标题 / 主题色，**左上角系统名左侧的侧边栏徽标始终是内置旧图**。
  两条独立链路（前端构建期 `preferences.logo.source` vs 后端 `brand.logoUrl`）已打通：`applyBrand` 同步 `updatePreferences({ logo })`；
  同时把默认路径 `/public/imgs/logo.png`（nginx 下无 `/public` 映射，必然 404）改为 `/imgs/logo.png`，并换上新的 GJB 徽标资源。
  **升级后**：在「系统管理 → 品牌设置」上传 Logo 并保存，强刷即生效于侧边栏、登录页品牌图与浏览器标签 favicon。

## 三、升级说明

- **无 SQL、无配置变更**：有数据库的现场无需执行任何升级件，SQL 台账仍停 `V2.1.0_01~04`。
- 升级方式：用 v2.1.2 交付包，解包后 `bash upgrade.sh` 一条命令（详见 `UPGRADE.md`）。

## 四、兼容性

- 接口、数据模型、配置零变更；后端 Java 代码与 v2.1.1 一致。
- 前端差异仅 4 个文件 + 2 个图标资源（`public/imgs/logo.png`、`public/favicon.ico`），不涉及路由与权限。
- 已知限制沿用 v2.1.0/v2.1.1（`BL-75` 跟踪的 9 条 P1 以已延期随版）。

## 五、验证

- `package.sh` 8/8 步通过；出货门禁 **五项 PASS**（含 `fresh-db-install` 真装一次与 `upgrade-entry`）。
- 自测：`t09_upgrade_selftest.sh` 22/22；真包回归 `t07_gate_selftest.sh` 12/12（含「包根缺 upgrade.sh ⇒ FAIL」反例）。
- 品牌图标端到端（开发栈 9091 实测）：上传返回 `/uploads/data-agent/brand/<uuid>.png`；匿名 GET → `200 / image/png`；
  BUG-215 修复后 `/imgs/logo.png` → `200 / image/png`，`/platform/brand-config` 的 `logoUrl` 指向新图。

## 六、交付包

- `phoenix-2.1.2-amd64.tar.gz` ｜ sha256 `1a84f672d0e7f2a99240add4f0bf597eb43654070699875d22dd4166805dded0`
- 门禁证据目录：`docker/dist/gate-2.1.2-amd64-20261031-134927`
- 作废包（不含 BUG-214/215）：`phoenix-2.1.2-amd64.superseded-ebecb5f6.tar.gz`（已改名留存，勿用于交付）
