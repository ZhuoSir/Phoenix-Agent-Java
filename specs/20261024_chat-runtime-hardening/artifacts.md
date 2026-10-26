# 升级件登记：chat-runtime-hardening

> 版本: v2.1.0 | 更新: 2026-10-25

无库表变更；仅**配置默认值变更 + 新增配置项**（升级方必须知晓）。

| 类型 | 键 | 旧 | 新 | 说明 |
|---|---|---|---|---|
| 默认值变更 | `phoenix.agent.chat.attachment-max-count` | 5（硬编码） | **50** | 单次对话附件上限；前端启动经 `GET /api/chat/attachment/limit` 拉取 |
| 默认值变更 | `phoenix.agent.model-first-frame-timeout-seconds`（compose env `PHOENIX_AGENT_MODEL_FIRST_FRAME_TIMEOUT_SECONDS`） | 180 | **600** | 模型首帧等待；0=关闭首帧超时 |
| 默认值变更 | `phoenix.agent.tool-repeat-breaker-threshold` | 3（累计） | **5（连续）** | 工具重复调用熔断，语义由"累计"改"连续" |
| 新增 | `phoenix.agent.ollama.think` | — | `auto` | ollama 思考模式：auto/true/false |
| 依赖 | `agentscope` | 2.0.0 | **2.0.4** | 思考内容支持（pom 内，随镜像） |

**回滚**：改回上表"旧"值即可（均为配置项，无需回滚 SQL）。
