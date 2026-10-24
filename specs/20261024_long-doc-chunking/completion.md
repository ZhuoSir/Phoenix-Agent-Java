> 版本: v1.0.0 | 状态: 完成（自行验证通过） | 更新: 2026-10-25

# 完成清单：超长文档分批处理（long-doc-chunking）

## 结论
**10/10 任务完成**，全部关键路径在真栈逐项验证；超长文档不再截断丢弃，改为分批理解并全部处理。

## 已完成
| 任务 | 内容 | 验证证据 |
|---|---|---|
| T-01 | 6 项配置落 `application.yml`（含 BL-72 的 24000 配置化） | 启动日志生效值 + env 覆盖生效 + 硬编码引用 0 |
| T-02 | extractor 返回全文；仅超 `safeMaxChars`(2M) 才截断 | 3 万字 → `extracted_chars=30029`、notice=null；210 万字 → `extracted_chars=2000000` + 安全上限提示 |
| T-03 | `DocumentChunker` 语义切片（6000/重叠 300/不切半句） | Java 断言 3 类样本 + 重叠校验全绿 |
| T-04 | `DocumentBatchProcessor`（map 要点+摘录 / reduce / 护栏 / 重试） | stub 断言 9 条全绿（含"失败块不说谎"） |
| T-05 | 进度帧 `notice`（正在处理第 x/N 批 + 结束标注） | SSE 实测 `1/6…6/6` + `共 6 块，成功 5 块；第 [1] 块处理失败`，正文未污染 |
| T-06 | 批数/预算护栏 + 摘要降级 | `maxChunks=2` 实测降级日志 + 文案覆盖 2/6、无"已全部处理" |
| T-07 | 单批失败重试 + 范围说明 | 30k 实测第 1 批 ollama 两次失败→跳过并标注 |
| T-08 | 幂等（同附件 10 分钟去重）+ 六条日志 | 重复附件命中缓存日志，未重复调模型 |
| T-09 | 装配器接入 + 未超阈值行为不变 | 5k 文档 0 条 doc-batch 日志、对话正常；30k/26k 正常触发 |
| T-10 | 端到端：后半段内容参与理解 | 26k 文档标记 `XYZ-2048` 置于 24000 字之后 → **回答落库内容 = XYZ-2048** ✅ |

## 交付面
- 后端（phoenix-data）：`ChatDocumentExtractor` 配置化 + 全文抽取
- 后端（phoenix-agent）：新增 `DocumentChunker`、`DocumentBatchProcessor`；装配器分批接入；进度帧与 mapper 透出；turn manager 修复 notice 帧被丢弃
- 配置：`application.yml` 6 项（业务参数，符合 L-113）
- 前端：无改动（复用 notice 通道）

## 期间修复/规避的坑
| 坑 | 处理 |
|---|---|
| Tika 触达写上限抛 `TIKA-237`（丢失 WriteLimitReached）→ 超长文档被误判解析失败 | 改"不设限 handler + 事后按安全上限截断" |
| 重复字符造的大 docx 被 Tika 判 zip-bomb → 测试失败 | 测试数据改用随机内容 |
| `HarnessTurnManager` 丢弃 content 为空的 notice 帧 → 进度帧到不了前端 | notice 加入白名单 |
| Spring env 名规则（短横线去掉）| 记入 L-113；业务配置改为 yml 优先 |
| 本地 ollama 逐块慢（60s/块）→ 9 块跑不完 | 端到端验证临时放大 chunk-size 至 12000（3 块），**验证后已恢复 6000** |

## 已知限制（不阻塞）
- 本地 ollama `qwen3:8b` 在 6k 块上偶发超时（单块 60s 上限）→ 该批跳过并在文案标注；可调 `attachment-chunk-timeout-seconds`
- 本 spec **不做** RAG 索引（Non-goal），超长文档仅在当轮分批理解
- 概览数字波动等问题与本 spec 无关（用户已确认为手动改数据）

## 遗留待办
- BL-70（admin-ui 权限体系）、BL-71（账号逻辑删级联）、BL-66（品牌配置化）等
