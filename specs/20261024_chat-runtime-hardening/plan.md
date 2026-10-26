> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-24 | 确认人: 陈卓 | 确认日期: 2026-10-24

# 技术方案：对话运行时加固（chat-runtime-hardening）

## 一、根因诊断（三处缺陷，只读调查结论）

### BUG-203 首帧超时后仍继续写、用户"超时报错但等一会又出结果"

**代码证据**（`phoenix-agent/phoenix-agent-core/.../harness/turn/HarnessTurnManager.java`）：
- `finish()`（:557）是 `synchronized` 且有 `if (sinkDone.get()) return;` 守卫 ⇒ **不会二次定稿** ✅
- 但 `onFrame()`（:439）**没有终态守卫**：`finish()` 里 `sourceSub.dispose()` 只是 Reactor 取消，
  在途帧/未及时取消的上游 HTTP 流仍会进入 `onFrame`，继续 `content.append(...)` 与 `pendingText.append(...)`，
  并触发增量落库 ⇒ **同一 message id 在超时之后仍被 UPDATE**（与客户图3 一致）
- `finish()` 把超时提示**追加进正文**（:591-593 `content.append(suffix)`）⇒ 之后再被追加真实输出时，
  用户同时看到"超时提示 + 后续内容"

**结论**：`取消不彻底 + 帧无终态守卫 + 提示语混入正文` 三者叠加；**不是"超时没生效"，而是"超时之后还在收尾"**。

### BUG-202 工具熔断误伤

**代码证据**（`.../harness/middleware/ToolLoopBreakerMiddleware.java`）：
- 清零条件写错：`bump()`（:86）`if (map.size() == 1 && !map.containsKey(signature)) map.clear();`
  ⇒ **只有"当前只存在 1 种签名"时才清零**。模型若交替使用 A/B 两种签名（A,B,A,B…），
  两个计数各自累加且**永不归零** ⇒ 累计到阈值就误熔断（而参数明明在变化）
- 计数键为 `agent.getName()`（:56）：**本条已撤回** —— `HarnessAgentRegistry` 按 `agentId@sessionId` 缓存实例，
  中间件随实例走 ⇒ 天然是**会话级**，不存在跨会话共享。
- **真正的缺陷是「跨轮不清零」**：计数表与实例（=会话）同寿命，一轮结束没有重置；
  若相邻两轮出现同一签名（例如每轮都先查同一句），计数会从上一轮继续累加，与 Javadoc 声明的「连续」不符。
- 阈值默认 3（`phoenix.agent.tool-repeat-breaker-threshold:3`），现场配成 30（日志 `25/30`）

**结论**：`清零条件脆弱（仅 map.size()==1 才清零）+ 跨轮不清零`；并非"模型没有死循环"，而是"正常的多轮也被累加计入"。

### BUG-201 思考未展示 + 历史被丢弃

**代码证据**：
- `.../service/harness/HarnessModelRegistry.java`（:113-127）：`provider=ollama` ⇒ `OllamaChatModel` +
  `new OllamaChatFormatter()`，`OllamaOptions` 只设 `temperature/numPredict`，**未开 think**
  ⇒ 思考模型（qwen3.8）在源头就不产出思考内容
- `OllamaChatFormatter` 对历史中的 `ThinkingBlock` 直接跳过并告警（客户图1 `Unsupported block type ThinkingBlock ... skipped`）
  ⇒ **历史思考被静默丢弃**
- `ReasoningRepairMiddleware`（:49-72）会给"含 tool_use 但无 thinking"的助手消息补 **占位** thinking（BUG-163 的变通）
- 事件侧 `HarnessEventMapper` 已映射 `ThinkingBlockDeltaEvent` ⇒ 后端 SSE 具备 reasoning 能力 ✅（前端渲染需实测）

**结论**：`未开 think（源头无思考）+ 表单器不认 ThinkingBlock（历史丢弃）`；展示半段需前端确认。

## 二、方案（按需求条款）

### R-01 附件上限可配置（BL-62）
- 配置：`phoenix.agent.chat.attachment-max-count`（**默认 20**，按 Q1 建议值；现网为硬编码 5 ⇒ **属默认值变更，需在发布说明标注**）
- 后端：附件绑定入口（`ChatAttachmentService.bindToMessage` 的调用方 / `ChatController`）做**数量校验**，
  超限返回明确错误（错误码 + "最多 N 个"）
- 前端：附件选择器上限改为**读配置**（启动配置或后端返回），超限即时提示
- **评估结论（本方案给出，供 requirements 附录引用）**：
  | 维度 | 现状 | 调大的约束 |
  |---|---|---|
  | 单文件上限 | 现有上传白名单/大小限制 | N × 单文件大小 应 < 请求体上限（nginx `client_max_body_size`，默认 1M 需同步调） |
  | 解析开销 | 每个附件抽取文本后进上下文 | N 越大，首帧越慢、上下文越容易爆 ⇒ 建议 N ≤ 20 |
  | 上下文占用 | 单附件截断 24000 字符（现网日志可见） | 20 × 24000 ≈ 48 万字符 ⇒ 已超多数模型上下文 ⇒ **建议默认 20、安全上限 50，并在超限时按序截断** |
- 兼容：默认由 5 变 20（行为变更，发布说明标注）；可配回 5 以保持旧行为

### R-02 生成文件路径提示（BL-63）
- 现状：产物落 `{root}/{runtimeKey}/{sessionId}`；事件帧已有 `agentFiles`（`onFrame` 读取 :456-458）
- 方案：① 落库消息 metadata 增 `generatedFiles: [{path, size, mtime}]`
  ② 回复正文尾部追加「📎 生成文件：<绝对路径>（<大小>）」清单（与文件面板同源）
  ③ 前端把路径渲染为可点击（走既有下载/打开接口）
- 验收：刷新重进会话仍可见并可下载

### R-03 首帧超时可配置 + 语义一致（BL-64 + BUG-203）
- 配置：沿用 `phoenix.agent.model-first-frame-timeout-seconds`（已有，默认 180）⇒ **建议默认 600**，支持按智能体覆盖
- 修 BUG-203：① `onFrame` 首行加终态守卫 `if (sinkDone.get()) return;`
  ② 超时提示**不写入 content**，改由 metadata（`notice`）表达，避免与后续输出混杂
  ③ 订阅侧显式 `cancel` 上游请求（确认 WebClient/OkHttp 可取消；不可取消则靠 ① 兜底）
- 兼容：默认值变更需在发布说明标注

### R-04 思考展示与历史保留（BUG-201）
- ① ollama 模型开启 thinking（`OllamaOptions.think(true)` 或框架等效开关），**配置项 + 默认开**（对不支持思考的模型自动忽略）
- ② 历史回传：`ThinkingBlock` **降级为文本**（或换用支持 thinking 的 formatter），杜绝"静默丢弃"
- ③ 前端思考区渲染（折叠、流式追加），沿用 `thinking` 帧字段
- ④ 保留 `ReasoningRepairMiddleware` 作为兼容兜底

### R-05 工具熔断修正（BUG-202）
- ① 计数键：`agentKey + "|" + sessionId`（必要时 + turn）
- ② 清零：**只保留最后一个签名**，变化即清零（不再依赖 `map.size()==1`）
- ③ 日志：只打签名摘要（工具名 + 参数 hash 前 8 位），避免超长日志
- ④ 阈值保持可配置（默认 3→建议 5，需你定）

### R-06 Xinference 接入（BL-65）
- **方案 A（首选，零 DDL）**：复用现有 `provider=custom/openai` 分支（`OpenAIChatModel` + base_url）
  ⇒ 仅需 UI 预设与文档，不改后端枚举、不动库结构
- 方案 B（备选）：新增 `ModelProvider.XINFERENCE` + 表单预设（仅当需要 thinking/工具调用特化时）
- 评估项：`/v1/chat/completions` 流式、tools 调用兼容、`/v1/embeddings` 维度与现有校验的适配

## 三、改动面

| 层 | 文件 | 改动 |
|---|---|---|
| 运行时·超时 | `harness/turn/HarnessTurnManager.java` | onFrame 终态守卫 / 提示移出正文 / 上游 cancel / 超时配置 |
| 运行时·熔断 | `harness/middleware/ToolLoopBreakerMiddleware.java`、`harness/factory/HarnessAgentFactory.java` | 计数键/清零/日志摘要/阈值 |
| 模型 | `service/harness/HarnessModelRegistry.java` | ollama think 开关 / ThinkingBlock 降级 |
| 附件 | `service/chat/ChatAttachmentServiceImpl.java`、`controller/ChatController.java` | 上限校验 + 配置项 |
| 产物 | `harness/turn/HarnessTurnManager.java` + 文件服务 | metadata `generatedFiles` + 正文提示 |
| 前端 | `packages/chat-shared/**`（渲染/附件选择器） | 思考区 / 上限提示 / 生成物点击 |
| SQL | 无 | R-06 不动库结构；其余仅 metadata JSON 扩展 |

## 四、测试与验收
- 单测：熔断（A/B 交替不熔断、同参数熔断、跨会话隔离、阈值可配）；超时（终态后帧丢弃、提示不入正文）；
  附件上限（N 通过 / N+1 拒绝，前后端一致）
- 联调：qwen3.8(ollama) 思考可见 + ≥3 轮带工具调用历史不丢；Xinference 一轮流式 + 工具调用
- 回归：**语义不变、默认值变**（附件 5→20、超时 180→600、熔断 3→5）；三项均可配置回旧值，发布说明须标注

## 五、风险与未决
- `OllamaChatFormatter` 对 ThinkingBlock 的支持属框架能力，可能只能"降级为文本"
- 上游 HTTP 取消能力需实测（WebClient/OkHttp）；不可取消时以终态守卫兜底（会浪费算力但不串内容）
- 默认值需你拍板（见下）

## 六、已定口径（Q1~Q5，2026-10-24 陈卓确认）
- **Q1** 附件上限：推荐默认 **20**、安全上限 **50**（超 50 不建议），是否接受？
- **Q2** 首帧超时：默认 **600s**；是否需要"按智能体覆盖"？
- **Q3** thinking：默认**开启**（模型不支持时自动忽略），是否接受？
- **Q4** 熔断阈值：默认 3 → 建议 **5**，是否接受？
- **Q5** Xinference：走**方案 A**（零 DDL、复用 custom provider）是否够用？
