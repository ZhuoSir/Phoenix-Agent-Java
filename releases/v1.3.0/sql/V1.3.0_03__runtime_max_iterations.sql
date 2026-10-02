-- 升级件草案：智能体运行时配置新增工具迭代上限（runtime-max-iterations）T-01 → 归位预期 V1.3.0_03
-- 幂等：ADD COLUMN IF NOT EXISTS；回滚见 03_runtime_max_iterations_rollback.sql（drop column，存量无损）
ALTER TABLE tbl_data_agent_runtime_config ADD COLUMN IF NOT EXISTS max_iterations integer;
COMMENT ON COLUMN tbl_data_agent_runtime_config.max_iterations IS '单轮内工具迭代上限（1~100）；NULL=沿用框架默认（行为与升级前一致）';
