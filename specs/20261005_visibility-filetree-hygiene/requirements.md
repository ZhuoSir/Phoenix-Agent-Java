# Requirements: visibility-filetree-hygiene

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05
> 挂载: **v1.7.0**（v1.6.0 已于 2026-10-05 发布 tag 并 `--no-ff` 合入 main；在途唯一 ⇒ v1.7.0 待立项双建）
> 来源: 用户 2026-10-05 口述「BUG-85 和 BL-28，还有 BL-12/15/11、§四 两条过期注记 + agent-config 历史快照销账，**列为一个 spec，新建**」

## 一、背景与问题（全部实测取证）

### 1.1 长轮静默不可见（BUG-85，高）
- 实测（会话 `6e9c09e0`「梅西蓝白 logo」长轮）：该轮 132 次模型调用、最长单次仅 2m11s；但 `17:39:34.717 ModelCallStartEvent(messages=27)` 之后**直到 17:47:57.666 才出现下一个事件（间隔 503 秒）**，其间**无 ModelCallEnd、无 WARN/ERROR/重试**（全量日志 0 条）→ TurnManager 无帧 → SSE 无输出 → 界面只剩「正在执行(mm:ss)」计时器，**用户判定"任务僵死"**。
- 看门狗为何不杀：空闲判据 600s（503 < 600，刚好不触发）＋总时长闸=0（设计上关闭）⇒ 34 分钟长轮按设计允许；该轮最终**自行恢复并正常收尾**（`status=done`、正文 2948 字）。
- 同轮金丝雀：`frames=179917 / emitted=5481 / dropped=47279 / textFrames=1280` —— 合并闸压缩 ≈33:1（客户端≈3.5 帧/秒），**前端未被压垮**；`dropped` 是空生命周期帧的设计内丢弃。

### 1.2 文件面板平铺（BL-28，中）
- `AgentFileServiceImpl.listBySession` 按会话**平铺查库**（orderBy create_time desc），VO 仅 `id/fileName/sizeBytes/mime/source/createTime` → 前端只能平铺；而智能体实际**按文件夹产出**，面板看不出结构。
- 目录层次在库中**可还原**：`store_key` 形如 `{agentKey}/{会话内相对路径}:{size}`（例 `agent-33/{sessionId}/{uid}/a/b.svg:1234`）；`rel_path` 是 tee 布局（`{agentId}/{sessionId}/{随机前缀}_{name}`）**不可用于展示**。

### 1.3 工程清账（BL-11/12/15 + §四 销账）
- **未跟踪噪声**（`git status --porcelain`）：`.mvn-home/`、`.pnpm-store/`、`AGENTS.md.bak.20260927101837`、`diagrams/`、`scripts/`、`WSL`（0 字节）、`或在`（0 字节，疑似误建 shell 重定向产物）。
- **死配置确证**：`PhoenixAgentProperties.skillPath` 字段**零引用**（`getSkillPath` 全仓 0 命中；`SkillZipSanitizer` 里的是同名局部变量，与配置无关）。
- **残留复核**：`RulesHarnessAgent.java` **已无未提交改动**（旧注记已过期，可销）。
- **过期注记**：backlog §四 的 agent-config 条目写「13 任务已勾 8 个、未勾 T-05/10/11/12/13」——**实测当前 13/13 全勾**（0 未勾），且已随 v1.2.0 发布、本地无该 feature 分支；里程碑注记停在「已冻结」，而 v1.6.0 已发布并合 main。

## 二、范围与非目标

**范围内**：R-01 长轮静默可见性与模型调用超时（BUG-85）｜R-02 会话文件文件夹树（BL-28）｜R-03 工程清账与台账销账（BL-11/12/15 + §四）
**非目标**：BL-26 工作区单根（C 方案）· BL-27 运行配置参数形态重做 · BL-29 Ollama 接入 · BL-25 API 插件 · BUG-69（留存观察中）· 任何工作区布局变更（R-02 只改**展示层**与**只读接口**）

## 三、需求（EARS）

### R-01 长轮静默可见性（含模型调用超时）
- **R-01.1** When 单次模型调用已发起且超过 `phoenix.agent.model-first-frame-timeout-seconds`（默认建议 **180s**）仍未收到任何首帧/增量，the system **shall** 中止该调用并以**明确原因文案**定稿本轮（新增"模型调用超时"类，与既有五分类并存）。
- **R-01.2** While 单次模型调用或工具执行进行中且静默时长达到 `phoenix.agent.silence-heartbeat-seconds`（默认建议 **15s**），the system **shall** 周期性下发**静默进度帧**（含已静默秒数；可选当前步骤/工具名），使前端可显示「仍在执行（已静默 Ns）」而非只有 mm:ss 计时器。
- **R-01.3** The 静默进度帧 **shall** 受节流约束（建议 ≤1 帧/5s，走既有合并闸口径），且 **shall not** 使 `framesSeen/emitted` 指标恶化到今日量级之上（防重演帧风暴）。
- **R-01.4** When 因 R-01.1 中止，the system **shall** 保留已生成内容（不丢），且 joining 客户端（join 追流）**shall** 能收到心跳与最终定稿。
- **R-01.5** The 阈值 **shall** 可配置（env 带默认）；默认行为 **shall not** 误杀正常的长思考/长工具调用（默认值须显著大于常见单步耗时）。

### R-02 会话文件文件夹树
- **R-02.1** When 用户打开会话文件面板，the system **shall** 返回以**会话目录为根**的文件夹树（每层含子文件夹与文件）。
- **R-02.2** When 用户点开某文件夹，the system **shall** 展示该层内容（支持进入/返回；**文件夹在前、文件在后**）。
- **R-02.3** The 树 **shall** 隐藏框架内部目录与占位：`.pylibs`、`.skills-cache`、`.agentscope`、`.index`、`large_tool_results`、`memory`、`sessions`、`tasks` 与 `call_*` 文件。
- **R-02.4** When 存在历史行（旧 `store_key` 无会话段，库里三代格式并存），the system **shall** 给出**可解释的归位**（单列「历史文件」节点，或按 plan 决策的等价方案），**shall not** 静默丢弃。
- **R-02.5** The 既有能力（下载 / 逻辑删除 / 面板刷新广播）**shall** 保持不变（row id 与接口语义不变）。
- **R-02.6** The 树 **shall** 规模受控（默认折叠 + 懒加载/分页），大目录（数千文件，如已清理前的 `0690b2d0` 会话）**shall** 不导致面板卡死。

### R-03 工程清账与台账销账
- **R-03.1** The `.gitignore` **shall** 覆盖构建/缓存/临时产物（至少 `.mvn-home/`、`.pnpm-store/`），使 `git status` 仅剩有意保留项。
- **R-03.2** When 清理工作区残留，the system **shall** 删除确认为误建/一次性条目（`WSL`、`或在` 两个 0 字节文件、`AGENTS.md.bak.*`），并 **shall** 对 `scripts/`、`diagrams/` 给出明确去向（入库/删除/忽略，见 Q5）。
- **R-03.3** The 死配置 `PhoenixAgentProperties.skillPath`（含访问器）**shall** 删除，且编译通过、运行行为零变化。
- **R-03.4** The backlog §四 **shall** 修正过期注记：agent-config 条目（13/13 已勾、历史快照、分支不存在）＋里程碑条目（v1.6.0 已发布并合 main；在途=v1.7.0）。

### R-04 验收与回归
- **R-04.1** The 本 spec **shall** 不减损既有基线：`docker/scripts/verify.sh` **13/13**、`vue-tsc` typecheck **213 条预存 / 0 新增**。
- **R-04.2** When 长轮实测（≥10 分钟、含工具阶段），界面 **shall** 始终可区分"在算"与"卡死"（静默期有秒数可见），且心跳 **shall not** 引入可感卡顿。
- **R-04.3** When 打开大目录会话的文件面板，the system **shall** 在 plan 约定的时延内可用（口径在 plan 定）。

## 四、开放问题（**plan 前需用户裁决**）

| # | 问题 | 建议 |
|---|---|---|
| Q1 | 模型调用超时默认值：只设"首帧超时"（180s）还是同时设"单次总上限"（如 600s）？ | 前者必做；后者默认关（与总时长闸=0 口径一致） |
| Q2 | 静默心跳形态：只报"已静默 Ns"，还是带"当前步骤/工具名"？ | 先做秒数（成本低、可观测性已够）；步骤名列为可选增强 |
| Q3 | **空文件夹**是否展示？（DB 只登记有内容的文件 → 空目录天然不在库里；要显示需扫磁盘目录树） | 不做（成本↑、收益低）；如要做请明示 |
| Q4 | 历史行（旧 `store_key`）如何呈现？ | 单列「历史文件」节点（不改归属，只改展示） |
| Q5 | `scripts/fib.py`、`diagrams/phoenix-architecture.html`、`AGENTS.md.bak.*` 的去向？ | fib.py=删（测试夹具）；diagrams 入库（交付文档）；bak 删 |
| Q6 | 心跳是否也覆盖**工具执行期**（今日 503s 静默在模型调用期，工具期同样会静默）？ | 覆盖（同一节流口径） |

## 五、风险
- `store_key` **三代格式**并存（`{uid}/…`、`{agentKey}/{uid}/…`、`{agentKey}/{sessionId}/…`）→ 解析器需夹具覆盖三代。
- 超时阈值过激会**误杀慢模型**（本地/Ollama 场景 = 未来 BL-29）；默认值须保守 + 可配。
- 心跳帧与 **join/replay** 的兼容：joining 客户端须能收到心跳与定稿（不得只在首发流可见）。
- `.gitignore` 若误忽略业务文件（`scripts/`、`diagrams/` 可能含交付物）→ 先定 Q5。
- 树接口若直扫磁盘会引入**IO 放大**（大会话数千文件）→ 以 `store_key` 解析为默认，扫盘仅用于 Q3 若采纳。

## 六、验收方式（预告，plan 细化）
- **R-01**：真实长轮 + 人为注入静默（桩/慢模型）→ 心跳可见、超时文案明确、内容不丢；帧速回归按金丝雀口径留档。
- **R-02**：夹具会话（多层目录 + 噪音文件 + 三代 store_key 历史行）→ 树结构/隐藏项/懒加载/下载删除回归实测。
- **R-03**：`git status` 前后对照、编译绿、台账闸（`mdtable_check`）全绿、`verify 13/13` 不回退。

## 七、裁决记录（2026-10-05 确认①）
- 用户口令「**确认**」→ 本 requirements v0.1.0 → **v1.0.0 已确认（陈卓，2026-10-05）**，进 Phase 2（plan）。
- **Q1~Q6 用户未逐条答复**：按 §四 的 agent 建议口径**暂定执行**，并在 plan 中作为明确设计输入；若与预期不符，请在 ② 关口一并纠正（改 Q 需回改本文档并重走确认）。
  - 暂定：Q1 只设首帧超时 180s（单次总上限默认关）；Q2 心跳先只报秒数；Q3 空文件夹不做；Q4 历史行单列「历史文件」节点；Q5 fib.py 删 / diagrams 入库 / bak 删；Q6 心跳覆盖工具执行期。
