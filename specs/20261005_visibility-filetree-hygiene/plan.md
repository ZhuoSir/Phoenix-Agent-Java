# Plan: visibility-filetree-hygiene

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05
> 上游: `requirements.md` v1.0.0（已确认①·陈卓 2026-10-05）｜挂载: v1.7.0｜特性分支: `feature/visibility-filetree-hygiene`
> Q1~Q6 按 requirements §七 的**暂定口径**设计（Q1 首帧超时 180s/总上限关；Q2 只报秒数；Q3 空文件夹不做；Q4 历史行单列；Q5 fib.py 删·diagrams 入库·bak 删；Q6 心跳覆盖工具期）

## 一、技术方案总览（三组互不耦合，可分批交付）

| 组 | 主刀文件 | 依赖 | 需 DDL |
|---|---|---|---|
| R-01 静默可见性 + 模型调用超时 | `HarnessTurnManager`（阶段标记/心跳/超时）、`HarnessChatServiceImpl`（模型调用事件回灌）、`HarnessEventMapper`（帧键）、前端两面（run 页 + 前台 chat） | 无 | **否** |
| R-02 会话文件树 | `AgentFileService(.Impl)`（树查询）、`AgentFileController`（新端点）、`ChatFilesPanel.vue`（树 UI，两面共用） | 无 | **否** |
| R-03 工程清账 | `.gitignore`、`PhoenixAgentProperties`、`specs/_project/backlog.md`、工作区残留文件 | 无 | **否** |

**零 DDL、零新增表** → 无 Flyway 件，回滚=回镜像（`releases/v1.7.0/sql/` 预期为空）。

## 二、R-01 设计（BUG-85）

### 2.1 为什么现有看门狗形同虚设（根因）
- 现判据只有 `lastActivityAt`（**任一帧**脉冲）＋ `turn-idle-timeout-seconds=600`。今天实测单次模型调用静默 **503s < 600s**，就"刚好"没被杀；而总时长闸 `turn-timeout-seconds=0`（设计关闭）⇒ 34 分钟长轮无人管。
- 关键缺口：**系统不知道"此刻在等模型"还是"在跑工具"**——两者都是"无帧"。所以不能简单把空闲阈值调小（会误杀长工具）。

### 2.2 方案：给轮次加"阶段"概念 + 心跳
1. **阶段标记（新增）**：`HarnessChatServiceImpl` 已在事件循环里识别 `ModelCallStartEvent/ModelCallEndEvent`（现仅打日志）→ 改为同时通知轮次：`turnManager.onPhase(sessionId, MODEL|TOOL, detail)`。TurnManager 每轮保存 `phase` 与 `phaseSince`。
   - `MODEL`：模型调用在飞；`TOOL`：工具/子代理执行中；`IDLE`：帧间隔中的其它阶段。
2. **静默心跳（新增 janitor 分支）**：复用现有 janitor 调度（`janitor`/`mergeJanitor` 同款 `Disposable`），按 `silence-heartbeat-seconds`（默认 **15s**）检查：若 `now - lastActivityAt ≥ 阈值`，下发**心跳帧**：
   `{ "content":"", "thinking":"", "silenceMs": 23000, "phase":"MODEL", "phaseLabel":"模型调用中" }`
   - **节流**：心跳自身 ≤1 帧/5s（`HEARTBEAT_MIN_GAP_MS`），且**不进** `framesWithText`；`framesEmitted` 计入但设单独计数器 `heartbeats`，金丝雀行打印 `heartbeats=`，便于对比今日量级（179917 源帧 / 5481 下发）。
3. **首帧超时（新增定稿原因）**：若 `phase==MODEL` 且 `now - phaseSince ≥ model-first-frame-timeout-seconds`（默认 **180s**，**0=关**）→ `finish(STATUS_MODEL_TIMEOUT, "\n\n⏱ 模型调用超时（180s 无任何响应），已中止本轮；已生成内容保留")` + `sourceSub.dispose()`（真中止，不挂死线程）。
   - **不误杀**：仅当阶段确为 MODEL 且从**调用发起**起算；工具期（TOOL）不适用该阈值（工具期只发心跳）。
   - **与 BUG-77 重试的关系**：重试只在"零产出"且签名可重试时发生一次（现状）；超时定稿路径**不走重试**（避免再等 180s），定稿文案明确可感知。
4. **配置**（env 带默认，均为既有 `@Value` 风格）：
   | 键 | 默认 | 语义 |
   |---|---|---|
   | `phoenix.agent.model-first-frame-timeout-seconds` | `180` | 模型调用无首帧即中止；**0=关** |
   | `phoenix.agent.silence-heartbeat-seconds` | `15` | 静默多久开始发心跳（阈值本身） |
   | `phoenix.agent.turn-idle-timeout-seconds` | `600`（既有） | 兜底总空闲闸（不动，保留最后一道） |
5. **前端**：两面（`components/run/index.vue` 与 `views/front/components/ChatMessages.vue`）读新帧键 → 在"正在执行（mm:ss）"旁显示 **「· 模型调用中 · 已静默 23s」（或 工具执行中）**；join 追流路径同样透传（帧键走 mapper 白名单，与 `agentFiles`/`thinking` 同策略）。

### 2.3 验证方式（可复现，不靠运气）
- **静默复现**：本地起一个 OpenAI 兼容 **stub 模型服务**（python http，可配延迟 N 秒才回响应），在模型配置里加一条指向它 → 真实走 harness 路径。
  - N=30s → 观察心跳帧按 15s 起出现、前端显示秒数递增、轮次最终正常定稿（不超时）。
  - N=200s → 观察 180s 触发**模型调用超时**定稿、文案正确、已生成内容保留、`turn/status` 收敛 false。
- **不误杀**：长工具（`sleep 200` 的 execute）**不得**被首帧超时杀掉（阶段=TOOL），且心跳显示"工具执行中"。
- **帧量回归**：金丝雀 `frames/emitted/heartbeats/dropped/textFrames` 与今日基线对比，确认无恶化。

## 三、R-02 设计（BL-28）

### 3.1 接口（新增，向后兼容）
```
GET /api/agent/files/tree?sessionId=<uuid>&path=<相对目录，默认根>&scan=false
→ ReturnVo<{ root: {name, path}, entries: [ {type:'dir'|'file', name, path, dirCount?, fileCount?, id?, sizeBytes?, mime?, source?, createTime?} ] }>
```
- **懒加载**：只返回 `path` 这一层（服务端按前缀过滤），大目录（今日清理前 `0690b2d0` 有 3001 行）也不会一次吐几千条。
- `scan=true` 保留抽屉补扫语义（与既有 `GET /api/agent/files` 一致）。
- 既有 `GET /api/agent/files`（平铺）**保留不动**（兼容外部/旧前端）；前端切到 tree。

### 3.2 相对路径解析（核心，三代 store_key 兼容）
`store_key` 形如 `{prefix}/{会话内相对路径}:{size}`，解析规则（**单一实现放 `WorkspacePaths` 旁边的新工具类**，供面板与测试复用）：

| 代 | store_key 形态 | 取相对路径的规则 |
|---|---|---|
| 三代（R-06 后） | `{agentKey}/{sessionId}/{…}:size` | 去掉 `{agentKey}/` + `{sessionId}/` 前缀 |
| 二代（agent 分根后） | `{agentKey}/{uid}/{…}:size` | 去掉 `{agentKey}/` 前缀 → 归入「历史文件」节点 |
| 一代（最早） | `{uid}/{…}:size` | 原样 → 归入「历史文件」节点 |
- 会话 UUID 段用正则识别（36 位、4 个连字符）与 `sessionId` 比对；不是本会话段即视为历史。
- **隐藏规则**：任意目录段以 `.` 开头 → 隐藏；并复用扫描器同名名单（`sessions/tasks/.index/memory/large_tool_results`）；文件级 `call_*` 前缀隐藏。**两侧共用同一常量**（从 `WorkspaceArtifactScanner` 抽出为公共工具，防两处漂移 → 落实 L-19「同源解析」）。
- 目录节点计数：为该层每个 dir 顺带统计 `dirCount/fileCount`（一条 SQL 分组即可，避免 N+1）。
- **空文件夹**（Q3 暂定不做）：库里无行即无节点；若将来要做，再加"扫盘补空目录"开关。

### 3.3 前端（`ChatFilesPanel.vue`，run 页与前台共用 → 天然两面一致）
- 顶部**面包屑**（会话目录 → 子目录…）+ 返回上级；列表**文件夹在前**（图标+名称+内含文件数）、文件在后（沿用现下载/删除按钮与大小/MIME）。
- 进入文件夹=改 `path` 重新拉该层（懒加载）；`FILES_CHANGED_EVENT`（SSE 产物推送）触发**当前层**刷新。
- 状态：`currentPath`、`entries`、`loading`；切会话重置到根。
- 兼容：`temp-` 临时会话的空面板提示保留。

### 3.4 验证方式
- 夹具会话造：`{uid}/a/b/c.svg`、根级散文件、`.pylibs/x.py`、`large_tool_results/call_1`、`call_2`、外加手工插入"二代 store_key"行 → 断言：树层级正确、噪音全隐、历史文件单列、下载/删除可用、进入/返回正常。
- 大目录：用 `0690b2d0`（594 件真产物）实测首层加载时延与滚动可用性（R-04.3 口径：首层 ≤1s、展开 ≤500ms，本地 dev 栈为准）。

## 四、R-03 清账清单（逐条可核）
| # | 动作 | 证据/口径 |
|---|---|---|
| 1 | `.gitignore` 增 `.mvn-home/`、`.pnpm-store/`（并按 Q5 决定 `scripts/`、`diagrams/`） | `git status` 前后对照 |
| 2 | 删 `WSL`、`或在`（0 字节误建）、`AGENTS.md.bak.20260927101837` | `ls`/`git status` 复核 |
| 3 | `scripts/fib.py` 删（测试夹具）；`diagrams/phoenix-architecture.html` 入库（Q5 口径） | 前者删后 `git status` 无该条；后者 `git add` |
| 4 | 删 `PhoenixAgentProperties.skillPath` 字段（零引用已确证） | 编译绿 + `grep getSkillPath` 0 命中 |
| 5 | backlog §四 两条注记修正（agent-config 13/13 已勾 · v1.6.0 已发布合 main · 在途=v1.7.0） | 台账闸绿 |
| 6 | `RulesHarnessAgent.java` 旧注记销账（已无未提交改动） | `git status` 空 |

## 五、共享面（身份矩阵，防"改 A 面伤 B 面"）
| 对象 | 面 1 | 面 2 | 对面断言 |
|---|---|---|---|
| 心跳/超时帧 | 首发流（本页 SSE） | join 追流（刷新后） | 两路都能看到心跳与定稿；仅一路可见=不通过 |
| 文件树归属 | 会话目录面 | 历史/跨用户面 | 本会话树不含他会话目录；历史行只在「历史文件」节点；跨用户仍 42031 |
| 隐藏规则 | 面板展示 | 扫描登记（`WorkspaceArtifactScanner`） | **同一常量来源**（抽出公共工具后两侧一致） |
| 清账 | 工作树 | `git status`/ignore | 清完仍能 `mvn package` 绿 + 前端 build 绿 |

## 六、风险与对策
| 风险 | 对策 |
|---|---|
| 首帧超时误杀慢模型/长工具 | 仅 MODEL 阶段生效；默认 180s 保守；`0=关` 可一键回退；TOOL 期不适用 |
| 心跳又造帧风暴 | 心跳单独计数 + ≤1 帧/5s 节流；金丝雀行新增 `heartbeats=` 便于回归对比 |
| `store_key` 三代解析错位 | 解析器唯一实现 + 三代夹具各一；解析失败的行**不静默丢弃**（归「历史文件」） |
| 树接口引入 N+1 / 大响应 | 单层返回 + 分组计数一次 SQL；不做全树递归查询 |
| 清账误删交付物 | Q5 已定去向；`diagrams/` 入库而非忽略；`scripts/` 仅删确认夹具 |
| 前端两面行为漂移 | 面板是**同一组件**（`ChatFilesPanel.vue`），两侧只传 `sessionId` |

## 七、非目标（明确不做）
BL-26 工作区单根（C 方案）· BL-27 运行配置参数形态 · BL-29 Ollama · BL-25 API 插件 · BUG-69（观察中）· 任何工作区布局/迁移变更（本 spec **零 DDL、零布局改动**）

## 八、交付顺序（建议）
1. **R-03 清账**（零风险、独立，先清干净再动代码，避免脏工作树干扰验证）
2. **R-02 文件树**（后端解析器+接口 → 前端树 UI → 夹具实测）
3. **R-01 静默可见性**（阶段标记 → 心跳 → 首帧超时 → 前端显示 → stub 模型实测）
4. 收口：verify 13/13 + typecheck 基线 + 台账（BUG-85/BL-11/12/15/28 翻账）

## 九、确认记录
- **② 确认（2026-10-05，陈卓）**：plan v0.1.0 → **v1.0.0 已确认**；Q1~Q6 暂定口径随本 plan 一并生效（首帧超时 180s/总上限关·心跳只报秒数·空文件夹不做·历史行单列·fib.py 删+diagrams 入库+bak 删·心跳覆盖工具期）；进 Phase 3。
