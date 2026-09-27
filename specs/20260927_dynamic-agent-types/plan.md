> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-09-27 | 确认人: 陈卓 | 确认日期: 2026-09-27

# 技术方案：对话智能体统一化与去硬编码

规范依据：`.specrc.yml` → api-design / database = global（本会话已加载）；信封沿用项目现状 `ReturnVo`。

## 方案概述
把「智能体运行时」从 **Java @Component 自注册 + sn 静态加载** 改造为 **库配置驱动 + agentId 寻址**：
① 新增 `tbl_data_agent_runtime_config` 一表承载对话智能体的运行配置（模型/工具开关/数据源/计划模式/记忆）；
② 新增 `HarnessAgentFactory`（按配置构建 HarnessAgent，工具**按 agent 实例化**绑定其 id 与数据源）+ `HarnessAgentRegistry`（agentId→实例，配置指纹变更即重建，兼容存量 sn 静态加载）；
③ 把知识库工具从写死 `agentId=18` 泛化为按 agent 配置；新增**取数工具**（复用 `Nl2SqlService.generateSql/fineSelect`）与**深度分析工具**（复用既有 NL2SQL 状态图，独立 threadId）；
④ 对话通道统一按 agentId 寻址（后台 `/api/admin/harness/chat` 支持 agentId，前台 `/platform/harness/chat` 直达 registry）；
⑤ 前端**移除四个类型标签**（列表去掉类型列/筛选/徽标、新建不选类型），服务端新建一律 `type=harness`；存量 `sql/agent/workflow` 仅"去标签"，功能与面板不变。
MCP 与工作流编排进 `specs/_project/backlog.md`（BL-01/BL-02），不在本期。

## 涉及模块与数据流
```
admin-ui            列表去类型列/筛选/徽标；新建不选类型；抽屉新增「对话智能体」配置面板
                    （提示词/模型/技能/知识库开关/数据库两级开关+数据源/计划模式）
phoenix-agent-api   + AgentRuntimeConfigDTO / AgentRuntimeConfigVO（新）
phoenix-agent-core  + AgentRuntimeConfigMapper（新表）
                    + HarnessAgentFactory  ← 按配置构建（toolkit/工具/skillRepository/prompt/model）
                    + HarnessAgentRegistry ← agentId→实例；指纹失效；存量 sn 回退静态加载器
                    + KnowledgeRetrievalTool(agentId)      ← 泛化自 RulesRagTool（去掉写死 18）
                    + DatabaseQueryTool(agentId, dsId)     ← 新：NL→SQL→只读结果
                    + DeepAnalysisTool(agentId, dsId)      ← 新：内部走 NL2SQL 状态图（独立 threadId）
                    + AgentScopedSkillRepository 支持按 agentId 过滤（新增 SQL，保留 sn 版兼容）
phoenix-agent-rest  + GET/PUT /api/agent/{id}/runtime-config
                    · /api/admin/harness/chat 支持 agentId（sn 保留兼容）
phoenix-data        不改接口；提供 Nl2SqlService / 状态图调用复用（agent-core 已依赖 data-core）
SQL 升级件          03_agent_runtime_config.sql（新表 + type 默认值与 NULL 回填 + 回滚）

数据流（对话）：请求(agentId) → Registry.get(agentId) → 命中缓存? 指纹一致→复用；否则 Factory 构建
  → HarnessAgent.streamEvents(userMessage, runtimeContext) → SSE
数据流（取数工具）：LLM 调用工具 → Nl2SqlService.generateSql(数据源) → 只读执行 → 结果摘要回模型
数据流（深度分析工具）：LLM 调用工具 → 状态图(独立 threadId, 该 agent 数据源) → 报告/结论回模型
```

## 接口设计
| 方法/路径 | 语义 | 关键约束 |
|---|---|---|
| GET `/api/agent/{id}/runtime-config` | 读取对话智能体运行配置 | 无配置时返回默认值（不报错） |
| PUT `/api/agent/{id}/runtime-config` | 保存运行配置（模型/工具开关/数据源/计划模式/记忆） | 开启数据库工具但未选数据源→参数错误；工具数超上限→错误 |
| POST `/api/agent`（改） | 新建智能体 | **服务端强制 `type=harness`**，忽略入参 type；不再要求前端选择 |
| PUT `/api/agent/{id}`（改） | 更新智能体 | **SHALL NOT 改写 type**（存量类型保持） |
| GET `/api/agent/list`（不变） | 列表 | 仍返回 `type`（供运行页分流），但前端不展示 |
| POST `/api/admin/harness/chat`（改） | 后台对话 | 新增 `agentId`；`harnessSn` 保留兼容存量自注册智能体；两者都缺→参数错误 |
| POST `/platform/harness/chat`（既有，改内部） | 前台对话 | 已按 agentId 寻址；内部改为 Registry 解析（保留组可见性+技能三重校验） |

## 数据模型变更（DDL 草案）
```sql
-- 1) 对话智能体运行配置（1:1）
CREATE TABLE IF NOT EXISTS tbl_data_agent_runtime_config (
    id                       varchar(32) NOT NULL,
    agent_id                 bigint      NOT NULL,
    model_config_id          bigint,
    plan_mode                smallint    NOT NULL DEFAULT 0,
    memory_enabled           smallint    NOT NULL DEFAULT 1,
    knowledge_enabled        smallint    NOT NULL DEFAULT 0,
    db_query_enabled         smallint    NOT NULL DEFAULT 0,
    db_deep_analysis_enabled smallint    NOT NULL DEFAULT 0,
    datasource_id            bigint,
    filesystem_policy        varchar(32) NOT NULL DEFAULT 'local',
    creator varchar(32), create_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updator varchar(32), update_time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag smallint NOT NULL DEFAULT 0,
    CONSTRAINT pk_agent_runtime_config PRIMARY KEY (id)
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_arc_agent ON tbl_data_agent_runtime_config (agent_id) WHERE del_flag = 0;
-- 全字段 COMMENT
-- 2) 消除 type 为空（R-01：不再产出无类型智能体）
UPDATE tbl_data_agent SET type = 'harness' WHERE type IS NULL OR type = '';
ALTER TABLE tbl_data_agent ALTER COLUMN type SET DEFAULT 'harness';
```
回滚：`DROP TABLE tbl_data_agent_runtime_config;` + `ALTER COLUMN type DROP DEFAULT;`（NULL 回填不可逆，回滚说明中注明）。

## 关键决策
1. **每智能体一个 HarnessAgent 实例 + 指纹失效缓存**（Registry 持有 `agentId→(fingerprint, instance)`，指纹=agent.update_time+工具开关+技能绑定版本）。拒绝：每请求重建（builder 要组装 toolkit/prompt/model，成本高且与 memory/state 绑定复杂）；拒绝：继续 sn 静态注册（违背去硬编码目标）。
2. **工具按 agent 实例化并绑定其 id/数据源**（Factory 内 `new KnowledgeRetrievalTool(agentId)` 等）。拒绝：让工具从 RuntimeContext 取 agentId（上游 `@Tool` 是否注入 RuntimeContext 未证实，且隐式依赖易踩坑）——本方案已验证 `RuntimeContext` 可用于中间件注入系统提示，但工具侧不赌上游行为。
3. **运行配置独立表**而非 `tbl_data_agent` 加 JSONB 列。拒绝：JSONB 列（字段难约束/难索引/动热表，且不符合 database 规范的状态枚举用列表达）。
4. **数据库工具复用 `Nl2SqlService`（generateSql + fineSelect）**，深度分析复用既有状态图。拒绝：工具内自写 SQL 生成与多步编排（与 data 域重复、方言与只读校验会漂移）。
5. **type 值保留、仅去展示标签**；新建强制 harness。拒绝：改枚举值/迁移存量（用户已明确存量不迁移，改造面与风险都大）。
6. **前台/后台双通道统一 agentId 直达 Registry**，存量自注册智能体走 sn 回退分支。拒绝：只改前台（后台运行页仍是主入口，双轨会造成行为不一致）。

## 风险与规避
- **实例生命周期与内存**：智能体数量增长会累积 HarnessAgent 实例 → Registry 设 LRU 上限（如 200）+ 空闲淘汰；淘汰时调用实例 `close()`（注意共享单例仓库不可关，见 skills spec 的 close 语义）。
- **深度分析工具并发**：状态图依赖 RedisSaver/threadId → 工具内部**启用独立 threadId**（`tool-deep-{sessionId}-{uuid}`），避免污染主对话状态；同时限制单会话并发深度分析次数。
- **身份与权限下传**：数据源查询应以**当前对话用户**身份执行（既有 `LoginUserAgentInterceptor` 机制），工具内需取到 userId（RuntimeContext 已含 userId，工具实例可按需从当前请求上下文解析；实现首任务验证取值路径）。
- **存量兼容**：5 个自注册智能体继续走静态加载器；寻址分支必须显式（DB 驱动 harness → agentId；存量 → sn），禁止 `sn || id` 兜底。
- **前端隐藏类型后的运行页分流**：后端仍返回 `type`，前端按 type 选择通道但**不展示**；若 type 缺失（历史脏数据）→ 明确报错提示。
- **提示词/工具预算**：工具数上限（如 ≤6）与知识 topK 上限校验，防 context 爆炸。
- **skills spec 回归**：`AgentScopedSkillRepository` 改为按 agentId 过滤时，须保证存量 sn 版路径（制度专家）不回归 → 保留双查询并回归测试。

## 依赖与前置
- `Nl2SqlService` / `DbConfigBO` / 智能体↔数据源绑定（Implement 首任务核实表名与 API，前端已有 `AgentDataSourceConfig` 面板）
- `HarnessModelRegistry`（模型解析）与「模型配置」表
- AgentScope Harness `builder()` 能力面（toolkit/skillRepository/filesystem/memory/PlanMode）
- 既有 skills spec 交付物（已合并 main `ed2e3f9`）
- 升级件脚本编号顺延：`03_agent_runtime_config.sql`（+回滚），登记 artifacts.md

## Plan 阶段自查（database 规范 §六）
- [x] 新表命名/COMMENT/公共字段齐备（id 雪花、create/update_time、del_flag）
- [x] 业务防重：`uk_arc_agent(agent_id)` 部分唯一索引
- [x] 状态/开关字段用 smallint + 代码枚举（不建 magic 值）
- [x] 迁移脚本 + 回滚说明（NULL 回填不可逆已注明）
- [x] 无 `SELECT *`、无 `${}`（实现层要求，写入任务验收）
