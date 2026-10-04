> 版本: v1.1.0 | 状态: 已确认 | 更新: 2026-10-01 | 确认人: 陈卓 | 确认日期: 2026-09-30

# 技术方案：一键部署 Docker 交付包（对齐 requirements v1.1.0）

## 0. 总览与拓扑

```
宿主 :9080(.env 可改) ┌────────────────────────── phoenix-net（内网，其余服务零宿主端口）──────────┐
                      │  [nginx]──/api──►[backend:8066]──►[postgres:5432] [redis:6379]           │
                      │  静态 dist        temurin21-jre     pgvector/pg16   redis:7              │
                      │        ▲               ▲                ▲                                │
                      │  [migrator] 首启基线+增量；[migrator-post] backend healthy 后补跑 V01     │
                      └──────────────────────────────────────────────────────────────────────────┘
交付物：docker/ 目录（compose+Dockerfile+conf+init+scripts+README）；离线态=镜像 tar.gz+本目录
```

卷：`pgdata`、`redisdata`、`applogs`（named volumes，R-12）。project name `phoenix-release`（与开发容器 `phoenix-pg/phoenix-redis` 完全隔离，A-05）。

## 1. 镜像构建（两个自研镜像，基础镜像全部 pin 版本）

**backend.Dockerfile**（多阶段）
- build：`maven:3.9-eclipse-temurin-21`，`COPY . /src`（`.dockerignore` 排除 `.mvn-home/ web-frontend/node_modules/ diagrams/ .git`），`RUN mvn -q package -DskipTests -Dspring-javaformat.skip=true -pl phoenix-admin/phoenix-admin-manager -am`（BuildKit cache mount `/root/.m2`；内网仓经 `--build-arg MAVEN_SETTINGS_FILE=docker/maven/settings.xml` 注入，R-15 文档化）
- run：`eclipse-temurin:21-jre`（jar 目标字节码=Java21，工程实际用 JDK23 编译——运行时用 21 前需实施期以 `mvn -V`+字节码 major 验证，风险①）；`ENTRYPOINT java ${JAVA_OPTS} -jar /app/phoenix-admin.jar`
- 环境注入（不烘配置）：`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`、Redis uri（Spring relaxed binding，实施 T-02 逐键验证 `--dry-run` 不可行→以起服后日志/接口反证）
- `JAVA_OPTS` 默认 `-XX:MaxRAMPercentage=70 -XX:+UseContainerSupport -Duser.timezone=Asia/Shanghai`（R-10）

**frontend.Dockerfile**（多阶段）
- build：`node:20` + `corepack enable pnpm`，workspace 根上下文，`pnpm install --folding-lockfile`→`pnpm --filter admin-ui build`（若仓库无 pnpm-lock 则 `--no-frozen-lockfile` 并在 README 钉死后果，实施 T-03 核实）
- run：`nginx:1.27-alpine`，`COPY dist → /usr/share/nginx/html`，`COPY docker/nginx/phoenix.conf → /etc/nginx/conf.d/`

**跨架构**：开发机 Apple Silicon → `docker buildx build --platform linux/amd64 --load`（Docker Desktop 自带 qemu）；交付仅 amd64（Q4 默认）。

## 2. nginx 运行参数（R-09）
```
client_max_body_size 50m;                    # 技能 ZIP
location /api { proxy_pass http://backend:8066;
  proxy_buffering off; proxy_request_buffering off;
  proxy_read_timeout 300s; proxy_send_timeout 300s;
  proxy_set_header Host $host; X-Real-IP/X-Forwarded-* }
location / { try_files $uri $uri/ /index.html; }   # SPA history
gzip on; gzip_types text/css application/javascript application/json;
```
SSE（`/api/stream/search`）与 90s 生成（R-01 实测 AC-03）依赖 `buffering off + 300s`。

## 3. 初始化与增量（R-05/06/08/13，migrator 设计）

- **镜像**：`postgres:16-alpine`（只当 psql 客户端用）
- **挂载**：`sql/`（基线+种子）与 `releases/`（V/R 件）只读挂入，脚本引用**唯一权威路径**（R-02，不复制第二份）
- **哨兵与台账**：`migrator` 先查 `tbl_phoenix_release`（本包自建的小表：`seq text PK, file text, applied_at timestamptz`）
  - 表不存在 ⇒ 首启：**`all_data.sql`（完整基线：结构+数据+序列，实测空库 0 报错——R-05 v1.2.0 勘误，原 schema→data 叠加顺序必炸）→ `10_seed_admin.sql` → 遍历 releases/v1.2.0/sql/V1.2.0_01..05**，每件一个事务，成功即 INSERT 台账；失败即退出非零（compose 可见）
  - 表存在 ⇒ 非首启：跳过基线，只执行台账中缺失的 V 件（升级即"新版本 releases 目录挂入后重跑一次 up"，R-13）
- **两拍顺序**（坑②）：`migrator-post`（同镜像）`depends_on: backend: condition: service_healthy`，重放 `V1.2.0_01`（脚本自带存在性容错，harness 表已被应用建出后补齐 status 列），不写台账（幂等件，重复执行 no-op）
- **非首启+新库并存的裁决**：以哨兵表为唯一判据，不用"目录里有没有文件"之类启发式

## 4. compose 服务契约（R-01/04/11）

| 服务 | 镜像 | healthcheck | 依赖 | 端口 |
|---|---|---|---|---|
| postgres | `pgvector/pgvector:pg16`（pin digest） | `pg_isready -U phoenix` | — | 无宿主映射 |
| redis | `redis:7-alpine` | `redis-cli ping` | — | 无 |
| backend | 自研 | `GET /echo/ok`（10s×60 次，冷启含建表） | pg/redis/migrator healthy\|completed | 无（nginx 代理） |
| migrator | postgres:16-alpine | 一次性（completed_successfully） | postgres | — |
| migrator-post | 同上 | 一次性 | backend healthy | — |
| nginx | 自研 | `wget -qO- localhost/ ≥200` | backend | `${PHOENIX_HTTP_PORT:-9080}:80` |

`.env.example`：`PHOENIX_HTTP_PORT=9080 PG_PASSWORD=phoenix REDIS_PASSWORD= IMAGE_TAG=v1.2.0 JAVA_OPTS=… TZ=Asia/Shanghai`；镜像内零口令（build arg 不落层，运行注入，R-04）。

## 5. 首登种子（R-07，实测事实）
- 登录校验：`SecureUtil.md5(LoginConstant.PASSWORD_SALT + 明文)`，`PASSWORD_SALT="phoenix"`（`LoginServiceImpl:68`）
- `admin/123456` 哈希=`f1c457c84af9bc85acaeb64bee218755`
- `docker/init/10_seed_admin.sql`：`INSERT INTO tbl_privilege_user(username,password,...) SELECT 'admin','f1c4…' WHERE NOT EXISTS(...)`；**所需列与非空约束、以及 admin 是否还需角色/组关联才能见到菜单**——现库 admin 行的关联结构在 T-05 里照抄（含 `tbl_privilege_group*`/ACL 关联），这是方案里唯一"实施期核实"点，若关联缺失导致首登无菜单，AC-02 会暴露
- 改密路径复用现有「修改密码」接口（已存在 `PrivilegeUserServiceImpl`）

## 6. 离线交付与运维脚本（R-03/14）
- `docker/scripts/build.sh`（buildx 双镜像+拉基础镜像）→ `save-offline.sh`：`docker save -o phoenix-v1.2.0-images.tar pgvector redis:7-alpine postgres:16-alpine nginx:1.27-alpine backend:v1.2.0 frontend:v1.2.0` + `sha256sum.txt`（约 2.3G，gzip 后 ~1.3G）
- `load-and-run.sh`（目标机）：`docker load -i` → `docker compose up -d`
- `backup.sh`：容器内 `pg_dump -U phoenix -Fc phoenix > backup/phx_$(date).dump` + 卷清单说明；`upgrade.sh <vX.Y.Z>`：校验 releases 目录存在→compose 重跑 migrator→migrator-post；回滚指引=引用 `releases/vX.Y.Z/UPGRADE.md` 逆序章节（不重复造流程，R-14）
- README（R-15）八节：一键起停/离线部署/首登改密/配置表/备份恢复/升级/故障排查/安全红线

## 7. 关键决策与被否方案

| # | 决策 | 被否的替代 & 理由 |
|---|---|---|
| D1 | compose 多容器交付包 | 单镜像 s6 混部——否（评审已决：备份/升级/健康检查全面劣化） |
| D2 | migrator 独立一次性服务 + 哨兵台账表 | entrypoint 里塞初始化——否（与应用启动耦合、失败不可见、重跑语义混乱）；Flyway 依赖——否（引新依赖违反零改动约束 R-16，releases 机制已是权威） |
| D3 | admin 种子放包内 10_seed_admin.sql | 改仓库 all_data.sql——否（R-16 精神；且 all_data 是 pg_dump 产物、手改易碎） |
| D4 | 镜像内不含 profile 专属 yml，全走 env | 烘一份 application-docker.yml 进镜像——否（口令进层风险；env 更贴 Spring 习惯）。回退方案：若发现某键 relaxed binding 覆盖不了（jetcache uri 等），以 `SPRING_CONFIG_ADDITIONAL_LOCATION=/config/` 挂载卷外置 yml（仍在包内不在镜像内） |
| D5 | `V1.2.0_01` 由 migrator-post 每启动重放一次 | 只在首启跑两拍——否（用户可能只起应用不管库的中间态；幂等件反复补无代价） |
| D6 | 端口默认 9080 明文 http | 443+自签——否（Q3 用户口径 9 开头端口；TLS 产品化出 Non-goals） |
| D7 | jar 用工程现行 `mvn package` 产物路径 | 依赖本地预构建 jar 拷进镜像——否（不可复现、构建机环境差异进交付物；但 build.sh 提供 `--skip-build` 复用本地 jar 作应急通道） |

## 8. 风险登记（各配缓解，验证任务见 tasks）
1. **JDK23 编译产物跑在 JRE21**：若字节码/preview 特性超 21 → 运行层直接上 `eclipse-temurin:23-jre`（T-02 首步即验）
2. **env 覆盖不全**（jetcache/redis uri 等特殊键）：T-02 起服日志回显 + 接口反证；兜底= D4 外挂 yml 卷
3. **前端构建依赖锁缺失/差异**：T-03 先跑 `pnpm build` 本机验证→容器复跑比对产物大小
4. **admin 关联数据不足致"能登录无菜单"**：T-05 从现库 dump 真实 admin 行与组/ACL 关联生成种子（含防泄密：只导结构行，不带 sk）
5. **SSE 在 nginx 下断流**：AC-03 实测；参数已按 WebFlux 惯例给全（§2）
6. **qemu 跨架构构建慢**（分钟级→十分钟级）：接受；README 说明
7. **pgvector 基础镜像源**：pin `pgvector/pgvector:pg16` digest；内网环境走 build.sh 预拉
8. **compose 变量注入遗漏**（R-04 零明文红线）：T-09 用 `docker history`+镜像内 grep 断言（AC-07 同源）

## 9. 与既有机制的衔接
- 升级件序号权威在 `releases/v1.2.0/`（M3 已冻结）；新包只是**执行器**，不产生新 SQL（除哨兵表与 admin 种子，均在包内）
- 里程碑：经用户 09-30 明示改为**挤入 v1.2.0**（解冻重走 M2，v1.2.1 不再单独立项）；哨兵台账表为部署包私有（tbl_phoenix_release），不登记进 releases 序号体系（避免版本语义混淆——它属于包机制而非产品升级件，此点若你认为应入库 releases 请在确认时说）
- 测试口径沿用项目无测试基建现实：AC-01~08 以真实部署/接口/断言输出为证据
