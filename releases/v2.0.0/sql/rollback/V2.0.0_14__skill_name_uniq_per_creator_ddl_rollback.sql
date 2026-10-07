-- =====================================================================
-- 版本: v2.0.0  序号: 14  类型: DDL(回滚)
-- 配对正向件: ../V2.0.0_14__skill_name_uniq_per_creator_ddl.sql
-- 前置: 回滚时不得已存在跨用户同名行（否则 UNIQUE(name) 重建冲突）
-- 可重入: 是
-- =====================================================================
BEGIN;
DROP INDEX IF EXISTS tbl_harness_skills_name_creator_key;
ALTER TABLE tbl_harness_skills ADD CONSTRAINT tbl_harness_skills_name_key UNIQUE (name);
COMMIT;
