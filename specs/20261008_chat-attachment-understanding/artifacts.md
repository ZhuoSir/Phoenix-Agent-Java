# 升级件登记 — chat-attachment-understanding

> 版本: v1.5.0 | 更新: 2026-10-09

| 件 | 类型 | 文件 | 版本 | 说明 | 回滚 |
|---|---|---|---|---|---|
| 聊天附件表 | DDL | sql/V2.0.0_15__chat_attachment_ddl.sql | v2.0.0 | tbl_data_chat_attachment（T-02） | 配对 rollback |
| 会话空间列 | DDL+回填 | sql/V2.0.0_16__chat_session_source_ddl.sql | v2.0.0 | tbl_data_chat_session.source（CR-03 T-10；存量回填 FRONT_CHAT=口径A）+ 索引 idx_chat_session_agent_source | sql/rollback/V2.0.0_16__chat_session_source_ddl_rollback.sql |
