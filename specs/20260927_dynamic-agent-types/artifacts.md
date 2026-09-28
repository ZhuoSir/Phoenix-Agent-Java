# 升级件登记: dynamic-agent-types

| 类型 | 内容摘要 | 来源任务 | 草案位置 | 已汇总至里程碑 |
|---|---|---|---|---|
| DDL | 新表 `tbl_data_agent_runtime_config`（对话智能体运行配置 1:1：模型/计划模式/记忆/三类工具开关/数据源/文件系统策略；含 `uk_arc_agent` 部分唯一索引） | T-01 / T-02 | `sql/03_agent_runtime_config.sql` | - |
| DML | `tbl_data_agent.type` 存量 NULL/'' 回填为 `harness` + 列默认值改为 `harness`（幂等、含表存在性容错） | T-04 | `sql/03_agent_runtime_config.sql` | - |
| DDL | `tbl_data_agent_runtime_config` 增 `knowledge_top_k`（int，默认 10）、`knowledge_similarity_threshold`（double precision，默认 0.65） | T-07 | `sql/04_knowledge_tool_params.sql` | - |
| 配置 | `phoenix.agent.runtime.max-instances`（运行实例 LRU 上限，默认 200） | T-06 | plan §设计（代码默认值，无外部配置源） | - |
| 配置 | 工具装配上限 `AgentRuntimeConstant.MAX_TOOL_COUNT=3`、schema 内省上限 `MAX_SCHEMA_TABLES=30`/`MAX_COLUMNS_PER_TABLE=40`、结果展示 `MAX_RESULT_ROWS_SHOWN=50`、SQL 生成超时 60s | T-08 | plan §风险（代码常量，无外部配置源） | - |
| 配置 | `phoenix.agent.tool.deep-analysis-timeout-seconds`（默认 180）、`phoenix.agent.tool.deep-analysis-max-chars`（默认 6000）、`phoenix.agent.tool.deep-analysis-max-concurrent`（默认 2） | T-09（追加验证期改为可配置） | `DeepAnalysisToolContributor` @Value 默认值 | - |
| 依赖 | 复用 `phoenix.agent.skill.max-explicit`（spec: agent-skill-management 登记，默认 3） | T-11 | 见 `specs/20260927_agent-skill-management/artifacts.md` | - |
| 回滚 | `03_agent_runtime_config_rollback.sql`（drop 表 + 撤销 type 默认值；NULL 回填不可逆，脚本文末注明）；`04_knowledge_tool_params_rollback.sql`（drop 两列，丢失已配置参数） | T-01 / T-07 | `sql/` | - |

## 重放验证记录（T-16）

**环境**：容器 `phoenix-pg`（PostgreSQL），全新空库 `phoenix_replay`（`drop database if exists` → `create database`）。

| 步骤 | 命令 | 结果 |
|---|---|---|
| 基线 | `psql -f sql/all_schema.sql` | **64 处报错**（建表引用缺失序列，属 bugs.md **B-01**，与本期升级件无关；与 spec-1 登记的 64 处一致） |
| 01 技能升级件 | `psql -v ON_ERROR_STOP=1 -f specs/20260927_agent-skill-management/sql/01_agent_skill_upgrade.sql` | **exit=0，零报错** |
| 02 技能菜单 | 同上 `02_skill_menu.sql` | **exit=0，零报错** |
| 03 运行配置 | 同上 `specs/20260927_dynamic-agent-types/sql/03_agent_runtime_config.sql` | **exit=0，零报错** |
| 04 知识库参数 | 同上 `04_knowledge_tool_params.sql` | **exit=0，零报错** |
| 幂等复跑 | 01→02→03→04 各再执行一次 | **四次均 exit=0、零报错** |
| 对象校验 | `information_schema` + 菜单查询 | `tbl_data_agent_runtime_config` 两新增列 = 2；新表 3（`tbl_data_agent_skill_info` / `tbl_platform_group_skill_info` / `tbl_data_agent_runtime_config`；`tbl_harness_skills` 由应用启动创建，空库重放场景下不存在，与 spec-1 场景A 结论一致）；菜单行 = 1 |

**执行顺序要求**：`all_schema.sql` → spec-1 `01` → spec-1 `02` → spec-2 `03` → spec-2 `04`（`03` 的 type 回填依赖 `tbl_data_agent` 存在，`04` 的列变更依赖 `03` 建表；两个脚本均自带存在性容错与幂等保护）。

## 回滚操作说明

1. `04_knowledge_tool_params_rollback.sql`：撤销两个知识库参数列（已配置的 topK/阈值丢失，回落代码默认 10 / 0.65）。
2. `03_agent_runtime_config_rollback.sql`：drop `tbl_data_agent_runtime_config`（全部智能体运行配置丢失）+ 撤销 `type` 默认值。**注意**：`type` 的 NULL→harness 回填不可逆，回滚后存量智能体仍为 harness（其运行时能力仍由本期代码提供，故回滚前须先回滚应用版本）。
3. 代码层回滚：回退至本期之前的提交；`knowledge_top_k`/`knowledge_similarity_threshold` 两列可保留（旧代码不读，无副作用），`tbl_data_agent_runtime_config` 亦可保留（旧代码不读）。
4. 无「先破坏后修复」式变更：全部升级件为**纯增量**（新增表/新增列/回填 NULL），未修改或删除任何既有列与既有数据。
