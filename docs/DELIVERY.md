# 交付与安装说明（v2.1.0 交付链加固）

> 适用：`phoenix-<版本>-<架构>.tar.gz` 交付包（R-01/R-02/R-03/R-05）
> 相关：`docs/OFFLINE-ENGINE.md`（引擎离线件）、`docs/VECTOR-DIM-MIGRATE.md`（向量维度迁移）

## 一、目标机前置要求

| 项 | 要求 | 不满足时 |
|---|---|---|
| 操作系统 | Linux（Debian 系已实测；通用静态二进制路径不挑发行版） | — |
| 架构 | 与包一致（`manifest.json` 的 `arch`）；`uname -m` 对照 | 安装器**在步骤 1 直接拒绝**（可用 `PHOENIX_ALLOW_ARCH_MISMATCH=1` 做本机模拟，不建议用于生产） |
| 引擎 | Docker Engine `OSType=linux`（Windows 原生容器引擎不支持，L-12） | 步骤 1 拒绝并说明原因 |
| Compose | **必须 Compose v2**（`docker compose`）；插件系统级路径 `/usr/libexec/docker/cli-plugins/docker-compose` | 步骤 1 拒绝，并打印安装指引；**不再回退 v1**（R-03 / BUG-193） |
| 执行用户 | 建议**普通用户 + `docker` 组**（不要 root/普通用户混跑，L-100） | root 可能看不到用户级安装的 compose 插件 |
| 端口 | 默认取 `docker/.env` 的 `PHOENIX_HTTP_PORT`，可用 `--port` 覆盖 | 冲突时安装失败并提示 |
| 磁盘 | ≥ 8GB 可用（安装器会 WARN） | 可能 load/up 失败 |

## 二、包内结构（自包含）

```
phoenix-<ver>-<arch>/
├── install.sh            安装器（9 步状态机，可重跑幂等）
├── manifest.json         版本/架构/镜像清单/**底座差异记录**（见第三节）
├── SHA256SUMS            全量校验清单（install.sh 步骤 3 强制校验）
├── images/               六个镜像的 tar（docker load 用）
├── engine/               可选的离线引擎件（静态二进制 tgz；见 OFFLINE-ENGINE.md）
├── docker/               compose 资产 + scripts/（含 gate.sh、vector-dim-migrate.sh）
├── sql/ + releases/      库基线 + 各版本升级件
└── docs/                 本文档等
```

## 三、底座差异记录怎么读（R-01）

`manifest.json` 中的字段：

| 字段 | 含义 |
|---|---|
| `base_images.jre` | **本包实际使用**的 JRE 底座镜像 |
| `base_images.jre_reference` | 参考底座（期望值） |
| `base_images.jre_substituted` | 是否发生替代（`true` 表示与参考不一致） |
| `equivalence_note` | 差异说明：架构 ↔ JRE 底座 ↔ 参考底座 ↔ `JAVA_BIN` ↔ 是否替代 ↔ 影响 |

**出货规则**：`jre_substituted=true` 的包**必须**各自跑通 `install.sh`（到收据）方可交付——两架构底座不同是允许的，但必须**显式可见**。

## 四、安装步骤（照抄）

```bash
# 1) 校验包完整性（可选但推荐）
shasum -a 256 -c phoenix-<ver>-<arch>.tar.gz.sha256     # 若有同名 .sha256
# 2) 解包并安装
tar -xzf phoenix-<ver>-<arch>.tar.gz && cd phoenix-<ver>-<arch>
bash install.sh                      # 需 sudo 时脚本自行使用；端口冲突加 --port 8199
# 3) 成功标志：屏幕打印「Phoenix 安装收据 —— 安装成功」，并生成 RECEIPT 文件
```

**重跑幂等**：已完成的步骤会跳过（`.phoenix-install.state`）；`.env` 不会被覆盖（R-07）。
**注意**：请**始终用同一个用户**执行；换用户前先 `sudo chown -R "$(id -un):$(id -gn)" <包目录>`（L-100）。

## 五、出货门禁（R-05，打包方使用）

```bash
bash docker/scripts/gate.sh --payload <解包目录> --out docker/dist/gate-<ts>
```
四项自检：①`SHA256SUMS` 全量 ②镜像内应用 jar 对**运行用户**可读 ③前置一致（架构 + Compose v2）④**全新空库安装到收据**。
任一项 FAIL → 非 0 退出，证据落 `--out` 目录（md 文件含每项命令与输出）。
`package.sh` 出货时自动调用本门禁（`--skip-gate` 仅调试，禁止用于出货）。

## 六、外部 URL 清单与可达性（L-71：逐条核实，2026-10-11 实测）

| 用途 | URL | 本机实测 |
|---|---|---|
| Docker 静态二进制（aarch64） | `https://mirrors.aliyun.com/docker-ce/linux/static/stable/aarch64/` | HTTP 200 ✅（`docker-29.7.2.tgz` = 77,323,501B） |
| Docker 静态二进制（x86_64） | `https://mirrors.aliyun.com/docker-ce/linux/static/stable/x86_64/` | HTTP 200 ✅（`docker-29.7.2.tgz` = 85,700,518B） |
| 官方静态二进制（备选） | `https://download.docker.com/linux/static/stable/<arch>/` | **不可达（HTTP 000 超时）** ⚠️ 故默认改用阿里云镜像 |
| Debian 系 docker-ce 仓库（内网装机用） | `https://mirrors.aliyun.com/docker-ce/linux/debian/` | HTTP 200 ✅ |
| 在线装引擎（`install.sh` 非离线路径） | `https://get.docker.com` | 未在本次发版核验（受限网络下会回退国内源；失败信息含处置） |
| 镜像站（compose 资产引用） | 见 `docker/.env.example` 注释 | 部署时按现场网络选择 |

> 结论：**离线引擎件来源以阿里云镜像为准**；`download.docker.com` 在受限网络不可用，故不依赖它。
