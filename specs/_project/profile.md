# 项目画像 Phoenix-Agent-Java
> init 生成于 2026-09-27 | 最近刷新: 同前 | 事实变更时更新（Implement 中发现不符顺手更新）

## 基本事实
技术栈: Java 21 / Maven 多模块（无 wrapper，本机 mvn 3.8.6 + `~/.m2/settings.xml`）
- Spring Boot 4.0.0 · Spring AI 2.0.0-M1 · Spring AI Alibaba 2.0.0-M1.1
- MyBatis 4.0.1 + MyBatis-Flex 1.11.7 · PostgreSQL + PgVector · DashScope SDK 2.15.1 · Druid 1.2.22 · Sa-Token(Reactor) · OTel→Langfuse
- 前端: `web-frontend/` vben-admin monorepo（Vue3，pnpm@11.7.0 + turbo）；apps: admin-ui(@vben/web-ele) / mobile-ui / pc-ui(v5.5.2)

模块（根 pom 12 个）: parent(BOM) · data · agent · admin · privilege · platform · common · tool · codegen · flink · rag · kg
- 除 tool/codegen（单层）、flink（api/core/**server**）外均为三层 `api / core / rest`
- **phoenix-data 已是三层**（api/core/rest），非 AGENTS.md 所述单层主应用
- **真实启动模块: `phoenix-admin/phoenix-admin-manager`** → `com.phoenix.admin.PhoenixAgentApplication`（@ComponentScan "com.phoenix"，@EnableScheduling，@EnableTransactionManagement）；AGENTS.md 所称 `phoenix-data/.../DataAgentApplication` 已不存在
- 状态图/NL2SQL 工作流代码现位于 `phoenix-data/phoenix-data-core`

命令:
- 构建 `mvn clean install -Dspring-javaformat.skip=true`（spring-javaformat validate 绑定默认生命周期）
- 格式化 `mvn spring-javaformat:apply`
- 后端测试 `mvn test`（仅 2 个测试类）| 前端 `pnpm test:unit`（vitest）| 前端 lint `pnpm lint`（vsh: eslint/oxlint/stylelint/cspell）
- 本地启动 `mvn spring-boot:run`（-pl 以启动模块 phoenix-admin/phoenix-admin-manager 为准，未实测）
- ⚠ AGENTS.md 指定的 settings 路径 `/Users/liuwenjun/java/doc/maven-setting/dragon-settings.xml` 属他人机器、本机不存在；本机使用 `~/.m2/settings.xml`

SQL/迁移: `sql/all_schema.sql` + `sql/all_data.sql`（PostgreSQL 语法：IDENTITY/JSONB/COMMENT ON），**无 Flyway/Liquibase**，DDL 手工执行
CI: 未发现（无 .github/workflows、.gitlab-ci.yml、Jenkinsfile）
容器: 根目录 `Dockerfile` + `docker/` 存在（多阶段：maven:3.9-temurin-21 构建后端；与 AGENTS.md「无 Dockerfile」不符）
hooks: 根与 web-frontend 的 `lefthook.yml` 均为全文件注释 = **未启用**（AGENTS.md 该项描述属实）

## 既有约定（权威来源，摘录关键条目）
- **AGENTS.md**（描述性条目已过期者以本画像为准，见 §已知技术债）：
  - spring-javaformat 每次构建强制执行，可 `-Dspring-javaformat.skip=true` 跳过
  - 编译启用 `-parameters`（MyBatis 参数名反射依赖）
  - 零 resource 配置文件：全部走环境变量 + `DataAgentProperties`（前缀 `spring.ai.phoenix.data-agent.*`）
  - 双包模式：本地 `com.phoenix.data.*` 引用上游 `com.alibaba.cloud.ai.dataagent.*`（graph-core JAR 提供）
  - 支持的 SQL 方言：MySQL/PostgreSQL/Oracle/SQL Server/H2/Hive/达梦（各 DBAccessor+JdbcDdl）
  - 向量库默认 PgVectorStore
- **docs/**（ARCHITECTURE / DEVELOPER_GUIDE / QUICK_START / ADVANCED_FEATURES / KNOWLEDGE_USAGE / PRIVILEGE）：描述性文档，无规范条款
- 未发现 CLAUDE.md / .cursorrules / docs/conventions/ / README 规范章节
- skill `code-standards`（会话 catalog）：明示为 **BotCloud 工程**规范（代码/接口/数据库/Redis/Feign/定时任务），**非本项目**，未在路由中引用

## 测试基线（I3 实测或标注）
未测（init 时跳过，2026-09-27。原因: 用户选择跳过——后端仅 `phoenix-admin-manager` 下 2 个测试类（ReactAgentTest / HarnessAgentTest），可能依赖外部环境/密钥；之后可随时「补测基线」）
既有失败清单: （待补测后记录）

## 运行时机制实测记录（2026-09-27）
### AgentScope Harness 与 Skill（端到端验证 ✅）
- 依赖 io.agentscope 2.0.0（core / harness / extensions-skill-postgresql-repository / extensions-postgresql-state / extensions-redis）
- Skill 存储：`tbl_harness_skills`（name 唯一 / description / skill_content / source / metadata_json）+ `tbl_harness_skill_resources`（resource_path+content，FK 级联）；应用启动时 `createIfNotExist(true)` 自动建表
- skill_content = 标准 **SKILL.md** 格式：YAML frontmatter（name、description）+ markdown 正文；运行时渐进披露（提示注入 name+description，模型 load_skill 取正文后按指令行动）
- 挂载范围：仅 `RulesHarnessAgent`（制度专家）builder 有 `.skillRepository(postgresSkillRepository)`；`HumanInTheLoop` **未挂**；React/Workflow/SQL 智能体不涉及
- Harness 智能体是 Java 硬编码 @Component（AbstractHarnessAgent 子类，启动 register 写 tbl_data_agent type=harness），非后台可创建类型
- **无 skill 管理入口**（前端与 REST 均无 CRUD）——当前只能手工 INSERT 落库
- 会话入口：`POST /api/admin/harness/chat`（SSE；body: harnessSn/userId/sessionId/message）+ `/confirm` 人工干预续跑
- 实测：手工插 1 条触发式 skill（「报诗」→ 输出标记短语）→ 对制度专家对话 → 模型正确匹配、加载、按模板回复 ✅

### 本地运行环境（当前生效）
- docker 容器组 network=`phoenix-agent`：`phoenix-pg`(pgvector/pg16, 127.0.0.1:5432, db/user/pw=phoenix/phoenix/phoenix) + `phoenix-redis`(redis:7, 127.0.0.1:6379 无密码, 应用用 db8)；数据卷 phoenix_pg_data / phoenix_redis_data
- 启动实测可用姿势：`JAVA_HOME=jdk23 mvn spring-boot:run -pl phoenix-admin/phoenix-admin-manager -Dspring-javaformat.skip=true`（需先 `mvn install -pl … -am` 把兄弟 SNAPSHOT 装入本地仓）；前端 `cd web-frontend && pnpm dev:ele`（5777，/api→8066）
- ⚠ 运维坑：`pkill -f phoenix-admin.jar` **杀不掉** spring-boot:run 拉起的 JVM（命令行不含 jar 名）；停后端用 `lsof -tiTCP:8066 -sTCP:LISTEN | xargs kill`
- ⚠ 登录 401 排查过一次「旧 JVM 僵尸进程监听 8066 吃旧数据」的坑，改库后务必确认监听者身份
- 种子数据事实：tbl_privilege_user 密码=md5("phoenix"+明文)（LoginServiceImpl，注意失败分支误用 OLD_PASSWORD_ERROR=23007 码）；已本地重置全员为 12345678；tbl_data_model_config 内 API Key 为占位符（sk-xxxx），用前需在后台换真实 key

## 已知技术债（不得顺手改；改需另立 spec 并先考古原因）
> 实测发现的缺陷明细与修复状态见同目录 **bugs.md**（B-01~B-08）
- **AGENTS.md 与代码现实大面积脱节**: 入口类、模块表（缺 rag/kg/data 三层/admin-manager）、「无测试」「无 Dockerfile」「无 pre-commit」部分失实、settings 路径指向他人机器（考古: 2026-09-27 init 实测）
- **SQL 无版本管理**: 仅一份全量 `sql/all_schema.sql`，schema 增量无法追溯（改需另立 spec 引入 Flyway 风格）
- **`${revision}` 版本号未解析**: flatten-maven-plugin 在父 POM 被注释，多模块版本占位符存在构建隐患
- **双包名体系**: `com.phoenix.*` 与上游 `com.alibaba.cloud.ai.dataagent.*` 并存，易误判代码归属
- springdoc-openapi 依赖注释未接线；checkstyle/spotless/jacoco 在 POM 声明但未启用
- TODO/FIXME 共 7 处（data-core 的 McpServerConfig/PlanExecutorNode/Nl2SqlServiceImpl 等、privilege 的 LoginHelper/LoginServiceImpl），密度低，暂不构成阻碍

## git 现状
主分支: main（origin/HEAD → main）| 保护: 未发现（本地视角无法确认）| tag: 无
提交风格: 混杂——部分 `feat:`/`fix：`（中英冒号混用），大量无类型纯中文（「优化链接池缓存」「初始化」），有重复提交信息；与 conventional commits 差距大
分支痕迹: 历史合并过 `phoenix-1.1.1-dev` / `phoenix-1.1.1-release` / `phoenix-1.1.2-dev`（版本号 dev/release 双分支模型）
分支模型建议: **B 档**（依据: release 分支痕迹 + 1.1.x 发布节奏；milestone 可映射 releases/vX.Y.Z）

## 规范路由
见项目根 `.specrc.yml`（init I5 生成，本表不重复维护）
