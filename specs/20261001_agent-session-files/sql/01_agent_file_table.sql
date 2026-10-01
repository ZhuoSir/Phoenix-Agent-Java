-- 升级件草案：会话文件登记表（agent-session-files）T-01
-- 目标库：PostgreSQL（phoenix）
-- 幂等：CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS，可重复执行
-- 回滚：见 01_agent_file_table_rollback.sql（drop 表，纯新增不动既有数据）
--
-- 背景：智能体经 AbstractFilesystem 产出文件后需在会话页可见可下载。本表登记每个产物的
-- 元信息 + tee 副本落点（rel_path 相对 uploads 根）。agent_id 用 bigint（对齐 tbl_data_agent.id，
-- 规避 BUG-29 那类 varchar/bigint 比较问题）；id 用 varchar 雪花（对齐 agent 域 UserMemoryInfo/HarnessSkill 惯例，
-- 与 plan §1 的 bigint 差异以此为准，已在 spec changelog 记录）。

CREATE TABLE IF NOT EXISTS tbl_data_agent_file (
    id           varchar(64)  NOT NULL,
    agent_id     bigint       NOT NULL,
    session_id   varchar(64)  NOT NULL,
    file_name    varchar(255) NOT NULL,
    rel_path     varchar(512) NOT NULL,
    size_bytes   bigint       NOT NULL DEFAULT 0,
    mime         varchar(128),
    source       varchar(32)  NOT NULL DEFAULT 'tool',
    backend      varchar(16)  NOT NULL DEFAULT 'local',
    store_key    varchar(512),
    creator      varchar(64),
    del_flag     smallint     NOT NULL DEFAULT 0,
    create_time  timestamp(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_tbl_data_agent_file PRIMARY KEY (id)
);

COMMENT ON TABLE  tbl_data_agent_file           IS '智能体会话产物文件登记（会话文件面板 BL-19）';
COMMENT ON COLUMN tbl_data_agent_file.rel_path  IS 'tee 副本相对 uploads 根的路径，下载权威';
COMMENT ON COLUMN tbl_data_agent_file.source    IS '产出来源：tool=fs工具写入 / scan=会话轮末扫描 / materialize=消息物化';
COMMENT ON COLUMN tbl_data_agent_file.backend   IS '原产物后端：local / remote(Redis)';
COMMENT ON COLUMN tbl_data_agent_file.store_key IS '原后端定位串，仅溯源用；下载恒走 rel_path';

CREATE INDEX IF NOT EXISTS idx_daf_session ON tbl_data_agent_file (session_id, del_flag);
CREATE INDEX IF NOT EXISTS idx_daf_agent   ON tbl_data_agent_file (agent_id, del_flag);
