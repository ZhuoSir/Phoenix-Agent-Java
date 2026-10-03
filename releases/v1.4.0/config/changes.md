# v1.4.0 配置变更清单

## 数据库
**零 DDL**（本版无 schema 变更；migrator 重放 v1.2/v1.3 升级件不受影响）。
运维数据脚本（非 Flyway 件，幂等，升级时执行一次）：`sql/ops/04_orphan_cleanup_ops.sql`
——BUG-14 存量孤儿清理（删智能体遗留的运行配置/技能绑定/组授权/库绑定/数据源绑定行）。

## compose 新增环境变量（backend 服务，均带默认值，可不配）
| 键 | 默认 | 说明 |
|---|---|---|
| PHOENIX_AGENT_TURN_TIMEOUT_SECONDS | 600 | 脱离式轮次兜底时限，超时定稿标「超时中断」（R-07） |
| PHOENIX_AGENT_TURN_FLUSH_SECONDS | 5 | 助手消息增量落库周期（R-02） |
| PHOENIX_AGENT_TURN_BUFFER_FRAMES | 2000 | 追流 replay 帧闸（超限截尾，DB 增量兜历史可见性） |

## nginx（phoenix.conf）
- 新增 exact location：`= /auth/login`、`= /auth/register` 按方法分流（GET 导航→SPA index.html；POST 等→后端）——BUG-56 双身份共存，verify[13] 断言看护
- 既有：/api、/platform 读写超时 900s（v1.3.0 引入，本版未变）

## 构建（multistage 国内源默认化）
- 后端：`ARG MAVEN_SETTINGS=docker/maven/settings.aliyun.xml`（阿里云中央仓，已入库）；内网/海外 `--build-arg MAVEN_SETTINGS=docker/maven/settings.xml` 覆盖
- 前端：`ARG NPM_REGISTRY=https://registry.npmmirror.com`；海外 `--build-arg NPM_REGISTRY=https://registry.npmjs.org`
- `.env.example`：IMAGE_TAG 默认纠正为 v1.3.0（历史 v1.2.0 陈旧值）+ 受限网络镜像加速示例注释

## 前端工程
- dev 端口生效 `.env.development` 的 VITE_PORT=5777（BUG-25）
