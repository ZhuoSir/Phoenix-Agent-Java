> 版本: v0.1.0 | 生成: 2026-10-06 | 挂载: v1.7.0

# 变更记录：kb-access-isolation

## v0.1.0（2026-10-06）创建
- 立项（Phase 0）：用户 2026-10-06 口令「先解决 3 和 4 的问题」= **③ 提示面对齐（BUG-104）+ ④ 共享目录越权（BUG-105）**
- **挂载: v1.7.0**（在途，用户 2026-10-06 确认；不新开版本）
- **整改取向**（用户 2026-10-06 确认）：**A + B 组合** = 原件移出 agent 可读面（根治）+ shell/文件工具路径护栏（兜底）
- **范围追加**（本次排查新发现，已登记 BUG-108）：`/uploads/**` 裸静态暴露 ⇒ 无鉴权可下载知识库原件与会话工作区文件，纳入本 spec 的 R-01
- **一并收口**（用户 2026-10-06 确认）：BUG-86 原场景复测，不可复现则翻「已验证(v1.7.0)」
- requirements.md v0.1.0 草稿产出（R-01~R-08 + 待确认 Q1~Q4）→ **待确认①**
- 立项时 Phase 0 审计发现并修正 2 处台账滞后（`version.md` 漏挂 unified-account-center；MILESTONE 需求行仍写"待合并"）→ commit `757548a`

## v1.0.0（2026-10-06）① requirements 确认通过
- **① 确认通过**（用户 2026-10-06 回「确认，按你的建议」；确认人：**陈卓**）→ requirements **v1.0.0 已确认**
- **Q1~Q4 裁定**（写入 requirements「确认时的裁定」节）：Q1 改写/覆盖框架提示（不物化 `knowledge/`）；Q2 新增受控接口 + `/uploads` 仅图片白名单；Q3 工作区文件一起做；Q4 静态清单 + 会话级动态补充（动态精细版二期）
- **范围追加**：BUG-108（`/uploads/**` 裸静态暴露）纳入 R-01（用户确认时一并采纳）
- plan.md 完成草稿（Phase 2）：坑核对 37 条（相交 20）、决策 1~5（含被拒替代）、`/uploads` 共享面身份矩阵 6 个身份、迁移与回滚设计、风险表
- **技术前提已探明**：`javap` 实证 `agentscope-harness-2.0.0.jar` 暴露 `disableWorkspaceContext()` / `useLegacyXmlWorkspaceContext(boolean)` / `additionalContextFile(...)`，矛盾段位于 `WorkspaceContextMiddleware.onSystemPrompt(...)` ⇒ 决策 4 有可落地开关（副作用需 spike 实测）
