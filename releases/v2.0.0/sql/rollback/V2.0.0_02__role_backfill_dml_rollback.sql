-- =====================================================================
-- 版本: v2.0.0  序号: 02  类型: DML-ROLLBACK
-- 对应正向件: sql/V2.0.0_02__role_backfill_dml.sql
-- 目的: 撤销本件补写的关系行 —— 仅删除本件写入的行（以 create_by 标记精确识别），
--       不影响任何既有角色关系（含用户手工新增的、以及孤儿行）
-- 可重入: 是（按标记 DELETE；无命中时为 no-op）
-- 说明: 回滚后「存活且无角色的用户」回到升级前状态（非 0），这是预期的历史状态
-- =====================================================================
BEGIN;

DO $$
DECLARE v_deleted int;
BEGIN
  DELETE FROM tbl_privilege_user_role
   WHERE create_by = 'V2.0.0_02';

  GET DIAGNOSTICS v_deleted = ROW_COUNT;
  RAISE NOTICE '[V2.0.0_02-rollback] 已撤销 % 行补角色关系', v_deleted;
END $$;

COMMIT;
