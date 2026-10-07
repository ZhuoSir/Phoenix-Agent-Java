-- =====================================================================
-- 版本: v2.0.0  序号: 06  类型: DML
-- 来源: specs/20261007_user-role-group-model (T-18 / R-12, requirements v2.1.0)
-- 目的: 后台信息架构调整 —— 「权限管理」+「前台管理」合并为「系统管理」；账号管理唯一化
-- 前置: V2.0.0_01~05 已应用（本件依赖 04 重建后的 ACL 基线：超管=存活菜单、普通=7）
-- 可重入: 是（全部按固定 id 操作；重复执行影响 0 行）
-- 回滚: rollback/V2.0.0_06__menu_merge_system_management_dml_rollback.sql（含被删 2 行保真重建）
-- ---------------------------------------------------------------------
-- 用户口令（2026-10-07，确认人陈卓）：权限管理和前台管理合并成系统管理，然后前台管理的账号管理删掉，
--   现在只有一套账号管理，不分前后台了。这样角色，用户，组都在一起。
-- 目标 id（生产库实测）:
--   系统管理（原权限管理） 02b733aa08774219a23c2f21f1b3f6b5  /permission-management → /system-management
--   组管理                 8b1a156184cf49fba34389e8caea4269  前台管理 → 系统管理，url → /system-management/group
--   菜单/权限值/日志管理    eecdaae7… / b6520f15… / 6c0ee41b…  次序 3/4/5 → 4/5/6
--   删：前台 账号管理      6752c28a59c048cb9d08179ccadb38b4  /platform-account/account-info
--   删：前台管理（空目录） 638d2319c2d54f3ea36b2a519c9a1d0f  /platform-account
-- 不变量: 存活菜单 21 → 19；超管 ACL 行数 == 存活菜单数；普通角色 ACL 仍 7；
--         组管理与角色管理的 ACL 行不因搬迁/改名而丢失。
-- =====================================================================
BEGIN;

-- ① 「权限管理」原地更名「系统管理」（**id 不变** ⇒ 其 ACL 行继续有效）
UPDATE tbl_privilege_module
   SET name = '系统管理', url = '/system-management', sn = 'SystemManagement', update_time = now()
 WHERE id = '02b733aa08774219a23c2f21f1b3f6b5'
   AND coalesce(del_flag, 0) = 0;

-- ② 「组管理」迁入系统管理，与 角色/账号 相邻（次序 2 → 3）
UPDATE tbl_privilege_module
   SET pid = '02b733aa08774219a23c2f21f1b3f6b5', url = '/system-management/group', order_no = 3, update_time = now()
 WHERE id = '8b1a156184cf49fba34389e8caea4269'
   AND coalesce(del_flag, 0) = 0;

-- ③ 后继菜单次序顺延，保持 角色→账号→组→菜单→权限值→日志
UPDATE tbl_privilege_module SET order_no = 4, update_time = now() WHERE id = 'eecdaae775a54fa8b267c329e489e2';
UPDATE tbl_privilege_module SET order_no = 5, update_time = now() WHERE id = 'b6520f15610f49ecb30f647bdb13ca5f';
UPDATE tbl_privilege_module SET order_no = 6, update_time = now() WHERE id = '6c0ee41bea32413080907d4e5584cda3';

-- ③b 子菜单 URL 前缀与父目录保持一致（/permission-management/* → /system-management/*；
--     用户 2026-10-07 确认「URL 一并改成 /system-management」；全仓无硬编码引用，改之安全）
UPDATE tbl_privilege_module SET url = '/system-management/role',             update_time = now() WHERE id = '12068648a756427ebe34a89f7c3502b3';
UPDATE tbl_privilege_module SET url = '/system-management/account',          update_time = now() WHERE id = 'b5632ad3233348b1966665ae6ca2a1ba';
UPDATE tbl_privilege_module SET url = '/system-management/menu',             update_time = now() WHERE id = 'eecdaae775a54fa8b267c329e489e2';
UPDATE tbl_privilege_module SET url = '/system-management/permission-value', update_time = now() WHERE id = 'b6520f15610f49ecb30f647bdb13ca5f';
UPDATE tbl_privilege_module SET url = '/system-management/log',              update_time = now() WHERE id = '6c0ee41bea32413080907d4e5584cda3';

-- ④ 先删授权行（指向被删两个菜单的），再删菜单行；避免残留悬空授权
DELETE FROM tbl_privilege_acl
 WHERE module_id IN ('6752c28a59c048cb9d08179ccadb38b4',
                     '638d2319c2d54f3ea36b2a519c9a1d0f');

-- ⑤ 删「前台管理 / 账号管理」（唯一账号管理保留在 系统管理 下）
DELETE FROM tbl_privilege_module WHERE id = '6752c28a59c048cb9d08179ccadb38b4';

-- ⑥ 删变空的「前台管理」目录
DELETE FROM tbl_privilege_module WHERE id = '638d2319c2d54f3ea36b2a519c9a1d0f';

-- ⑦ 自检（**环境无关的不变量**；绝对数量随环境而定，故只校验结构与守恒关系，
--     并把实测数量打到 NOTICE 供证据留痕 —— 生产升级路径实测 21→19、超管 ACL 19、普通 7）
DO $chk$
DECLARE alive int; admin_acl int; common_acl int; group_acl int; leftovers int; legacy int; sys_kids int;
BEGIN
  -- ① 旧目录必须消失
  SELECT count(*) INTO legacy FROM tbl_privilege_module
   WHERE name IN ('权限管理', '前台管理') AND coalesce(del_flag, 0) = 0;
  IF legacy <> 0 THEN RAISE EXCEPTION '[V2.0.0_06] 旧目录（权限管理/前台管理）仍存活 % 条', legacy; END IF;

  -- ② 「系统管理」下必须恰好 6 个子菜单（角色/账号/组/菜单/权限值/日志）
  SELECT count(*) INTO sys_kids FROM tbl_privilege_module
   WHERE pid = '02b733aa08774219a23c2f21f1b3f6b5' AND coalesce(del_flag, 0) = 0;
  IF sys_kids <> 6 THEN RAISE EXCEPTION '[V2.0.0_06] 系统管理子菜单 % <> 6', sys_kids; END IF;

  -- ③ 组管理必须挂在系统管理下、次序 3（与 角色/账号 相邻）
  IF NOT EXISTS (SELECT 1 FROM tbl_privilege_module
                  WHERE id = '8b1a156184cf49fba34389e8caea4269'
                    AND pid = '02b733aa08774219a23c2f21f1b3f6b5' AND order_no = 3
                    AND coalesce(del_flag, 0) = 0) THEN
    RAISE EXCEPTION '[V2.0.0_06] 组管理未正确挂到系统管理下（pid/order_no 不符）';
  END IF;

  -- ④ 被删菜单不得残留授权行
  SELECT count(*) INTO leftovers FROM tbl_privilege_acl
   WHERE module_id IN ('6752c28a59c048cb9d08179ccadb38b4', '638d2319c2d54f3ea36b2a519c9a1d0f');
  IF leftovers <> 0 THEN RAISE EXCEPTION '[V2.0.0_06] 残留被删菜单的授权行 % 条', leftovers; END IF;

  -- ⑤ 守恒：超管 ACL 行数 == 存活菜单数（R-12 明示不变量）
  SELECT count(*) INTO alive FROM tbl_privilege_module WHERE coalesce(del_flag, 0) = 0;
  SELECT count(*) INTO admin_acl FROM tbl_privilege_acl
   WHERE release_id = '428007432736870400' AND coalesce(del_flag, 0) = 0;
  IF admin_acl <> alive THEN
    RAISE EXCEPTION '[V2.0.0_06] 超管 ACL 行 % <> 存活菜单 %（守恒被破坏）', admin_acl, alive;
  END IF;

  -- ⑥ 组管理授权不得因搬迁/改名丢失
  SELECT count(*) INTO group_acl FROM tbl_privilege_acl
   WHERE module_id = '8b1a156184cf49fba34389e8caea4269' AND coalesce(del_flag, 0) = 0;
  IF group_acl < 1 THEN RAISE EXCEPTION '[V2.0.0_06] 组管理 ACL 行丢失（搬迁不应影响授权）'; END IF;

  SELECT count(*) INTO common_acl FROM tbl_privilege_acl
   WHERE release_id = '431285032083144704' AND coalesce(del_flag, 0) = 0;

  RAISE NOTICE '[V2.0.0_06] 自检通过：存活菜单 % / 超管 ACL % / 普通角色 ACL % / 组管理 ACL %（结构不变量全过）',
    alive, admin_acl, common_acl, group_acl;
END $chk$;

COMMIT;
