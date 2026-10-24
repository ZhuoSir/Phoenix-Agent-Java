# v2.1.0 发布说明（草稿 · 待冻结）

> 状态：**在途**（未 tag、未 push）。本文件随版本冻结点更新。
> 组成：`release-delivery-hardening`（10/10）+ `vector-dim-flexibility`（16/16）+ `chat-runtime-hardening`（进行中）。

## 一、⚠️ 默认值变更（升级方必须知晓）

| 配置项 | 旧值 | 新值 | 影响 |
|---|---|---|---|
| `phoenix.agent.chat.attachment-max-count`（原硬编码） | 5 | **20** | 单次对话可附加的附件数变多 |
| `phoenix.agent.model-first-frame-timeout-seconds`（compose env `PHOENIX_AGENT_MODEL_FIRST_FRAME_TIMEOUT_SECONDS`） | 180 | **600** | 首帧等待时间变长（慢模型的请求不再轻易被判超时） |
| `phoenix.agent.tool-repeat-breaker-threshold` | 3 | **5** | 工具连续重复调用熔断阈值放宽 |

> 三项均可配置回旧值；**语义不变**（仅默认值调整）。

## 二、新增配置项

| 配置项 | 默认 | 说明 |
|---|---|---|
| `phoenix.agent.ollama.think` | `auto` | ollama 思考模式：`auto`（按模型名判断思考模型）/ `true` / `false` |
| `VITE_ATTACHMENT_MAX_FILES`（前端构建期） | 20 | 前端附件选择器上限（后端为最终裁决方） |

## 三、修复（本版）

- **超时语义**：首帧超时/取消后不再继续追加同一消息内容（`onFrame` 终态守卫）；超时提示不再无条件混入正文（改落 `metadata.notice`）。
- **工具熔断**：计数回归"连续"语义（只保留最后签名、变化即清零），避免正常多轮检索被误熔断。
- **思考展示**：ollama 思考模型显式开启 `think`（此前源头无思考）；历史 `ThinkingBlock` 降级为文本，不再静默丢弃。

## 四、能力增强

- 附件上限可配置（默认 20；推荐 20、安全上限 50——依据：单附件截断 24000 字符 × N 与上下文/解析开销）。
- 智能体生成文件后，回复正文给出 **生成物相对路径 + 可点击下载链接**，并落 `metadata.generatedFiles`（刷新后仍可见）。
- 支持 **Xinference**（Xorbits Inference）接入：`provider=custom` + `base_url=.../v1`，零代码（详见 `docs/XINFERENCE.md`）。

## 五、已知限制

- 前端暂未提供"重试"入口（超时后重试落新消息的能力后端已具备）。
- 思考展示依赖既有 `thinking-display` 组件；ollama 思考模型在 `auto` 模式下按模型名启用。
- Xinference 的流式/工具调用尚未在真实实例上验证（见 `docs/XINFERENCE.md` §三）。
