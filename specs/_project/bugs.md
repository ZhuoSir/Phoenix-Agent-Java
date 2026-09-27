# 缺陷清单 Phoenix-Agent-Java
> 首建：2026-09-27（本地部署 + Skill 验证过程实测发现）| 每条附证据；修复需逐条确认后动手
> 勘误：早前会话中两条口头推断经核实**不成立**，未列入——① buildLoginResult 对 NULL 密码 NPE（实际只走"用户名或密码错误"分支）② yml 有 server.port=3333（文件与日志中均无此值）

## P1 — 阻塞/数据类

### B-01 `all_schema.sql` 缺 5 个序列 → 全新环境导入必失败
- **现象**：demo 表 `tbl_data_categories / order_items / orders / products / users` 建表报 `relation "..._id_seq" does not exist`（CREATE TABLE 引用 `nextval()` 但全文没有对应 CREATE SEQUENCE）
- **影响**：任何全新初始化必复现；本次已在运行容器内手工补齐（**SQL 文件未修**），重导仍会炸
- **附带**：这批表 + `tbl_tmp_*` 疑似早期 demo 遗留，应用代码零引用，可考虑整体移出种子
- **证据**：`sql/all_schema.sql` 1590 行区段；本次导入日志
- **修复方向**：文件内补 `CREATE SEQUENCE`（或改 IDENTITY）；另立 spec 清理 demo 遗留

### B-02 HumanInTheLoop 智能体未挂 skillRepository（同框架行为不一致）
- **现象**：两个 harness 智能体里只有 `RulesHarnessAgent` 能加载技能，`HumanInTheLoop` 静默加载不到
- **根因**：`HumanInTheLoop.createHarnessAgent()` 的 builder 缺 `.skillRepository(postgresSkillRepository)` 一行（grep 计数 1 vs 0）
- **修复方向**：补一行；属小 bugfix 可直接修

## P2 — 登录/账号体系（本次排障重灾区）

### B-03 前台账号创建时密码无必填校验 → 制造永久无法登录的"死账号"
- **现象**：后台"前台账号"新建表单密码留空也能保存成功；此后任何密码登录都返回「用户名或密码错误」，无"未设密码"提示
- **根因**：前端 `account-info/data.ts:186` password 字段无 required 规则（placeholder「留空则不修改」是编辑场景文案被带到新建）；后端 `AccountInfoServiceImpl.save():298` `isNotBlank` 才加密，NULL 直接入库
- **实测**：chenzhuo 前台账号 12:19 创建后 password=NULL
- **修复方向**：新建必填（前端 rules + 后端 create 校验），编辑保留"留空不改"

### B-04 双账号体系：两张表、状态语义相反、密码互不相通
- 管理端 `POST /api/privilege/auth/login` → `tbl_privilege_user`（status **1=禁用**）
- 前台 `POST /auth/login` → `tbl_platform_account_info`（status **0=禁用**）
- 同名账号是两条独立记录，后台改了 A 表密码、前台登 B 表——本次部署在此耗费大量排障时间
- 列表接口还把 password 脱敏成 null（`AccountInfoServiceImpl:108`），界面永远"看起来没密码"，加剧误判
- **修复方向**：属设计问题 → 建议记技术债另行立项（统一账号中心或至少加同页提示）

### B-05 登录密码错误返回了「原密码错误」的错误码
- `LoginServiceImpl:69-71`：密码比对失败返回 message=`PASSWORD_ERROR(密码错误)` 但 code=`OLD_PASSWORD_ERROR(23007)`
- 实测多次：`{"code":"23007","msg":"密码错误"}`——码文不符，误导排障
- **修复方向**：一行改 `PASSWORD_ERROR.getCode()`；小 bugfix

## P3 — Skill / Harness 能力缺口

### B-06 Skill 无任何管理入口，且技能池全局共享
- 前端 0 页面、后端 0 REST；只能手工 INSERT `tbl_harness_skills`(+resources 表)
- 表无 agent 维度字段 → 所有 harness 智能体共享同一技能池
- `phoenix.agent.skillPath` 属性定义了但全仓无引用（死配置）
- **修复方向**：缺失功能非 bug → 立项 spec「智能体 Skill 管理」

### B-07 harness 的 shell 能力与远程文件系统硬绑互斥
- `ShellExecuteTool` 只接受 `AbstractSandboxFilesystem`；项目用的 `RemoteFilesystemSpec`(redis/pg) 只实现 `AbstractFilesystem` → 想用脚本类技能必须换 Local FS 且去掉 `disableShellTool()`，两处都写死在 Java builder 里，无配置开关
- **修复方向**：把 filesystem 策略与 shell 开关提为 `phoenix.agent.*` 配置项（配合权限规则）

### B-08 `application-test.yml` 与实际部署环境不一致
- datasource `password: 123456`（本机容器为 phoenix）；注释里的 maven settings 路径指向他人机器
- AGENTS.md 宣称「零 resource 文件」，本模块实际有 application.yml + application-test.yml（profile=test 默认激活）
- **修复方向**：改文档或改配置二选一，另议

### B-09 前台与 harness 智能体无对话通道（已被 agent-skill-management 修复）
- **现象**：前台对话页 auth 存在 harness 分支，但指向 `/api/front/harness/chat` —— 后端**无此端点**，B-09 即前台侧通道缺失
- **状态**：✅ 已由 `specs/20260927_agent-skill-management` 的 T-11/T-14 修复：后端新增 `/platform/harness/chat`（前台身份+组可见性+技能三重校验），前端 transport 改指该端点
- **注**：requirements.md R-09 引用的 B-09 即本条（原 bugs.md 漏登记，2026-09-27 补记）

### B-10 后台新建智能体 type 为空，且无类型选择入口
- **现象**：管理端「新建智能体」抽屉的提交 payload 不含 `type` 字段（`agent-create-drawer.vue handleSave`），`tbl_data_agent.type` 无 DB 默认值 → 新智能体 type=NULL；列表类型列显示空白，语义不明（用户实测：新建后类型概念不清）
- **连带影响**：harness 类型**无法通过后台创建**——它由 Java `@Component extends AbstractHarnessAgent` 启动时注册，运行时经 `HarnessStaticLoader` 按 sn 取内存实例（缺失即 `NoSuchElementException`）。因此**不要把库里某行 type 直接改成 harness**，会得到无法运行的坏数据
- **修复方向**：① 新建时明确默认 type（如 sql）并在 UI 展示类型；② 若要支持"后台可创建 harness 智能体"，需把 harness 构建参数（sysPrompt/工具/模型）数据驱动化 + 动态注册 → 属新功能，另立 spec

### B-11 前台 HITL 确认接口缺失（前端调用 404）
- **现象**：`views/front/chat` 链路的人工确认走 `POST /api/front/harness/confirm`（`api/front/chat.ts:350` `confirmFrontHarnessChat`），但**后端全仓库无此端点**（`grep -rn "harness/confirm" --include=*.java` 零命中；后台为 `/api/admin/harness/confirm`，前台控制器只有 `getMySkills`/`chat`）
- **影响**：前台对话一旦触发需要确认的工具调用（HITL），确认按钮必然失败（404）——spec-1 前台通道遗留，非本期引入
- **修复方向**：在 `FrontHarnessController` 增加 `/harness/confirm`（复用 `HarnessChatService.confirmStream`，带组可见性校验）；或前台直接把确认请求打到后台确认端点（需权限口径确认）。本期（dynamic-agent-types）不修，已登记

### B-12 SqlSecurityValidator 子串匹配误杀只读查询（本期已修）
- **现象**：`phoenix-tool/.../SqlSecurityValidator.validate` 用 `upperSql.contains(keyword)` 做危险关键字判定 → `create_time`、`update_time`、`delete_flag`、`last_update` 等**普通列名/表名**一律命中 `CREATE`/`UPDATE`/`DELETE`，只读 SELECT 被误判为危险语句
- **影响面**：任何取数/问数链路的只读 SQL 都会被大面积误杀（本期 T-08 取数工具一上线即暴露）；`BpmToolSearch` 同受影响
- **修复**：改为**整词匹配**（`\b(...)\b`）并先剔除字符串字面量/引用标识符；真实拦截不放宽
- **验证（jshell 直调，JDK23）**：`select create_time, update_time from t` → SAFE；`select delete_flag, last_update from t` → SAFE；`WITH ... select` → SAFE；`DROP TABLE x` → BLOCKED；`select * from t; delete from t` → BLOCKED[DELETE]；`UPDATE t SET a=1` → BLOCKED；`select ... where b='drop table x'` → SAFE（字面量不误杀）

### B-13 embedding 模型 404（✅ 已解决，2026-09-27）
- **现象**：模型配置「测试」一直 404。**两个独立原因**：
  1. **base_url 多带了一段 `/v1`**：应用用 Spring AI `OpenAiApi`，它会在 base_url 后自动拼默认路径 `/v1/embeddings`；配置写成 `https://dashscope.aliyuncs.com/compatible-mode/v1` → 实际请求 `…/compatible-mode/v1/v1/embeddings` → 404。对照：CHAT 配置用的是 `https://api.deepseek.com`（不带 `/v1`）所以正常。
  2. **模型名不被 OpenAI 兼容模式支持**：`qwen3-vl-embedding` → `404 {"error":{"message":"Unsupported model `qwen3-vl-embedding` for OpenAI compatibility mode.","code":"model_not_supported"}}`
- **实测矩阵**（同一 key，直连 + 经应用测试接口双向验证）：

  | base_url | model | 结果 |
  |---|---|---|
  | `…/compatible-mode/v1` | `qwen3-vl-embedding` | 404（双重错误） |
  | `…/compatible-mode/v1` | `text-embedding-v4` | 404（仅 base_url 问题） |
  | `…/compatible-mode` | `qwen3-vl-embedding` | 404（仅模型名问题） |
  | `…/compatible-mode` | `text-embedding-v4` | ✅ 成功 |

- **处理**：已把 id=6 改为 `base_url=https://dashscope.aliyuncs.com/compatible-mode` + `model_name=text-embedding-v4`（`text-embedding-v3` 同样可用；`text-embedding-v2` 固定返回 1536 维不可用——向量表是 `vector(512)`，而代码固定请求 `dimensions(512)`，v4/v3 都支持）。原值：`…/compatible-mode/v1` + `qwen3-vl-embedding`。
- **验证**：应用「测试」→ `连接测试成功！模型可用。`；真实写入链路 → `Schema初始化成功`，`tbl_vector_store_simple_data` 出现 agentId=25 的 28 条 512 维向量。

### B-16 三张 Spring AI 向量表缺主键 → ON CONFLICT 插入必失败（✅ 已修，2026-09-27）
- **现象**：embedding 修好后 `POST /api/agent/{id}/datasources/init` 仍失败：`BatchUpdateException: INSERT INTO public.tbl_vector_store_simple_data …` → `PSQLException: there is no unique or exclusion constraint matching the ON CONFLICT specification`
- **根因**：`sql/all_schema.sql` 建的 `tbl_vector_store_simple_data` / `tbl_vector_store_rag` / `tbl_vector_store_user_memory` **只有 HNSW 向量索引、没有主键/唯一约束**，而 Spring AI `PgVectorStore` 的 upsert 依赖 `ON CONFLICT (id)`。（对照：AgentScope 自建的 `tbl_harness_vector_store_knowledge` 有主键，正常。）
- **影响**：所有 schema/知识文档写入向量库的路径**全部不可用**（不只是深度分析）→ 也解释了为何 `/api/agent/{id}/datasources/init` 从来没成功过
- **修复**：① 运行库三表补 `PRIMARY KEY (id)`（幂等 DO 块，已执行并复核 3 条 pkey）；② **基线 `sql/all_schema.sql` 一并补齐**（按该文件既有的「末尾 ALTER 块加主键」风格追加 3 条语句）——空库重放验证：报错数与基线一致（64 处，均为 B-01 既有问题，无一与向量表相关），且三张向量表主键建成

### B-17 图链路在非 HTTP 调用方取 Sa-Token 登录态直接抛异常（✅ 已修，2026-09-27）
- **现象**：B-13/B-16 修好后，深度分析报 `SaTokenContext 上下文尚未初始化`（图内 `handleNewProcess` → `builerLoginVo()` → `StpUtil.getSession()`）
- **根因**：`GraphServiceImpl.builerLoginVo()` 无条件取当前登录态；对话智能体的深度分析工具运行在 AgentScope 工具线程（`boundedElastic`），Sa-Token 上下文不随行 → 抛异常打断整条图链路。MCP 工具回调等非 HTTP 调用方同理
- **修复**：`builerLoginVo()` 捕获无上下文异常并降级为空串（与原 `loginVO == null` 分支同义），不再打断图执行
- **验证**：重跑深度分析 → `深度分析完成: agentId=25, datasourceId=11, elapsedMs=93485, chars=6024`，产出按 type 分组统计 + 时间分布归因的完整报告（图链路真实执行，非取数工具降级）

### B-14 删除智能体残留孤儿数据（运行配置/技能绑定/组授权）
- **现象**：`DELETE /api/agent/{id}` 只删 `tbl_data_agent` 行；实测删除智能体 29 后，`tbl_data_agent_runtime_config`（1 行）、`tbl_data_agent_skill_info`（1 行）、`tbl_platform_group_agent_info`（1 行）**全部残留**，成为孤儿数据
- **影响**：运行配置残留会在日后新建同 id 智能体时误命中（id 自增不会复用，但孤儿行长期占用/污染统计）；组授权残留会随历史累计
- **修复方向**：删除智能体时级联清理三张关联表（或在各表的查询侧统一加 `exists` 校验）；本期（dynamic-agent-types）不修，已登记；本次回归的孤儿行已手工清理

### B-15 智能体列表关键字搜索在 PG 下 500（本期已修）
- **现象**：`GET /api/agent/list?keyword=xxx` → `500 {"message":"服务器内部错误"}`，日志 `PSQLException: ERROR: could not determine data type of parameter $1`，SQL 为 `name LIKE CONCAT('%', ?, '%')`
- **根因**：PostgreSQL 无法从 `CONCAT` 推断未定类型的参数
- **修复**：`AgentMapper.searchByKeyword` 改为 `'%' || CAST(#{keyword} AS text) || '%'`
- **验证**：keyword=制度/巡逻（存量）→ `[]`；智能体/销售 → 命中平台内创建的智能体，均 HTTP 200 正常返回

### B-18 QA/FAQ 类型知识只向量化「问题」，答案不参与检索
- **现象**：以 `type=QA` 创建知识（question+content）后，向量库只有 1 条文档且 `content` = **question**，metadata 仅有 `{agentId, vectorType:agentKnowledge, agentKnowledgeId, concreteAgentKnowledgeType:QA}`，**答案文本没进向量库**。知识库检索工具返回的就是这个问题，模型据此无法作答（实测：模型明确回报「疑似入库时内容为空或索引只存了标题」）
- **影响**：凡是用 QA/FAQ 类型建的知识，走「向量检索 → 作答」链路都拿不到答案（只能用 DOCUMENT 类型）
- **可能修复**：① 知识检索工具在 metadata 含 `agentKnowledgeId` 且类型为 QA/FAQ 时，回查 `tbl_data_agent_knowledge.content` 一并返回；② 或嵌入端把 answer 一起写入文档文本。需产品确认口径
- **对照验证**：改用 `type=DOCUMENT`（上传 .md）后向量内容为文件正文，检索作答正常（见 dynamic-agent-types changelog 追加变更）

### B-19 harness 对话入参缺失时返回 500（✅ 已修，2026-09-27）
- **现象**：`POST /api/admin/harness/chat` 既不传 `agentId` 也不传 `harnessSn` → `HTTP 500 {"message":"服务器内部错误"}`（落到 `loadAgent(null)` 抛 IllegalArgumentException）
- **修复**：`HarnessChatServiceImpl` 显式校验，两者皆缺抛 `InvalidInputException` → 400 + 「agentId 与 harnessSn 至少需要一个」；`confirmStream` 同理
- **验证**：三分支复测（仅 agentId / 仅 harnessSn / 皆缺）分别为 200 / 200 / 400

---

## 工作区遗留状态（非缺陷，处置需确认）
- `RulesHarnessAgent.java` 有**未提交实验改动**（开 shell + LocalFilesystemSpec），已编译进 `.mvn-home`；还原：`git checkout -- phoenix-agent/phoenix-agent-core/src/main/java/com/phoenix/agent/harness/agent/rules/RulesHarnessAgent.java` 后重新 install
- 本期环境改动（dynamic-agent-types 验证所必需）：数据源 id=11「本地测试」的 `host` 由不可达的 `192.168.66.19` 改为 `127.0.0.1`、`connection_url` 同步、`password` 由 `123456` 改为容器实际密码 `phoenix`（改前取数工具一律连接失败/认证失败）
- 库中实验数据：技能 `py-fib-demo`（含 scripts/fib.py）、`phx-poem-weather`；前台账号 chenzhuo 密码现=12345678；两套账号表密码现均=12345678
- 未跟踪：`.mvn-home/`、`.pnpm-store/`、`diagrams/`（建议进 .gitignore）；`AGENTS.md` 有 init 追加段（备份 `AGENTS.md.bak.*`）

## 修复状态跟踪
| ID | 级别 | 状态 |
|---|---|---|
| B-01 | P1 | 未修（容器内已临时补序列；向量表缺主键已另修，见 B-16） |
| B-02 | P1 | 未修 |
| B-03 | P2 | 未修 |
| B-04 | P2 | 不修（设计问题→技术债/spec） |
| B-05 | P2 | 未修（一行） |
| B-06 | P3 | 不修（缺功能→待立项） |
| B-07 | P3 | 未修 |
| B-08 | P3 | 未修 |
| B-09 | P2 | ✅ 已修（agent-skill-management T-11/T-14） |
| B-10 | P2 | 部分解决（dynamic-agent-types：新建强制 type=harness、列表去类型标签；存量 5 个自注册类保留） |
| B-11 | P2 | 未修（前台 HITL 确认端点缺失，前端 404） |
| B-12 | P2 | ✅ 已修（dynamic-agent-types T-08：整词匹配 + 剔除字面量，jshell 8 例验证） |
| B-13 | P2 | ✅ 已解决（base_url 去掉多余 /v1 + 模型名改 text-embedding-v4，实测通过） |
| B-14 | P2 | 未修（删除智能体残留运行配置/技能绑定/组授权孤儿行） |
| B-15 | P2 | ✅ 已修（列表关键字搜索 PG `CONCAT` 参数类型报错 → 改字符串拼接） |
| B-16 | P2 | ✅ 已修（运行库三表补主键 + 基线 all_schema.sql 同步补齐，空库重放验证报错数不变） |
| B-17 | P2 | ✅ 已修（非 HTTP 调用方无 Sa-Token 上下文时降级，不再打断图链路） |
| B-18 | P2 | 未修（QA/FAQ 知识只向量化问题，答案检索不到；DOCUMENT 类型正常） |
| B-19 | P3 | ✅ 已修（harness 对话入参缺失 500 → 400 + 明确提示） |
