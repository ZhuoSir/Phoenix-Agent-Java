-- 回滚件：撤销知识库检索工具参数配置化（dynamic-agent-types）T-07
-- 注意：回滚即丢失已配置的 topK/阈值（回落为代码内默认值 10 / 0.65），不可恢复。

ALTER TABLE tbl_data_agent_runtime_config
    DROP COLUMN IF EXISTS knowledge_top_k;

ALTER TABLE tbl_data_agent_runtime_config
    DROP COLUMN IF EXISTS knowledge_similarity_threshold;
