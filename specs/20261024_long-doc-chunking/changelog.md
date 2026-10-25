# 变更记录：long-doc-chunking

## 2026-10-24 v0.1.0 创建 + 挂载

- 来源：用户 2026-10-24 要求把 **BL-67（超长文档分批处理）** 做掉；同时 **BL-72（抽取上限 24000 配置化）** 合并进本 spec。
- 挂载: **v2.1.0**（2026-10-24，用户确认口令「挂2.1.0」）
- 关联: BL-67 → 已立项；BL-72 → 并入本 spec
- 四件套：requirements.md（草稿 v0.1.0）/ plan.md / tasks.md / changelog.md
- 待确认：Q2 默认参数（24000 触发 / 6000 每批 / 20 批上限）、Q3 超限降级方式（摘要覆盖全文）

## 2026-10-24 requirements v1.0.0 确认①

- 用户「确认」→ 定版 v1.0.0（确认人 陈卓，2026-10-24）
- 已定参数：24000 触发 / 6000 每批 / 20 批上限；超限 = 摘要覆盖全文 + 明确提示
- 下一步：Phase 2 Plan

## 2026-10-24 plan v1.0.0 确认②

- 用户「确认」→ 定版 v1.0.0（陈卓 2026-10-24）
- 要点：extractor 返回全文（安全上限 2M）/ 语义切片 6000+overlap300 / map-reduce（要点+关键摘录）/ 串行 / 护栏 20 批或 20k 累计 / 摘要降级 / 进度走 notice 帧 / 6 个配置项
- 坑核对 111 条相交 5 条；共享面 4 身份；日志点 6 条；被拒方案 5 个
- 下一步：Phase 3 Tasks

## 2026-10-24 tasks v1.0.0 确认③ + 建施工分支

- 用户「确认」→ tasks 定版 v1.0.0（陈卓 2026-10-24）
- 三重确认自检通过：requirements v1.0.0 / plan v1.0.0 / tasks v1.0.0 均「已确认」且确认人非空
- 施工分支：`feature/long-doc-chunking`（自 `2.1.0` 切出）

## 2026-10-24 T-01 完成（6 项配置化，真栈验证）

- `ChatDocumentExtractor` 增 6 个 `@Value` 配置 + `@PostConstruct` 打印生效值 + getter；`MAX_EXTRACT_CHARS` → `DEFAULT_MAX_EXTRACT_CHARS`（仅作默认）
- 两个调用方（装配器 / 上传服务）提示文案改为读配置
- **默认值写在 `application.yml`**（`phoenix.agent.chat.attachment-*` 6 项，带注释）；compose **不再**塞 env（避免覆盖 yml）
- **实测**：启动日志 `[attachment-batch] 生效配置: maxExtractChars=24000, safeMaxChars=2000000, chunkSize=6000, …` ✅；
  传 `PHOENIX_AGENT_CHAT_ATTACHMENTCHUNKSIZE=4000` 重启后日志变 `chunkSize=4000` ✅；
  代码中硬编码 24000 引用数 0 ✅
- 坑：**Spring env 名规则是"短横线去掉"** —— `...attachment-chunk-size` → `PHOENIX_AGENT_CHAT_ATTACHMENTCHUNKSIZE`（写成 `_CHUNK_SIZE` 不生效，首次验证就踩到）
- 按用户要求改为**配置文件驱动**：默认值落 `application.yml`（`phoenix.agent.chat.attachment-*`），
  实测不传任何 env 时日志显示 6 项默认值 ✅；容器内 ATTACHMENT env 条数 = 0 ✅（env 仍可覆盖，优先级更高）

## 2026-10-24 T-02 完成（extractor 返回全文 + 2M 安全上限，真栈验证）

- `ChatDocumentExtractor`：抽取阶段**不再按 24000 截断**；仅受 `safeMaxChars`（默认 2,000,000）保护；
  handler 改为**不设限**并事后截断（Tika 触达写上限时抛 TIKA-237、异常链丢失 WriteLimitReached，会误判为解析失败）
- 两处文案改为安全上限语义（装配器 / 上传服务）
- **实测**：
  - 3 万字 docx → `extracted_chars=30029`、`status=ACTIVE`、`notice=null`（证明不再 24000 截断）✅
  - 210 万字 docx（**随机文本**，避免 Tika zip-bomb 误判）→ `extracted_chars=2000000`、
    `notice=文档超过单文档安全上限（2000000 字符），仅处理前 2000000 字符` ✅；
    日志 `[attachment-batch] 超安全上限截断: name=…, chars=2102399, safeMaxChars=2000000` ✅
- 坑 ①：Tika 的写上限异常是 `TIKA-237: Illegal SAXException`，不能靠异常类型判断 ⇒ 改为不设限 + 事后截断
- 坑 ②：用大段重复字符造的大 docx 会被 Tika **zip-bomb 保护**判失败（压缩比过高）⇒ 测试数据要用**随机内容**

## 2026-10-24 T-03 完成（DocumentChunker 语义切片，断言全绿）

- 新增 `phoenix-agent-core/.../attachment/DocumentChunker.java`（纯函数，无状态）：
  段落优先切 → 单段超长退到句末标点（。！？；.!?;）→ 极端无标点长串按 chunkSize 兜底硬切；
  贪心装箱 ≤6000，块间保留 300 字重叠；输出 `Chunk(index,text,startOffset,endOffset)`
- **断言（Java 单文件跑通，3 类样本）**：
  - ① 无标题超长段落（9490 字）→ 2 块、每块 ≤6000、切点落在句末 ✅
  - ② 多级标题文档（10236 字）→ 2 块、首块含"第1章" ✅
  - ③ 中英混排（7490 字）→ 2 块、每块 ≤6000 ✅
  - ④ 重叠校验：第 2 块以第 1 块尾部 300 字开头 ✅
- 测试脚本修正：样本② 原仅 2676 字（<6000）本就应 1 块，是断言写错，非切片缺陷

## 2026-10-24 T-04 完成（DocumentBatchProcessor，9 条断言全绿）

- 新增 `DocumentBatchProcessor`（纯编排，模型调用以 `ChatFn` 注入）：
  map = 每块「要点 + 3~5 条逐字关键摘录」；reduce = 按块序拼成结构化摘要；
  串行 + 单块重试 + 批数/预算护栏 + 进度回调 + 六条日志（开始/单批/失败/降级/结束）
- **断言（stub 模型，Java 单文件）9 条全绿**：
  ① 15000 字 → 3 块、全部处理、未降级、reduce 含 K1/K2/K3 ②进度 [1/3,2/3,3/3]
  ③ 批数上限 2 → 降级且只处理 2 块、文案「已按摘要方式处理 2/3（约 67%）」不含"已全部处理"
  ④ 预算 60 字 → 降级 ⑤ 失败 2 次后成功（retry=2）⑥ 永久失败 → 标注失败块且**不说谎**
- 自查纠错：coverage 文案原在"有失败块"时仍拼接"已全部处理"（自相矛盾），已改为
  「共 X 块，成功 Y 块；第 Z 块处理失败」
- 说明：**真模型**验证放在 T-09/T-10（装配器接入 + 端到端），本任务用 stub 保证编排逻辑可独立验证

## 2026-10-24 执行顺序调整（用户同意）+ T-09 主体落地

- **顺序调整**：T-09 提前到 T-05 之前。原因：T-05（进度帧）需要装配器**真的跑分批**才有东西可发，
  而装配器接模型属 T-09。仅调整依赖顺序，不改需求与验收标准（T-05 依赖改为 T-04,T-09；T-09 依赖改为 T-02,T-04）。
- **T-09 主体**（代码已落地，待补齐进度帧与三链路断言）：
  - `ChatAttachmentAssembler` 新增 `prepare(ids, viewerId, Consumer<String> progress)` 重载；
    超长文档（> `maxExtractChars`）→ `DocumentBatchProcessor` 逐批摘要 → 以「摘要+关键摘录」替换原文注入；
    notice 追加「内容较长，已分批处理（摘要+关键摘录）」；无 CHAT 模型/异常 ⇒ **回退原文注入**（不阻断对话）
  - **真栈实测（30k docx，附件 37）**：`[doc-batch] 开始 chars=30029, chunkCount=6`；第 1 批因 ollama 报错重试 2 次后跳过
    （`failed=[1]`），其余 5 批完成（`processed=5`）；对话正常返回
  - **回归（5k docx，附件 38）**：最近 90 秒 `doc-batch` 日志 **0 条** ⇒ 未触发分批（R-10 行为不变）；对话正常应答
- **待办（下一步）**：
  1. 进度帧接线（T-05）：`HarnessChatServiceImpl` 三处 `prepare(...)` 传入 progress consumer，并把 notice 帧并入流
  2. `HarnessEventMapper` 透出 `notice` 字段
  3. 修 `BatchResult.totalMs` 打印为 1 的问题（疑似耗时统计位置/精度）

## 2026-10-25 T-05~T-10 完成（真栈逐项验证）

**T-05 进度帧（实现 + 实测）**
- `HarnessChatServiceImpl.doStream`：进度 sink + `noticeOutput()` + `Flux.merge`（进度先于正文）
- `HarnessEventMapper` 透出 `notice`
- **关键坑**：`HarnessTurnManager.onFrame` 的「纯空生命周期帧」过滤会把 content 为空的 notice 帧**丢弃** ⇒ 已加入白名单
- 实测：30k docx（6 块）SSE 收到 `正在处理第 1/6…6/6 批…` + 结束 `共 6 块，成功 5 块；第 [1] 块处理失败`；**正文未被污染**

**T-06 护栏/降级（实测）**
- 临时把 `attachment-max-chunks` 置 2 → 日志 `[doc-batch] 降级: 达到最大批数 maxChunks=2, processed=2/6`；
  notice `共 6 块，成功 2 块；第 [1] 块处理失败`；**不含「已全部处理」**
- `totalMs` 统计已正确（此前打印 1 的问题随重构消失）

**T-07 失败重试（实测）**
- 30k 实测中第 1 批 ollama 两次失败（`attempt=1/2`、`2/2`）后跳过，其余完成；结束文案标注失败块

**T-08 幂等（实测）**
- 同附件（37）重复使用 → 日志 `[doc-batch] 命中幂等缓存，跳过重复分批: attachmentId=37, chunkChars=30029`；未重复调用模型

**T-09 装配接入 + 未超阈值行为不变（实测）**
- 5k docx：请求期间 `doc-batch` 日志 0 条（未触发分批），对话正常
- 30k/26k docx：正常触发分批

**T-10 端到端（关键证据）**
- 造 26026 字 docx，标记 `XYZ-2048` **放在 24000 字之后**（原截断方案必然丢弃）
- 提问「第4章第3条规定的专用条款编号是什么？」
- 结果：进度帧 `1/3…3/3` + `共 3 块，已全部处理`；**落库回答内容 = `XYZ-2048`** ✅
  ⇒ 后半段内容确实参与理解（旧行为下该内容会被截断丢弃）

## 配置恢复
- 验证期间临时改过的 `chunk-size=12000` / `chunk-timeout=180` / `max-chunks=2` **均已恢复默认**
  （6000 / 60 / 20），启动日志已核对

## 2026-10-25 收尾（completion 生成）

- 10/10 任务完成，completion.md 已生成；证据见各任务 changelog 段

## 2026-10-25 实测调优（用户自测暴露的问题，逐个修）

| # | 问题（用户实测暴露） | 处理 |
|---|---|---|
| 1 | 39.5k 文档 7 块，单块 >60s 全线超时 | `attachment-chunk-timeout-seconds` 60→**180**；分批调用加 `temperature=0.2 / maxTokens=800 / thinkingBudget=0`（关思考链） |
| 2 | 块长 6300 > 配置 6000（重叠尾巴 + 超长段落单元） | `DocumentChunker` 收口时若「尾巴+单元 > chunkSize」丢弃尾巴；末尾兜底再切 |
| 3 | 模型把"抽取"答成"评审意见" | map prompt 改**指令三明治**（前指令+原文+尾提醒），并要求严格三行【要点】【标识】【摘录】 |
| 4 | 模型原样回抄模板（`【要点】… `） | 增加 `looksLikeEcho` 检测 |
| 5 | 编号/代码仍可能不被模型抄出 | 增加**规则扫描**（`[A-Z]{2,}-\d+` / 日期 / `N 个工作日` / 第N章第N条），每块确定性拼接，并在 digest **顶部置顶全文标识汇总** |
| 6 | 严格校验导致大量块判失败（用户实测 6/7、2 块失败） | 改为**确定性兜底**：不判失败，用「原文前 400 字 + 规则扫描」补位，并在结束文案中如实标注兜底块数 |

**当前实测证据**：块 5/7 规则扫描明细首个即 `TC-8899`；digest 头部含 `TC-8899`；
结束提示 `共 7 块，已全部处理（其中 N 块模型输出不合格式，已用原文摘录+规则扫描兜底）`。

**已知限制（未解决）**：本地 `qwen3:8b` 作最终回答模型时，倾向总结/复述条款编号而**不直接给出代码**；
注入材料里已确定含 `TC-8899`。结论：分批管道可用，**答案质量受本地小模型能力限制**；
建议换更强模型或专用问答智能体验证。

## 2026-10-25 A+B 优化（用户要求实测）—— 编号类问题已能答对 ✅

**问题**：材料里已含 `TC-8899`，本地 8B 模型仍只总结条款编号。
**根因**：① 摘要把「专项管理代码 TC-8899」拆成裸值列表 ⇒ 标签↔值绑定丢失；② 约 300 个编号淹没 1 个目标，信噪比 ~0.3%。

**实现**
- **A 标签化上下文**：`DocumentBatchProcessor.scanLabeledContexts(text, 24)` 对每个标识取前后文窗口，
  输出「本章设立专项管理代码 TC-8899，专项工作由质量…」这类**保留绑定**的片段；digest 顶部列出前 10 条。
- **B 检索式前置**：`prepare(..., question)` 把用户问题传入；按关键词（代码/编号/文号/日期/时限/金额/比例/部门/责任人）
  命中项**优先排序**并抽成 `【直接答案（规则匹配原文，逐字）】`，在注入文本**首尾各放一次**（8B 对中间内容注意力弱）。

**真栈实测（附件 43，39,533 字 / 7 块）**
- digest 头部：`【直接答案（规则匹配原文，逐字）】「代码」第200条 本章设立专项管理代码 TC-8899，…`
- 结束提示：`共 7 块，已全部处理`
- 落库回答：`代码：第200条 本章设立专项管理代码 **TC-8899**，专项工作由质量与标准部负责解释。` ✅ **含 TC-8899**
- 断言：`回答含 TC-8899 = ✅`（此前 A 单独做时仍 ❌，说明首尾双注入是决定性因素）

## 2026-10-25 环境事故与处置（Docker 磁盘写满）

- 现象：Redis 报 `MISCONF Errors writing to the AOF file: No space left on device`，登录 500/502。
- 根因：反复 `docker commit` 生成大量 2.5GB 悬空镜像（132 个镜像 / 37GB）+ 11GB 构建缓存，Docker VM 磁盘写满。
- 处置：`docker image prune -f`（回收 18.89GB）+ `docker builder prune -f`（回收 13.43GB），重启 redis/backend，
  登录恢复 200；**未动任何 volume（数据零影响）**。
- 改法：后续更新后端改用 `docker cp jar + compose restart`，不再 commit 新镜像。
