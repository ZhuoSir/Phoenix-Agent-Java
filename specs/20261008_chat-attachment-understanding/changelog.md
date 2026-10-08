# Changelog: chat-attachment-understanding

## v1.0.0（2026-10-08）确认① 通过

- 确认人: **陈卓**（用户 2026-10-08 选定「确认通过（确认人：陈卓）」）；三重确认门第 **①** 重达成
- 同轮裁定 **Q4-1**：模型类型枚举值 = **`MULTIMODAL`**（非 VISION；语义为多模态大模型，qwen3.8 类原生图文模型同归此类型）；
  首行 `model_name = qwen-vl-max`，provider/base_url/api_key 复用现有 qwen（DashScope compatible-mode，id=7）
- 修改：假设 4 重写（MULTIMODAL + qwen-vl-max + OpenAI 兼容 `image_url` 部件 + 不新增 SDK）；
  裁定表 Q4 行去掉「模型名待定」；Q4-1 段落翻为「已裁定」；待确认问题节标题改「已全部裁定」
- 保留兜底纪律：Implement 首任务 = 多模态可用性探针（真实带 `image_url` 调用），探针不过即停
- 下一步：Phase 2 Plan（须先加载 lessons 全部 active 坑 + api-design/database 规范源，坑核对节置顶）


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
