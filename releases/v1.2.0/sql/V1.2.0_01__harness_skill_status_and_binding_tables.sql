-- ============================================
-- 版本: v1.2.0  序号: 01  类型: DDL
-- 来源: specs/20260927_agent-skill-management (T-01/T-05)；草案 sql/01_agent_skill_upgrade.sql
-- 前置: 无（依赖基线库；tbl_harness_skills 不存在时由脚本容错跳过/应用启动自建）
-- 可重入: 是（全 IF NOT EXISTS / 存在性判断）
-- 预估: <1s（配置级小表）
-- 回滚: rollback/R1.2.0_01__harness_skill_status_and_binding_tables.sql
-- ============================================

-- 升级件草案：智能体技能管理（agent-skill-management）T-01
-- 目标库：PostgreSQL（phoenix）
-- 幂等：全部 IF NOT EXISTS / IF EXISTS，可重复执行
-- 回滚：见 01_agent_skill_rollback.sql
-- 兼容性：tbl_harness_skills 由 AgentScope PostgresSkillRepository 建管（createIfNotExist），
--   其 insert 均显式列名，新增带默认值的 status 列对其读写无破坏（先加后删原则）。

-- 1) 技能表增发布状态列（R-03/R-06/R-07）
--    注意：tbl_harness_skills 由应用启动时 AgentScope PostgresSkillRepository.createIfNotExist 创建，
--    不在 sql/all_schema.sql 中。故此处做存在性判断：全新环境若尚未启动过应用，跳过列变更并提示，
--    待应用首次启动后再重放本脚本即可（可重复执行）。
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'tbl_harness_skills') THEN
        ALTER TABLE tbl_harness_skills
            ADD COLUMN IF NOT EXISTS status varchar(16) NOT NULL DEFAULT 'draft';
        COMMENT ON COLUMN tbl_harness_skills.status IS '发布状态 draft=草稿 published=已发布；覆盖上传后回 draft';
    ELSE
        RAISE NOTICE 'tbl_harness_skills 不存在（由应用首次启动创建），已跳过 status 列变更；请在应用启动后重放本脚本';
    END IF;
END $$;

-- 2) 智能体↔技能绑定表（R-04）
CREATE TABLE IF NOT EXISTS tbl_data_agent_skill_info (
    id          varchar(32) NOT NULL,
    agent_id    bigint      NOT NULL,
    skill_id    bigint      NOT NULL,
    creator     varchar(32),
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updator     varchar(32),
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag    smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_data_agent_skill_info PRIMARY KEY (id)
);
COMMENT ON TABLE  tbl_data_agent_skill_info IS '智能体-技能绑定（多对多）';
COMMENT ON COLUMN tbl_data_agent_skill_info.id          IS '主键，雪花字符串（对齐 GroupAgentInfo 风格）';
COMMENT ON COLUMN tbl_data_agent_skill_info.agent_id    IS '智能体ID，关联 tbl_data_agent.id';
COMMENT ON COLUMN tbl_data_agent_skill_info.skill_id    IS '技能ID，关联 tbl_harness_skills.id';
COMMENT ON COLUMN tbl_data_agent_skill_info.del_flag    IS '逻辑删除 0正常 1删除';
-- 业务防重最终防线（部分唯一索引：仅约束未删行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_dasi_agent_skill
    ON tbl_data_agent_skill_info (agent_id, skill_id) WHERE del_flag = 0;
-- 反查「技能被哪些智能体绑定」（删除引用计数/级联清理）
CREATE INDEX IF NOT EXISTS idx_dasi_skill ON tbl_data_agent_skill_info (skill_id);

-- 3) 组↔技能授权表（R-07，与 tbl_platform_group_agent_info 同构）
CREATE TABLE IF NOT EXISTS tbl_platform_group_skill_info (
    id          varchar(32) NOT NULL,
    group_id    varchar(32) NOT NULL,
    skill_id    bigint      NOT NULL,
    creator     varchar(32),
    create_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updator     varchar(32),
    update_time timestamp   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    del_flag    smallint    NOT NULL DEFAULT 0,
    CONSTRAINT pk_group_skill_info PRIMARY KEY (id)
);
COMMENT ON TABLE  tbl_platform_group_skill_info IS '用户组-技能授权（多对多，发布时授权）';
COMMENT ON COLUMN tbl_platform_group_skill_info.id          IS '主键，雪花字符串';
COMMENT ON COLUMN tbl_platform_group_skill_info.group_id    IS '组ID，关联 tbl_platform_group_info.id';
COMMENT ON COLUMN tbl_platform_group_skill_info.skill_id    IS '技能ID，关联 tbl_harness_skills.id';
COMMENT ON COLUMN tbl_platform_group_skill_info.del_flag    IS '逻辑删除 0正常 1删除';
CREATE UNIQUE INDEX IF NOT EXISTS uk_pgsi_group_skill
    ON tbl_platform_group_skill_info (group_id, skill_id) WHERE del_flag = 0;
CREATE INDEX IF NOT EXISTS idx_pgsi_skill ON tbl_platform_group_skill_info (skill_id);
