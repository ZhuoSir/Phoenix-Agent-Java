# 升级件登记：long-doc-chunking

> 版本: v2.1.0 | 更新: 2026-10-25

无库表变更；新增 6 个附件分批配置（`application.yml`，均可环境变量覆盖）。

| 键 | 默认 | 说明 |
|---|---|---|
| `phoenix.agent.chat.attachment-max-extract-chars` | 24000 | 触发分批的字符阈值（原"截断阈值"语义改为"分批阈值"） |
| `phoenix.agent.chat.attachment-safe-max-chars` | 2000000 | 抽取安全上限（仅超它才真截断） |
| `phoenix.agent.chat.attachment-chunk-size` | 6000 | 每批字符数 |
| `phoenix.agent.chat.attachment-chunk-overlap` | 300 | 块间重叠 |
| `phoenix.agent.chat.attachment-max-chunks` | 20 | 最大批数（护栏） |
| `phoenix.agent.chat.attachment-chunk-timeout-seconds` | 180 | 单批模型调用超时 |

**回滚**：全部为配置项，改回旧值/或删除（走代码默认）即可；无 SQL。
**注意**：`attachment-max-extract-chars` 语义由"截断"变为"分批"，升级后同样 24000 阈值的**行为不同**（不再丢弃后半段）。
