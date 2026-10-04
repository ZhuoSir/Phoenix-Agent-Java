-- ============================================
-- 版本: v1.6.0  序号: 02  类型: DML
-- 来源: specs/20261004_mcp-client-tools (T-02)
-- 前置: V1.6.0_01
-- 可重入: 是（md5 常量 id + NOT EXISTS 防重）
-- 预估: <1s
-- 回滚: rollback/R1.6.0_02__mcp_plugin_menu_acl_dml.sql
-- ============================================

-- 插件市场三层菜单：插件市场(目录) → 插件管理(目录) → MCP(页面)
INSERT INTO tbl_privilege_module (id, pid, name, url, sn, component, type, order_no, is_show, status, image, create_time, del_flag)
SELECT md5('phoenix-plugin-market'), '', '插件市场', '/plugin-market', 'PluginMarket', NULL, 0, 17, 1, 1, 'lucide:store', now(), 0
WHERE NOT EXISTS (SELECT 1 FROM tbl_privilege_module WHERE id = md5('phoenix-plugin-market'));

INSERT INTO tbl_privilege_module (id, pid, name, url, sn, component, type, order_no, is_show, status, image, create_time, del_flag)
SELECT md5('phoenix-plugin-manage'), md5('phoenix-plugin-market'), '插件管理', '/plugin-market/manage', 'PluginManage', NULL, 0, 0, 1, 1, 'lucide:layout-grid', now(), 0
WHERE NOT EXISTS (SELECT 1 FROM tbl_privilege_module WHERE id = md5('phoenix-plugin-manage'));

INSERT INTO tbl_privilege_module (id, pid, name, url, sn, component, type, order_no, is_show, status, image, create_time, del_flag)
SELECT md5('phoenix-plugin-mcp'), md5('phoenix-plugin-manage'), 'MCP', '/plugin/mcp', 'PluginMcp', '#/views/plugin/mcp/index.vue', 1, 0, 1, 1, 'lucide:plug', now(), 0
WHERE NOT EXISTS (SELECT 1 FROM tbl_privilege_module WHERE id = md5('phoenix-plugin-mcp'));

-- ACL：按「智能体列表」模块(f0c0d2d3a7bb452cb5ff98328575b41a)既有授权角色逐一复制（三层菜单各一份，镜像 V1.2.0_02 写法）
INSERT INTO tbl_privilege_acl (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, del_flag)
SELECT gen_random_uuid()::text, a.release_id, a.release_sn, a.system_sn, m.mid, a.module_sn, a.acl_state, now(), 0
FROM tbl_privilege_acl a
CROSS JOIN (VALUES (md5('phoenix-plugin-market')), (md5('phoenix-plugin-manage')), (md5('phoenix-plugin-mcp'))) AS m(mid)
WHERE a.module_id = 'f0c0d2d3a7bb452cb5ff98328575b41a'
  AND a.del_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM tbl_privilege_acl b
      WHERE b.module_id = m.mid AND b.release_id = a.release_id AND b.release_sn = a.release_sn AND b.del_flag = 0
  );
