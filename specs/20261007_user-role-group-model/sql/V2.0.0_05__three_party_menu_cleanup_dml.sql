-- =====================================================================
-- 版本: v2.0.0  序号: 05  类型: DML
-- 来源: specs/20261007_user-role-group-model (T-15)
-- 目的: R-11 —— 下线「三方平台」配置菜单，并连带删除其唯一父目录「基础管理」
-- 前置: 无（与 03 件同族：03 管组织菜单，05 管三方平台菜单）
-- 可重入: 是（按固定 id 删除；重复执行影响 0 行）
-- 回滚: rollback/V2.0.0_05__three_party_menu_cleanup_dml_rollback.sql（2 行保真重建）
-- ---------------------------------------------------------------------
-- 目标行（来源：sql/all_data.sql 与生产库实测，两处一致）:
--   父目录 基础管理  71b572d54b7042359e73e64a6fc40dfc  (/basic,               type=0, order_no=400)
--   子菜单 三方平台  37def68697b54109a57c18508fc4358c  (/basic/platform-info, type=1, order_no=1)
-- **连带删除父目录的依据（实测，非推断）**：基础管理的子节点**只有**三方平台一个
--   （`select * from tbl_privilege_module where pid='71b572d…'` 返回 1 行，无软删子节点）
--   ⇒ 只删子菜单会在导航里留下一个**空目录**；R-11 的意图是平台配置整体下线，故一并删除。
-- ACL 行: 「三方平台」「基础管理」均**无**授权行（实测 0 行），故本件无需删 ACL。
-- 前端侧同批下线见 T-11（views/platform/platform-info 与 api/core/platform-info.ts 已删）。
-- =====================================================================
BEGIN;

-- ① 防御性清理授权行（实测 0 行；若某环境曾单独授权过，此处一并清掉）
DELETE FROM tbl_privilege_acl
 WHERE module_id IN ('37def68697b54109a57c18508fc4358c',
                     '71b572d54b7042359e73e64a6fc40dfc');

-- ② 先删子菜单、再删父目录（可读性；无外键约束，顺序不影响结果）
DELETE FROM tbl_privilege_module WHERE id = '37def68697b54109a57c18508fc4358c';
DELETE FROM tbl_privilege_module WHERE id = '71b572d54b7042359e73e64a6fc40dfc';

COMMIT;
