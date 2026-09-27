# Changelog: dynamic-agent-types

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
