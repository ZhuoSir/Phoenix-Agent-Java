> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-31

# 技术方案：一键升级入口 + 品牌图标可访问

## 0. 坑核对（必读项，先于方案）

已核对 `specs/_project/lessons.md` 全部 active 坑（115 条），与本方案相交的 13 条及规避方式：

| 坑 | 相交点 | 本方案如何避开 |
|---|---|---|
| **L-114** 仓库里有脚本 ≠ 客户机能用 | 本次直接起因：`upgrade.sh` 不入包根、门禁不校验它 | 三件齐全：`package.sh` 入包清单 + `gate.sh` 第 5 项 `upgrade-entry` 断言 + 在**真包解包载荷**上跑 `t07` |
| **L-115** 上传验收必须 GET 回来的 URL 断言是图片 | BUG-214 的成因：旧验收只看"接口有返回 data" | V-7 断言三段：返回形态 / 匿名 GET `Content-Type: image/png` + 字节数 / PUT 回读一致 |
| L-111、L-110 静默失效与"构建通过≠运行可用" | 路径当 URL 也是静默失效（HTTP 200 但回的是 HTML） | 断言打在**可观察结果**上（图片字节、库内值），不看编译与 200 本身 |
| L-106 运维 shell 的"静默不执行"陷阱 | `upgrade.sh` 大量 `docker … \| grep …` 管道 | `set -euo pipefail` 下所有 `VAR=$(…)` 取值管道补 `\|\| true`；空栈/无匹配场景实跑过（`t09` 场景 2/3） |
| L-104 字符串 replace 必须断言命中数 | 本次改 `t07`/`gate.sh`/台账与文档多处 | 一律 `assert s.count(old)==1` 后替换；改后回读关键行（表检 + `grep` 复核） |
| L-102 环境三对齐、用例独立 | `t09` 各场景都要"包根"形态 | 每个场景独立 `mkpkg` 载荷副本 + 独立工作目录，禁止共用状态 |
| L-103 `mktemp` 模板以 `XXXXXX` 结尾 | 自测脚本建临时目录 | `mktemp -d /tmp/t09up.XXXXXX` |
| L-05 bash 3.2 兼容契约 | 目标机普遍是 bash 3.2/4.x | `upgrade.sh` 头部声明 bash 3.2；`t09` 场景 1 显式用 `/bin/bash`（macOS 3.2.57）跑判别 |
| L-12 交付目标机架构/引擎/网络三查 | 本次出 **amd64** 包，构建机是 arm64 | 门禁以 `PHX_ALLOW_ARCH_MISMATCH=1` 放行并在 MILESTONE 记「出货前在真实 amd 机复验一次」 |
| L-09 curl 超时参数过小造成假象 | `upgrade.sh` 收尾自检的登录探测 | 统一 `-m 10` 且**非 200 不判失败**（改过口令的现场属正常），只给提示 |
| L-06 多入口枚举缺失 | `storeFile()` 有三个调用方 | 全量枚举（见 §2 矩阵）：头像/会话附件/品牌图——只有品牌图坏，其余对面断言未回归 |
| L-07 假阳性验证 | dry-run 全绿 ≠ 真升级可用 | 另跑真包五项门禁（含 `fresh-db-install` 空库真装），dry-run 只作快速回归 |
| L-16 用户实测期间禁止静默部署 | 我重启了 Docker Desktop 与开发栈 9091 | 会话内即时声明；台账 §七 记录时间线与影响 |

## 1. 方案概述

### 1.1 一键升级入口（R-01~R-05）

`docker/scripts/upgrade.sh` 重写为**场景自动判别的统一入口**，不造第二套流程：

```
判别：包根存在 install.sh + manifest.json ⇒ 交付包模式；否则 ⇒ 仓库开发栈模式

交付包模式（客户机）
  ① 项目名：docker ps -a 找 *-backend* 容器 → 读 label com.docker.compose.project
     · 唯一 → 自动取；· ≥2 套 → 中止并列表要求 --project；· 找不到 → 中止并给查询命令
  ② 旧 .env：读容器 label …project.config_files 同目录的 .env
     · 取不到 → 在 /opt /srv /data ~ 内搜带 PG_PASSWORD= 的 docker/.env（跳过 git 工作树内的）
     · 仍取不到 → 中止（绝不带着随机库密码继续装）；找到了但与 compose 目录不同层 → 继续但 ⚠️ 提醒核对
  ③ 转调：bash install.sh --offline --timeout 900 --project <名> --env-from <路径> [透传其余参数]
  ④ 收尾速览：地址 / 登录状态码 / 容器镜像 / 迁移台账查询命令
  --dry-run ⇒ 只打印 ③ 的命令；--online ⇒ 去掉 --offline

仓库开发栈模式
  有 releases/<VER>/sql 才跑 migrator（PATCH 版无 SQL ⇒ 跳过而非拒绝）；
  IMAGE_TAG 真正写入 docker/.env 或本次注入（原死变量已修）；重建 backend/nginx + migrator-post
```

**交付链接线**：`package.sh` 把 `upgrade.sh` 与 `install.sh` 同级拷入包根；`gate.sh` 门禁四项 → **五项**，
新增 `upgrade-entry`：包根必须含 `upgrade.sh`，且 `--dry-run` 必须组装出 `install.sh --offline …` 命令、
且不得产生 `.phoenix-install.state` / `docker/.env`（既存文件不误伤，采前后快照对比）。

### 1.2 品牌图标可访问（R-06~R-07）

服务端补上缺失的一次转换：

```java
// 修前：把磁盘相对路径当 URL 直接回给前端
storage.storeFile(file, "brand")                      →  data-agent/brand/<uuid>.png     ❌
// 修后：与头像上传同构，过一道 getFileUrl()
storage.storeFile(file, "brand").map(storage::getFileUrl) →  /uploads/data-agent/brand/<uuid>.png ✅
```

- 本地盘模式下前缀取自配置 `phoenix.data.file.url-prefix`（默认 `/uploads`），由 `WebConfig` 的资源处理器出图；
  OSS 模式 `getFileUrl()` 返回对象域名地址 ⇒ 两种存储实现都自然正确，前端零改动。
- 读取侧与写入侧都过 `toAccessUrl()`：命中「裸存储路径」形态时补 `/uploads/` 前缀并打 INFO 日志
  （历史值、旧客户端 replay 都兜得住），其余形态仍按原校验拒绝（不放宽成"什么都能存"）。

## 2. 共享面身份矩阵（本方案触碰的都是共享面）

**自检问句：还有谁依赖这个对象？有一个以上"是"即为共享面。**

| 共享面对象 | 既有身份（方法 × 调用方 × 端点） | 变更后预期行为 | 对面断言（实测） |
|---|---|---|---|
| `FileStorageService.storeFile(FilePart/MultipartFile, subPath)` 的返回值语义 | ① 头像：`FileUploadController.uploadAvatar` ② 会话附件：`ChatAttachmentServiceImpl`（只用路径落库/回滚删除） ③ 品牌图：`BrandAssetServiceImpl.upload` | ①② 不变；③ 之后额外过 `getFileUrl()` | ① `POST /api/upload/avatar` → `/uploads/data-agent/avatars/….png`，匿名 GET → `200 image/png` ✅ ② 附件 VO `url` 仍指受鉴权端点、不出存储直链（代码核对 + 未改动）✅ ③ V-7 ✅ |
| `/uploads/**` 静态资源面（`WebConfig` 资源处理器） | ① 登录页匿名取品牌图/favicon ② 头像显示 ③ 敏感前缀黑名单 `data-agent/agent-knowledge`、`agent-workspace` | ①② 可匿名取图；③ 继续返回 404 | ①② 实测 200 图片字节 ✅ ③ 未改动该清单（R-09）✅ |
| `docker/scripts/upgrade.sh` 这个脚本文件 | ① 交付包根（客户机一键升级，新身份） ② 仓库 `docker/scripts/`（开发栈升版，原身份） ③ 门禁/CI 不调用它 | ①转调 install.sh ②保留原语义并修门槛/死变量 | `t09` 场景 1/2/3（包模式）+ 场景 6/7（仓库模式）+ 8（参数错误）✅ |
| 出货门禁项集合 | ① `package.sh` 步骤 7 `pkg_ship_gate`（计数动态，不写死） ② `t07_gate_selftest` 断言 `通过=4` ③ `gate.sh -h` 的 sed 行号范围 ④ 历史证据（v2.1.0/v2.1.1 记的是"4/4 PASS"） | ①②③ 同步为五项；④ **不回改**（tag 是历史凭证） | `t07` 12/12 且日志 `通过=5 失败=0` ✅；`gate.sh -h` 打印无串位 ✅ |
| `install.sh` 参数契约（`--offline/--timeout/--project/--env-from/--port`） | ① 人工执行 ② `install.ps1` 转发 ③ 门禁 `fresh_install` ④ 新入口 `upgrade.sh` 转调 | ①②③ 不变；④ 复用同一契约，不新增安装分支 | `t09` 1b 断言组装出的命令行与契约逐字一致 ✅ |

## 3. 关键日志点清单

| 场景 | 级别 | 触发点 | 必带字段 | traceId/runId |
|---|---|---|---|---|
| 品牌图上传成功（外部文件副作用） | INFO | `BrandAssetServiceImpl.upload` | `by`（操作人）、`ext`、`bytes`、`path`（磁盘相对）、`url`（访问地址） | 无（同步请求，靠 by+path 关联） |
| 裸存储路径兜底 | INFO | `BrandConfigServiceImpl.toAccessUrl` | 原值（便于事后确认是历史值还是新写入） | 无 |
| 品牌配置更新（安全事件相邻） | INFO | `BrandConfigServiceImpl.update` | `by`、`systemName`、`primaryColor`、`logoSet`、`faviconSet`（不落完整 URL 之外信息） | 无 |
| 升级入口场景判别 | stdout | `upgrade.sh` 开头 | 场景（交付包/仓库）、包版本 | 无 |
| 自动探测结果 | stdout | `upgrade.sh` ①② | 项目名 + 来源容器名；旧 `.env` 绝对路径 | 无 |
| 拒绝猜测而中止 | stderr + 非 0 退出码 | `upgrade.sh` ①② | 多栈时列「容器 → 项目名」；缺 `.env` 时给后果与补救命令 | 无 |
| 出货门禁项结论 | INFO/ERROR | `gate.sh` `phx_gate_item` | `item=upgrade-entry`、`runId`、失败时附复现命令行 | `runId`（gate-* 证据文件内） |

## 4. 被拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| 前端拼 `/uploads/` 前缀补全 | 把服务端存储实现细节（`url-prefix`、OSS 域名）泄漏进前端；OSS 模式下必然错；根因在返回值语义，修前端等于把坑埋进第二个地方 |
| nginx 加 `location /data-agent/` 反代兜住坏路径 | 为迁就错误返回值扩大共享面（网关路径），且仍治不了 OSS；同时把"匿名可枚举上传目录"的风险面拉大 |
| `upgrade.sh` 里自己实现载镜像/迁移/健康等待 | 与 `install.sh` 九步状态机重复——重复即分叉（断点续传、幂等、verify 都要重写一遍）；改成转调，一个流程一个入口 |
| 找不到旧 `.env` 时自动生成一份继续装 | 现场表现是"升级后连不上旧库、数据像丢了"，比中止恶劣一个量级；宁可让用户补一个参数 |
| 写 SQL 迁移修正历史品牌地址值 | 值不该由迁移硬改（且 `tbl_platform_brand_config` 现网可能为空/默认）；读取侧兜底幂等、可观察、不动库 |
| 直接改 `storeFile()` 的返回语义为 URL | 它是被三个调用方共享的存储面原语（见 §2），改它等于同时改头像与附件链路；本次只在品牌侧做转换 |

## 5. 风险与规避

| 风险 | 规避 |
|---|---|
| 门禁在 arm64 构建机上跑 amd64 包（模拟执行），健康等待可能超时或行为偏差 | 已用 `PHX_ALLOW_ARCH_MISMATCH=1` + `PHX_GATE_TIMEOUT=1500` 实跑通过（247s）；出货前在**真实 amd64 机器**按 `releases/v2.1.2/UPGRADE.md` 复验一次（L-12） |
| 现场手上的旧包（2026-10-26 的 2.1.1-arm64、2026-10-31 09:25 的 2.1.2-amd64）内容不同 | 文档同时保留手填 `install.sh` 写法（R-08）；sha 变化写进 MILESTONE；建议把作废包移走（Q2 待拍板） |
| `phoenix.data.file.url-prefix` 被现场改成别的值 | `upgrade`/`toAccessUrl` 的兜底前缀取自配置默认；若现场改此值，须同步 nginx `location /uploads` —— 已列为已知限制，verify 侧断言留待后续（本次不做，避免扩大面） |
| 自动探测在多栈机器上选错对象 | 设计上禁止：≥2 个项目即中止（V-2），不提供"猜一个"的分支 |
| 用户正在实测时我动了环境（L-16） | 本轮重启 Docker Desktop 与开发栈 9091 已即时声明；验证产生的 2 个临时图与所有配置改动均已还原（品牌值回到修前状态） |
