# v2.0.1（hotfix）— 部署/交付问题修复

| 项 | 值 |
|---|---|
| 类型 | **hotfix**（在 v2.0.0 基础上，非新需求） |
| 基线 | `v2.0.0`（分支 `2.0.0` @ `0fff33e`） |
| 施工分支 | **`hotfix/2.0.1`** —— 按用户口令：**未 push、未合并** |
| 版本号 | **2.0.1**（用户 2026-10-11 口令「整体版本号就是 2.0.1」） |
| tag / 冻结 | 无 |
| 变更性质 | **仅脚本/构建层**（未改数据库结构、未改应用代码） |

## 一、修复清单

| BUG | 问题 | 修复 |
|---|---|---|
| BUG-189 | 全新空库安装死锁：迁移件依赖“应用自建表”，而 backend 又等 migrator 成功 ⇒ exit 3 | `docker/init/migrate.sh` 迁移前幂等预建（`to_regclass` 守卫，重装可重入） |
| BUG-190 | migrator 连接竞态：健康检查经 unix socket 先过、TCP 5432 未就绪 ⇒ exit 2 | `migrate.sh`/`post_start.sh` 增加“等 TCP 就绪”重试 |
| BUG-191 | 镜像内 jar 为 root:root 0600 ⇒ 非 root(10001) 读不到，backend 无限重启 | `Dockerfile.backend.multistage` 加 `--chown=app:app` + `RUN chmod 644`；thin 路径同口径 |
| BUG-192 | `package.sh` 步骤 3 崩：31 处 `$var` 紧贴中文 ⇒ `unbound variable` | 6 个脚本统一改 `${var}` |
| BUG-193 | 仅 Compose v1 的目标机解析 compose 失败，且无友好提示 | **部分修复**（本次仅加跨架构开关）；v2 检测见 BL-57 |
| BUG-194 | 交付包 `.sha256` 缺文件名 ⇒ 交付脚本首步退出 | 改为 `哈希␣␣文件名` |
| BUG-195 | 站点 `tbl_vector_store_simple_data` 缺 `id` 唯一约束 ⇒ upsert 报 ON CONFLICT 错、文档 FAILED | **待现场执行**（SQL/自动补齐脚本已提供） |

另含两项工程改进：`.dockerignore` 排除产物目录（构建上下文 3.5GB → 几十 MB）；`install.sh` 增加默认关闭的
`PHOENIX_ALLOW_ARCH_MISMATCH`（跨架构模拟验证用，默认仍严格拒绝架构不符）。

## 二、验证证据（本次实际跑出来的）

- 两个交付包各自**从重打包产物**执行 `install.sh`：**9/9 步、退出码 0、收据「安装成功」**；
  装出的栈 4 容器 healthy、**首页 HTTP 200**、**登录 `code:100` 且返回 token**、升级台账 **32 件**（末件 `V2.0.0_16`）
- `migrate.sh` 修复在**独立全新 PG** 上演练：exit 0 / ERROR 0 / 台账 32 件 / 关键表齐
- jar 权限机制微构建实证：`--chown` ⇒ 非 root 可读；不 chown ⇒ 复现 NOT-READABLE

## 三、交付物状态

⚠️ **当前盘上两个包仍是 `2.0.0` 版本号**（`docker/dist/phoenix-2.0.0-{arm64,amd64}.tar.gz`）。
按 v2.0.1 交付需以 `--version 2.0.1` **重打**（见 BL-60）。

## 四、未闭环（已登记待办）

BL-55 两架构底座不一致（arm64 UBI9+ES JDK / amd64 temurin）· BL-56 包内无离线引擎 deb（install.sh 步骤 2 未实测）
· BL-57 install.sh 强制 Compose v2 检测 · BL-58 迁移脚本加固 + 向量表 id 唯一约束自检 · BL-59 出货门禁四项自检
· BL-60 v2.0.1 重打与交付

## 五、复盘

详见 **`releases/v2.0.1/DEPLOY-RETROSPECTIVE.md`**（16 条问题：打包链 4 / 交付包 3 / 目标机适配 3 / 站点功能 2 / 我方事故 3 + 未闭环）
