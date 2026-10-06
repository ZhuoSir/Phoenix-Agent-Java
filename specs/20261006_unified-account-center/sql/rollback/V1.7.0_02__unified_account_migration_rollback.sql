-- =====================================================================
-- v1.7.0 统一账号中心（一期）· 升级件 02 回滚：账号数据迁移（**行级精确**，v1.2.0）
-- 事实锚：tbl_unified_account_migration_rows（行级审计：哪些行被搬过）
-- 为何不用映射表批量反向：演练证伪——`WHERE <col>=new_id` 会把**目标账号自有数据**误迁
--   （实测旧 id 侧 chat_session 30 ≠ 迁移前 28）
-- 执行：psql -v ON_ERROR_STOP=1 -f（单事务；任何门禁失败整体回滚）
-- 注意：M8 删除的测试账号 thinktest 需从**迁移前 pg_dump** 恢复（见 R5 样例）
-- =====================================================================
BEGIN;

-- R0 前置：审计表必须非空（否则视为未迁移/审计缺失，拒绝回滚）
DO $$
DECLARE n int;
BEGIN
  SELECT count(*) INTO n FROM tbl_unified_account_migration_rows;
  IF n = 0 THEN RAISE EXCEPTION 'R0：行级审计为空，拒绝回滚'; END IF;
  RAISE NOTICE 'R0 审计行数=%（按行精确反向）', n;
END $$;

-- R1 + R2 按行精确反向，并逐行门禁（只动审计登记过的行）
DO $$
DECLARE r record; n int; sql text; pairs text := '';
BEGIN
  FOR r IN SELECT * FROM (VALUES
      ('tbl_data_chat_session','user_id','id::text'),
      ('tbl_data_agent_file','creator','id::text'),
      ('tbl_agent_user_agent_info','user_id','id::text'),
      ('tbl_agent_user_memory_info','user_id','id::text'),
      ('tbl_agent_user_profile_info','user_id','agent_sn||''|''||user_id'),
      ('tbl_data_agent_preset_question','account_id','id::text'),
      ('tbl_platform_account_group_info','account_id','id::text'),
      ('tbl_platform_account_tenant_info','account_id','id::text')
    ) AS t(tab, col, keyexpr) LOOP
    sql := format('UPDATE %I x SET %I = a.old_id FROM tbl_unified_account_migration_rows a '
                  'WHERE a.table_name = %L AND x.%s = a.pk_value', r.tab, r.col, r.tab, r.keyexpr);
    EXECUTE sql; GET DIAGNOSTICS n = ROW_COUNT;
    RAISE NOTICE 'R1 回滚 %.% → % 行', r.tab, r.col, n;
    sql := format('SELECT count(*) FROM %I x JOIN tbl_unified_account_migration_rows a '
                  'ON a.table_name = %L AND x.%s = a.pk_value WHERE x.%I <> a.old_id',
                  r.tab, r.tab, r.keyexpr, r.col);
    EXECUTE sql INTO n;
    IF n > 0 THEN RAISE EXCEPTION 'R2 门禁失败：%.% 仍有 % 行未回到旧归属', r.tab, r.col, n; END IF;
  END LOOP;
  RAISE NOTICE 'R2 门禁通过：审计中每一行均已回到旧归属';
END $$;

-- R3 规模对照（供与迁移前快照人工比对）
DO $$
DECLARE m record; n int;
BEGIN
  FOR m IN SELECT old_account_id AS old_id, new_user_id AS new_id FROM tbl_unified_account_map LOOP
    SELECT count(*) INTO n FROM tbl_data_chat_session WHERE user_id = m.old_id;
    RAISE NOTICE 'R3 旧 id=% 会话数=%（应=迁移前快照）', m.old_id, n;
    SELECT count(*) INTO n FROM tbl_data_chat_session WHERE user_id = m.new_id;
    RAISE NOTICE 'R3 新 id=% 会话数=%（应含其自有数据）', m.new_id, n;
    SELECT count(*) INTO n FROM tbl_platform_account_group_info WHERE account_id = m.old_id;
    RAISE NOTICE 'R3 旧 id=% 授权组关系=%', m.old_id, n;
  END LOOP;
END $$;

-- R4 恢复旧表注释
COMMENT ON TABLE tbl_platform_account_info IS '前台账号信息表';

-- R5 恢复 M8 删除的测试账号（从迁移前备份；样例）
--   \copy tbl_platform_account_info FROM 'backups/tbl_platform_account_info.csv' CSV HEADER
--   \copy tbl_platform_account_group_info FROM 'backups/tbl_platform_account_group_info.csv' CSV HEADER

-- 审计与映射数据**保留**（回滚取证）；其 DDL 由升级件 01 回滚负责删除。
COMMIT;
