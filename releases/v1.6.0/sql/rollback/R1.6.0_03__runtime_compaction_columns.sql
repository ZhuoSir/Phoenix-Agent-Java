-- 回滚 V1.6.0_03：删上下文治理三列
ALTER TABLE tbl_data_agent_runtime_config
    DROP COLUMN IF EXISTS compaction_trigger_tokens,
    DROP COLUMN IF EXISTS compaction_keep_messages,
    DROP COLUMN IF EXISTS tool_result_max_chars;
