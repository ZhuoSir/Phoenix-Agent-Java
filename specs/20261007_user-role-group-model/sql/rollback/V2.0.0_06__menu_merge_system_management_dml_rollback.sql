-- =====================================================================
-- 版本: v2.0.0  序号: 06  类型: DML(回滚)
-- 配对正向件: ../V2.0.0_06__menu_merge_system_management_dml.sql
-- 目的: 还原信息架构 —— 「系统管理」改回「权限管理」；组管理迁回前台管理；
--       次序复位；保真重建被删的「前台管理」与其「账号管理」两行及授权行
-- 可重入: 是（UPDATE 幂等；INSERT ... ON CONFLICT (id) DO NOTHING）
-- 数据来源: 被删两行由**正向执行前**的生产库逐行导出（字段级保真，未改写任何列值）
-- =====================================================================
BEGIN;

-- ① 先保真重建被删的两行（前台管理 + 前台账号管理）与其授权行
INSERT INTO tbl_privilege_module (id, name, url, sn, state, component, system_id, status, image, order_no, is_show, create_time, create_by, update_time, update_by, del_flag, pid, category_id, type) VALUES ('6752c28a59c048cb9d08179ccadb38b4', '账号管理', '/platform-account/account-info', 'PlatformAccountInfo', '31', '#/views/account/account-info/index.vue', 110, 1, 'lucide:user', 1, 1, '2026-07-07T01:20:57.321454', 'admin', NULL, NULL, 0, '638d2319c2d54f3ea36b2a519c9a1d0f', 111, '1') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_module (id, name, url, sn, state, component, system_id, status, image, order_no, is_show, create_time, create_by, update_time, update_by, del_flag, pid, category_id, type) VALUES ('638d2319c2d54f3ea36b2a519c9a1d0f', '前台管理', '/platform-account', 'PlatformAccount', '31', NULL, 110, 1, 'lucide:users', 350, 1, '2026-07-07T11:28:13.004', 'admin', NULL, NULL, 0, NULL, 111, '0') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_acl (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, create_by, update_time, update_by, del_flag) VALUES ('e0e26d2d83f8ffac68d15a79a9adadbb', '428007432736870400', 'role', NULL, '638d2319c2d54f3ea36b2a519c9a1d0f', 'PlatformAccount', '31', '2026-10-07T18:34:56.430888', 'V2.0.0_04', '2026-10-07T18:34:56.430888', 'V2.0.0_04', 0) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_acl (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, create_by, update_time, update_by, del_flag) VALUES ('09435e0eba0462678d1108aa40f67a92', '428007432736870400', 'role', NULL, '6752c28a59c048cb9d08179ccadb38b4', 'PlatformAccountInfo', '31', '2026-10-07T18:34:56.430888', 'V2.0.0_04', '2026-10-07T18:34:56.430888', 'V2.0.0_04', 0) ON CONFLICT (id) DO NOTHING;

-- ② 次序复位（3/4/5 是原值）
UPDATE tbl_privilege_module SET order_no = 3, update_time = now() WHERE id = 'eecdaae775a54fa8b267c329e489e2';
UPDATE tbl_privilege_module SET order_no = 4, update_time = now() WHERE id = 'b6520f15610f49ecb30f647bdb13ca5f';
UPDATE tbl_privilege_module SET order_no = 5, update_time = now() WHERE id = '6c0ee41bea32413080907d4e5584cda3';

-- ②b 子菜单 URL 前缀复位
UPDATE tbl_privilege_module SET url = '/permission-management/role',             update_time = now() WHERE id = '12068648a756427ebe34a89f7c3502b3';
UPDATE tbl_privilege_module SET url = '/permission-management/account',          update_time = now() WHERE id = 'b5632ad3233348b1966665ae6ca2a1ba';
UPDATE tbl_privilege_module SET url = '/permission-management/menu',             update_time = now() WHERE id = 'eecdaae775a54fa8b267c329e489e2';
UPDATE tbl_privilege_module SET url = '/permission-management/permission-value', update_time = now() WHERE id = 'b6520f15610f49ecb30f647bdb13ca5f';
UPDATE tbl_privilege_module SET url = '/permission-management/log',              update_time = now() WHERE id = '6c0ee41bea32413080907d4e5584cda3';

-- ③ 组管理迁回前台管理、url 复位
UPDATE tbl_privilege_module
   SET pid = '638d2319c2d54f3ea36b2a519c9a1d0f', url = '/platform-account/group-info', order_no = 2, update_time = now()
 WHERE id = '8b1a156184cf49fba34389e8caea4269';

-- ③ 「系统管理」改回「权限管理」（先重建被删的父目录，才能挂回组管理）
-- ④ 目录名/url/sn 复位
UPDATE tbl_privilege_module
   SET name = '权限管理', url = '/permission-management', sn = 'PermissionManagement', update_time = now()
 WHERE id = '02b733aa08774219a23c2f21f1b3f6b5';

COMMIT;
