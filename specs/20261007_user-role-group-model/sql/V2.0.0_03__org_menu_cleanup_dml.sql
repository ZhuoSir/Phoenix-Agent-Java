-- =====================================================================
-- 版本: v2.0.0  序号: 03  类型: DML
-- 来源: specs/20261007_user-role-group-model (T-05)
-- 目的: R-01 —— 下线「组织管理」菜单族（1 个目录 + 3 个子菜单）及其角色授权行
-- 前置: 无（不依赖 V2.0.0_01；先删授权行、再删菜单行）
-- 可重入: 是（按固定 id 删除；重复执行影响 0 行）
-- 回滚: rollback/V2.0.0_03__org_menu_cleanup_dml_rollback.sql（含原始 8 行的保真重建）
-- ---------------------------------------------------------------------
-- 目标 id（来源：sql/all_data.sql / 生产库实测）:
--   目录：组织管理     2bafd881f51f43519a54a429be0dabb5  (/organization,           type=0, order_no=10)
--   叶子：公司管理     90511f372a564de1b1ab8df780c640b7  (/organization/company,   order_no=1)
--        部门管理     5db7285051204f81867995e2565931c1  (/organization/department,order_no=2)
--        人员管理     8ec79aa5e5ab4365805d49a4ff0c5116  (/organization/employee,  order_no=3)
-- AC/前端侧同批下线见 T-11（页面/路由/API 客户端）；本件只动库内菜单与授权数据。
-- 注：ACL 表 module_id 是**过期冗余列 module_sn 之外的权威列**（L-29 已实测 join 结论），
--     因此一律按 module_id 删除，禁用 module_sn 反查。
-- =====================================================================
BEGIN;

-- ① 先删授权行（只删指向这 4 个菜单的行，其余角色授权不受影响）
DELETE FROM tbl_privilege_acl
 WHERE module_id IN ('2bafd881f51f43519a54a429be0dabb5',
                     '5db7285051204f81867995e2565931c1',
                     '8ec79aa5e5ab4365805d49a4ff0c5116',
                     '90511f372a564de1b1ab8df780c640b7');

-- ② 再删菜单行（3 个叶子 → 1 个目录；无层级依赖，一条 IN 即可）
DELETE FROM tbl_privilege_module
 WHERE id IN ('2bafd881f51f43519a54a429be0dabb5',
              '5db7285051204f81867995e2565931c1',
              '8ec79aa5e5ab4365805d49a4ff0c5116',
              '90511f372a564de1b1ab8df780c640b7');

-- ③ 兜底：按 url 前缀再清一次（防 id 漂移；正常情况下影响 0 行）
DELETE FROM tbl_privilege_acl
 WHERE module_id IN (SELECT id FROM tbl_privilege_module WHERE url LIKE '/organization%');

DELETE FROM tbl_privilege_module
 WHERE url LIKE '/organization%';

-- ④ 自检：库内不应再残留组织维度菜单与其授权行
DO $$
DECLARE v_mod int; v_acl int;
BEGIN
  SELECT count(*) INTO v_mod FROM tbl_privilege_module WHERE url LIKE '/organization%';
  SELECT count(*) INTO v_acl
    FROM tbl_privilege_acl a
   WHERE a.module_id IN ('2bafd881f51f43519a54a429be0dabb5',
                         '5db7285051204f81867995e2565931c1',
                         '8ec79aa5e5ab4365805d49a4ff0c5116',
                         '90511f372a564de1b1ab8df780c640b7');
  IF v_mod > 0 OR v_acl > 0 THEN
    RAISE EXCEPTION '[V2.0.0_03] 自检失败：残留组织菜单 % 行 / 授权 % 行', v_mod, v_acl;
  END IF;
  RAISE NOTICE '[V2.0.0_03] 自检通过：组织菜单与其授权行均为 0';
END $$;

COMMIT;
