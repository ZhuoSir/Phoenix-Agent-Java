-- =====================================================================
-- 版本: v2.0.0  序号: 03  类型: DML-ROLLBACK
-- 对应正向件: sql/V2.0.0_03__org_menu_cleanup_dml.sql
-- 目的: 保真重建被删除的「组织管理」菜单族（1 目录 + 3 叶子）与其角色授权行
-- 数据来源: pg_dump 自演练库 phoenix_drill（2026-10-07）导出的原始行，逐列保真
-- 可重入: 是（先按 id 清理同 id 残留，再插入）
-- 注意: 若生产库这 8 行与演练库不同（例如 create_time 或 acl_state 被改过），
--       以生产库备份 backups/pre_v2.0.0_*.sql 中的同名行为准覆盖本文件数据段。
-- =====================================================================
BEGIN;

-- ① 幂等清理（防重复回滚产生主键冲突）
DELETE FROM tbl_privilege_acl
 WHERE module_id IN ('2bafd881f51f43519a54a429be0dabb5',
                     '5db7285051204f81867995e2565931c1',
                     '8ec79aa5e5ab4365805d49a4ff0c5116',
                     '90511f372a564de1b1ab8df780c640b7');
DELETE FROM tbl_privilege_module
 WHERE id IN ('2bafd881f51f43519a54a429be0dabb5',
              '5db7285051204f81867995e2565931c1',
              '8ec79aa5e5ab4365805d49a4ff0c5116',
              '90511f372a564de1b1ab8df780c640b7');

-- ② 重建菜单行（4 行）
INSERT INTO public.tbl_privilege_module (id, name, url, sn, state, component, system_id, status, image, order_no, is_show, create_time, create_by, update_time, update_by, del_flag, pid, category_id, type) VALUES ('5db7285051204f81867995e2565931c1', '部门管理', '/organization/department', 'DepartmentManagement', '31', '#/views/organization/department/index.vue', 110, 1, 'lucide:network', 2, 1, '2026-07-07 01:20:57.321454', 'admin', NULL, NULL, 0, '2bafd881f51f43519a54a429be0dabb5', 111, '1');
INSERT INTO public.tbl_privilege_module (id, name, url, sn, state, component, system_id, status, image, order_no, is_show, create_time, create_by, update_time, update_by, del_flag, pid, category_id, type) VALUES ('8ec79aa5e5ab4365805d49a4ff0c5116', '人员管理', '/organization/employee', 'EmployeeManagement', '31', '#/views/organization/employee/index.vue', 110, 1, 'lucide:users', 3, 1, '2026-07-07 01:20:57.321454', 'admin', NULL, NULL, 0, '2bafd881f51f43519a54a429be0dabb5', 111, '1');
INSERT INTO public.tbl_privilege_module (id, name, url, sn, state, component, system_id, status, image, order_no, is_show, create_time, create_by, update_time, update_by, del_flag, pid, category_id, type) VALUES ('90511f372a564de1b1ab8df780c640b7', '公司管理', '/organization/company', 'CompanyManagement', '31', '#/views/organization/company/index.vue', 110, 1, 'lucide:building', 1, 1, '2026-07-07 01:20:57.321454', 'admin', NULL, NULL, 0, '2bafd881f51f43519a54a429be0dabb5', 111, '1');
INSERT INTO public.tbl_privilege_module (id, name, url, sn, state, component, system_id, status, image, order_no, is_show, create_time, create_by, update_time, update_by, del_flag, pid, category_id, type) VALUES ('2bafd881f51f43519a54a429be0dabb5', '组织管理', '/organization', 'OrganizationManagement', '31', NULL, 110, 1, 'lucide:building-2', 10, 1, '2026-07-07 01:20:57.321454', 'admin', NULL, NULL, 0, NULL, 111, '0');

-- ③ 重建授权行（4 行）
INSERT INTO public.tbl_privilege_acl (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, create_by, update_time, update_by, del_flag) VALUES ('431929027977158656', '428007432736870400', 'role', NULL, '5db7285051204f81867995e2565931c1', 'DepartmentManagement', '31', '2026-07-07 10:32:45.08', NULL, '2026-07-07 10:32:45.139725', NULL, 0);
INSERT INTO public.tbl_privilege_acl (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, create_by, update_time, update_by, del_flag) VALUES ('431929027977158657', '428007432736870400', 'role', NULL, '8ec79aa5e5ab4365805d49a4ff0c5116', 'EmployeeManagement', '31', '2026-07-07 10:32:45.08', NULL, '2026-07-07 10:32:45.139725', NULL, 0);
INSERT INTO public.tbl_privilege_acl (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, create_by, update_time, update_by, del_flag) VALUES ('431929027977158658', '428007432736870400', 'role', NULL, '90511f372a564de1b1ab8df780c640b7', 'CompanyManagement', '31', '2026-07-07 10:32:45.08', NULL, '2026-07-07 10:32:45.139725', NULL, 0);
INSERT INTO public.tbl_privilege_acl (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, create_by, update_time, update_by, del_flag) VALUES ('431929027985547264', '428007432736870400', 'role', NULL, '2bafd881f51f43519a54a429be0dabb5', 'OrganizationManagement', '31', '2026-07-07 10:32:45.08', NULL, '2026-07-07 10:32:45.139725', NULL, 0);

-- ④ 自检：4 行菜单 + 4 行授权应已恢复
DO $$
DECLARE v_mod int; v_acl int;
BEGIN
  SELECT count(*) INTO v_mod FROM tbl_privilege_module
   WHERE id IN ('2bafd881f51f43519a54a429be0dabb5','5db7285051204f81867995e2565931c1',
                '8ec79aa5e5ab4365805d49a4ff0c5116','90511f372a564de1b1ab8df780c640b7');
  SELECT count(*) INTO v_acl FROM tbl_privilege_acl
   WHERE module_id IN ('2bafd881f51f43519a54a429be0dabb5','5db7285051204f81867995e2565931c1',
                       '8ec79aa5e5ab4365805d49a4ff0c5116','90511f372a564de1b1ab8df780c640b7');
  IF v_mod <> 4 OR v_acl <> 4 THEN
    RAISE EXCEPTION '[V2.0.0_03-rollback] 自检失败：菜单 % 行（应 4）/ 授权 % 行（应 4）', v_mod, v_acl;
  END IF;
  RAISE NOTICE '[V2.0.0_03-rollback] 自检通过：菜单 4 行 + 授权 4 行已恢复';
END $$;

COMMIT;
