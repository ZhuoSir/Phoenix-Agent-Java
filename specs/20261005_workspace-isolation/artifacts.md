# Artifacts: workspace-isolation

> 版本: v1.0.0 | 更新: 2026-10-05

## SQL
零 DDL/零 DML/零 DB 重写（下载走 tee 副本实证，workspace 搬家不碰 tbl_data_agent_file）。

## 后端（5 文件：2 新 3 改，前端零改动）
- 新增 phoenix-agent-core/util/WorkspacePaths.java（runtimeKey/agentRoot 单一实现，factory/scanner/migration 三方同源）
- 新增 phoenix-agent-core/service/file/WorkspaceMigrationRunner.java（ApplicationRunner 归档迁移：允许集 DB 实时查/只 move 不删/冲突加时间戳/失败不阻塞/幂等）
- 改 harness/factory/HarnessAgentFactory.java（.workspace 下沉 {root}/{runtimeKey}；runtimeKey 委托 WorkspacePaths）
- 改 service/file/WorkspaceArtifactScanner.java（扫描根同源下沉；storeKey 加 runtimeKey 前缀防跨 agent 误撞）
- 改 service/file/AgentFileServiceImpl.java（existsByStoreKeyAnySession 全局首占）
- 改 agent-rest/controller/AgentFileController.java（补扫窄窗=最近 assistant 消息起，无消息不补扫）

## 数据/现场（dev 栈已生效）
- /app/uploads/agent-workspace/_legacy_shared/ ← 88MB 存量归档（16 项）
- /app/uploads/agent-workspace/agent-36/ ← 新结构首例（随用随建）

## 文档/台账
- requirements/plan/tasks 均 v1.0.0 已确认；changelog 全程；completion/artifacts 本件
- bugs：BUG-67/68 翻已验证(v1.6.0在途)；MILESTONE 第三需求行；记忆重置提示移交 M3 RELEASE-NOTES/UPGRADE

## v1.1.0 增量改动面（R-06 会话级工作区，2026-10-05）
| 文件 | 归属 | 变更 |
|---|---|---|
| `util/WorkspacePaths.java` | **WS 独有（新）** | `sessionRoot(root, agentKey, sessionId)` |
| `harness/factory/HarnessAgentFactory.java` | **WS+LTR 共有文件**：`.workspace`/`.project`/会话目录预建=**WS**；compaction 两级回退=**LTR T-02** | 布局行 vs 配置行，互不重叠 |
| `harness/factory/HarnessAgentRegistry.java` | **WS+LTR 共有文件**：会话缓存键 `agentId@sessionId`/会话重载/`invalidate`=**WS**；三列进指纹=**LTR T-02** | 同上 |
| `harness/mcp/McpMountService.java` | WS | 变体键含 sessionId（MCP 会话不回落共享根） |
| `harness/send/impl/HarnessChatServiceImpl.java` | WS | 对话/确认续跑按（智能体,会话）取实例 |
| `service/file/WorkspaceArtifactScanner.java` | **WS**（BUG-78/82 亦落此文件） | 扫描根=会话目录；runtimeKey 取库；storeKey 归一；`call_*`/点目录/`large_tool_results` 过滤 |
| `service/file/AgentFileService(.Impl).java`、`agent-rest/controller/AgentFileController.java` | WS 区（BUG-80） | `canAccessSession`（属主或管理员）+ 孤儿行回落创建者 |

### 归属对账（与 long-turn-resilience 的边界，2026-10-05）
- **文件夹布局的唯一 owner 是 workspace-isolation**（v1.0.0 智能体级 → v1.1.0 会话级 + 迁移 + shell cwd + 扫描/读路径）。`long-turn-resilience` **从未改动布局**，仅在工厂/注册表两个文件里改**配置与指纹**（不同代码区）。
- 两 spec 的提交在**同一条分支**上交错（R-06 实现期间 LTR 尚未收口），故 `f7a0964`(R-06+BUG-80) 等提交跨了归属；`5b78f1f`(BUG-78 扫描器) 虽记在 LTR 批次、逻辑归属 **WS**。
- 合并/发版时的口径：布局变更按**一件整体变更**写进 v1.6.0 UPGRADE（含记忆重置提示），不按两 spec 拆成两条互相引用的说明。
