-- =====================================================================
-- 版本: v2.0.0  序号: 14  类型: DDL
-- 来源: specs/20261007_user-role-group-model (CR-02 / T-27 / R-19, requirements v2.8.0)
-- 目的: 技能名称唯一性由「全局」改为「按创建人」—— 不同用户可上传同名技能
-- 前置: V2.0.0_13 已应用（creator 列存在且存量已回填 admin）
-- 可重入: 是（DROP/CREATE IF EXISTS）
-- 回滚: rollback/V2.0.0_14__skill_name_uniq_per_creator_ddl_rollback.sql
--       （前置：回滚时不得已存在跨用户同名行，否则重建 UNIQUE(name) 冲突）
-- ---------------------------------------------------------------------
-- 用户口径（2026-10-07，陈卓）：「两个不同用户上传的同名 skill 按道理可以上传，重复判断要加用户判断」
-- 设计：用**表达式唯一索引** (name, coalesce(creator,'')) 而非加 NOT NULL ——
--   运行期 AgentScope 等上游插入可能不带 creator，加 NOT NULL 会打断它们；
--   表达式索引让 NULL creator 归一为 ''，同 name+同 creator（含双 NULL）互斥、跨 creator 共存。
-- =====================================================================
BEGIN;

-- 原名唯一是**约束**（非裸索引），须 DROP CONSTRAINT（活库实测 DROP INDEX 报 requires it）
ALTER TABLE tbl_harness_skills DROP CONSTRAINT IF EXISTS tbl_harness_skills_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS tbl_harness_skills_name_creator_key
    ON tbl_harness_skills (name, coalesce(creator, ''));

-- 自检（环境无关）
DO $chk$
DECLARE dup int; idx int;
BEGIN
  SELECT count(*) INTO dup FROM (
    SELECT name, coalesce(creator,'') c FROM tbl_harness_skills GROUP BY 1, 2 HAVING count(*) > 1) x;
  IF dup <> 0 THEN RAISE EXCEPTION '[V2.0.0_14] 存在 % 组 (name,creator) 重复', dup; END IF;
  SELECT count(*) INTO idx FROM pg_indexes
   WHERE tablename='tbl_harness_skills' AND indexname='tbl_harness_skills_name_creator_key';
  IF idx <> 1 THEN RAISE EXCEPTION '[V2.0.0_14] 唯一索引 (name,creator) 未建立'; END IF;
  RAISE NOTICE '[V2.0.0_14] 自检通过：(name,creator) 重复 0 组 / 按创建人唯一索引已建';
END $chk$;

COMMIT;
