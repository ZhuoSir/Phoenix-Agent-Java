# Artifacts: mcp-client-tools

> 版本: v1.0.0 | 更新: 2026-10-04

## SQL（releases/v1.6.0/sql/）

| 件 | 内容 |
|---|---|
| V1.6.0_01__mcp_plugin_tables.sql | 三表：tbl_mcp_server（config jsonb，敏感值 enc:v1: 密文）/ tbl_platform_group_mcp_info / tbl_data_agent_mcp_info + 索引（name 部分唯一） |
| V1.6.0_02__mcp_plugin_menu_acl_dml.sql | 菜单两层（插件管理顶级+MCP 页）+ACL 复制（智能体列表角色） |
| rollback/R1.6.0_01 / R1.6.0_02 | 逆执行（R02 防御性含已废弃市场层 id） |

## 后端（phoenix-agent 三层 + phoenix-data 一行级联）

- api：McpServerInfo/GroupMcpInfo/AgentMcpInfo 实体、McpSaveDTO/McpTestDTO/McpBindDTO、McpListVO/McpDetailVO/McpTestResultVO/McpOptionVO、McpErrorCodeEnm(46xxx)
- core：Mapper×3、McpSecretCipher（AES-GCM+平台同款掩码）、McpAdminService(Impl)（CRUD/启停/防悬挂/组授权/测试连接+HTTP预探/options/bound/bind）、FrontMcpAccessService(Impl)（前台三重交集/admin 绑定∧启用）、harness/mcp/McpMountService（变体缓存 LRU64/签名含 name/boundedElastic/单 server 失败跳过）、harness/mcp/PrefixedAgentTool（前缀委托壳）
- 接线：HarnessAgentRegistry.buildUncached、HarnessChatServiceImpl（doStream/confirmStream/effectiveMcp）、HarnessRequest/ConfirmRequest.channel、AgentServiceImpl 级联 n6
- rest：McpAdminController（/api/mcp 十端点）、三控制器 channel 打标

## 前端（admin-ui）

- api/core/mcp.ts（11 函数）
- views/plugin/mcp/index.vue（列表/编辑抽屉/测试连接/详情/授权弹窗）
- views/agent/edit/components/AgentPluginConfig.vue（「插件配置」块，抽屉+独立页双入口挂载）

## 配置/环境

- PHOENIX_MCP_CIPHER_KEY（新 env，生产必配——见 completion §环境新配置项）

## 文档/台账

- requirements v1.1.0 已确认 / plan v1.1.0 已确认 / tasks v1.0.0（9/9）/ changelog 全程 / completion / artifacts
- bugs：BUG-65/66 已验证(v1.6.0在途)；BUG-64 新建；lessons：L-15 新增，L-03 ×1、L-06 ×4 回炉
- backlog：BL-01 已立项(v1.6.0)（发版时翻已交付）；BL-25 API 插件入册
