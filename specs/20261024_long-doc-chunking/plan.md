> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-24 | 确认人: 陈卓 | 确认日期: 2026-10-24

# 技术方案：超长文档分批处理（long-doc-chunking）

## 一、坑核对（必备节，置顶）

已核对 `specs/_project/lessons.md` 全部 **111 条** active 坑，相交 5 条：

| 坑 | 与本方案的关系 | 避坑做法 |
|---|---|---|
| **L-104** 字符串 replace 必须断言命中数 | 会改 `MAX_EXTRACT_CHARS`、截断逻辑、提示文案、装配器注入文案等多处 | 每处改动前后 `grep` 计数 + 断言；改完统一 `mdtable_check` 与编译 |
| **L-111** 未导出/写错绑定导致点击静默失效 | 本次新增配置项与提示，若前端/接口未接会被当"没生效" | 配置项落地后**用真实环境变量验证生效**（日志打印生效值） |
| **L-112** 配置对象需整份给 / 重复键覆盖 | 新增多个配置项，易在 compose/`.env`/代码默认三处打架 | 配置优先级写清：env > `.env` > 代码默认；启动日志打印最终生效值 |
| **L-63** 服务端写死的判别列/字段不可信前端 | 分批结果（批次数、是否降级）由服务端决定，前端只展示 | 降级/批次信息由后端计算并下发，前端不做推断 |
| **L-105** "关键词检索"其实是向量检索的教训 | 本方案**不**走 RAG 索引（明确 Non-goal） | 方案里显式标注：分批=同轮内理解，不涉及向量检索 |

## 二、方案概述

```
附件上传（不变）
  └─ ChatDocumentExtractor.extract()
        ├─ 抽取全文（不再 24000 硬截断；改为「安全上限」2,000,000 字符防 OOM）
        └─ 返回全文 + chars

对话轮次开始（HarnessChatServiceImpl.doStream → attachmentAssembler.prepare）
  └─ DocumentBatchProcessor.process(att, fullText)   ← 新增
        ├─ len <= 触发阈值(24000)  ⇒ 原样注入（R-10：行为不变）
        └─ len > 阈值 ⇒ 语义切片（DocumentChunker）
              ├─ 逐批 map：调模型抽「本批要点 + 关键原文摘录」（每批 ≤6000 字符，重叠 300）
              ├─ 批间节流 + 单批超时 + 失败重试 1 次
              ├─ 进度：通过 **notice/content 帧** 下发「正在处理第 x/N 批」
              └─ reduce：把各批要点汇总成「文档要点摘要」注入上下文
                    └─ 批数 > 20 或累计超预算 ⇒ 摘要降级：只保留已处理批要点 + 明确提示
```

**为什么是 map-reduce 的"要点+关键原文摘录"**：纯摘要会丢原文细节（用户要答"第 4 章第 3 条"）；纯原文拼接会超上下文。
所以每批同时产出「要点」与「**逐字摘录的关键句**」，reduce 时按章节标题聚合成结构化摘要 ⇒ 细节可答、总量可控。

## 三、改动面

**后端（phoenix-data）**

| 文件 | 改动 |
|---|---|
| `ChatDocumentExtractor.java` | `MAX_EXTRACT_CHARS` 硬编码 → `@Value` 配置项（默认 24000，作为**触发阈值语义**）；改为**返回全文**，另设安全上限 `MAX_SAFE_CHARS`（默认 2,000,000）防 OOM；`ExtractResult` 增 `chars`（已有）与 `fullTextAvailable` |

**后端（phoenix-agent）**

| 文件 | 改动 |
|---|---|
| `attachment/DocumentChunker.java`（新） | 语义切片：按 `\n\n`/标题行（`第X章|一、|1.`）/分页符切；块 ≤6000，overlap 300；不切断句子 |
| `attachment/DocumentBatchProcessor.java`（新） | 批处理编排：map→reduce、节流、单批超时、重试、进度回调、护栏与降级 |
| `attachment/ChatAttachmentAssembler.java` | `prepare(...)` 内接入 processor；注入「摘要+关键摘录」；notices 增加「已分批处理（共 N 批）」/降级说明；新增 `progressConsumer` 参数用于下发进度 |
| `harness/send/impl/HarnessChatServiceImpl.java` | 把进度的 notice 帧并入对话流（在 agent 流开始前先发进度帧） |
| `config/*Properties`（或 `@Value`） | 新增 6 个配置项（见 §六） |

**前端**：不改（进度走现有 `notice`/`content` 通道；前端已能渲染 notice）

## 四、接口/数据流

无新增对外接口（复用 `/api/chat/attachment` 与现有对话 SSE）。
SSE 帧新增/沿用：
```json
{ "content": "", "notice": "正在处理第 3/8 批…", "end": false }
{ "content": "", "notice": "文档较大：共 8 批，已全部处理（要点+关键摘录）" }
```

## 五、关键设计决策

1. **谁调模型**：复用 `HarnessModelRegistry` 的对话模型（不引入新模型依赖）；每批 prompt 固定、温度低、输出限长（≤800 字）。
2. **批间节流**：默认串行（简单、成本可控）；不做并发（避免打爆 provider 限流）。
3. **降级阈值**：批数 > 20 或累计 map 输出 > 20,000 字 ⇒ 停止 map，用**已抽要点**做 reduce + 提示「文档过大，已按摘要方式处理（覆盖前 N 批约 X% 内容）」。
4. **幂等**：同一 attachmentId 在同一轮内只处理一次（按 `sessionId+attachmentId+轮次` 去重缓存，超时 10 分钟）。
5. **失败重试**：单批失败重试 1 次；仍失败 ⇒ 记 WARN，跳过该批并在提示中标注「第 x 批处理失败」。

## 六、配置项（全部可配）

| 键 | 默认 | 说明 |
|---|---|---|
| `phoenix.agent.chat.attachment-max-extract-chars` | 24000 | **触发分批**的字符阈值（原 24000，语义从"截断"变"分批"） |
| `phoenix.agent.chat.attachment-safe-max-chars` | 2000000 | 抽取安全上限（防 OOM），超出才真截断并提示 |
| `phoenix.agent.chat.attachment-chunk-size` | 6000 | 每批字符数 |
| `phoenix.agent.chat.attachment-chunk-overlap` | 300 | 块间重叠字符 |
| `phoenix.agent.chat.attachment-max-chunks` | 20 | 最大批数（护栏） |
| `phoenix.agent.chat.attachment-chunk-timeout-seconds` | 60 | 单批模型调用超时 |

## 七、共享面身份矩阵（必备）

共享面判据自检：「还有谁依赖这个抽取结果/文案？」—— 是（3 处），故逐身份列出。

| 共享对象 | 既有身份 | 变更后预期行为 |
|---|---|---|
| `ChatDocumentExtractor.extract()` | ① `ChatAttachmentAssembler.load()`（对话注入）② `ChatAttachmentServiceImpl`（上传时抽取/落库统计） | 两者都改为读配置阈值；**返回值语义**：文本仍为全文（受安全上限约束），`truncated` 仅在超**安全上限**时为 true（此前超 24000 即 true） |
| `MAX_EXTRACT_CHARS` 常量 | ① 装配器提示文案 ② 上传服务提示文案 | 常量保留为 `DEFAULT_MAX_EXTRACT_CHARS` 供默认值引用；两处文案改为读实际配置值 |
| 提示文案「已按顺序截断至…」 | 上传结果 VO `notice` | 未超阈值：不再出现截断文案；超阈值：改为「已分批处理（共 N 批）」；超安全上限：「文档超大，仅处理前 X 字符」 |
| `ChatAttachmentAssembler.prepare()` | ① 后台 `/harness/chat` ② 前台 `/front/harness/chat` ③ confirm 续跑 | 三处都经同一装配器 ⇒ 分批自动对三条链路生效；进度帧需在三条链路都下发（实现时统一在 `HarnessChatServiceImpl` 与前台映射处理） |

## 八、关键日志点清单（必备）

| 场景 | 级别 | 触发点 | 必带字段 |
|---|---|---|---|
| 分批开始 | INFO | `DocumentBatchProcessor.process` | `attachmentId, chars, chunkCount, chunkSize` |
| 单批完成 | DEBUG | 每批 map 结束 | `attachmentId, index, ms, outChars` |
| 单批失败重试 | WARN | 批次异常 | `attachmentId, index, attempt, error` |
| 降级触发 | WARN | 超批数/预算 | `attachmentId, processedChunks, reason` |
| 分批结束 | INFO | reduce 完成 | `attachmentId, chunkCount, processed, degraded, totalMs` |
| 安全上限截断 | WARN | extractor | `attachmentId, chars, safeMax` |

四类强制日志点核对：服务启停/定时批处理 不涉及；**外部调用**（模型调用）已覆盖（单批 DEBUG + 失败 WARN）；安全事件（附件越权）沿用既有 `adminRoleGuard`。

## 九、被拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| 把所有块拼成一次长 prompt | 超出模型上下文，且成本不可控 |
| RAG 索引化后检索再答 | 需要向量化/检索链路，属另一 spec（Non-goal）；且首轮无索引 |
| 前端切片分批上传 | 用户不该感知切片；且上传接口语义会变 |
| 并发调用各批 | 打爆 provider 限流、成本与失败面放大 |
| 只调大 24000（不做分批） | 治标；模型上下文仍会截断/变慢 |

## 十、风险与规避

| 风险 | 规避 |
|---|---|
| 分批导致单轮耗时显著增加（10 批 × 30s ≈ 5min） | 进度可见 + 单批超时 60s + 最大 20 批；必要时调小批数/批size |
| 批次失败导致答案缺块 | 重试 1 次 + 明确提示失败批次 |
| 摘要丢失细节 | 每批同时抽取"关键原文摘录"，reduce 保留章节结构 |
| 与既有 600s 空闲超时冲突 | 分批期间持续发 notice 帧 ⇒ 空闲计时被刷新（需在实现中确认 `onFrame` 计数口径） |
| 超限降级被误解为"已处理全文" | 降级文案明确写"覆盖前 N 批约 X%"，不得声称全部 |
