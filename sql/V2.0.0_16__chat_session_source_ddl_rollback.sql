-- CR-03 回滚: 撤销会话空间隔离列与索引（存量数据回到无空间状态）
DROP INDEX IF EXISTS idx_chat_session_agent_source;
ALTER TABLE tbl_data_chat_session DROP COLUMN IF EXISTS source;
