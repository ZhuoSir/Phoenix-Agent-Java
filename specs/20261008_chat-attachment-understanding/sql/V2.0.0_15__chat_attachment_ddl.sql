-- =====================================================================
-- 版本: v2.0.0  序号: 15  类型: DDL
-- 来源: specs/20261008_chat-attachment-understanding (T-02 / R-01~R-04、R-10、R-11, requirements v1.1.0)
-- 目的: 对话附件表（归属/类型/大小/存储路径/状态），支撑附件上传、鉴权与历史回看
-- 前置: V2.0.0_01~14 已应用
-- 可重入: 是（CREATE TABLE/INDEX IF NOT EXISTS）
-- 回滚: rollback/V2.0.0_15__chat_attachment_ddl_rollback.sql
-- ---------------------------------------------------------------------
-- CR-01 裁定：本件**只做 DDL**，不插入任何模型配置行（MULTIMODAL 行经「模型配置」管理页配置，密钥不进版本库）
-- 设计（plan 决策4）：不设 del_flag（避开 L-21 墓碑行隐形），用 status 显式状态 + 物理清理；不加外键（沿用本项目风格）
-- =====================================================================
BEGIN;

CREATE TABLE IF NOT EXISTS tbl_data_chat_attachment (
    id              bigserial    PRIMARY KEY,
    session_id      varchar(64),
    message_id      bigint,
    uploader_id     varchar(64)  NOT NULL,
    kind            varchar(16)  NOT NULL,
    ext             varchar(16)  NOT NULL,
    mime            varchar(128) NOT NULL,
    file_name       varchar(255) NOT NULL,
    size_bytes      bigint       NOT NULL,
    storage_path    varchar(512) NOT NULL,
    status          varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    extracted_chars integer,
    create_time     timestamp    NOT NULL DEFAULT now(),
    update_time     timestamp    NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_chat_attachment_uploader ON tbl_data_chat_attachment (uploader_id);
CREATE INDEX IF NOT EXISTS idx_chat_attachment_message  ON tbl_data_chat_attachment (message_id);

COMMENT ON TABLE  tbl_data_chat_attachment IS '对话附件（chat-attachment-understanding R-01~R-04/R-10/R-11）';
COMMENT ON COLUMN tbl_data_chat_attachment.uploader_id  IS '归属用户 id（R-11 鉴权依据；服务端由 token 反查写入，不信任入参）';
COMMENT ON COLUMN tbl_data_chat_attachment.kind         IS 'DOCUMENT | IMAGE';
COMMENT ON COLUMN tbl_data_chat_attachment.status       IS 'ACTIVE | ORPHAN | EXTRACT_FAILED（不使用 del_flag，见 plan 决策4）';
COMMENT ON COLUMN tbl_data_chat_attachment.storage_path IS '写入方生成的存储路径；读取端不得由入参拼接（L-19）';

-- 自检（只校结构不变量，禁硬编码环境相关绝对数 —— L-51）
DO $chk$
DECLARE tbl int; idx int; nn text;
BEGIN
  SELECT count(*) INTO tbl FROM information_schema.tables
   WHERE table_name = 'tbl_data_chat_attachment';
  IF tbl <> 1 THEN RAISE EXCEPTION '[V2.0.0_15] 附件表未建立'; END IF;

  SELECT count(*) INTO idx FROM pg_indexes
   WHERE tablename = 'tbl_data_chat_attachment'
     AND indexname IN ('idx_chat_attachment_uploader','idx_chat_attachment_message');
  IF idx <> 2 THEN RAISE EXCEPTION '[V2.0.0_15] 索引缺失（应为 2，实为 %）', idx; END IF;

  SELECT is_nullable INTO nn FROM information_schema.columns
   WHERE table_name = 'tbl_data_chat_attachment' AND column_name = 'uploader_id';
  IF nn <> 'NO' THEN RAISE EXCEPTION '[V2.0.0_15] uploader_id 允许为空（归属列必须 NOT NULL）'; END IF;

  RAISE NOTICE '[V2.0.0_15] 自检通过：表已建 / 索引 2 个 / uploader_id NOT NULL';
END $chk$;

COMMIT;
