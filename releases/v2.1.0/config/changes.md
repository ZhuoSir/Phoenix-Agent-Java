# v2.1.0 配置变更汇总

> 从各 spec `artifacts.md` 聚合（2026-10-25）。**均为可选覆盖**：不配置时走代码/yml 默认值。

## 一、默认值变更（升级方必须知晓）

| 配置项 | 旧默认 | 新默认 | 影响 | 来源 spec |
|---|---|---|---|---|
| `phoenix.agent.chat.attachment-max-count`（原硬编码 5） | 5 | **50** | 单次对话附件上限提高 | chat-runtime-hardening（+2026-10-25 用户要求 20→50） |
| `phoenix.agent.model-first-frame-timeout-seconds`（compose `PHOENIX_AGENT_MODEL_FIRST_FRAME_TIMEOUT_SECONDS`） | 180 | **600** | 首帧等待更久（0=关） | chat-runtime-hardening |
| `phoenix.agent.tool-repeat-breaker-threshold` | 3（累计） | **5（连续）** | 熔断语义改"连续"，阈值放宽 | chat-runtime-hardening |
| `agentscope`（依赖，非配置） | 2.0.0 | **2.0.4** | 思考内容支持 | chat-runtime-hardening |

## 二、新增配置项

| 配置项 | 默认 | 说明 | 来源 spec |
|---|---|---|---|
| `phoenix.agent.ollama.think` | `auto` | ollama 思考模式 auto/true/false | chat-runtime-hardening |
| `phoenix.agent.chat.attachment-max-extract-chars` | 24000 | 触发**分批**的字符阈值（语义由"截断"改为"分批"） | long-doc-chunking |
| `phoenix.agent.chat.attachment-safe-max-chars` | 2000000 | 抽取安全上限 | long-doc-chunking |
| `phoenix.agent.chat.attachment-chunk-size` | 6000 | 每批字符数 | long-doc-chunking |
| `phoenix.agent.chat.attachment-chunk-overlap` | 300 | 块间重叠 | long-doc-chunking |
| `phoenix.agent.chat.attachment-max-chunks` | 20 | 最大批数（护栏） | long-doc-chunking |
| `phoenix.agent.chat.attachment-chunk-timeout-seconds` | 180 | 单批超时 | long-doc-chunking |
| `phoenix.platform.brand.cache-seconds` | 60 | 品牌信息公开缓存秒数 | brand-configuration |
| `phoenix.platform.brand.asset-max-bytes` | 2097152 | 品牌图上限（2MB） | brand-configuration |

## 三、前端构建期项

| 项 | 默认 | 说明 |
|---|---|---|
| `VITE_ATTACHMENT_MAX_FILES` | 50 | 附件选择器兜底上限；**后端 `attachment-max-count` 为最终裁决方**（启动经 `/api/chat/attachment/limit` 下发） |

## 四、环境变量命名规则（重要）

Spring 宽松绑定：配置键中的**短横线去掉**再转大写。
例：`phoenix.agent.chat.attachment-chunk-size` → `PHOENIX_AGENT_CHAT_ATTACHMENTCHUNKSIZE`
（写成 `..._CHUNK_SIZE` **不生效** —— 已踩坑，见 L-113）
