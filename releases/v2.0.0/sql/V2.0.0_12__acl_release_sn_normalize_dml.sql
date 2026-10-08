-- =====================================================================
-- 版本: v2.0.0  序号: 12  类型: DML
-- 来源: specs/20261007_user-role-group-model（BUG-138 数据修复）
-- 目的: 把角色 ACL 中被误写为「角色业务 sn」的 release_sn 归正为类型值 'role'
-- 前置: V2.0.0_01~11 已应用；后端须为含 BUG-138 修复的版本（saveModuleAcl 强制 release_sn='role'）
-- 可重入: 是（仅命中 release_sn != 'role' 的角色 ACL 行；归正后 0 行）
-- 回滚: rollback/V2.0.0_12__acl_release_sn_normalize_dml_rollback.sql
-- ---------------------------------------------------------------------
-- 根因（BUG-138）：saveModuleAcl 原信任 DTO.releaseSn，前端传 role.sn（'COMMON'/'ROLE_ADMIN'），
--   而登录菜单 buildUserAclMap / getRoleAcls 均按 release_sn='role' 过滤 ⇒ 这些授权行对菜单**不可见**
--   （表现为：给 common 角色授了技能/插件/MCP，但 chenzhuo 左侧目录不变）。
--   saveAllAcl 一直硬编码 'role'（正确），saveModuleAcl 信任 DTO（错误）⇒ 口径不一致。
-- 本件只修数据；代码侧 saveModuleAcl 已改为强制 'role'（插入+更新双分支，存量自愈）。
-- =====================================================================
BEGIN;

UPDATE tbl_privilege_acl
   SET release_sn = 'role'
 WHERE coalesce(del_flag, 0) = 0
   AND release_sn IS DISTINCT FROM 'role'
   AND release_id IN (SELECT id FROM tbl_privilege_role);

-- 自检：角色 ACL 不得再有 release_sn != 'role' 的行
DO $chk$
DECLARE bad int;
BEGIN
  SELECT count(*) INTO bad FROM tbl_privilege_acl
   WHERE coalesce(del_flag, 0) = 0
     AND release_sn IS DISTINCT FROM 'role'
     AND release_id IN (SELECT id FROM tbl_privilege_role);
  IF bad <> 0 THEN
    RAISE EXCEPTION '[V2.0.0_12] 仍有 % 行角色 ACL 的 release_sn 非 role', bad;
  END IF;
  RAISE NOTICE '[V2.0.0_12] 自检通过：角色 ACL 中 release_sn 非 role 的行 = 0';
END $chk$;

COMMIT;
