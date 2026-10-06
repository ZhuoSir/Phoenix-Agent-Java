# Artifacts: visibility-filetree-hygiene

> 版本: v1.0.0 | 生成: 2026-10-05/06 | 挂载: v1.7.0

## 代码面（净新增/改动）
| 层 | 文件 | 说明 |
|---|---|---|
| 后端·工具 | `util/SessionWorkspaceFilters.java`（**新增**） | 内部件判定单一实现（目录名单∪点开头 / 文件名单∪`call_*`∪点开头无扩展名∪内部后缀 / 会话 UUID 判据） |
| 后端·工具 | `util/SessionFileTree.java`（**新增**） | 三代 `store_key` 解析（SESSION/OTHER_SESSION/NO_SESSION）+ `foldUserNamespace`（循环折叠 `{uid}`） |
| 后端·服务 | `service/file/AgentFileServiceImpl.java` | `treeLevel()` 单层树查询；`existsByStoreKeyAnySession` 改**原生 SQL**（BUG-87）；物化文件回落；`canAccessSession`/`requireSessionOwner` 沿用 |
| 后端·接口 | `service/file/AgentFileService.java` | 新增 `TreeNode`/`TreeLevel`/`treeLevel` + `HISTORY_PATH`/`HISTORY_NAME` 常量 |
| 后端·接口 | `service/file/WorkspaceArtifactScanner.java` | 内部件判据改为**同源引用**共享工具（删 5 个局部名单常量）；会话 UUID 判据同源 |
| 后端·REST | `controller/AgentFileController.java` | 新增 `GET /api/agent/files/tree`；`scan` 补扫抽为 `maybeScan`（平铺与树共用） |
| 后端·轮次 | `harness/turn/HarnessTurnManager.java` | 阶段常量与字段、`onPhase`/`onPhaseIfNot`、静默心跳（≤1 帧/5s + `heartbeats` 计数）、首帧超时（`model_timeout` + 文案落库） |
| 后端·对话 | `harness/send/impl/HarnessChatServiceImpl.java` | 注入 `HarnessTurnManager`；ModelCall/ToolCall/ToolResult 生命周期 → 阶段回灌 |
| 后端·配置 | `docker/docker-compose.yaml` | 透传 `PHOENIX_AGENT_SILENCE_HEARTBEAT_SECONDS` / `PHOENIX_AGENT_MODEL_FIRST_FRAME_TIMEOUT_SECONDS` |
| 前端·API | `api/core/agentFiles.ts` | `getAgentFileTreeApi` + `AgentFileTreeNode`/`AgentFileTreeLevel` + `HISTORY_PATH` |
| 前端·组件 | `views/front/components/ChatFilesPanel.vue` | 树形浏览（面包屑/返回/文件夹优先/单层懒加载/历史节点）；下载/预览/删除不变 |
| 前端·组件 | `views/front/components/ChatMessages.vue` | 静默提示（前台 chat） |
| 前端·组件 | `components/run/index.vue` | 静默提示（run 页，按会话 `silenceText`）；`applySilenceFrame` 由 `useSessionStateManager` 导出 |
| 前端·共享 | `packages/chat-shared/src/stores/chat.ts` | 会话级 `silenceByS`（心跳帧不入消息列表） |
| 工程/台账 | `.gitignore`、`PhoenixAgentProperties`、`specs/_project/{bugs,backlog,lessons}.md` | 清账（T-01~T-03）与台账翻账 |

## 数据库/配置面
- **零 DDL**（本 spec 无 `releases/v1.7.0/sql` 件）；回滚=回镜像
- 新增两个环境键（均有默认值）：`SILENCE_HEARTBEAT_SECONDS=15`、`MODEL_FIRST_FRAME_TIMEOUT_SECONDS=180`（0=关）
- 数据面一次性动作：BUG-87 复活行按"删除意图优先"回填 4 行（回滚名单 `/tmp/bug87_ids.txt`，同谓词可回滚）

## 夹具/证据件
- `fixture/TreeProbe.java`——三代 `store_key` + 隔离 + 折叠 14 用例（`PASS=14 FAIL=0`）
- 旁路验证法：`docker compose run --rm -d -p <host>:8066 -e <覆盖> backend`（复用镜像，不动生产配置）
