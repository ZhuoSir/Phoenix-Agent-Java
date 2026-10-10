-- =====================================================================
-- 版本: v2.0.0  序号: 15  类型: DDL(回滚)
-- 配对正向件: ../V2.0.0_15__chat_attachment_ddl.sql
-- 说明: 新表无存量业务依赖，直接 DROP（附件物理文件不在此清理范围）
-- 可重入: 是
-- =====================================================================
BEGIN;
DROP INDEX IF EXISTS idx_chat_attachment_message;
DROP INDEX IF EXISTS idx_chat_attachment_uploader;
DROP TABLE IF EXISTS tbl_data_chat_attachment;
COMMIT;
