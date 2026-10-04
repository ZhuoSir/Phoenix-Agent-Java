# Artifacts: docker-auto-pipeline（v1.5.0 升级件登记）

## SQL
无（零 DDL）。

## 配置
无新增 compose/env 键。新脚本参数体系自文档化（--help）。

## 交付物清单（全部 docker/ 下，随 v1.5.0 发布）
| 件 | 性质 |
|---|---|
| scripts/package.sh | 新增：一键打包（八步，五镜像 payload） |
| scripts/install.sh | 新增：Linux/mac 九步安装（幂等/断点/离线降级/--env-from 升级承接） |
| scripts/install.ps1 | 新增：Windows WSL2 路线一键安装 |
| scripts/lib/common.sh + selftest.sh | 新增：共用基座+22断言自测 |
| scripts/mirrors.list | 新增：镜像加速候选源（竞速选优） |
| maven/settings.default.xml | 新增：海外直连 settings（--overseas 用） |
| Dockerfile.{backend,frontend}.multistage | 扩展：jar/dist 工件阶段 + NODE_IMAGE/NGINX_IMAGE ARG + ENV CI=true（默认行为不变） |
| payload-docs/ 三件套 | 新增：随包文档 |
| scripts/{build,save-offline,load-and-run}.sh | 仅头注（手工链定位），行为零变化 |

## 升级影响
既有部署零影响（无 DDL/无配置键/旧手工链行为不变——开发栈 verify 13/13 回归实证）。

## R-10 增量与合并后直入件补记（2026-10-04，M3 对账）
| 件 | 性质 |
|---|---|
| scripts/bootstrap.sh / bootstrap.ps1 | 新增：目标机源码一键全链 |
| scripts/phoenix-ctl.sh / phoenix-ctl.ps1 | 新增：运维控制一条入口（BUG-63 随附，用户直接指令） |
| scripts/README.md | 新增：工具箱使用指南（用户直接指令） |
| docker-compose.yaml | 变更：四长驻服务 +restart: unless-stopped（BUG-63） |
| scripts/verify.sh | 变更：PHOENIX_COMPOSE_PROJECT/PHOENIX_FRESH 参数化（默认行为不变） |
| scripts/install.sh | 变更：+--port/--env-from；.phoenix-project 锚 |
