-- ============================================
-- 版本: v1.6.0  序号: 03  类型: DDL
-- 来源: specs/20261005_long-turn-resilience (T-02 / R-05)
-- 前置: 无
-- 可重入: 是（IF NOT EXISTS）
-- 预估: <1s
-- 回滚: rollback/R1.6.0_03__runtime_compaction_columns.sql
-- ============================================

-- 上下文治理三参数（DSH compaction 比例水位对标；NULL=用全局默认 env）
ALTER TABLE tbl_data_agent_runtime_config
    ADD COLUMN IF NOT EXISTS compaction_trigger_tokens integer,
    ADD COLUMN IF NOT EXISTS compaction_keep_messages  integer,
    ADD COLUMN IF NOT EXISTS tool_result_max_chars     integer;

COMMENT ON COLUMN tbl_data_agent_runtime_config.compaction_trigger_tokens IS '压缩触发令牌水位；NULL=全局默认（phoenix.agent.compaction-trigger-tokens，默认 102400≈DSH 0.8×128k 换算）';
COMMENT ON COLUMN tbl_data_agent_runtime_config.compaction_keep_messages  IS '压缩保留原文条数；NULL=全局默认（默认 20，对齐框架原值）';
COMMENT ON COLUMN tbl_data_agent_runtime_config.tool_result_max_chars     IS '工具结果超此字符数即回收（头部保留+落盘）；NULL=全局默认（默认 8192=DSH tool-result-pruner）';
