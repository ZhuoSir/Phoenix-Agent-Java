# 接入 Xorbits Inference（Xinference）

> 结论先行：**零代码接入** —— Xinference 暴露 OpenAI 兼容 API，平台侧用 `provider=custom` + `base_url=http://<xinference>:<port>/v1` 即可，
> 不需要改后端、不需要改库结构（chat-runtime-hardening T-15 实测验证；T-16 补 UI 预设与本文档）。

## 一、为什么能零代码接入

- `DynamicModelFactory`（以及 harness 的 `HarnessModelRegistry`）对 **非 ollama** 的 provider 统一走 Spring AI 的 `OpenAiApi`：
  `baseUrl` 可配、`completionsPath`/`embeddingsPath` 可配（`tbl_data_model_config` 已有这两个字段）。
- `ModelProvider` 枚举含 `CUSTOM("custom")`，Xinference 的 `/v1/chat/completions` 与 `/v1/embeddings` 与 OpenAI 同构。

## 二、配置步骤（管理端）

1. 打开「模型配置」→ 新增模型
2. **提供商**：选 `Custom / Xinference（OpenAI 兼容）`
3. **Base URL**：`http://<xinference 主机>:9997/v1`（Xinference 默认端口 9997）
4. **模型名称**：填 Xinference 里已 launch 的 model id（如 `qwen2.5-instruct`）
5. **API Key**：Xinference 未开鉴权时随便填一个非空值（如 `xinference`）；开了鉴权就填真实 key
6. 点「测试连接」→ 应返回 `连接测试成功！模型可用。`
7. 按需设为该类型（CHAT / EMBEDDING）的默认模型

## 三、已验证 / 未验证（如实标注）

| 项 | 结论 |
|---|---|
| `POST /api/model-config/test`（provider=custom, base_url=…/v1, CHAT） | ✅ 实测通过（v2.1.0 栈 + OpenAI 兼容 mock，模拟 Xinference） |
| 同上，EMBEDDING（`/v1/embeddings`） | ✅ 实测通过 |
| 真实 Xinference 实例 | ❌ 未验证（本机未安装，用同构 mock 替代） |
| 流式 SSE / 工具调用 | ❌ 未在"连接测试"端点覆盖，待联调（T-17/T-18） |
| `/v1/models` | 不依赖（平台不调用） |

## 四、注意事项

- **apiKey 不能为空**：Spring AI 的 `OpenAiApi` 强制非空；Xinference 无鉴权时请填占位值。
- 若 Xinference 的路径不是标准 `/v1`（很少见），用 `completionsPath` / `embeddingsPath` 覆盖即可。
- 维度校验：EMBEDDING 模型的维度由平台探测/配置决定，接入后请走「知识库 → 向量模型」绑定，勿手工改库。
