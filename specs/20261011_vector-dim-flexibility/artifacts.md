# 升级件登记：vector-dim-flexibility

> 版本: v2.1.0 | 更新: 2026-10-25

| 类型 | 文件 | 说明 | 回滚 |
|---|---|---|---|
| SQL DDL | `sql/V2.1.0_01__kb_embedding_binding_ddl.sql` | 知识库增 `embedding_model_id` / `model_space_key` | `sql/rollback/V2.1.0_01__kb_embedding_binding_ddl_rollback.sql` |
| SQL DDL | `sql/V2.1.0_02__kb_rebuild_job_ddl.sql` | 向量重建任务表 `tbl_data_kb_rebuild_job`（断点续跑/失败清单） | `…/V2.1.0_02__kb_rebuild_job_ddl_rollback.sql` |
| SQL DDL | `sql/V2.1.0_03__vector_tables_pk_ddl.sql` | 三张历史向量表补主键/唯一约束（幂等；重复 id 告警跳过） | `…/V2.1.0_03__vector_tables_pk_ddl_rollback.sql` |

**执行顺序**：01 → 02 → 03（03 可独立执行，但须在 01/02 之后以复用已整理的表清单）。
**幂等性**：三件均 `IF NOT EXISTS` / `ON CONFLICT` 保护，可重跑。
**验收**：R-09 五项门禁（归属体检/迁移对账/维度一致/验收报告/回滚可逆），历史实测报告见 `releases/v2.1.0/acceptance/`。
