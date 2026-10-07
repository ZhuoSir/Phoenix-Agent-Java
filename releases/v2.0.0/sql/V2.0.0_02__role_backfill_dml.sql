-- =====================================================================
-- 版本: v2.0.0  序号: 02  类型: DML
-- 来源: specs/20261007_user-role-group-model (T-05)
-- 目的: R-07 —— 为存量「存活且无任何有效角色」的用户补齐至少一个角色
-- 前置: 无（与 V2.0.0_01 的删表删列无依赖，先后皆可）
-- 可重入: 是（NOT EXISTS 判缺 + 确定性主键 md5('v2.0.0_role_backfill:'||user_id)）
-- 预估: 本库 5 行量级，<1s
-- 回滚: rollback/V2.0.0_02__role_backfill_dml_rollback.sql
-- ---------------------------------------------------------------------
-- 要点:
--   ① 默认角色按 `upper(sn) = 'COMMON'` 定位 —— 库中实际存 'COMMON'（大写），
--      而 Java 侧历史代码用 'common'（小写）比对 ⇒ 命中 0 行、兜底从未生效（BUG-123）。
--      本件显式大小写不敏感，避免把历史坑带进迁移。
--   ② 找不到该角色时 **RAISE EXCEPTION 直接失败**，不静默跳过（迁移必须响亮）。
--   ③ 只补「无任何有效角色（del_flag=0）」的存活用户；已有角色的用户零改动，
--      多角色用户不会被截断或覆盖。
--   ④ 孤儿 user_role 行（引用不存在的角色）**不删**——按 BUG-118 口径「保留 + 记账」，
--      清理另议（本 spec 不做）。
--   ⑤ 角色有效性沿用既有代码口径（`isLogicDelete` ⇒ del_flag=0）；用户侧亦只取 del_flag=0。
--      status 语义不一致（BUG-101 已延期）⇒ 本件不据 status 过滤。
-- =====================================================================
BEGIN;

DO $$
DECLARE
  v_role_id   varchar(64);
  v_role_name varchar(32);
  v_before    int;
  v_written   int;
BEGIN
  SELECT r.id, r.name
    INTO v_role_id, v_role_name
    FROM tbl_privilege_role r
   WHERE upper(r.sn) = 'COMMON'
     AND coalesce(r.del_flag, 0) = 0
   ORDER BY r.id
   LIMIT 1;

  IF v_role_id IS NULL THEN
    RAISE EXCEPTION '[V2.0.0_02] 未找到 sn=COMMON 的启用角色，拒绝继续（禁止静默跳过）';
  END IF;

  SELECT count(*) INTO v_before
    FROM tbl_privilege_user u
   WHERE coalesce(u.del_flag, 0) = 0
     AND NOT EXISTS (SELECT 1 FROM tbl_privilege_user_role ur
                      WHERE ur.user_id = u.id AND coalesce(ur.del_flag, 0) = 0);

  INSERT INTO tbl_privilege_user_role
        (id, user_id, user_no, role_id, end_date, valid_month,
         create_time, create_by, update_time, update_by, del_flag)
  SELECT md5('v2.0.0_role_backfill:' || u.id),
         u.id,
         u.username,
         v_role_id,
         NULL,
         NULL,
         now(),
         'V2.0.0_02',
         now(),
         'V2.0.0_02',
         0
    FROM tbl_privilege_user u
   WHERE coalesce(u.del_flag, 0) = 0
     AND NOT EXISTS (SELECT 1 FROM tbl_privilege_user_role ur
                      WHERE ur.user_id = u.id AND coalesce(ur.del_flag, 0) = 0);

  GET DIAGNOSTICS v_written = ROW_COUNT;
  RAISE NOTICE '[V2.0.0_02] 补角色完成：待补 % 人 / 实际写入 % 行（角色 % / %）',
               v_before, v_written, v_role_id, v_role_name;
END $$;

-- 自检：执行后不应再有「存活且无有效角色」的用户；有则整体失败（回滚本件）
DO $$
DECLARE v_left int;
BEGIN
  SELECT count(*) INTO v_left
    FROM tbl_privilege_user u
   WHERE coalesce(u.del_flag, 0) = 0
     AND NOT EXISTS (SELECT 1 FROM tbl_privilege_user_role ur
                      WHERE ur.user_id = u.id AND coalesce(ur.del_flag, 0) = 0);
  IF v_left > 0 THEN
    RAISE EXCEPTION '[V2.0.0_02] 自检失败：仍有 % 个存活用户无有效角色', v_left;
  END IF;
  RAISE NOTICE '[V2.0.0_02] 自检通过：存活用户无角色数 = 0';
END $$;

COMMIT;
