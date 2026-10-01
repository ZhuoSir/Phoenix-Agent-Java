# Changelog: knowledge-base

## v1.0.0 方案确认 + tasks（2026-10-01）确认人: 陈卓
- **第②关通过**（用户「确认方案」，P1~P6 与 4 风险整体接受）→ plan 转 v1.0.0 已确认
- Phase 3：tasks T-01~T-11 生成即确认（沿用既定授权）；开分支 feature/knowledge-base，进入 Implement

## Plan v0.1.0（2026-10-01）初稿
- 实证取巧点：向量过滤 validIds 本为条目表实时查询（DynamicFilterService:48/56），故**向量零迁移**（P1）
- 域划分：写侧校验在 platform（组数据域），召回读侧在 data（同域读绑定表）（P3）
- 端点复用策略 P5（agent-knowledge 换作用域参数），删 agent 不再级联删向量 P6（风险注记 2 条）
- 待第②关确认

## v1.0.0（2026-10-01）确认人: 陈卓
- **三重确认第①关通过**（用户「确认需求」；A-01~A-06 默认生效，尤其 A-05 绑定期校验语义、A-06 迁移命名）
- 进入 Phase 2 Plan

## v0.2.0（2026-10-01）五问决议入册
- Q1~Q5 全部落条款：R-11 定稿（绑定多选）；新增 **R-13~R-15 组授权**（用户主动扩大范围，对齐 tbl_platform_group_agent_info 同构；组校验时机=绑定期，运行时只认绑定表——A-05 待纠正窗口保留）
- Non-goals 复核：业务知识不搬（Q4 拍板维持）；里程碑 v1.3.0（Q5）已 M1 挂接
- 迁移命名假设 A-06 新增

## v0.1.0（2026-10-01）创建
- Specify 起稿：12 条 EARS / 6 AC / Non-goals / 假设 A-01~05
- 现状实证：tbl_data_agent_knowledge(agent_id 强绑, 类型 DOCUMENT/QA/FAQ, isRecall/embeddingStatus/retry-embedding, 上传链路)；前端在 agent-create-drawer 知识 tab；向量表 tbl_harness_vector_store_knowledge
- 待用户回答 Q1~Q6 后进 v1.0.0 确认门
