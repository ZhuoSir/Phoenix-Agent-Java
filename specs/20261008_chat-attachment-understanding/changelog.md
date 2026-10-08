# Changelog: chat-attachment-understanding

## v0.2.0（2026-10-08）用户裁定 Q1~Q6（草稿期，待确认①）

- 裁定：Q1 仅会话级 / Q2 按草案白名单（.svg 排除）/ Q3 20MB·5 个 / Q4 允许加 `model_type=VISION` 配置行 /
  **Q5 降级但显式提示（非硬失败）** / Q6 超长顺序截断并告知 + 扫描件报错不转视觉
- 修改：**R-07 整条改写**（硬失败 → 降级 + 用户可见告知 + metadata 留标注 + 只问图时不编造）；
  **R-08 口径固化**（顺序截断、保留开头、告知可见、记 metadata）；**R-09 扫描件场景改为明确报错**
- 修改：假设区 8 条标 ✅ 并写入裁定值；新增「裁定记录」表（Q1~Q6 逐条落点）
- 新增：**Q4-1 唯一遗留阻塞项** = 视觉模型确切模型名（agent 不代猜；Implement 首任务为可用性探针，探针不过即停）
- 侦察事实：现有 `tbl_data_model_config` 有 qwen / DashScope compatible-mode 行（id=7，api_key 在库）⇒ 视觉模型可复用同 provider
- 状态：**确认①仍未通过**，未进入 Plan；未触碰源码


## v0.1.0（2026-10-08）草稿创建

- 创建：requirements / plan / tasks / changelog 四件套（Phase 0）
- 挂载: **v2.0.0**（2026-10-08，用户确认「挂 v2.0.0（在途）」；台账在途唯一，未新开版本）
- 功能名: `chat-attachment-understanding`（用户从两个候选中选定）
- 范围拍板（用户 2026-10-08）：入口 = **admin-ui + mobile-ui 两端**；图片理解 = **真多模态（新增视觉模型）**
- requirements v0.1.0 草稿成文，含 8 条待确认问题（阻塞项 6 条）——**未确认，禁止进入 Plan**
