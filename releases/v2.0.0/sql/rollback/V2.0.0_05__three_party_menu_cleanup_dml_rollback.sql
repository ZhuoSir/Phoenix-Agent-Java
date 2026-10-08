-- =====================================================================
-- 版本: v2.0.0  序号: 05  类型: DML(回滚)
-- 配对正向件: ../V2.0.0_05__three_party_menu_cleanup_dml.sql
-- 目的: 保真重建「基础管理」父目录与「三方平台」子菜单两行
-- 可重入: 是（INSERT ... ON CONFLICT (id) DO NOTHING）
-- 数据来源: sql/all_data.sql 中删除前的原始 INSERT 行（逐字复制，未改写任何列值）
-- =====================================================================
BEGIN;

INSERT INTO public.tbl_privilege_module ("id", "name", "url", "sn", "state", "component", "system_id", "status", "image", "order_no", "is_show", "create_time", "create_by", "update_time", "update_by", "del_flag", "pid", "category_id", "type") VALUES ('37def68697b54109a57c18508fc4358c', '三方平台', '/basic/platform-info', 'PlatformInfo', '31', '#/views/platform/platform-info/index.vue', 110, 1, 'lucide:globe', 1, 1, '2026-07-16 00:00:00', 'admin', NULL, NULL, 0, '71b572d54b7042359e73e64a6fc40dfc', 111, '1')
  ON CONFLICT (id) DO NOTHING;
INSERT INTO public.tbl_privilege_module ("id", "name", "url", "sn", "state", "component", "system_id", "status", "image", "order_no", "is_show", "create_time", "create_by", "update_time", "update_by", "del_flag", "pid", "category_id", "type") VALUES ('71b572d54b7042359e73e64a6fc40dfc', '基础管理', '/basic', 'BasicManagement', '31', NULL, 110, 1, 'lucide:settings', 400, 1, '2026-07-16 14:34:51.878', 'admin', '2026-07-16 14:34:51.986', '428011841386577920', 0, NULL, 111, '0')
  ON CONFLICT (id) DO NOTHING;

COMMIT;
