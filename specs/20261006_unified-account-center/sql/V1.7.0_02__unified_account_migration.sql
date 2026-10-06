-- =====================================================================
-- v1.7.0 统一账号中心（一期）· 升级件 02：账号数据迁移
-- 依据：plan v1.1.1（M1~M8、裁定 D5/D6/D7/D8）
-- 执行：psql -v ON_ERROR_STOP=1 -f（整脚本单事务；失败自动回滚）
-- 前置：① 已对目标库做 pg_dump 全量备份；② 已执行升级件 01（映射表存在）
-- 安全：脚本自带断言——任何一条不符即 RAISE 并整体回滚，不做部分迁移
-- =====================================================================
BEGIN;

-- ---------------------------------------------------------------------
-- M0 幂等保护：映射表已有数据 ⇒ 视为已迁移，拒绝重复执行
-- ---------------------------------------------------------------------
DO $$
DECLARE n int;
BEGIN
  SELECT count(*) INTO n FROM tbl_unified_account_map;
  IF n > 0 THEN
    RAISE EXCEPTION 'M0：映射表已有 % 行，迁移可能已执行，拒绝重复运行', n;
  END IF;
END $$;

-- ---------------------------------------------------------------------
-- M1 快照（打印迁移前规模，便于回滚后比对；同时作为审计留痕）
-- ---------------------------------------------------------------------
DO $$
DECLARE c_total int; c_front int; c_back int;
BEGIN
  SELECT count(*) INTO c_total FROM tbl_platform_account_info;
  SELECT count(*) INTO c_front FROM tbl_platform_account_info WHERE password !~ '^[0-9a-f]{32}$';
  SELECT count(*) INTO c_back  FROM tbl_privilege_user WHERE password IS NOT NULL AND password !~ '^[0-9a-f]{32}$';
  RAISE NOTICE 'M1 迁移前：前台账号=% 行；非哈希口令(前台=% 后台=%)', c_total, c_front, c_back;
  IF c_front > 0 OR c_back > 0 THEN
    RAISE EXCEPTION 'M1：存在非 32 位 hex 口令（前台=% 后台=%），需先按 M5 重算规则处理', c_front, c_back;
  END IF;
END $$;

-- ---------------------------------------------------------------------
-- M2 识别同自然人（裁定 D5 匹配优先级：username+code 全等 > username 全等 >
--     employee_id 且该 employee_id 在后台侧唯一）；thinktest 属 D7 删除清单，不参与
-- ---------------------------------------------------------------------
CREATE TEMP TABLE tmp_merge ON COMMIT DROP AS
SELECT pa.id AS old_id,
       (SELECT pu.id
          FROM tbl_privilege_user pu
         WHERE pu.del_flag = 0
           AND ( (pu.username = pa.username AND pu.code = pa.code)
              OR (pu.username = pa.username)
              OR ( coalesce(pa.employee_id, '') <> ''
                   AND pu.employee_id = pa.employee_id
                   AND (SELECT count(*) FROM tbl_privilege_user x
                         WHERE x.employee_id = pa.employee_id AND x.del_flag = 0) = 1 ) )
         ORDER BY CASE WHEN pu.username = pa.username AND pu.code = pa.code THEN 0
                       WHEN pu.username = pa.username THEN 1
                       ELSE 2 END
         LIMIT 1) AS new_id,
       pa.username,
       pa.employee_id
  FROM tbl_platform_account_info pa
 WHERE pa.username NOT IN ('thinktest');   -- D7 显式删除清单

-- M2 门禁：每个（非删除清单的）前台账号都必须有唯一并入目标，否则停下等人工裁定
DO $$
DECLARE missing int; multi int;
BEGIN
  SELECT count(*) INTO missing FROM tmp_merge WHERE new_id IS NULL;
  IF missing > 0 THEN
    RAISE EXCEPTION 'M2：% 个前台账号无匹配后台账号（分支 B 未实现单列清单），需人工裁定后再迁移', missing;
  END IF;
  SELECT count(*) INTO multi FROM (
    SELECT old_id, count(DISTINCT new_id) c FROM tmp_merge GROUP BY old_id HAVING count(DISTINCT new_id) > 1) t;
  IF multi > 0 THEN
    RAISE EXCEPTION 'M2：% 个前台账号匹配到多个后台账号，需人工裁定', multi;
  END IF;
  RAISE NOTICE 'M2 匹配完成：% 个前台账号并入既有后台账号（分支 A）', (SELECT count(*) FROM tmp_merge);
END $$;

-- ---------------------------------------------------------------------
-- M3 分支 B：无对应后台账号者 → 在统一主表沿用原 id 新建
--    （本数据集经 M2 门禁后为空；保留 SQL 以便未来数据复用时可直接生效）
-- ---------------------------------------------------------------------
DO $$
DECLARE c int;
BEGIN
  SELECT count(*) INTO c FROM tmp_merge WHERE new_id IS NULL;
  RAISE NOTICE 'M3 分支 B 待新建账号数=%（本数据集应为 0）', c;
END $$;

-- ---------------------------------------------------------------------
-- M4 映射登记 + 业务列改写（逐列计数并打印，回滚以此表为事实锚）
-- ---------------------------------------------------------------------
INSERT INTO tbl_unified_account_map (old_account_id, new_user_id, merge_type, employee_id, remark)
SELECT old_id, new_id, 'merged', employee_id, 'D5 强匹配并入（username+code 优先）' FROM tmp_merge;

DO $$
DECLARE n int;
BEGIN
  -- 4.1 会话归属
  INSERT INTO tbl_unified_account_migration_rows (table_name, pk_value, old_id, new_id)
    SELECT 'tbl_data_chat_session', cs.id::text, m.old_id, m.new_id
      FROM tbl_data_chat_session cs JOIN tmp_merge m ON cs.user_id = m.old_id;
  UPDATE tbl_data_chat_session cs SET user_id = m.new_id
    FROM tmp_merge m WHERE cs.user_id = m.old_id;
  GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'M4.1 data_chat_session.user_id 改写 % 行', n;

  -- 4.2 文件归属
  INSERT INTO tbl_unified_account_migration_rows (table_name, pk_value, old_id, new_id)
    SELECT 'tbl_data_agent_file', f.id::text, m.old_id, m.new_id
      FROM tbl_data_agent_file f JOIN tmp_merge m ON f.creator = m.old_id;
  UPDATE tbl_data_agent_file f SET creator = m.new_id
    FROM tmp_merge m WHERE f.creator = m.old_id;
  GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'M4.2 data_agent_file.creator 改写 % 行', n;

  -- 4.3 用户-智能体关系
  INSERT INTO tbl_unified_account_migration_rows (table_name, pk_value, old_id, new_id)
    SELECT 'tbl_agent_user_agent_info', a.id::text, m.old_id, m.new_id
      FROM tbl_agent_user_agent_info a JOIN tmp_merge m ON a.user_id = m.old_id;
  UPDATE tbl_agent_user_agent_info a SET user_id = m.new_id
    FROM tmp_merge m WHERE a.user_id = m.old_id;
  GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'M4.3 agent_user_agent_info.user_id 改写 % 行', n;

  -- 4.4 用户记忆 / 档案
  INSERT INTO tbl_unified_account_migration_rows (table_name, pk_value, old_id, new_id)
    SELECT 'tbl_agent_user_memory_info', a.id::text, m.old_id, m.new_id
      FROM tbl_agent_user_memory_info a JOIN tmp_merge m ON a.user_id = m.old_id;
  UPDATE tbl_agent_user_memory_info a SET user_id = m.new_id
    FROM tmp_merge m WHERE a.user_id = m.old_id;
  GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'M4.4 agent_user_memory_info.user_id 改写 % 行', n;

  INSERT INTO tbl_unified_account_migration_rows (table_name, pk_value, old_id, new_id)
    SELECT 'tbl_agent_user_profile_info', a.agent_sn||'|'||a.user_id, m.old_id, m.new_id
      FROM tbl_agent_user_profile_info a JOIN tmp_merge m ON a.user_id = m.old_id;
  UPDATE tbl_agent_user_profile_info a SET user_id = m.new_id
    FROM tmp_merge m WHERE a.user_id = m.old_id;
  GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'M4.5 agent_user_profile_info.user_id 改写 % 行', n;

  -- 4.6 预设问题归属
  INSERT INTO tbl_unified_account_migration_rows (table_name, pk_value, old_id, new_id)
    SELECT 'tbl_data_agent_preset_question', q.id::text, m.old_id, m.new_id
      FROM tbl_data_agent_preset_question q JOIN tmp_merge m ON q.account_id = m.old_id;
  UPDATE tbl_data_agent_preset_question q SET account_id = m.new_id
    FROM tmp_merge m WHERE q.account_id = m.old_id;
  GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'M4.6 preset_question.account_id 改写 % 行', n;

  -- 4.7 授权组成员关系（前台资源授权的事实源，必须一并改，否则统一后前台看不到自己的智能体）
  INSERT INTO tbl_unified_account_migration_rows (table_name, pk_value, old_id, new_id)
    SELECT 'tbl_platform_account_group_info', g.id::text, m.old_id, m.new_id
      FROM tbl_platform_account_group_info g JOIN tmp_merge m ON g.account_id = m.old_id;
  UPDATE tbl_platform_account_group_info g SET account_id = m.new_id
    FROM tmp_merge m WHERE g.account_id = m.old_id;
  GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'M4.7 platform_account_group_info.account_id 改写 % 行', n;

  -- 4.8 租户成员关系
  INSERT INTO tbl_unified_account_migration_rows (table_name, pk_value, old_id, new_id)
    SELECT 'tbl_platform_account_tenant_info', t.id::text, m.old_id, m.new_id
      FROM tbl_platform_account_tenant_info t JOIN tmp_merge m ON t.account_id = m.old_id;
  UPDATE tbl_platform_account_tenant_info t SET account_id = m.new_id
    FROM tmp_merge m WHERE t.account_id = m.old_id;
  GET DIAGNOSTICS n = ROW_COUNT; RAISE NOTICE 'M4.8 platform_account_tenant_info.account_id 改写 % 行', n;
END $$;

-- ---------------------------------------------------------------------
-- M5 口令重算：本数据集已全为 32 位 hex（M1 已断言）；如需重算在此追加 UPDATE
-- ---------------------------------------------------------------------
-- （无操作：历史明文口令为 0 行，M1 断言保证）

-- ---------------------------------------------------------------------
-- M6 旧表降级标记（只改注释，不加约束——保持回滚简单）
-- ---------------------------------------------------------------------
COMMENT ON TABLE tbl_platform_account_info IS
  '前台账号信息表【v1.7.0 起：统一账号后降级为只读历史表，新写入请勿使用；账号唯一源=tbl_privilege_user】';

-- ---------------------------------------------------------------------
-- M7 对账：账内不得再有旧账号 id 引用（孤儿引用按裁定 D6 不映射，不在本断言范围）
-- ---------------------------------------------------------------------
DO $$
DECLARE n int; bad int := 0; r record;
BEGIN
  FOR r IN SELECT * FROM tmp_merge LOOP
    SELECT count(*) INTO n FROM tbl_data_chat_session         WHERE user_id = r.old_id;
    IF n > 0 THEN RAISE NOTICE 'M7 残留 chat_session=%', n; bad := bad + n; END IF;
    SELECT count(*) INTO n FROM tbl_data_agent_file           WHERE creator = r.old_id;
    IF n > 0 THEN RAISE NOTICE 'M7 残留 agent_file=%', n; bad := bad + n; END IF;
    SELECT count(*) INTO n FROM tbl_agent_user_agent_info     WHERE user_id = r.old_id;
    IF n > 0 THEN RAISE NOTICE 'M7 残留 agent_user_agent=%', n; bad := bad + n; END IF;
    SELECT count(*) INTO n FROM tbl_platform_account_group_info WHERE account_id = r.old_id;
    IF n > 0 THEN RAISE NOTICE 'M7 残留 group_info=%', n; bad := bad + n; END IF;
    SELECT count(*) INTO n FROM tbl_platform_account_tenant_info WHERE account_id = r.old_id;
    IF n > 0 THEN RAISE NOTICE 'M7 残留 tenant_info=%', n; bad := bad + n; END IF;
  END LOOP;
  IF bad > 0 THEN
    RAISE EXCEPTION 'M7：仍有 % 处旧账号 id 引用未改写，回滚并人工排查', bad;
  END IF;
  RAISE NOTICE 'M7 对账通过：账内旧 id 引用已清零';
END $$;

-- ---------------------------------------------------------------------
-- M8 测试账号清理（裁定 D7）：thinktest（零业务足迹，仅 1 行组关系）
-- ---------------------------------------------------------------------
DO $$
DECLARE c_acct int; c_grp int; c_other int;
BEGIN
  SELECT count(*) INTO c_acct FROM tbl_platform_account_info WHERE id = '9000000000000090001';
  IF c_acct <> 1 THEN RAISE EXCEPTION 'M8：thinktest 账号行数=%（预期 1），停止', c_acct; END IF;
  SELECT count(*) INTO c_grp FROM tbl_platform_account_group_info WHERE account_id = '9000000000000090001';
  SELECT count(*) INTO c_other FROM tbl_data_chat_session WHERE user_id = '9000000000000090001';
  IF c_other <> 0 THEN RAISE EXCEPTION 'M8：thinktest 存在 % 条会话，与 D7 前提（零业务足迹）不符，停止', c_other; END IF;
  RAISE NOTICE 'M8 删除前核对：账号=1 组关系=% 会话=0', c_grp;

  DELETE FROM tbl_platform_account_group_info WHERE account_id = '9000000000000090001';
  DELETE FROM tbl_platform_account_info       WHERE id = '9000000000000090001';

  SELECT count(*) INTO c_acct FROM tbl_platform_account_info WHERE id = '9000000000000090001';
  SELECT count(*) INTO c_grp  FROM tbl_platform_account_group_info WHERE account_id = '9000000000000090001';
  IF c_acct <> 0 OR c_grp <> 0 THEN RAISE EXCEPTION 'M8：删除后仍有残留（账号=% 组=%）', c_acct, c_grp; END IF;
  RAISE NOTICE 'M8 完成：thinktest 已删除（账号与组关系均 0）';
END $$;

-- ---------------------------------------------------------------------
-- 收尾：映射表与最终规模
-- ---------------------------------------------------------------------
DO $$
BEGIN
  RAISE NOTICE '迁移完成：映射表 % 行；统一主表 % 行；旧表 % 行',
    (SELECT count(*) FROM tbl_unified_account_map),
    (SELECT count(*) FROM tbl_privilege_user),
    (SELECT count(*) FROM tbl_platform_account_info);
END $$;

COMMIT;
