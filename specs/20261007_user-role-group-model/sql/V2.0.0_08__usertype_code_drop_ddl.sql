-- =====================================================================
-- 版本: v2.0.0  序号: 08  类型: DDL/DML
-- 来源: specs/20261007_user-role-group-model (T-20 / R-14, requirements v2.3.0)
-- 目的: 用户类型（自建/IDM）与工号维度整体下线
-- 前置: V2.0.0_01~07 已应用；应用本件后**后端须为 v2.3.0 代码**（实体已不再映射这些列）
-- 可重入: 是（DROP COLUMN IF EXISTS；UPDATE 幂等）
-- 回滚: rollback/V2.0.0_08__usertype_code_drop_ddl_rollback.sql（保真恢复列与原值）
-- ---------------------------------------------------------------------
-- 用户口令（2026-10-07，确认人陈卓）：「确认执行 R-14（确认人：陈卓）」+「工号也删掉」
-- 依据（全栈实测，见 evidence/T-20_scan.txt）：
--   user_type          仅 2 处 Java 引用（登录透传 + 列表过滤），无任何逻辑分支；库内 user_type=1 共 0 条
--   it_user_id/name    Java 0 引用（IDM 同步包已随 R-06 删除；2 条 IDM 异常枚举定义但从未抛出）
--   code（工号）        按工号查询/写绑定冗余列/参与搜索/审计写入；用户裁定一并下线
-- 保留（非本件范围）：tbl_platform_account_info.code 属前台账号自身标识，仍被前台登录/查询/搜索使用
-- =====================================================================
BEGIN;

-- ① 工号与用户类型/IDM 列下线
ALTER TABLE IF EXISTS tbl_privilege_user DROP COLUMN IF EXISTS code;
ALTER TABLE IF EXISTS tbl_privilege_user DROP COLUMN IF EXISTS user_type;
ALTER TABLE IF EXISTS tbl_privilege_user DROP COLUMN IF EXISTS it_user_id;
ALTER TABLE IF EXISTS tbl_privilege_user DROP COLUMN IF EXISTS it_user_name;
ALTER TABLE IF EXISTS tbl_privilege_user_role DROP COLUMN IF EXISTS user_no;

-- ② 组归属的 account_name 由「工号」改为「用户名」（工号下线后语义随之变化）
UPDATE tbl_platform_account_group_info a
   SET account_name = u.username
  FROM tbl_privilege_user u
 WHERE a.account_id = u.id
   AND a.account_name IS DISTINCT FROM u.username;

-- ③ 自检（L-51：只校验结构不变量，不硬编码环境相关绝对数）
DO $chk$
DECLARE left_cols int; alive int; chen_role int; bad_names int;
BEGIN
  SELECT count(*) INTO left_cols FROM information_schema.columns
   WHERE table_schema = 'public'
     AND ((table_name = 'tbl_privilege_user' AND column_name IN ('code', 'user_type', 'it_user_id', 'it_user_name'))
       OR (table_name = 'tbl_privilege_user_role' AND column_name = 'user_no'));
  IF left_cols <> 0 THEN RAISE EXCEPTION '[V2.0.0_08] 仍有 % 个待删列存在', left_cols; END IF;

  SELECT count(*) INTO alive FROM tbl_privilege_user WHERE coalesce(del_flag, 0) = 0;
  IF alive < 1 THEN RAISE EXCEPTION '[V2.0.0_08] 存活账号为 0（异常）'; END IF;
  IF NOT EXISTS (SELECT 1 FROM tbl_privilege_user WHERE username = 'chenzhuo' AND coalesce(del_flag, 0) = 0) THEN
    RAISE EXCEPTION '[V2.0.0_08] chenzhuo 账号不存在（异常）';
  END IF;

  SELECT count(*) INTO chen_role FROM tbl_privilege_user_role
   WHERE user_id = (SELECT id FROM tbl_privilege_user WHERE username = 'chenzhuo')
     AND coalesce(del_flag, 0) = 0;
  IF chen_role < 1 THEN RAISE EXCEPTION '[V2.0.0_08] chenzhuo 角色绑定丢失'; END IF;

  SELECT count(*) INTO bad_names FROM tbl_platform_account_group_info a
    JOIN tbl_privilege_user u ON u.id = a.account_id
   WHERE a.account_name IS DISTINCT FROM u.username;
  IF bad_names <> 0 THEN RAISE EXCEPTION '[V2.0.0_08] 组归属 account_name 未同步为用户名（% 行）', bad_names; END IF;

  RAISE NOTICE '[V2.0.0_08] 自检通过：待删列 0 个 / 存活账号 % / chenzhuo 角色绑定 % / 组归属名称已同步',
    alive, chen_role;
END $chk$;

COMMIT;
