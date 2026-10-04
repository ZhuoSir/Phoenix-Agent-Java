-- 回滚 V1.6.0_02：删菜单与 ACL（含已废弃的市场层 id，防御两代形态）
DELETE FROM tbl_privilege_acl WHERE module_id IN (md5('phoenix-plugin-market'), md5('phoenix-plugin-manage'), md5('phoenix-plugin-mcp'));
DELETE FROM tbl_privilege_module WHERE id IN (md5('phoenix-plugin-market'), md5('phoenix-plugin-manage'), md5('phoenix-plugin-mcp'));
