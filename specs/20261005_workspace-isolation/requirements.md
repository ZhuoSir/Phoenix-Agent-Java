# Requirements: 智能体 workspace 隔离（workspace-isolation）

> 版本: v1.1.0 | 状态: **待重确认** | 确认人: 陈卓（v1.0.0）| 确认日期: 2026-10-05 | 更新: 2026-10-05
> v1.0.0→v1.1.0（用户新增要求，铁律3 回退待重确认）：「我要求的是每个智能体每个会话都有单独的文件夹，无论是写入还是读取」——v1.0.0 只做到"按智能体隔离"，实测暴露三种写入深度（file 工具落 `{agent}/{uid}/`、**shell 落 `/app`**、绝对路径落 `{agent}/` 根级），且面板读不到（BUG-78/79）→ 新增 R-06 统一到"每智能体每会话一个文件夹"
> 挂载: v1.6.0（用户「不冻结续收」现行指令；BUG-67+BUG-68 同刃双修）

## 背景与目标

**勘察终版事实（2026-10-05，容器内实证）**：
- workspace 根=`/app/uploads/agent-workspace`（持久卷 uploads，2468 文件/88MB）；构建点=HarnessAgentFactory `.workspace(Path.of(workspaceRoot))` **全智能体同根**
- 真串扰通道①：`{userId}/MEMORY.md`+`{userId}/memory/`——框架用户级长期记忆**无 agent 维**，同用户所有智能体互读互写（BUG-68 主犯）
- 真串扰通道②：workspace 根级散文件（welcome.txt/onboarding-guide.md/generate_checklist.py 等）全智能体共享（产物互串+BUG-67 文件面板收编源之一）
- **已排除**（勘察实证清白）：会话转录 `{userId}/agents/agent-{id}/sessions/`（agent 维在路径）✓ 任务区 `agents/{id}/tasks/` ✓ Postgres 状态键 userId:sessionId（跨 agent 碰撞=0）✓ Redis 分布式存储（仅 legacy HITL 智能体使用，键含 agentName）✓
- BUG-67（文件面板跨会话收编）：per-agent 隔离后跨智能体源消失，**同智能体跨会话**收编仍在（补扫时间窗=会话创建时间起）——需第二刀

**目标**：智能体 workspace 一刀切按 agent 隔离（记忆/产物/索引全落各自专属根），文件面板只显本会话产物；存量混合数据按用户裁决策略迁移。

**成功画面**：同一用户在智能体 A 教的事，智能体 B 不再"记得"；B 的文件面板只有 B 会话的产物；升级迁移后旧数据归档可查、新结构干净。

## 需求条款（EARS）

### R-01 workspace 按智能体隔离
WHEN 构建任何库驱动智能体, 系统 SHALL 以 `{workspaceRoot}/{runtimeKey}` 为该智能体专属 workspace 根（runtimeKey=sn 或 agent-{id}，既有稳定标识），其用户 home/长期记忆/任务/产物/索引路径 SHALL 全部落于专属根内；不同智能体 SHALL 互不可见对方文件与记忆。

**验收场景**
- GIVEN 智能体 A、B 同用户对话 WHEN A 写入记忆/产物 THEN B 的会话中不可见不可读（文件层面+模型回答层面双证）。
- GIVEN 升级后新对话 WHEN 检查磁盘 THEN A/B 各自目录树独立，根级无新增散文件。

### R-02 长期记忆隔离（BUG-68 主症状）
不同智能体下同一用户的 MEMORY.md/memory 区 SHALL 相互独立；智能体 SHALL 不再读到其它智能体的记忆内容。

**验收场景**
- GIVEN 用户在 A 会话中说"记住暗号 alpha" WHEN 切到 B 新会话问暗号 THEN B 答不出 alpha（A 的 MEMORY.md 在 A 专属根内）。

### R-03 会话文件归属（BUG-67）
文件面板 SHALL 仅显示本会话产生的文件：补扫归属判定 SHALL 从「会话创建时间窗」改为防跨会话收编的精确规则（已归属其它会话的文件排除，或轮次边界窗——plan 定案）；per-agent 隔离后跨智能体收编源天然消失。

**验收场景**
- GIVEN 同智能体两个会话 S1、S2 WHEN S2 打开文件面板 THEN 仅见 S2 轮次产物，S1 文件不出现（即使 mtime 晚于 S2 创建）。

### R-04 存量迁移
升级 SHALL 将现共享根存量（{userId}/ 树、根级散文件、agents/ 树）整体迁入归档目录 `_legacy_shared/`（不被任何智能体读取，保留人工回查/下载）；各智能体从干净专属根开始（长期记忆重置——Q1 已裁决按推荐则执行）。迁移 SHALL 幂等可重入、失败不阻塞启动（WARN+降级空目录）。

**验收场景**
- GIVEN 带存量数据升级 WHEN 启动完成 THEN `_legacy_shared/` 含原 88MB 数据、新根为 per-agent 空树、服务 healthy、二次重启不重复迁移。

### R-06 每会话独立目录（读写统一，v1.1.0 新增 · 用户要求）
每个「智能体 × 会话」SHALL 拥有**唯一目录**，且**所有读写路径统一**到该目录：

```
{PHOENIX_AGENT_WORKSPACE_ROOT}/{agentKey}/{sessionId}/
        agentKey = sn（非空）否则 agent-{id}
```

- **写**：file 工具（相对路径）、shell/exec（**cwd 必须=该目录**，修复 BUG-79 当前 `/app`）、python/脚本、下载落盘 —— 全部落该目录
- **读**：file 工具读取、文件面板列表、产物登记与扫描（修复 BUG-78）—— 全部以该目录为唯一根
- **会话间隔离**：同一智能体的不同会话互相不可见（A 会话文件不出现在 B 会话）
- **存量兼容**：历史文件不强制迁移；面板对旧会话保留"legacy 只读可见"（不丢数据）

**验收场景**
- GIVEN 智能体 A 会话 S1 WHEN 分别用 file 工具、shell、脚本各写一个文件 THEN 三者**同处** `{root}/{A}/{S1}/` 且面板三条全可见
- GIVEN 同智能体另开会话 S2 WHEN 打开面板 THEN 看不到 S1 的任何文件
- GIVEN 旧会话（隔离前产物）WHEN 打开面板 THEN 旧文件仍可见（legacy 只读兼容）
- GIVEN shell 执行 `pwd` WHEN 会话 S1 THEN 输出=该会话目录（不再 `/app`）

**实现取舍（必须在确认①裁决）**：框架的 workspace 根在**构建期**绑定，要做到"按会话"须让实例按 (agent, session) 构建（注册表键加入 sessionId）——
- 代价：每个会话首次对话多一次构建（含 MCP 初始化的智能体约 +0.2~2s），实例缓存改按 (agent, session) LRU
- 替代：保持按智能体构建，仅 shell cwd 按轮注入（file 工具仍落 `{uid}`）→ **不满足"读写统一"**，故不推荐
- 推荐：**接受按会话构建成本**（正确性优先；缓存 LRU 控内存）

### R-05 对面零变化（身份矩阵）
legacy Java 自注册智能体 workspace（默认根 .agentscope/workspace）、REMOTE 策略 Redis 存储、Postgres stateStore 键、会话转录/任务区既有结构、技能加载/产物下载/HITL/断线续传等全部消费方 SHALL 行为不变（L-06 消费方枚举 plan 全量列）。

**验收场景**
- GIVEN 升级后 WHEN legacy 智能体对话/技能加载/文件下载/断线续传回归 THEN 全绿（verify 13/13 + 专项抽查）。

## Non-goals（范围外）
- PostgresAgentStateStore 键加 agentId 维（已实证零串，无需）
- legacy 智能体 workspace 治理/其 Redis 键清理
- 存量混合记忆的内容级拆分（多智能体记忆混写一文件，不可靠拆——仅归档）
- 分布式多机 workspace 共享（单机卷语义不变）
- 文件面板 UI 改动（归属逻辑在服务端扫描层）

## 我正在做的假设
- **A-1** 改动核心=factory workspace 根拼接一处 + 扫描器归属规则 + 启动迁移器；框架内部路径布局不动（换根即全隔离）
- **A-2** runtimeKey 稳定性：sn 不可变（唯一注册约束）/agent-{id} 天然稳定——智能体改名不影响目录归属
- **A-3** uploads 卷与 compose 挂载不变（目录结构在卷内重组）
- **A-4** MCP 变体实例（buildUncached 走同一 factory）自动继承新隔离，无需另改

**现在纠正，否则按此执行。**

## 待确认问题（阻塞项，确认①前请裁决）
- ~~Q1 存量策略~~ → **归档重置**（用户「按推荐」2026-10-05）
- ~~Q2 记忆重置副作用~~ → **接受**（用户「按推荐」；聊天面板历史不受影响已明示）
