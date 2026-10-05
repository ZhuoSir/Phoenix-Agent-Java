# v1.6.0 配置变更（config changes）

> 全部**新增键均有默认值**：不配置即沿用默认，行为与冻结基线一致；需要覆盖时写入 `docker/.env`。

## 一、新增键（5）

| 键（Spring 属性） | compose/env 名 | 默认 | 语义 | 来源 |
|---|---|---|---|---|
| `phoenix.agent.turn-timeout-seconds` | `PHOENIX_AGENT_TURN_TIMEOUT_SECONDS` | `0` | 轮次**总时长闸**；`0`=**关闭**（长活轮不被总时长杀） | long-turn-resilience T-01 |
| `phoenix.agent.turn-idle-timeout-seconds` | `PHOENIX_AGENT_TURN_IDLE_TIMEOUT_SECONDS` | `600` | **空闲看门狗**：600s 无任何帧才判挂起并定稿 | long-turn-resilience T-01 |
| `phoenix.agent.compaction-trigger-tokens` | （未透传，走 `@Value` 默认） | `102400` | 压缩触发 token 数（≈0.8×128k，DSH 换算） | long-turn-resilience T-02 |
| `phoenix.agent.compaction-keep-messages` | （同上） | `20` | 压缩后保留的最近消息条数 | long-turn-resilience T-02 |
| `phoenix.agent.tool-result-max-chars` | （同上） | `8192` | 单个工具结果超过即回收为预览（原文落盘可回读） | long-turn-resilience T-02 |

- 交付包 `docker/docker-compose.yaml` 已透传前两键（`${...:-0}` / `${...:-600}`）；后三键走 Spring `@Value` 默认，交付环境如需覆盖再补 env。
- 三个上下文治理键**进实例指纹**：改值即触发运行实例重建（实测 `instanceSource` 在 `cached`/`built` 间正确切换）；留空=全局默认。

## 二、既有键语义未变

| 键 | 说明 |
|---|---|
| `PHOENIX_AGENT_WORKSPACE_ROOT` | 仍是工作区根；**根内布局变化**：v1.5.0 为 `{root}/{agentKey}/`，v1.6.0 为 `{root}/{agentKey}/{sessionId}/`（框架内再拼 `{uid}`）。键本身不用改 |
| `PHOENIX_AGENT_FILES_ROOT` | 产物 tee 副本根（`/app/uploads`），未变 |
| `phoenix.agent.runtime.max-instances` | 运行实例 LRU 上限（默认 200）。**语义影响**：v1.6.0 起实例按「智能体+会话」缓存，故上限按**会话实例**计数（高并发多会话环境可考虑上调） |

## 三、历史库噪音清理（可选，非迁移件）

扫描器过滤只防**新增**噪音；升级前的历史噪音行需按 `UPGRADE.md §A.3.3` 的 SQL 手工清理（逻辑删、可一键回滚）。dev 栈实测：存活 4643 → 清理 2499 → 剩 2144，真产物零误伤。
