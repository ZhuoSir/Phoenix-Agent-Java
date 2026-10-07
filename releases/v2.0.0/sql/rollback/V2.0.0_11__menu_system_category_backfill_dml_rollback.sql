-- =====================================================================
-- 版本: v2.0.0  序号: 11  类型: DML(回滚)
-- 配对正向件: ../V2.0.0_11__menu_system_category_backfill_dml.sql
-- 目的: 把本件补过的 5 个菜单 system_id/category_id 复位置空（回到修复前）
-- 范围: 仅限正向执行前 system_id 为空的那 5 个 id
-- 可重入: 是
-- =====================================================================
BEGIN;

UPDATE tbl_privilege_module SET system_id = NULL, category_id = NULL, update_time = now()
 WHERE id IN ('31c0b8846d77c8fdf307b1d919c4d7e9',  -- MCP
              '1b54cdb090239a9522e0cf11d9a1865f',  -- 技能管理
              '8fd5c27977edb0b647111e003f62f7cc',  -- 插件管理
              'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70',  -- 智能体中心
              '8785daa6205b62774f689c4d6addab4f'); -- 知识库

COMMIT;
