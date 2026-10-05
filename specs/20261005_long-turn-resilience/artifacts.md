# Artifacts: long-turn-resilience

> 版本: v1.0.0 | 更新: 2026-10-05 | 改动面以 git 提交为准，本文件为对账清单

## 一、后端（phoenix-agent / phoenix-admin）

| 文件 | 变更 |
|---|---|
| `harness/turn/HarnessTurnManager.java` | T-01 看门狗（`lastActivityAt` 帧脉冲、空闲判据、总时长闸默认关、五分类文案）、T-03b 帧合并（100ms `mergeJanitor`/`flushPendingText`）、BUG-69 金丝雀、BUG-77 重试（`sourceSupplier`/`retryUsed`）、`sweepStaleGenerations` 重启注记、`join()` 追流入口 |
| `harness/state/HarnessStateRepairService.java` | **新增**（BUG-77 根治）：修复 `context[]` 中"仅 tool_use 无 thinking"的助手消息 |
| `harness/factory/HarnessAgentFactory.java` | T-02 上下文治理两级回退（agent 配置→全局 env 默认）；R-06 会话级工作区（`buildWithSummary(agent, sessionId)`）+ shell cwd（`.project`）+ 会话目录预建 |
| `harness/factory/HarnessAgentRegistry.java` | 三列进指纹；R-06 会话级缓存键 `agentId@sessionId` + 会话重载 + `invalidate` 连带清理 |
| `harness/mcp/McpMountService.java` | 变体键含 sessionId（R-06） |
| `harness/send/impl/HarnessChatServiceImpl.java` | 对话/确认续跑按（智能体,会话）取实例；轮末产物扫描 |
| `service/file/WorkspaceArtifactScanner.java` | 扫描根=会话目录、`runtimeKey` 以库中智能体为准、storeKey 归一、`call_*`/点目录/`large_tool_results` 过滤 |
| `service/file/AgentFileService(.Impl).java` | BUG-80：`canAccessSession`（属主或后台管理员）+ 孤儿行回落创建者 |
| `util/WorkspacePaths.java` | R-06：`sessionRoot(root, agentKey, sessionId)` |
| `controller/AgentFileController.java` | 抽屉补扫放行管理员（BUG-80） |

## 二、前端（web-frontend/apps/admin-ui）

| 文件 | 变更 |
|---|---|
| `api/core/graph.ts` | **新增** `streamHarnessTurnJoin`（T-04 追流）+ 抽出 `consumeHarnessSse` 与 chat 共用解析 |
| `components/run/index.vue` | T-03 统一 `applyServerRowRender`/活性指示；T-04 join 续渲（原位更新+150ms 节流+有界重连+`closeJoin`）；BUG-73/74/75/76/81 状态分片与守卫；中断轮时间归属；T-03② 快照 unload 兜底 |
| `utils/stream-snapshot.ts` | T-03②：`flushStreamSnapshot`（绕节流直写） |
| `views/front/api-transport.ts` | R-02/前台：`incrementalMarkdown`（完成块冻结）+ `pushInterval()` 400ms；joinActiveTurn；cancelTurn |
| `views/front/components/{ChatComposer,ChatMessages,ThinkingBlock}.vue` | 流式态/思考块/贴底跟随/空体守卫 |
| `api/core/agentRuntime.ts`、`views/agent/list/components/AgentRuntimeConfig.vue` | T-06 三输入（**设计被否 → BL-27**，代码保留待重做） |

## 三、SQL / 配置

| 件 | 内容 |
|---|---|
| `releases/v1.6.0/sql/V1.6.0_03__runtime_compaction_columns.sql` | `tbl_data_agent_runtime_config` 加 `compaction_trigger_tokens` / `compaction_keep_messages` / `tool_result_max_chars`（nullable）+ rollback |
| `releases/v1.6.0/sql/rollback/*V1.6.0_03*` | 三列 DROP（回滚零残留） |
| Spring `@Value` 新键 ×5 | `turn-timeout-seconds:0`、`turn-idle-timeout-seconds:600`、`compaction-trigger-tokens:102400`、`compaction-keep-messages:20`、`tool-result-max-chars:8192`（详见 completion.md §四，移交 M3） |
| `docker/docker-compose.yaml` | 透传 `PHOENIX_AGENT_TURN_TIMEOUT_SECONDS`/`..._IDLE_TIMEOUT_SECONDS` |

## 四、台账 / 文档

- `specs/_project/bugs.md`：BUG-70/71/72/73/74/75/76/77/81/82 状态与证据；BUG-83（落点差异→已延期 BL-26）
- `specs/_project/lessons.md`：L-17（组件可达性）、L-18（框架规格先读）、L-19（路径同源解析）、L-20（视图态按会话分片）
- `specs/_project/backlog.md`：BL-26（工作区单根收敛）、BL-27（运行配置参数形态重做）
- `releases/v1.6.0/MILESTONE.md`：第四需求行状态
