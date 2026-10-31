> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-31

# 交付链一键升级 + 品牌图标可显示（delivery-upgrade-and-brand-icon）

## 一、背景与目标

### 背景（两件事都源自用户现场反馈，非假设）

1. **升级太难敲**：2026-10-26 演练后给出的升级命令需要人肉查「旧安装的 compose 项目名」和「旧 `docker/.env` 路径」，
   用户原话：「太磨叽了，没有完整的一键升级操作吗」「你这个太复杂了看不明便」。
   而仓库里已有的 `docker/scripts/upgrade.sh` **对交付包升级根本不可用**（BUG-213，三处硬伤：
   `releases/<VER>/sql` 硬门槛把 PATCH 版挡死、`TAG` 是赋值后未使用的死变量、脚本不入交付包根）。
2. **外观设置的图标显示不出来**：2026-10-31 用户反馈「图标上传后，无法正常显示，看路径应该是被拦截了，返回到了主页」。
   实测根因是 `BrandAssetServiceImpl` 把 `storeFile()` 返回的**磁盘相对路径**当 URL 回给前端（BUG-214），
   浏览器于是请求 `/data-agent/brand/….png`，被 nginx 的 SPA 兜底送回 `index.html`。
   对照实现（头像上传 `/api/upload/avatar`）本就正确的走 `getFileUrl()`，实测可用。

### 目标（成功长什么样）

- 实施人员拿到交付包，升级动作 = **一条命令**，无需先查询任何东西；查不出目标时**宁可中止也不猜**。
- 外观设置里上传的 Logo / favicon **上传完就能看到、保存后登录页也显示**（登录页要匿名取图）。
- 上述两个能力**随交付包存在**，并由出货门禁在打包时把关（缺了/坏了就打不出包）。

## 二、需求条款（EARS）

| 编号 | 条款 |
|---|---|
| R-01 | WHEN 在解包后的交付包根目录执行 `bash upgrade.sh`, 系统 SHALL 自动确定旧安装的 compose 项目名与旧安装 `docker/.env` 的绝对路径，并转调 `install.sh` 完成「校验→载镜像→承接 env→重建容器→迁移→健康等待→verify→收据」全流程，不额外造第二套安装流程。 |
| R-02 | IF 机器上存在两套及以上 Phoenix 栈且调用方未给 `--project`, 系统 SHALL 拒绝猜测、列出「容器名 → 项目名」并给出补参数的用法；IF 无法定位旧 `docker/.env`, 系统 SHALL **中止**（不得继续），并说明后果（缺它 ⇒ 安装器随机生成库密码 ⇒ 新容器连不上旧数据卷）与补救命令。 |
| R-03 | WHEN 执行 `bash upgrade.sh --dry-run`, 系统 SHALL 只打印将要执行的命令；该次执行 MUST NOT 产生安装状态文件、`docker/.env`、收据或任何容器/镜像变更（零副作用）。 |
| R-04 | WHEN 在仓库开发栈执行 `upgrade.sh vX.Y.Z`, 系统 SHALL 保持原语义（切换镜像 tag、重建 backend/nginx），且：有 `releases/vX.Y.Z/sql` 才执行迁移，无 SQL 的 PATCH 版不得被拒绝执行；目标 tag MUST 真正生效（不得存在"赋值不使用"的死变量）。 |
| R-05 | WHERE 交付包被打出, 包根 SHALL 与 `install.sh` 同级包含 `upgrade.sh`；AND 出货门禁 SHALL 含断言项：包根缺 `upgrade.sh`、或 `bash upgrade.sh --dry-run` 组装不出 `install.sh` 命令、或 dry-run 出现副作用 ⇒ 该项 FAIL、整包不可出货。 |
| R-06 | WHEN 管理员在「外观设置」上传 Logo 或 favicon, 系统 SHALL 返回**可直接访问的 URL**（本地存储为 `/uploads/data-agent/brand/<uuid>.<ext>`；OSS 模式为其对象地址）；AND 该 URL SHALL 在**未登录**状态下返回图片字节（`Content-Type: image/*`），因为登录页要在鉴权前显示品牌图。 |
| R-07 | IF 写入或读取到的品牌地址是「裸存储路径」形态（无前导 `/`、非 `http(s)://`、形如 `x/y/z.png`）, 系统 SHALL 自动补 `/uploads/` 前缀并记 INFO 日志，而不是让它显示不出来或在校验处报错。 |
| R-08 | WHILE 交付包尚未重打（现场手上还是旧包）, 系统 SHALL 保持 `bash install.sh --offline --project <名> --env-from <旧 .env>` 这条手填参数路径可用，文档同时给出两种写法与各自适用条件。 |
| R-09 | WHEN 本次改动落地, 既有文件面行为 MUST 不回归：头像上传返回的 URL 仍可用；会话附件仍只经受鉴权端点暴露（不得出现存储直链）；知识库原件仍被静态面拒绝（`DENIED_STATIC_PREFIXES` 语义不变）。 |

## 三、验收场景

| # | 对应 | GIVEN / WHEN / THEN | 实测结果（2026-10-31） |
|---|---|---|---|
| V-1 | R-01/R-05 | GIVEN 解包后的包根 → WHEN `bash upgrade.sh --dry-run --project X --env-from <旧 .env>` → THEN 打印 `bash install.sh --offline --timeout 900 --project X --env-from …` 且退出码 0 | ✅ `t09` 场景 1（5 条断言） |
| V-2 | R-02 | GIVEN 机器上 5 套栈并存 → WHEN 不给 `--project` → THEN 退出非 0，列出「容器 → 项目名」 | ✅ 输出列出 `phx210/phxupg/t10arm/phoenix-release/phxarm` |
| V-3 | R-02 | GIVEN 旧安装的 compose 目录已不存在 → WHEN 找不到旧 `.env` → THEN 中止并提示 `--env-from`，**不放行** | ✅ `--project phxupg` 实测拒绝执行 |
| V-4 | R-03 | GIVEN 包根 → WHEN `--dry-run` → THEN 包目录内不出现 `.phoenix-install.state` / `docker/.env` / `RECEIPT` | ✅ `t09` 1d/1e；门禁 `upgrade-entry` 亦断言 |
| V-5 | R-04 | GIVEN 仓库 → WHEN `upgrade.sh v2.1.1 --dry-run`（PATCH 无 SQL）→ THEN 打印「跳过迁移」且无 migrator 调用；`IMAGE_TAG=2.1.1` 出现在将执行命令里 | ✅ `t09` 6b/6c |
| V-6 | R-05 | GIVEN 真交付包解包载荷 → WHEN 跑出货门禁 → THEN `upgrade-entry` PASS；删掉包根 `upgrade.sh` 后同项 FAIL | ✅ `t07` 12/12（反例 C 专测） |
| V-7 | R-06 | GIVEN 超管 token → WHEN 上传 70 字节 png → THEN 返回 `/uploads/data-agent/brand/<uuid>.png`；**不带 token** GET 该地址 → THEN `200 / image/png / 70 字节` | ✅ 实测两次（修后），对照头像 `/api/upload/avatar` 同样通过 |
| V-8 | R-07 | WHEN 用旧格式 `data-agent/brand/<uuid>.png` 调 PUT → THEN 落库为 `/uploads/data-agent/brand/<uuid>.png` 且有 INFO 日志 | ✅ 实测日志「检测到存储相对路径，兜底补访问前缀 /uploads/: …」 |
| V-9 | R-08 | GIVEN 旧包（包根无 upgrade.sh）→ WHEN 按文档手填 `install.sh` 两参数 → THEN 升级流程照常 | ✅ 2026-10-26 演练 9/9 步 + 11 项验证 |
| V-10 | R-09 | WHEN 回归头像/附件/知识库三处 → THEN 行为与修前一致 | ✅ 头像 URL 匿名 200 图；附件 VO 仍只给受鉴权端点；`DENIED_STATIC_PREFIXES` 未改动 |

## 四、范围外（Non-goals）

- 不做 Windows 侧 `upgrade.ps1` 一键入口（本次只覆盖 bash 目标机；Windows 升级仍需进 WSL 执行）。
- 不改外观设置页面的上传交互/控件形态（问题在后端返回值，不在前端）。
- 不做对象存储（OSS）迁移、不改 `FileStorageService` 契约与既有头像/附件链路。
- 不做「品牌图片自动裁剪/多尺寸/favicon 派生」；仍不开放 SVG（防脚本型 XSS）。
- 不把 `install.sh` 拆成新的独立升级器（复用九步状态机，避免第二套流程）。

## 五、我正在做的假设

1. 客户现场是 **x86_64（amd64）** 机器，本次交付物为 `phoenix-2.1.2-amd64.tar.gz`；若现场是 ARM 需另打 arm64 包。
2. 旧安装是用 compose 起的（容器带 `com.docker.compose.project` / `.config_files` 标签）——自动探测依赖它；标签缺失时退化为手填参数。
3. 旧 `docker/.env` 内含 `PG_PASSWORD`（探测时以此为判据之一），且现网库密码与升级后一致才算"承接成功"。
4. 登录页取品牌图必须是匿名可访问的（现状：`/uploads/**` 静态面不经 sa-token 判定，敏感前缀已由 DENIED 清单堵口）。
5. `v2.1.2` 只含交付脚本与这两处修复，**应用代码与 v2.1.1 一致**（`git diff v2.1.1..main` 对 `*.java/*.ts/*.vue/*.sql/*.yml` 为空）。

> 现在纠正，否则按此执行。

## 六、待确认问题

| # | 问题 | 我的建议 |
|---|---|---|
| Q1 | 是否同时补一份 **arm64** 的 2.1.2 包？ | 现场是 x86 就不必；不确定我就补打一份（约 15 分钟） |
| Q2 | 2026-10-31 09:25 打的那份 2.1.2 包（sha `078c51f8…`，不含图标修复）如何处置？ | 已作废标注 + 从 `docker/dist` 移走，防误发（要移走说一声） |
| Q3 | v2.1.2 的发版动作（tag / 合 main / push / RELEASE-NOTES+UPGRADE 汇总）口令何时给？ | 未收到口令前不打 tag、不 push、不动 main |
