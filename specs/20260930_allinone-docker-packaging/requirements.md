> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-09-30 | 确认人: 陈卓 | 确认日期: 2026-09-30

# 需求：一键部署 Docker 交付包（allinone-docker-packaging）

## 背景与现状事实（实测依据，2026-09-30）

- 后端=单体 fat jar `phoenix-admin.jar`（~432MB，含全部模块），端口 8066，WebFlux+SSE；健康检查 `GET /echo/ok`
- 前端=Vite monorepo（`web-frontend/apps/admin-ui`），构建产物纯静态；开发期经 vite 代理 `/api`
- 依赖服务：`phoenix-pg`（镜像 `pgvector/pgvector:pg16`，宿主映射 127.0.0.1:5432）+ `phoenix-redis`（redis:7，127.0.0.1:6379）
- 配置：`application.yml` 默认 `profiles.active: test`；`application-test.yml` 硬编码 `127.0.0.1` 数据源/Redis（容器内需改写）
- 数据库初始化：`sql/all_schema.sql`（含 `CREATE EXTENSION vector`）+ `sql/all_data.sql`（1973 条 INSERT 种子）+ 各版本 `releases/vX.Y.Z/sql/V*.sql`
- **已知坑（必须在部署包里解决的）**：
  - `tbl_harness_skills` 由应用首启自建，升级件需**应用启动后重跑一次**（UPGRADE 注意事项②）
  - `all_data.sql` 含 **3 处真实 sk- 模型密钥**（安全：不能随镜像/包分发到外部——处置见 Q1）
  - `all_data.sql` **无初始用户**（`tbl_privilege_user` 现库 7 条均为运行期数据）→ 全新装无账号可登录（处置见 Q2）
  - SSE 长流式与 AI 生成 45~90s 反代参数敏感（proxy_buffering/超时/上传体积）
  - 换 EMBEDDING 配置后向量需重建（装后引导页文案，非本包实现）
- 交付目标语义：**"一个 docker 包" = 一个 compose 编排目录/离线 tar，内含 nginx+后端+PG+Redis+初始化器 多容器**；不是单镜像混部（方案评审已定，2026-09-30 对话）
- 无 CI/CD、无 Makefile、无现有 Dockerfile（AGENTS.md/profile 证实）；本地 mvn/pnpm 流程不受影响

## 需求条款（EARS）

### 一、交付形态与编排

- **R-01** WHEN 用户在装有 Docker(≥24, compose v2) 的 Linux(x86_64) 主机执行 `docker compose up -d` THE 部署包 SHALL 按依赖顺序拉起 nginx/backend/postgres/redis/migrator 全套服务，仅 nginx 暴露宿主端口（**默认 9080**，`.env` 可改），其余服务不监听宿主端口（`ss -tlnp` 可验）
- **R-02** THE 部署包 SHALL 提供 `docker/` 目录承载全部新增文件（compose、两个 Dockerfile、nginx 配置、.env.example、init 脚本、README），仓库既有的 `sql/`、`releases/` 以相对路径复用（软链或构建上下文引用），不复制出第二权威
- **R-03** THE 部署包 SHALL 支持离线交付：`docker save` 产出单一镜像 tar（含全部自研镜像与基础镜像）+ `load-and-run.sh`，在完全断网（除模型 API 出网，见 A-03）主机上可完成部署与升级

### 二、配置与初始化

- **R-04** THE 部署包 SHALL 将所有环境相关配置（DB/Redis 主机与口令、JVM 堆、暴露端口、TZ/locale）经 `.env`/环境变量注入；镜像内不含任何明文口令与模型密钥
- **R-05** WHEN 全新空数据卷首次启动 THE migrator SHALL 依次执行 `all_schema.sql → V1.2.0_01~05`（序号权威=releases 目录，执行序写入代码常量清单文件），并保证重复执行幂等（脚本本身已幂等，重跑 no-op 属预期）
- **R-06** WHEN backend 首次健康检查通过 THE 部署包 SHALL 自动重跑 `V1.2.0_01`（补 `tbl_harness_skills.status` 列，解决首启自建表顺序坑），失败则明确报错退出而非静默
- **R-07** THE `sql/all_data.sql` 种子 SHALL 内置初始账号 **admin / 123456**（口令散列算法以工程现行登录校验实现为准，plan 期核实后取正确哈希，保证首登必过）；README 显著标注「装后首次登录立即改密」
- **R-08** IF 检测到数据卷已有数据（非首次启动）THEN THE 部署包 SHALL 跳过 `all_schema/all_data` 基线初始化，仅按需执行尚未跑过的 releases 增量件（版本号记录表或哨兵对象判断，机制见 plan），不覆盖既有数据

### 三、运行时正确性

- **R-09** THE nginx SHALL 按本项目的长连接特性配置：`/api` 反代 `proxy_buffering off`、`proxy_read_timeout ≥ 300s`、`client_max_body_size ≥ 50m`（技能 ZIP）、SPA history 路由 fallback、静态资源 gzip
- **R-10** THE backend 容器 SHALL 设置 `MaxRAMPercentage` 与内存 limits（默认 backend 3G/pg 1G/redis 256M 量级，`.env` 可调），并设 `TZ=Asia/Shanghai`
- **R-11** THE 部署包 SHALL 为各服务定义 healthcheck（backend 用 `/echo/ok`，pg 用 `pg_isready`，redis 用 PING），compose `depends_on` 以 healthy 为条件；"部署成功"的判定=全部服务 healthy 且经 nginx `GET /echo/ok` 返回 200
- **R-12** THE 数据 SHALL 全部落 named volumes（pgdata、redis、backend 如需落盘的技能/日志目录）；`docker compose down`（不带 `-v`）后重新 up，业务数据零丢失

### 四、升级与回滚衔接

- **R-13** THE 部署包 SHALL 提供升级入口：替换镜像标签后按 `releases/vX.Y.Z/UPGRADE.md` 的序号清单执行增量 SQL（脚本封装，不要求操作者手敲），与里程碑发版机制（M3 产物）直接衔接
- **R-14** THE 部署包 SHALL 提供备份/恢复入口：`backup.sh`（pg_dump -Fc + 卷清单说明）与恢复步骤文档；回滚指引引用 UPGRADE.md 逆序章节，不重复造第二套流程

### 五、文档与不回归约束

- **R-15** THE `docker/README.md` SHALL 覆盖：一键起停、离线部署、装后首登、配置项表、备份恢复、升级到 v1.2.x、常见故障（含 JVM 代理注入史坑的说明：容器内不适用但本地混调会遇到）
- **R-16** WHILE 本地 `mvn`/`pnpm dev` 开发流程不变 THE 部署包 SHALL 为纯增量（新增 docker/ 目录与两个 Dockerfile），不修改任何业务代码；若实施中发现必须改码（如配置键），须回本 spec 修订条款而非顺手改
- **R-17（例外声明）** `sql/all_data.sql` 中的 **3 处真实模型密钥 SHALL 全部替换为占位符 `sk-xxxxx`**（脱敏）：这是 R-16"纯增量"的唯一例外，属数据卫生修复；替换后 `grep -c 'sk-' sql/all_data.sql` 的命中必须全部等于 `sk-xxxxx` 占位符；**git 历史中的旧密钥视为已泄露，3 个密钥应轮换**（建议动作，登记为已验证事项之外的运维提示，见 AC-07/README）

## 验收标准（AC）

- **AC-01** 干净 Linux 主机（仅装 docker）：`git clone`→`cd docker`→`docker compose up -d`→5 分钟内 `docker compose ps` 全 healthy、浏览器打开暴露端口出登录页、`/echo/ok` 200
- **AC-02** 装后用 **admin / 123456** 直接登录成功（R-07），进入智能体列表；改密后旧口令失效
- **AC-03** 容器环境内完成一次 SSE 对话（流式吐字不断流）与一次 AI 生成（≤90s 返回描述+md 四段），日志可见 `默认对话模型: configId=`
- **AC-04** `docker compose down` + `up -d` 后：登录仍可用、既有智能体/技能/模型配置全在（零丢失）
- **AC-05** 离线机（无外网 registry）：`load-and-run.sh` 导入 tar 后完成同等部署（模型 API 可达前提下）
- **AC-06** 升级演练：v1.2.0 卷 → 换镜像 → 增量 SQL 脚本执行 → 功能复验；再按 R1.2.0 逆序回滚 → 服务可用
- **AC-07** `ss -tlnp` 核验仅 nginx:9080 对外；`sql/all_data.sql` grep 结果仅含 `sk-xxxxx` 占位符（无真实密钥）；README 含"旧密钥已入 git 历史、建议轮换"提示
- **AC-08** 重跑 `docker compose up -d` 三次（模拟重复初始化）无报错、数据不变

## Non-goals（明确不做）

- 不做 k8s/helm、不做 CI/CD 自动构建推仓（registry 前缀留 .env 钩子即可）
- 不做单镜像全家桶（方案评审已否决，见背景）
- 不做 TLS 证书管理（README 给自签示例，产品化 HTTPS 另立项）
- 不做监控/日志栈（Prometheus/Loki 等）；不装 x86 之外的架构镜像（见 Q4）
- 不改任何业务代码/接口/库表（R-16）

## Q 决议记录（2026-09-30，用户答复）

- **Q1 → 脱敏**：真实 sk- 密钥替换为占位符 `sk-xxxxx`（落 R-17；现网库不受影响，仅种子文件；旧密钥入 git 历史→README 建议轮换）
- **Q2 → 种子内置 admin/123456**（落 R-07；README 提示首登改密）
- **Q3 → 9 开头端口**：默认 **9080**（`.env` 可改，落 R-01/R-11/AC-07）
- **Q4 → 未明确表态，按建议默认**：仅交付 `linux/amd64`（确认本需求时如有异议一并提出）
- **Q5 → 移交 plan 期核实**：新环境种子完整性（菜单/权限是否已全在 all_data；缺则回本 spec 补 R 条款重确认）
- **版本**：用户明示 **v1.2.1**（A-04 定稿）

## 假设

- A-01 目标主机内存 ≥4G、磁盘 ≥20G（JVM 3G + PG + 镜像约 2.3G）
- A-02 Docker ≥24 且含 compose v2 插件；有 root 或 docker 组权限
- A-03 部署完成后，模型调用需出网可达 dashscope.aliyuncs.com / api.deepseek.com（完全断网环境功能降级为"可登录不可生成"，属预期）
- A-04 交付包版本随产品版本走，**定稿 v1.2.1**（用户 2026-09-30 确认；语义=交付物工程+数据卫生修复，产品代码零变更，取 PATCH）
- A-05 现网 `phoenix-pg`/`phoenix-redis` 两个开发容器与本交付包互不干扰（compose project name 隔离，宿主端口错开或默认不映射）
