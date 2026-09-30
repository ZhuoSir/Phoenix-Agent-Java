-- ============================================
-- 回滚件：对应 V1.2.0_02__skill_menu_and_acl_dml.sql
-- 作用: 删除「技能管理」菜单模块与其 ACL 授权（id 为 md5 常量，重复执行 no-op）
-- ============================================

DELETE FROM tbl_privilege_acl    WHERE module_id = md5('phoenix-skill-menu');
DELETE FROM tbl_privilege_module WHERE id        = md5('phoenix-skill-menu');
