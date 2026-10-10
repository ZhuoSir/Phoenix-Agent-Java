-- CR-03（会话空间隔离，chat-attachment-understanding v1.5.0）
-- 背景: admin 运行页（测试管理）与前台 chat（含 mobile）此前共用同一会话空间，互见对方会话；
--       拍板（陈卓 2026-10-09）: 两空间互不可见；存量回填口径 A = 全部 FRONT_CHAT；跨空间拒绝 = 404-as-不存在。
-- 说明: ADD COLUMN ... NOT NULL DEFAULT 在 PG 中对存量行以默认值填充；显式 UPDATE 为兜底（防历史 NULL）。
ALTER TABLE tbl_data_chat_session
    ADD COLUMN IF NOT EXISTS source varchar(16) NOT NULL DEFAULT 'FRONT_CHAT';

UPDATE tbl_data_chat_session SET source = 'FRONT_CHAT' WHERE source IS NULL OR source = '';

CREATE INDEX IF NOT EXISTS idx_chat_session_agent_source
    ON tbl_data_chat_session (agent_id, source);

COMMENT ON COLUMN tbl_data_chat_session.source IS
    '会话空间（CR-03）: ADMIN_RUN=管理端运行页(测试管理) / FRONT_CHAT=前台聊天(含mobile)；两空间互不可见';
