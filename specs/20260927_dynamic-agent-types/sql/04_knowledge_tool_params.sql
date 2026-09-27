-- 升级件：知识库检索工具参数配置化（dynamic-agent-types）T-07
-- 目标库：PostgreSQL（phoenix）
-- 幂等：ADD COLUMN IF NOT EXISTS + SET DEFAULT 可重复执行
-- 回滚：见 04_knowledge_tool_params_rollback.sql
-- 说明：plan（T-07 验收「topK/阈值来自配置」）要求知识库检索参数可配；03 升级件已确认不再改动，
--       故以增量升级件补齐两个字段。默认值 = 原 RulesRagTool 写死值（topK=10 / 0.65），存量行为不变。

ALTER TABLE tbl_data_agent_runtime_config
    ADD COLUMN IF NOT EXISTS knowledge_top_k integer NOT NULL DEFAULT 10;

ALTER TABLE tbl_data_agent_runtime_config
    ADD COLUMN IF NOT EXISTS knowledge_similarity_threshold double precision NOT NULL DEFAULT 0.65;

COMMENT ON COLUMN tbl_data_agent_runtime_config.knowledge_top_k                  IS '知识库检索召回条数（1~50，默认10）';
COMMENT ON COLUMN tbl_data_agent_runtime_config.knowledge_similarity_threshold   IS '知识库检索相似度阈值（0~1，默认0.65）';
