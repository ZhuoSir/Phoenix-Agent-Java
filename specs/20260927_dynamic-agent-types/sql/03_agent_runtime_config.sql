-- 升级件草案：对话智能体运行配置（dynamic-agent-types）T-01
-- 目标库：PostgreSQL（phoenix）
-- 幂等：CREATE TABLE IF NOT EXISTS / 条件 UPDATE / SET DEFAULT 可重复执行
-- 回滚：见 03_agent_runtime_config_rollback.sql（注意 NULL 回填不可逆，回滚只能撤销对象与默认值）

-- 1) 对话智能体运行配置（1:1，按 agent 存模型/工具开关/数据源/计划模式/记忆）
CREATE TABLE IF NOT EXISTS tbl_data_agent_runtime_config (
    id                       varchar(32) NOT NULL,
    agent_id                 bigint      NOT NULL,
    model_config_id          bigint,
    plan_mode                smallint    NOT NULL DEFAULT 0,
    memory_enabled           smallint    NOT NULL DEFAULT 1,
    knowledge_enabled        smallint    NOT NULL DEFAULT 0,
    db_query_enabled         smallint    NOT NULL DEFAULT 0,
    db_deep_analysis_enabled smallint    NOT NULL DEFAULT 0,
    datasource_id            bigint,
    filesystem_policy        varchar(32) NOT NULL DEFAULT 'local',
    creator                  varchar(32),
    create_time              timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updator                  varchar(32),
    update_time              timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag                 smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_agent_runtime_config PRIMARY KEY (id)
);
COMMENT ON TABLE  tbl_data_agent_runtime_config IS '对话智能体运行配置(1:1)，库配置驱动运行时构建';
COMMENT ON COLUMN tbl_data_agent_runtime_config.id                       IS '主键，雪花字符串';
COMMENT ON COLUMN tbl_data_agent_runtime_config.agent_id                 IS '智能体ID，关联 tbl_data_agent.id（唯一）';
COMMENT ON COLUMN tbl_data_agent_runtime_config.model_config_id          IS '对话模型配置ID，关联模型配置表；空=用默认模型';
COMMENT ON COLUMN tbl_data_agent_runtime_config.plan_mode                IS '计划模式开关 0关 1开';
COMMENT ON COLUMN tbl_data_agent_runtime_config.memory_enabled           IS '记忆开关 0关 1开';
COMMENT ON COLUMN tbl_data_agent_runtime_config.knowledge_enabled        IS '知识库检索工具开关 0关 1开';
COMMENT ON COLUMN tbl_data_agent_runtime_config.db_query_enabled         IS '数据库取数工具开关 0关 1开（NL→SQL→只读结果）';
COMMENT ON COLUMN tbl_data_agent_runtime_config.db_deep_analysis_enabled IS '数据库深度分析工具开关 0关 1开（内部走 NL2SQL 状态图）';
COMMENT ON COLUMN tbl_data_agent_runtime_config.datasource_id            IS '目标数据源ID（数据库类工具必填，关联数据源配置）';
COMMENT ON COLUMN tbl_data_agent_runtime_config.filesystem_policy        IS '文件系统策略 local=本地沙箱（可扩展）';
COMMENT ON COLUMN tbl_data_agent_runtime_config.del_flag                 IS '逻辑删除 0正常 1删除';
-- 业务防重：一个智能体仅一份运行配置
CREATE UNIQUE INDEX IF NOT EXISTS uk_arc_agent
    ON tbl_data_agent_runtime_config (agent_id) WHERE del_flag = 0;

-- 2) 消除 type 为空（R-01：不再产出无类型智能体；存量 NULL 回填为 harness）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'tbl_data_agent') THEN
        UPDATE tbl_data_agent SET type = 'harness' WHERE type IS NULL OR type = '';
        ALTER TABLE tbl_data_agent ALTER COLUMN type SET DEFAULT 'harness';
    ELSE
        RAISE NOTICE 'tbl_data_agent 不存在，已跳过 type 默认值与回填';
    END IF;
END $$;
