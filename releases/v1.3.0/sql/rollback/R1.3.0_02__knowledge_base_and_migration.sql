-- 回滚件：对应 02_knowledge_base.sql（knowledge-base T-01）
-- 无损保证：条目表仅去新列（数据行全保留）；库/绑定/组授权/菜单为纯新增物
DROP TABLE IF EXISTS tbl_platform_group_kbase_info;
DROP TABLE IF EXISTS tbl_data_agent_kbase_bind;
DROP TABLE IF EXISTS tbl_data_knowledge_base;
DROP INDEX IF EXISTS idx_dak_kb;
ALTER TABLE tbl_data_agent_knowledge DROP COLUMN IF EXISTS knowledge_base_id;
DELETE FROM tbl_privilege_acl    WHERE module_id = md5('phoenix-knowledge-base-menu');
DELETE FROM tbl_privilege_module WHERE id = md5('phoenix-knowledge-base-menu');
