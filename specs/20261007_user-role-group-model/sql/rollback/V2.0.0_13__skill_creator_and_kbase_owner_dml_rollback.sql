-- =====================================================================
-- 版本: v2.0.0  序号: 13  类型: DDL+DML(回滚)
-- 配对正向件: ../V2.0.0_13__skill_creator_and_kbase_owner_dml.sql
-- 目的: ① 知识库 id=1 的 creator 复原为 'system'；② DROP 技能 creator 列（回填值随之丢弃）
-- 可重入: 是
-- =====================================================================
BEGIN;

UPDATE tbl_data_knowledge_base SET creator = 'system' WHERE id = 1;

ALTER TABLE tbl_harness_skills DROP COLUMN IF EXISTS creator;

COMMIT;
