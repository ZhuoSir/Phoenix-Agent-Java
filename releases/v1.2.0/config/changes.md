# v1.2.0 配置变更汇总

> 本版本**无外部配置源变更**（项目无 application.yml 集中配置，全部为代码默认值 + 环境变量可覆盖）。
> 下表「新值」即代码默认值；仅当需要偏离默认值时才在对应环境变量/yml 中显式设置。

| # | 类型 | 位置 | key | 旧值 | 新值(默认) | 来源 spec | 需重启 |
|---|---|---|---|---|---|---|---|
| 1 | 新增配置键 | 代码 @Value（显式执行上限） | `phoenix.agent.skill.max-explicit` | 无 | `3` | specs/20260927_agent-skill-management (T-10) | 随发布生效 |
| 2 | 新增配置键 | 代码 @Value（运行实例 LRU 上限） | `phoenix.agent.runtime.max-instances` | 无 | `200` | specs/20260927_dynamic-agent-types (T-06) | 随发布生效 |
| 3 | 新增配置键 | 代码 @Value（深度分析超时秒） | `phoenix.agent.tool.deep-analysis-timeout-seconds` | 无 | `180` | specs/20260927_dynamic-agent-types (T-09) | 随发布生效 |
| 4 | 新增配置键 | 代码 @Value（深度分析结果字符上限） | `phoenix.agent.tool.deep-analysis-max-chars` | 无 | `6000` | specs/20260927_dynamic-agent-types (T-09) | 随发布生效 |
| 5 | 新增配置键 | 代码 @Value（深度分析并发上限） | `phoenix.agent.tool.deep-analysis-max-concurrent` | 无 | `2` | specs/20260927_dynamic-agent-types (T-09) | 随发布生效 |
| 6 | 新增配置键 | 代码 @Value（AI 生成调用超时秒） | `phoenix.agent.profile-generate-timeout-seconds` | 无 | `90`（原硬编码 60 会切断成功调用，BUG-23） | specs/20260927_agent-config-ai-generate (T-09/T-11) | 随发布生效 |
| 7 | **数据变更（非配置文件）** | 表 `tbl_data_model_config`（EMBEDDING 行） | `base_url` / `model_name` | `…/compatible-mode/v1` + 不被兼容模式支持的模型名 | `https://dashscope.aliyuncs.com/compatible-mode` + `text-embedding-v4` | BUG-13 | 需在管理端或 SQL 修改后重建向量 |

**注意（#7）**：现网库已手工修正（id=6）；**其他环境升级时需同步执行**，否则 EMBEDDING 测试/初始化恒 404。参考 SQL：
```sql
UPDATE tbl_data_model_config
   SET base_url='https://dashscope.aliyuncs.com/compatible-mode', model_name='text-embedding-v4'
 WHERE model_type='EMBEDDING' AND is_deleted=0;
```
（换 EMBEDDING 默认/地址后，历史向量需重新初始化才能与新模型一致——见 BUG-13 处置与 setDefault 接口 WARN 提示。）

- 代码常量类（非配置，列出备查）：`AgentProfilePromptTemplates`（名称≤64 字、描述≤120 字、提示词 300~600 字、骨架四段）；`AgentRuntimeConstant`（MAX_TOOL_COUNT=3、MAX_SCHEMA_TABLES=30、MAX_COLUMNS_PER_TABLE=40、MAX_RESULT_ROWS_SHOWN=50、SQL 生成超时 60s）。
- 前端：生成接口单独 `timeout: 120_000`（`api/core/agentProfile.ts`，BUG-30），全局 axios 30s 默认不变。
