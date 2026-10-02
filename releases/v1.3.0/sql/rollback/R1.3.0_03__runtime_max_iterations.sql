-- 回滚件：对应 03_runtime_max_iterations.sql
ALTER TABLE tbl_data_agent_runtime_config DROP COLUMN IF EXISTS max_iterations;
