-- =====================================================================
-- 版本: v2.0.0  序号: 13  类型: DDL+DML
-- 来源: specs/20261007_user-role-group-model (CR-01 / T-25 / R-18, requirements v2.7.0)
-- 目的: ① tbl_harness_skills 增加 creator 列（技能归属，支撑 R-18 管理页 own-only）
--       ② 存量技能回填 creator=内置 admin；③ 知识库历史 creator='system' 归 admin（Q-C3）
-- 前置: V2.0.0_01~12 已应用
-- 可重入: 是（ADD COLUMN IF NOT EXISTS + 幂等 UPDATE）
-- 回滚: rollback/V2.0.0_13__skill_creator_and_kbase_owner_dml_rollback.sql
-- ---------------------------------------------------------------------
-- 裁定（CR-01 §S）: Q-C2 技能存量回填 admin；Q-C3 知识库 'system' 改归 admin。
-- admin id 逐环境不同 ⇒ 子查询动态解析（同 L-51/L-57）。
-- =====================================================================
BEGIN;

-- ① 技能表加 creator 列
ALTER TABLE tbl_harness_skills ADD COLUMN IF NOT EXISTS creator varchar(64);

-- ② 存量技能回填 admin
UPDATE tbl_harness_skills
   SET creator = (SELECT u.id FROM tbl_privilege_user u
                   WHERE lower(u.username) = 'admin' AND coalesce(u.del_flag, 0) = 0
                   ORDER BY u.create_time LIMIT 1)
 WHERE creator IS NULL OR creator = '';

-- ③ 知识库历史 'system' 归 admin
UPDATE tbl_data_knowledge_base
   SET creator = (SELECT u.id FROM tbl_privilege_user u
                   WHERE lower(u.username) = 'admin' AND coalesce(u.del_flag, 0) = 0
                   ORDER BY u.create_time LIMIT 1)
 WHERE creator = 'system';

-- ④ 自检（环境无关）
DO $chk$
DECLARE null_skill int; sys_kb int; admin_cnt int;
BEGIN
  SELECT count(*) INTO admin_cnt FROM tbl_privilege_user
   WHERE lower(username) = 'admin' AND coalesce(del_flag, 0) = 0;
  SELECT count(*) INTO null_skill FROM tbl_harness_skills WHERE creator IS NULL OR creator = '';
  SELECT count(*) INTO sys_kb FROM tbl_data_knowledge_base WHERE creator = 'system';
  IF admin_cnt >= 1 AND null_skill <> 0 THEN
    RAISE EXCEPTION '[V2.0.0_13] 仍有 % 条技能无 creator', null_skill;
  END IF;
  IF sys_kb <> 0 THEN
    RAISE EXCEPTION '[V2.0.0_13] 仍有 % 条知识库 creator=system', sys_kb;
  END IF;
  RAISE NOTICE '[V2.0.0_13] 自检通过：admin % 个 / 无creator技能 % 条 / system知识库 % 条',
    admin_cnt, null_skill, sys_kb;
END $chk$;

COMMIT;
