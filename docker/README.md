# Phoenix 一键部署交付包（v1.2.0）

compose 编排的多容器交付包：nginx(前端+反代) / backend(Spring Boot) / postgres(pgvector) / redis / 初始化器。
**仅一个对外端口（默认 9080）**；数据全在 named volumes；镜像不含任何真实密钥与口令。

## 1. 快速开始（联网机器）
```bash
cp .env.example .env          # 至少改 PG_PASSWORD
sh scripts/build.sh           # 构建 backend/frontend 镜像 + 拉基础镜像
docker compose up -d          # 首次启动自动完成建库+种子+升级件（约 1~2 分钟）
sh scripts/verify.sh          # 7 项断言全 PASS 即部署成功
```
浏览器打开 `http://<主机>:9080`。

## 2. 离线部署（隔离网络目标机）
构建机：`sh scripts/build.sh --amd64 && sh scripts/save-offline.sh`（产出 tar.gz+sha256，约 2~3G）。
目标机（仅装 docker≥24）：解包本目录 → `sh scripts/load-and-run.sh phoenix-v1.2.0-images.tar.gz`。
注意：模型 API（dashscope/deepseek）需目标机可出网，否则对话/生成不可用（登录与界面正常）。

## 3. 首次登录与改密
- 账号 **admin / 123456**（包内种子创建；仓库种子中的 xtj/liufang 也是该口令，属演示遗留，生产建议删除或改密）
- 登录后经「修改密码」功能立即改密；菜单权限当前为开发期全放开（工程已知 TODO，acl 机制上线前请在内网使用）
- **模型密钥需自行录入**：管理端「模型管理」填入各家 api_key 并设默认（种子只有 `sk-xxxxxx` 占位符）

## 4. 配置项（.env）
| 键 | 默认 | 说明 |
|---|---|---|
| PHOENIX_HTTP_PORT | 9080 | 唯一对外端口 |
| PG_PASSWORD | phoenix | **生产必改**；同时改 compose 内三处引用源（只有此一个入口，改此即全量生效） |
| IMAGE_TAG | v1.2.0 | backend/frontend 镜像标签 |
| JAVA_OPTS | MaxRAMPercentage=70 | 容器感知堆 |
高级覆盖（可选）：`SPRING_DATA_REDIS_HOST/JETCACHE_REMOTE_DEFAULT_URI/SPRING_AGENT_*` 等按 Spring relaxed-binding 直接加到 `backend.environment`。

## 5. 备份与恢复
- 备份：`sh scripts/backup.sh` → `backup/phx_<时间>.dump`（pg_dump -Fc；Redis 为 AOF 卷 `phoenix-release_redisdata`，冷备停服打包即可）
- 恢复：见 backup.sh 输出尾行 `pg_restore` 命令；卷需先 `docker volume create` 同名卷

## 6. 升级 / 回滚
- 升级：拿到新 `releases/vX.Y.Z/`（SQL 权威）与新镜像 → 放好目录 → `sh scripts/upgrade.sh vX.Y.Z`（哨兵台账只补增量件，幂等）
- 回滚：**先回退 backend 镜像版本，再按 `releases/vX.Y.Z/UPGRADE.md`「回滚步骤」逆序执行**（回滚件在 `releases/vX.Y.Z/sql/rollback/`）；结构回滚不还原数据，兜底走 §5 的 dump

## 7. 故障排查
| 症状 | 处理 |
|---|---|
| `compose ps` 有 starting/exited | `docker compose logs --tail=100 <服务>`；migrator 失败会退出非零并留 psql 报错行 |
| 前端 404 接口全挂 | backend 未 healthy：`docker compose logs backend`；常见为 PG 口令不一致（.env 改过后 `docker compose down -v`? **勿带 -v 除非弃库**）→ `up -d --force-recreate backend` |
| 对话流式断流/生成 30s 报错 | 本包 nginx 已配 `buffering off/300s`；若前面还有企业代理，需在代理层同样放行 |
| 模型调用 Connection refused 且本机开过 Clash/系统代理 | 仅影响**非容器**本地起服：JDK 会注入死代理属性（BUG-30 环境坑）。容器内无此问题；本地调试用 `-DsocksProxyHost= -Dhttp.proxyHost=` 或关掉系统代理 |
| Apple Silicon 上跑交付镜像 | `--amd64` 产物经 qemu 可运行但慢；本机构建验证不带参数即可 |
| 登录报「未授权，请先登录」 | 浏览器 URL 带双重 /api 前缀属已知形态（BUG-33），nginx 已折叠适配；若你自建反代请完全复刻 dev 语义：`rewrite ^/api/(.*)$ /$1`（无条件剥一层，两种前缀形态都靠它） |
| 首登密码错误 | 确认库内 admin 行：`docker compose exec postgres psql -U phoenix -d phoenix -c "select username,status,del_flag from tbl_privilege_user"` |

## 8. 安全红线
- 任何真实密钥/口令禁止提交进仓库（进 git 即视为泄露）；种子仅允许 `sk-xxxxxx` 占位
- pg/redis/backend 均不映射宿主端口，仅 nginx:9080 对外；如需公网暴露，必须自加 TLS 与访问控制（本包 Non-goal）
- 本包 legacy 提示：`docker/entrypoint.sh`、`docker/nginx.conf` 为历史遗留文件，未被 compose 引用，可确认后删除

## 附：本机验证记录（2026-09-30）
- 一键链实测：fresh up → migrator(三步曲+V01~05) → backend(冷启≈40s) → migrator-post → healthy；`sh scripts/verify.sh` 7/7 PASS
- 模型链路实测：admin 首登 ✓；生成（qwen 默认，10.8s，四段 md）✓；SSE 对话（24 事件）✓ —— **测试密钥为临时注入，交付镜像/种子仅含 `sk-xxxxxx` 占位**
- 受限网络基座：`JRE_BASE_IMG` 可指向任何含 java≥21 的本地镜像（实测 ES9=UBI9 + `JAVA_BIN=/usr/share/elasticsearch/jdk/bin/java`），交付默认 temurin:21-jre-alpine
- 升级/回滚演练：删台账行重跑只补该行 ✓；R05↔V05 往返 `is_active` 快照逐字一致 ✓
- 首启顺序建议：**装完先在管理端录入模型密钥与默认标记，再发起首次对话/生成**（首启会按当时库内配置构建智能体，直改库注入密钥后需 `docker compose restart backend` 刷新缓存）
