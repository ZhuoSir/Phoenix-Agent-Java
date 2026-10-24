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
