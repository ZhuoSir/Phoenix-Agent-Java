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

## install.sh 常用参数
```
--timeout 600     # healthy 等待上限秒数（默认 300，慢机加大）
--project myphx   # compose 项目名（默认 phoenix；同机多套时区分）
--offline         # 引擎安装走本地 engine/*.deb（有）或打印指引（无），绝不外联
```

## 安装成功后（收据卡上有全部信息）
1. 浏览器开 `http://localhost:9080`，`admin/123456` 首登**立即改密**
2. 模型管理里配置真实 API key（安装包不带任何密钥）
3. 日常运维：`cd docker && docker compose -p phoenix ps / logs -f backend`

## 失败怎么办
- 屏幕上的失败输出自带「步骤 N/9 + 日志路径」——把**日志尾部 30 行**发给交付支持
- 修好环境后**直接重跑**同一命令：已完成的步骤自动跳过（断点续传），无副作用
