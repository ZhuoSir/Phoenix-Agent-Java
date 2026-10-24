# 变更记录：对话运行时加固（chat-runtime-hardening）

## 2026-10-24 立项（v0.1.0 草稿）
- 用户 2026-10-24 交办客户反馈 6 项；口径：**合成 1 个 spec**、**缺陷先定位再修**、**挂 v2.1.0**。
- 落账：BUG-201 / BUG-202 / BUG-203（`specs/_project/bugs.md`）；BL-62 / BL-63 / BL-64 / BL-65（`specs/_project/backlog.md`）。
- 本文档为 **requirements v0.1.0 草稿**，状态 `待确认`；未写任何生产代码。

## 2026-10-24 requirements v1.0.0 定版 + plan/tasks 草稿
- 用户口令「确认，先走spec，把需求做了」⇒ requirements 定版 **v1.0.0 已确认**（确认人 陈卓）。
- 按「定位再修」先做**只读定位**（未改任何代码），三处根因写入 `plan.md` 第一章：
  · BUG-203：`onFrame` 无终态守卫 + 提示语混入正文 + 上游取消不彻底
  · BUG-202：`bump()` 清零条件只在 `map.size()==1` 生效；计数键用 agent 名（跨会话）
  · BUG-201：ollama 未开 `think`（源头无思考）+ `OllamaChatFormatter` 丢弃 ThinkingBlock
- `plan.md` / `tasks.md` 为 **v0.1.0 待确认**；plan 附待确认 Q1~Q5（附件上限 20/50、超时 600s、
  thinking 默认开、熔断阈值 5、Xinference 方案 A）。

## 2026-10-24 plan / tasks v1.0.0 定版（三重确认齐）+ 建施工分支
- 用户口令「确认，继续执行」⇒ plan/tasks 定版 **v1.0.0 已确认**（陈卓 2026-10-24）。默认值口径：
  附件上限 20（安全 50）/ 首帧超时 600s / thinking 默认开 / 熔断阈值 5 / Xinference 方案 A。
- 按 git-workflow §三.3：文档全部落在版本分支 `2.1.0` 后，从 `2.1.0` 切施工分支
  **`feature/chat-runtime-hardening`**（命名=spec 目录去掉日期）。

## 2026-10-24 T-15 可行性实测（Xinference 接入方式）
- 结论：**Xinference 可用"零代码"接入** —— `provider=custom` + `base_url=http://<xinference>:<port>/v1` + `model_name`，
  `DynamicModelFactory` 对非 ollama 的 provider 统一走 `OpenAiApi`（`completionsPath/embeddingsPath` 可配）。
- 实测（v2.1.0 栈 + 本机 OpenAI 兼容 mock，模拟 Xinference `/v1`）：
  · `POST /api/model-config/test`（provider=custom, base_url=http://host.docker.internal:9000/v1, CHAT）
    → **"连接测试成功！模型可用。"**
  · 同参数 EMBEDDING（`/v1/embeddings`）→ **同样成功**
- 未覆盖（显式标注）：真实 Xinference 实例（本机未安装，用 mock 替代）；**流式（SSE）与工具调用**未在本次测试端点中验证，
  留到 T-17/T-18 联调；`/v1/models` 非必需（未依赖）。
