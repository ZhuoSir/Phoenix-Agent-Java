-- =====================================================================
-- 版本: v2.0.0  序号: 04  类型: DML-ROLLBACK
-- 对应正向件: sql/V2.0.0_04__acl_baseline_rebuild_dml.sql
-- 目的: ① 撤销本件新建的授权行（create_by 标记精确识别）
--       ② 恢复本件清理掉的脏数据行（从 JSONB 备份表 tbl_privilege_acl_bak_v200 还原）
-- 可重入: 是（ON CONFLICT DO NOTHING + 标记删除）
-- 说明: 备份表**保留不删**，便于二次核查；确认稳定后可在下一版 housekeeping 清理
-- =====================================================================
BEGIN;

DO $$
DECLARE v_ins int; v_del int;
BEGIN
  -- ① 撤销本件写入的授权行
  DELETE FROM tbl_privilege_acl WHERE create_by = 'V2.0.0_04';
  GET DIAGNOSTICS v_del = ROW_COUNT;

  -- ② 还原被清理/被替换的原始行（原样回填，含 del_flag 等全部列）
  --    冲突键用 (release_id, module_id)：幂等与"目标对先清后插"的语义下，同一键只应回填一行
  IF to_regclass('public.tbl_privilege_acl_bak_v200') IS NOT NULL THEN
    INSERT INTO tbl_privilege_acl
    SELECT (jsonb_populate_record(NULL::tbl_privilege_acl, b.row_json)).*
      FROM tbl_privilege_acl_bak_v200 b
    ON CONFLICT (release_id, module_id) DO NOTHING;
    GET DIAGNOSTICS v_ins = ROW_COUNT;
  ELSE
    v_ins := 0;
    RAISE NOTICE '[V2.0.0_04-rollback] 备份表不存在，跳过脏数据还原';
  END IF;

  RAISE NOTICE '[V2.0.0_04-rollback] 撤销授权行 % 行；还原脏数据行 % 行', v_del, v_ins;
END $$;

COMMIT;
