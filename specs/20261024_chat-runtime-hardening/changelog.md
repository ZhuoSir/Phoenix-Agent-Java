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
