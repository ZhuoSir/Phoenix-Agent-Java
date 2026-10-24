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
