# Phoenix 安装速查（五平台）

> 完整细节：仓库 `docker/README.md` 与 `releases/<版本>/UPGRADE.md`。本页只讲安装包怎么用。

## 一句话版
| 平台 | 命令（管理员/有 sudo 的终端） |
|---|---|
| Linux x86_64 / arm64 | 解包 → `bash install.sh` |
| mac（Apple Silicon 用 arm64 包 / Intel 用 amd64 包） | 先装 Docker Desktop 或 Colima（脚本不代装）→ 解包 → `bash install.sh` |
| Windows 10/11 / Server 2022 | 管理员 PowerShell → `.\install.ps1`（自动走 WSL2+Ubuntu；中途要求重启就重启后重跑，自动续接） |
| 离线内网机（已有引擎） | 解包 → `bash install.sh --offline` |
| 离线内网机（无引擎） | 先按 OFFLINE-ENGINE.md 装引擎，再上一行 |
| **升级到本版本（机器上已装着旧版）** | 解包 → `bash upgrade.sh`（见下节，一条命令） |

## install.sh 常用参数
```
--timeout 600     # healthy 等待上限秒数（默认 300，慢机加大）
--project myphx   # compose 项目名（默认 phoenix；同机多套时区分）
--offline         # 引擎安装走本地 engine/*.deb（有）或打印指引（无），绝不外联
--env-from PATH   # 升级用：承接旧安装的 docker/.env（库密码/端口不漂移）
--port 8901       # 仅首次生成 .env 时生效（改已装环境的端口请直接改 .env 后重建 nginx）
```
> 升级场景请直接用 `bash upgrade.sh`（它把上面几个参数自动填好），不必手敲。

## 升级（已有旧版在跑，想换成本版）
**一条命令，不用查项目名也不用找旧配置文件**：

```bash
tar -xzf phoenix-<版本>-arm64.tar.gz     # 解到哪都行
cd phoenix-<版本>-arm64
bash upgrade.sh                          # 自动探测旧安装，再走 install.sh 全套流程
```

它自己做的事：① 从容器反查旧安装的 compose 项目名；② 找到旧安装的 `docker/.env`
（**库密码/端口靠它承接**，否则新容器连不上旧数据）；③ 调 `install.sh --offline` 完成
校验→载镜像→重建→迁移→健康等待→自检→收据；④ 打印访问地址与核对命令。

| 情况 | 反应 |
|---|---|
| 机器上有多套栈 | **拒绝猜**，列出「容器 → 项目名」，要你用 `--project <名>` 指明 |
| 找不到旧 `.env` | **中止**并提示 `--env-from <旧安装目录>/docker/.env`（绝不带着随机密码往下装） |
| 想先看会执行什么 | `bash upgrade.sh --dry-run`（零副作用） |
| 数据 | 只换镜像/容器与增量迁移，业务数据不动；SQL 走台账幂等，重复执行自动跳过 |

> Windows：在 WSL 里进包目录跑上面同一条命令（或按 `install.ps1` 的方式进 WSL）。

## 安装成功后（收据卡上有全部信息）
1. 浏览器开 `http://localhost:9080`，`admin/123456` 首登**立即改密**
2. 模型管理里配置真实 API key（安装包不带任何密钥）
3. 日常运维：`cd docker && docker compose -p phoenix ps / logs -f backend`

## 失败怎么办
- 屏幕上的失败输出自带「步骤 N/9 + 日志路径」——把**日志尾部 30 行**发给交付支持
- 修好环境后**直接重跑**同一命令：已完成的步骤自动跳过（断点续传），无副作用
