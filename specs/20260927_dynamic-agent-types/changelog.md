# Changelog: dynamic-agent-types

## 追加验证（2026-09-27 · 补齐两处遗留证据）
**① T-09 深度分析：并发限流 + 超时文案 实测（原为「未触发路径」）**
- 为可测性把三个阈值改为可配置（默认值不变）：`phoenix.agent.tool.deep-analysis-timeout-seconds`(180)、`deep-analysis-max-chars`(6000)、`deep-analysis-max-concurrent`(2)；`DeepAnalysisTool` 增加全参构造，Contributor 读配置注入。理由：这些阈值与运行环境强相关（LLM 延迟、库表规模），做成编译期常量不合理。
- **并发限流实测**（临时 `max-concurrent=1`，两轮并发发起）→ 第二轮工具返回 `已有 1 个深度分析在进行中`，模型转述原话：「返回提示"已有 1 个深度分析在进行中"」
- **超时实测**（临时 `timeout=8`）→ 工具返回 `深度分析超时（超过 8 秒），请把问题拆解得更聚焦一些后重试`，日志 `深度分析超时: agentId=25, threadId=tool-deep-25-<uuid>, timeoutSeconds=8`，模型转述原话含 `深度分析超时（超过 8 秒）`
- 验证后已按默认值（180s / 2 并发）重启

**② 知识库按智能体隔离：两智能体各挂不同知识、各自只命中自身范围**
- 建两个测试智能体（31=A 范围 / 32=B 范围），各配 `knowledgeEnabled=1, topK=5, threshold=0.3`
- A 知识文档正文写「年假 **15 天**；满 10 年 20 天（A 范围专属）」，B 写「年假统一 **30 天**（B 范围专属）」；同一问题分别问两个智能体
- 结果：**A 答 15/20 天并声明「该文档标注 A 范围专属」；B 答 30 天并声明「仅来自本次检索到的 1 条文档」**；工具日志分别 `知识库检索: agentId=31, topK=5, threshold=0.3` 与 `agentId=32, …`（范围与参数都来自各自配置）
- 向量库侧证据：`tbl_vector_store_simple_data` 中 `agentId=31 → agentKnowledgeId=22`、`agentId=32 → 23`，正文各自独立
- **过程中发现 B-18**：`type=QA` 的知识只向量化「问题」、答案不进向量库（模型因此答不出），改用 `type=DOCUMENT`（上传 .md）后正常。已登记 B-18
- 测试数据已清理：智能体 31/32、其运行配置/知识行/向量行全部删除（顺带手工清理 B-14 类孤儿行）

## 追加变更（2026-09-27 · 合并后 · 用户报「向量模型测试一直 404」排查）
用户报「配置的向量模型测试一直 404」，排查出**三个独立问题**并全部修复，最终把此前被卡的 T-09 深度分析**端到端跑通**：

1. **B-13 embedding 配置两处错误**（用户配置，已代修正）
   - Spring AI `OpenAiApi` 会自动在 base_url 后拼 `/v1/embeddings`，原 `base_url=…/compatible-mode/v1` → 实际请求 `…/v1/v1/embeddings` → 404（对照 CHAT 配置 `https://api.deepseek.com` 不带 `/v1` 所以正常）
   - 模型名 `qwen3-vl-embedding` 不被 OpenAI 兼容模式支持 → `404 model_not_supported`
   - 已改为 `base_url=https://dashscope.aliyuncs.com/compatible-mode` + `model_name=text-embedding-v4`（v3 亦可；v2 固定 1536 维与 `vector(512)` 不匹配不可用）。实测：应用「测试」→ 连接测试成功；真实写入 → `Schema初始化成功`，agent25 写入 28 条 512 维向量
2. **B-16 三张 Spring AI 向量表缺主键**（既有 DDL 缺漏，已补）
   - `tbl_vector_store_simple_data/rag/user_memory` 只有 HNSW 索引、无主键，而 `PgVectorStore` upsert 依赖 `ON CONFLICT (id)` → `there is no unique or exclusion constraint matching the ON CONFLICT specification`
   - 影响面比 embedding 更大：**所有**向量写入路径（schema 初始化、知识文档）从来都写不进去
   - 已补 `PRIMARY KEY (id)`（幂等执行 + 复核）；**基线 `sql/all_schema.sql` 同步补齐**（按文件既有风格在末尾 ALTER 块追加），空库重放：报错数 64 与基线一致（全为 B-01 既有问题，无向量表相关），三张向量表主键建成
3. **B-17 图链路在非 HTTP 调用方取 Sa-Token 登录态抛异常**（已修）
   - `GraphServiceImpl.builerLoginVo()` 无条件 `StpUtil.getSession()`；深度分析工具运行在 AgentScope 工具线程，无 Sa-Token 上下文 → `SaTokenContext 上下文尚未初始化` 打断整图
   - 已改为捕获该异常并降级为空串（与原 `loginVO == null` 分支同义），MCP 工具回调等非 HTTP 调用方同样受益

**T-09 由此从「成功路径未验证」转为已验证**：`深度分析完成: agentId=25, datasourceId=11, elapsedMs=93485, chars=6024`，工具线程 `tool-deep-25-<uuid>` 独立 threadId，产出按 type 分组 + 时间分布归因的完整分析报告（真实图链路，非取数工具降级）。

## 追加变更（2026-09-27 · 合并后 · 列表去存量智能体）
- 用户反馈：「全部智能体里还显示那么多之前的」，要求列表只显示当前已创建的智能体。**用户选定方案：只从列表去掉（可逆），不删数据、不改自注册代码**。
- 背景事实：`AbstractHarnessAgent.register()` 启动时 `agentService.saveBySn()` 写库，而 `saveBySn` 是「不存在就插入」→ 19/20/21/23/24 这 5 个 Java 自注册智能体**删了也会在下次重启时长回来**，只删 DB 行解决不了。
- 实现：**只在列表查询侧过滤 `sn` 为空的记录**（平台内创建的智能体 sn 为空；Java 自注册类 sn 非空）
  - 后台：`GET /api/agent/list` 改走新方法 `AgentService.listCreatedInPlatform(status, keyword)`（`findAll/findByStatus/search` 语义不变，避免影响 `AgentStartupInitialization` 等既有调用方）
  - 前台：`AccountInfoServiceImpl.getMyAgents()` 同步过滤，口径与后台一致
  - 存量智能体仍保留在库中、仍可被 agentId 直达调用（构建预演/对话实测正常），Java 代码保留作参考 → 完全可逆（回退这两处过滤即恢复展示）
- 实测：后台列表 3 个（30 智能体02 / 25 智能体01 / 22 销售智能体），draft=[30,25]、published=[]、offline=[22]；关键字「制度」「巡逻」→ 空；存量 agent24 构建预演 `source=legacy` + 对话正常
- 附带修复 **B-15**：`AgentMapper.searchByKeyword` 在 PostgreSQL 下 `CONCAT('%', #{keyword}, '%')` 报 `could not determine data type of parameter $1`（列表搜索框一用就 500）→ 改 `'%' || CAST(#{keyword} AS text) || '%'`，四个关键词实测正常

## Implement 记录（2026-09-27 · T-16 端到端总回归）
**场景① 新建对话智能体全能力**（新建 id=29，不传 type → 落 `harness`；配 知识库 topK=8/阈值0.6 + 取数 + 深度分析 + 数据源11 + 计划模式；绑技能 weather；发布；授权通用组）
- 构建摘要：`agentId=29, runtimeKey=agent-29, planMode=true, memory=true, policy=local, tools=[todo, database_query, deep_analysis, knowledge_retrieval], skillPool=1`（四工具按配置装配、技能池按 agentId 解析）
- 后台对话（`/api/admin/harness/chat`，agentId=29，取数问题）：模型自主调用 `queryDatabase` → 日志 `数据库取数完成: agentId=29 …` → 回答 **8**，与 `select count(*) from tbl_data_agent` 一致
- 前台对话（`/platform/harness/chat`，同一问题）：组可见性 + 技能范围提示生效 → 回答 **8**（同一条库配置实例链路）
- 知识库工具：真实调用并留日志 `知识库检索: agentId=29, topK=8, threshold=0.6`（参数来自配置）；因向量库为空 + B-13（embedding 404）返回「未找到文档」类可读结果，模型如实说明无原文可引用
- 深度分析：装配与可读失败路径已验证（B-13 阻断成功路径，见 T-09 记录）
- **回归中发现并修复的真实缺陷**：前台可见性校验原以「sn 非空」为通过条件 → 新建（sn=NULL）对话智能体在前台被判「智能体不存在」。已改为新增 `FrontSkillAccessService.validateVisible`（只判组-智能体授权，不要求 sn），前台对话准入改用它，`harnessSn` 降为可选兼容字段；修复后前台对话恢复

**场景② 存量 5 个自注册智能体逐轮对话（不回归）**
| 智能体 | 链路 | 结果 |
|---|---|---|
| 19 BpmReactAgent | `/api/admin/agent/chat`（agentSn） | OK，正常自我介绍 |
| 20 ZhiduReactAgent | 同上 | OK，正常自我介绍 |
| 21 ParolCompiledGraph | `/api/admin/agent/stream/chatsql`（图链路） | OK，节点流正常（意图识别完成）+ `event:complete`，无 error 事件 |
| 23 HumanInTheLoop | `/api/admin/harness/chat`（agentId=23 → Registry legacy 分派） | OK，正常自我介绍 |
| 24 RulesHarnessAgent | agentId=24 **与** harnessSn 兼容路径各一轮 | OK，两条路径均正常 |

**场景③ skills spec 前台技能区与显式执行（不回归）**
- 前台技能区 `GET /platform/account-info/getMySkills?agentId=24` → `[weather(published)]`（三重交集：已发布 ∧ 已绑定 ∧ 组已授权）
- 显式执行：`/platform/harness/chat` 带 `enabledSkillIds=[10]` → 日志 `显式技能注入准备完成, agentId=24` （T-11 新增的 agentId 路径）+ 模型加载并遵循该技能

**场景④ 空库重放**：见 `artifacts.md` §重放验证记录（01/02/03/04 四次执行 + 幂等复跑全部 `ON_ERROR_STOP=1` 零报错；基线 `all_schema.sql` 64 处报错属既有 B-01）

**场景⑤ 升级件登记**：`artifacts.md` 已建（DDL/DML/配置/依赖/回滚 + 重放记录 + 回滚说明）

**回归中发现的其他问题（已登记，本期不修）**：B-14（删除智能体残留运行配置/技能绑定/组授权孤儿行，实测 3 张表各残留 1 行，已手工清理）
**环境修复（回归必需，已记录于 bugs.md §工作区遗留状态）**：数据源 id=11「本地测试」host 由不可达的 `192.168.66.19` 改为 `127.0.0.1`、`connection_url` 同步、密码由 `123456` 改为容器实际密码 `phoenix`
**测试数据清理**：T-16 测试智能体 29 及其运行配置/技能绑定/组授权、T-10 上传的测试技能 `poem` 均已删除，库回到回归前状态（7 个智能体、技能池仅 `weather`）

## Implement 记录（2026-09-27 · T-08 / T-09）
- **T-08**（取数工具，`queryDatabase`）：`DatabaseQueryTool` + Contributor（`dbQueryEnabled==1` 装配）+ 共享支撑 `DatabaseToolSupport`。
  - **实现细化（plan 决策4 语义不变）**：plan 设想「复用 SchemaService 向量召回 schema」，但实测其检索过滤器含 `agentId`，而文档写入用的是**做 schema 初始化的那个 agent**＋`query` 参数是死参数 → 对话智能体（尤其 sn=NULL 的新建智能体）召不回任何表。故 schema 改为**JDBC 实时内省**（`Accessor.showTables/showColumns` + `TableMetadataService.batchEnrichTableMetadata`，表 30/列 40 截断、表名排序保证可复现），SQL 生成与执行仍完全复用 data 域（`Nl2SqlService.generateSql` + `Accessor.executeSqlAndReturnObject` + `SqlSecurityValidator`），未自写 SQL 生成。
  - **发现并修复 B-12**：`SqlSecurityValidator` 用子串匹配关键字，`create_time`/`update_time` 等普通列名会被误判为危险语句，只读查询遭大面积误杀 → 改整词匹配 + 先剔除字符串字面量/引用标识符（jshell 8 例验证：3 类常见只读 SQL 放行、4 类真实写操作/堆叠语句拦截、字面量不误杀）。
  - 实测（真实对话链路，agent25 配数据源 11）：模型自主调用 `queryDatabase` → 日志 `数据库取数完成: agentId=25, datasourceId=11, rows=1, elapsedMs=3007` → 回答「7」，与 `select count(*) from tbl_data_agent` 一致。
- **T-09**（深度分析工具，`deepAnalyze`）：`DeepAnalysisTool` + Contributor；内部复用 NL2SQL 状态图（`GraphService.graphStreamProcess`），独立 threadId（`tool-deep-{agentId}-{uuid}`）、同线程阻塞收集（先订阅再投喂，`complete`/`error` 判终态）、180s 超时、每智能体并发上限 2（**实现取舍**：原计划「单会话并发」，因工具侧刻意不依赖 RuntimeContext 拿不到 sessionId，改为每智能体信号量，类注释已写明）；前置「确保就绪」= 缺 `tbl_data_agent_datasource` 绑定则补绑 + 缺 schema 文档则用全部表初始化。
  - 实测：模型自主调用 `deepAnalyze` 两次（工具装配证据：`tools=[todo, database_query, deep_analysis, knowledge_retrieval]`），失败路径均返回**可读文案**（「深度分析暂不可用：…（可稍后重试）」）且模型能降级改用取数工具完成回答——错误不抛栈、不污染主会话。
  - **未能端到端验证（如实登记）**：本环境 EMBEDDING 模型配置不可用（`404 Model not exist.`，见 bugs.md **B-13**）→ schema 向量文档无法生成 → 状态图 SchemaRecall 无输入 → 深度分析在**本环境**无法跑出结论。代码链路只验证到「就绪检查失败→可读错误」，成功路径（图产出报告、threadId 隔离、超时/限流文案）待 embedding 配置修复后重跑。

## Implement 记录（2026-09-27 · T-11~T-15）
- **T-11**（后台寻址）：`/api/admin/harness/chat` 与 `/confirm` 支持 `agentId`（agentId 优先，`harnessSn` 保留兼容）。`HarnessChatService` 增 `call/stream/confirmStream(HarnessRequest|ConfirmRequest)` 重载：agentId → `HarnessAgentRegistry`（存量自注册智能体在注册表内自动回退其 Java 实例），未传 agentId → 原 `HarnessStaticLoader` 路径。显式技能校验同步支持 `prepareByAgentId`（技能池按 agentId 解析）。
- **T-12**（前台寻址）：`/platform/harness/chat` 内部改走注册表（请求带 agentId），**保留**组可见性校验 + 显式技能三重交集校验 + 技能范围约束提示；`harnessSn` 仅作兼容字段，不做 `sn || id` 猜测。
- **T-13**（前端寻址）：运行页 harness 分支改为 `agentId`；删除全部 `sn || id` 兜底（`run/index.vue` 两处、`api-transport.ts`）；存量 `agent` 类型分支在缺 sn 时**显式报错**而不是拿 id 冒充 sn；确认请求改传 `agentId`。
- **T-14**（去类型标签）：后台列表移除类型徽标与 `getTypeText` 映射（四类型名称不再出现在 UI）；新建抽屉本就无类型选择（服务端强制 `harness`）；存量展示与功能不变。
- **T-15**（运行配置面板）：编辑抽屉新增「对话智能体」菜单分组 + `AgentRuntimeConfig.vue` 面板（模型选择/计划模式/记忆/知识库工具含 topK·阈值/数据库取数与深度分析两级 + 数据源/文件系统策略），含「构建预演」按钮直连 `runtime-config/preview` 回显生效工具与技能池；`edit/index.vue` 同步挂载；新增 `api/core/agentRuntime.ts`。技能配置面板复用（仅文案由「Harness 类智能体」改为「对话智能体」）。
- 前端校验：`npx vue-tsc --noEmit --skipLibCheck` 通过（`src/` 错误数维持基线 194，本次改动文件 0 错误）。
- 发现并登记 **B-11**：前台 HITL 确认调用的 `POST /api/front/harness/confirm` 后端**不存在**（spec-1 遗留），本期不修。
- 待办：T-11/T-12 的真实对话链路（SSE）与 T-13~T-15 的界面走查，并入 T-16 端到端回归。

## Implement 记录（2026-09-27 · T-10）
- 完成 T-10：`HarnessSkillMapper.selectAllowedSkillNamesByAgentId`（新）+ `AgentScopedSkillRepository` 双构造器（agentId 路径 / sn 路径并存）。
  - 库配置路径（Factory 构建的对话智能体）走 **agentId**；存量 Java 自注册路径（AbstractHarnessAgent）保持 **sn**，行为不变。
  - 构建预演新增 `skillNames`（按 agentId 解析的技能名清单），成为技能池隔离的可观测入口。
- 实测：agent25（sn=NULL）技能池由 T-05 时的 0 → `[weather]`（agentId 路径生效）；agent24 为 `[poem, weather]`，DB 反查 `poem` 仅绑 24 → 按智能体隔离成立；存量 sn 路径执行同款 SQL（sn=RulesHarnessAgent）返回 `[poem, weather]`，兼容路径未回归。
- 测试数据（有意保留至 T-16 回归）：新上传技能 `poem`（id=12，已发布、未授权任何用户组→前台不可见）并绑定 agent24；T-16 结束后清理。

## Implement 记录（2026-09-27 · T-07）
- 完成 T-07：新增 `KnowledgeRetrievalTool`（按智能体实例化）+ `KnowledgeRetrievalToolContributor`，检索范围 = 本实例 agentId，topK/阈值来自运行配置。
- **新增增量升级件 `04_knowledge_tool_params.sql`（+回滚件）**：`knowledge_top_k`(默认10) / `knowledge_similarity_threshold`(默认0.65)。原因：plan T-07 验收要求「topK/阈值来自配置」，已确认的 `03` 无此两列；不改 `03`（保持已确认件不动），以增量件补齐，默认值 = 原写死值故存量行为不变。已幂等执行 2 次验证。
- 校验新增错误码 `42005`（topK 1~50、阈值 0~1），常量集中在 `AgentRuntimeConstant`。
- 实测：agent25 开知识库 → 工具清单 `[todo, knowledge_retrieval]`，日志 `装配知识库检索工具: agentId=25, topK=10, threshold=0.65`；配 topK=20/阈值=0.5 → 回读一致、日志随配置变化；topK=99 → 42005；阈值=1.5 → 42005；关知识库 → 工具清单 `[todo]`。
- 遗留（明示，非遗漏）：`RulesRagTool`（存量参考实现，agent24 制度专家在用）仍写死 `agentId=18`；按 plan 决策1「存量 Java 保留作参考、不迁移」本期不动，其泛化归 BL-03 存量迁移。新工具代码内无任何硬编码 agentId。
- 「两个智能体挂不同知识范围、各自检索命中自身范围」的真实链路证据需 T-11/T-12 打通对话入口后才有可调用的工具链路（且当前向量库为空，需先灌知识文档），归入 T-16 端到端回归。

## Implement 记录（2026-09-27 · T-06）
- 完成 T-06：`HarnessAgentRegistry`（agentId→实例；配置指纹失效；LRU 上限 + close；存量 sn 回退）。
  - 双路径**确定性判定**（非 `sn || id` 猜测）：sn 命中 `HarnessStaticLoader` → 返回 Java 类内存实例（`source=legacy`，功能不变）；未命中 → 按运行配置构建并缓存（键 = agentId）。
  - 指纹 = agent.update_time + 运行配置.update_time + 模型/工具/计划/记忆/策略开关 + 提示词哈希 + 技能绑定版本（`HarnessSkillMapper.selectSkillBindingVersion`，查询失败降级不阻断）。
  - 容量 `phoenix.agent.runtime.max-instances`（默认 200），accessOrder LRU 淘汰即 `close()`；并发构建竞争时放弃后到产物并 close。
  - `HarnessStaticLoader` 增 `findAgent(sn)`（可空查询，`loadAgent` 原语义不变）。
  - 构建预演改为走 Registry（`acquire`），回显 `instanceSource`/`registryStats`，成为 T-06 的可观测验证入口。
- T-06 实测（临时 `max-instances=1` 启动）：① 首次 preview agent25 → `built`；② 再次 → `cached`（hits=1）；③ 改运行配置后再 preview → 指纹变 → `built`（builds=2，旧实例 close）；④ agent24（RulesHarnessAgent）→ `legacy`（技能池=1，存量路径未回归）；⑤ 新建临时智能体并 preview → 淘汰 agent25（evictions=1，日志「超 LRU 上限(1)淘汰」）；⑥ 再 preview agent25 → 重新 built 成功（证明 close 不影响共享 stateStore/distributedStore/技能仓库；另经字节码核实 `ReActAgent.close()` 为空实现，仅释放实例自有 workspace index）。临时智能体已删除，服务已按默认 LRU=200 重启。

## Implement 记录（2026-09-27 · T-01~T-05）
- 完成 T-01~T-04（分支 `feature/dynamic-agent-types`）：升级件 03 + 实体/枚举/DTO/VO + 配置服务与校验 + 端点与类型强制。实测：新建不带 type 落 `harness`；存量 sql 智能体即使请求带 `type=harness` 也不被改写；未登录业务码 401；无数据源开库工具→42001；非法策略→42003；保存回读一致。
- 完成 T-05：`HarnessAgentFactory` + 工具装配扩展点 `AgentToolContributor`（ObjectProvider 收集，允许暂无实现）；
  - **实现细化（不改变 plan 决策2 语义）**：plan 原文「Factory 内 `new KnowledgeRetrievalTool(agentId)`」→ 改为「每个工具能力各实现 `AgentToolContributor`，由 Factory 构建时按运行配置实例化」。理由：工具实例化仍发生在工厂内、仍绑定各自的 agentId/数据源（决策2 目标不变），但 T-07/T-08/T-09 可独立落任务、Factory 无需二次改动。已用 ObjectProvider 避免「暂无实现」导致启动失败。
  - **实测发现（纳入 T-10 验收）**：库中新建的对话智能体 `tbl_data_agent.sn` 为 **NULL**（存量自注册才有 sn）→ 运行身份改为「有 sn 用 sn，否则 `agent-{id}`」（R-08 agentId 寻址），构建不再依赖自注册类；但 `AgentScopedSkillRepository` 现按 sn 查绑定，故此类智能体技能池暂为 0，须由 T-10 的 agentId 路径补齐。
  - 验证入口：`GET /api/agent/{id}/runtime-config/preview`（管理端「构建预演」，真实构建一次后立即 close）。实测 agent 25（sn=NULL，知识库+取数开、数据源1）：buildOk=true，summary 回显 `runtimeKey=agent-25, planMode=true, memory=true, policy=local, tools=[todo], skillPool=0`；记忆关+远程策略分支同样构建成功；不存在智能体→42004。
  - 附带：`HarnessModelRegistry` 支持按 `model_config_id` 解析模型（空/非 CHAT 降级全局默认并告警）。

## v1.0.0（2026-09-27）确认人: 陈卓
- 三重确认第①关通过：requirements.md（R-01~R-11 + Non-goals + 假设7条；Q1/Q2/Q5/Q6 已决，命名统一为「对话智能体」，四类型标签全部去掉，MCP 入 backlog）
- 三重确认第②关通过：plan.md（Factory+Registry、两类数据库工具接线、知识库工具泛化、agentId 寻址统一、前端去标签与配置面板、03 升级件；6 个决策含被拒方案）
- 进入 Phase 3：tasks.md 草稿（T-01~T-16，五要素齐备，R 全覆盖自检通过）

## v0.1.0（2026-09-27）草稿期修订（第二次）
- **命名与概念统一**：对外只称「对话智能体」（不再叫 Harness 智能体）；**去掉 sql/agent/workflow/harness 四个类型标签**（列表不显示类型列/徽标、不提供类型筛选、新建不选类型）→ R-01 重写
- **Q6 决议**：选 A + 四个标签全部去掉
- **Q5 决议**：数据库查询两级（取数工具默认开 + 深度分析工具走 NL2SQL 状态图）→ R-05 重写
- **待办登记**：新建 `specs/_project/backlog.md` — BL-01 MCP 工具接入、BL-02 工作流编排、BL-03 存量自注册类迁移
- R-06/R-07/R-09 文案同步（存量只去标签、功能不变；workflow/MCP 不在本期）

## v0.1.0（2026-09-27）草稿期修订
- 方向收敛为「**Harness 统一化**」：以 harness 承载对话+知识库+数据库查询；`agent`/`sql` 存量保留（代码留作参考，不删除不迁移）；`workflow` 本期不做
- R 条款重写：R-01 类型选择(harness 推荐) / R-02 新能力不依赖自注册(存量保留) / R-03 harness 数据驱动 / R-04 知识库工具 / R-05 数据库查询工具(层级待 Q5) / R-06 存量类型保持可用 / R-07 workflow 不做 / R-08 寻址统一 agentId / R-09 类型化面板 / R-10 权限不变 / R-11 兼容回滚
- 新增待确认：Q5 数据库查询层级（单次取数/完整分析链路/两者）、Q6 存量类型 UI 呈现

## v0.1.0（2026-09-27）
- 初始化创建（requirements.md 草稿全文；plan/tasks 占位）
- 需求来源：用户提出「把代码写死的智能体全部去掉，改成四种类型由前端选择创建」
- 立论依据：实测 7 个智能体中 5 个为 Java 自注册（bugs.md B-10）；workflow 无编排能力与图定义存储；harness 运行时按 sn 取内存实例
