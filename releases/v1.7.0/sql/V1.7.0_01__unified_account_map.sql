-- =====================================================================
-- v1.7.0 统一账号中心（一期）· 升级件 01：迁移映射/审计表
-- 用途：记录「前台账号 → 统一账号」的映射，支撑回滚（反向重写）与审计
-- 生命周期：一期使用；二期评估下线
-- 注意：本表为 append-only 审计表，**不设逻辑删列**（del_flag）——行一旦写入不删除，
--       异常订正走 remark 留痕（database-design §19 的例外已在此注释说明理由）
-- 执行方式：psql -f（禁止内联三层引号，见 L-05）
-- =====================================================================

CREATE TABLE IF NOT EXISTS tbl_unified_account_map (
  old_account_id varchar(64)  NOT NULL,
  new_user_id    varchar(64)  NOT NULL,
  merge_type     varchar(16)  NOT NULL,
  employee_id    varchar(64),
  migrated_at    timestamp    NOT NULL DEFAULT now(),
  remark         varchar(255),
  create_time    timestamp    NOT NULL DEFAULT now(),
  update_time    timestamp    NOT NULL DEFAULT now(),
  CONSTRAINT pk_tbl_unified_account_map PRIMARY KEY (old_account_id)
);

COMMENT ON TABLE  tbl_unified_account_map IS '统一账号迁移映射与审计（v1.7.0 一期；二期可下线）';
COMMENT ON COLUMN tbl_unified_account_map.old_account_id IS '原前台账号 tbl_platform_account_info.id（主键，天然防重）';
COMMENT ON COLUMN tbl_unified_account_map.new_user_id IS '统一后账号 tbl_privilege_user.id';
COMMENT ON COLUMN tbl_unified_account_map.merge_type IS 'merged=并入既有后台账号；created=沿用前台 id 新建';
COMMENT ON COLUMN tbl_unified_account_map.employee_id IS '自然人键（取自前台账号，可能为空；不作为唯一匹配依据）';
COMMENT ON COLUMN tbl_unified_account_map.migrated_at IS '迁移时刻';
COMMENT ON COLUMN tbl_unified_account_map.remark IS '留痕：异常/人工裁定说明';

CREATE INDEX IF NOT EXISTS idx_uam_new_user ON tbl_unified_account_map (new_user_id);

-- 行级审计：记录"哪些行被搬过"——回滚精确反向的唯一事实锚（v1.2.0，演练发现的必修项）
CREATE TABLE IF NOT EXISTS tbl_unified_account_migration_rows (
  table_name  varchar(64) NOT NULL,
  pk_value    varchar(64) NOT NULL,
  old_id      varchar(64) NOT NULL,
  new_id      varchar(64) NOT NULL,
  create_time timestamp   NOT NULL DEFAULT now(),
  CONSTRAINT pk_tbl_unified_account_migration_rows PRIMARY KEY (table_name, pk_value)
);
COMMENT ON TABLE  tbl_unified_account_migration_rows IS '统一账号迁移**行级审计**（v1.7.0；回滚按行精确反向的事实锚）';
COMMENT ON COLUMN tbl_unified_account_migration_rows.table_name IS '被改写的表名';
COMMENT ON COLUMN tbl_unified_account_migration_rows.pk_value   IS '被改写行的主键值';
COMMENT ON COLUMN tbl_unified_account_migration_rows.old_id     IS '改写前归属（前台账号 id）';
COMMENT ON COLUMN tbl_unified_account_migration_rows.new_id     IS '改写后归属（统一账号 id）';
CREATE INDEX IF NOT EXISTS idx_uamr_new_id ON tbl_unified_account_migration_rows (new_id);
