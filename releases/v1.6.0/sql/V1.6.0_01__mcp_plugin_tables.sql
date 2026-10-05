-- ============================================
-- 版本: v1.6.0  序号: 01  类型: DDL
-- 来源: specs/20261004_mcp-client-tools (T-02)
-- 前置: 无（基线库即可）
-- 可重入: 是（全 IF NOT EXISTS）
-- 预估: <1s（三张配置级小表）
-- 回滚: rollback/R1.6.0_01__mcp_plugin_tables.sql
-- ============================================

-- MCP 插件三件套（与技能三件套 tbl_harness_skills/tbl_platform_group_skill_info/tbl_data_agent_skill_info 同构）

-- 1) MCP Server 注册表（平台级统一管理，R-01）
CREATE TABLE IF NOT EXISTS tbl_mcp_server (
    id          varchar(32)  NOT NULL,
    name        varchar(64)  NOT NULL,
    transport   varchar(24)  NOT NULL,
    config      jsonb        NOT NULL DEFAULT '{}',
    description varchar(255),
    status      varchar(16)  NOT NULL DEFAULT 'enabled',
    creator     varchar(32),
    create_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updator     varchar(32),
    update_time timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag    smallint     NOT NULL DEFAULT 0,
    CONSTRAINT pk_mcp_server PRIMARY KEY (id)
);
COMMENT ON TABLE  tbl_mcp_server IS 'MCP Server 注册表（插件市场·插件管理·MCP）';
COMMENT ON COLUMN tbl_mcp_server.id        IS '主键，雪花字符串（对齐 agent_skill_info 风格）';
COMMENT ON COLUMN tbl_mcp_server.transport IS 'stdio | sse | streamable_http | http';
COMMENT ON COLUMN tbl_mcp_server.config    IS 'url/headers/command/args/env/enableTools/timeoutMs/initTimeoutMs；敏感值加密存储（复用 api_key 机制），回显 sk-xxxxx 式脱敏';
COMMENT ON COLUMN tbl_mcp_server.status    IS 'enabled=启用 disabled=停用（停用不参与任何挂载）';
CREATE UNIQUE INDEX IF NOT EXISTS uk_mcp_server_name ON tbl_mcp_server (name) WHERE del_flag = 0;
CREATE INDEX IF NOT EXISTS idx_mcp_server_status ON tbl_mcp_server (status) WHERE del_flag = 0;

-- 2) 用户组↔MCP 授权表（与智能体/技能组授权同构，R-02；列型对齐 group_skill: group_id=varchar）
CREATE TABLE IF NOT EXISTS tbl_platform_group_mcp_info (
    id          varchar(32) NOT NULL,
    group_id    varchar(32) NOT NULL,
    mcp_id      varchar(32) NOT NULL,
    creator     varchar(32),
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updator     varchar(32),
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag    smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_platform_group_mcp_info PRIMARY KEY (id)
);
COMMENT ON TABLE tbl_platform_group_mcp_info IS '用户组-MCP 授权（多对多，与组-技能授权同构）';
CREATE INDEX IF NOT EXISTS idx_group_mcp_group ON tbl_platform_group_mcp_info (group_id) WHERE del_flag = 0;
CREATE INDEX IF NOT EXISTS idx_group_mcp_mcp   ON tbl_platform_group_mcp_info (mcp_id)   WHERE del_flag = 0;

-- 3) 智能体↔MCP 绑定表（R-03；agent_id bigint 对齐 agent_skill，避开 group_agent varchar 历史坑）
CREATE TABLE IF NOT EXISTS tbl_data_agent_mcp_info (
    id          varchar(32) NOT NULL,
    agent_id    bigint      NOT NULL,
    mcp_id      varchar(32) NOT NULL,
    creator     varchar(32),
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updator     varchar(32),
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag    smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_data_agent_mcp_info PRIMARY KEY (id)
);
COMMENT ON TABLE tbl_data_agent_mcp_info IS '智能体-MCP 绑定（多对多，与智能体-技能绑定同构）';
CREATE INDEX IF NOT EXISTS idx_agent_mcp_agent ON tbl_data_agent_mcp_info (agent_id) WHERE del_flag = 0;
CREATE INDEX IF NOT EXISTS idx_agent_mcp_mcp   ON tbl_data_agent_mcp_info (mcp_id)   WHERE del_flag = 0;
