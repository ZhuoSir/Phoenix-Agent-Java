# v1.5.0 配置变更清单

## 数据库
**零 DDL、零运维数据脚本**（本版为纯交付工具链版本，应用代码零变化）。

## compose（docker-compose.yaml）
- **四个长驻服务新增 `restart: unless-stopped`**（BUG-63：宿主/Docker 重启后栈自愈；migrator 两个一次性服务保持 `restart: "no"`）
- ⚠ 既有部署应用此变更需重建容器：`docker compose up -d --force-recreate --pull never postgres redis backend nginx`（数据卷无损）

## 新增脚本参数面（全部自带默认值，零强制配置）
| 脚本 | 参数 |
|---|---|
| package.sh | `--arch amd64\|arm64` `--version` `--mirror <前缀>` `--overseas` `--with-engine-debs` `--out` |
| install.sh | `--timeout` `--project` `--port` `--offline` `--env-from <旧.env>` |
| install.ps1 / bootstrap.ps1 | `-Timeout -Project -Port -Distro -Offline`（ps1 惯例参数） |
| bootstrap.sh | `--version --project --port --timeout --offline --mirror` |
| phoenix-ctl.sh/.ps1 | `start\|stop\|restart\|down\|status\|logs\|verify\|purge [服务] [--project] [--dir]` |
| verify.sh | 环境：`PHOENIX_COMPOSE_PROJECT`（显式绑定目标栈）、`PHOENIX_FRESH=1`（软化首启断言[7]）、`PHOENIX_HTTP_PORT` |

## 构建面
- `docker/scripts/mirrors.list`：镜像加速候选清单（竞速选优，可直接编辑增删）
- `docker/maven/settings.aliyun.xml`：+30s socket 超时（防死连接永挂）；`settings.default.xml` 新增（--overseas 用）
- multistage Dockerfile：`ARG NODE_IMAGE/NGINX_IMAGE`（默认=历史值）、`jar/dist` 工件提取阶段（默认末阶段不变）、`ENV CI=true`（monorepo prepare 钩子无 .git 兼容）
- `docker/.gitignore`：+`dist/` +`*.tar.gz`（打包产物闸门）

## 无变更面（如实声明）
应用代码/接口/库表/前端页面：**零变化**。v1.4.0 栈升 v1.5.0 = 仅换 compose 文件应用重启策略（或干脆不动，新装自然带上）。
