-- =====================================================================
-- v1.7.0 统一账号中心（一期）· 升级件 02 回滚：账号数据迁移
-- 事实锚：tbl_unified_account_map（先备份其内容再执行本脚本）
-- 注意：M8 删除的测试账号 thinktest 需从**迁移前 pg_dump** 恢复（本脚本末尾给出恢复 SQL 样例）
-- =====================================================================
BEGIN;

-- R1 反向改写（顺序与正向相反；以映射表为唯一依据）
UPDATE tbl_platform_account_tenant_info t SET account_id = m.old_account_id
  FROM tbl_unified_account_map m WHERE t.account_id = m.new_user_id;
UPDATE tbl_platform_account_group_info g SET account_id = m.old_account_id
  FROM tbl_unified_account_map m WHERE g.account_id = m.new_user_id;
UPDATE tbl_data_agent_preset_question q SET account_id = m.old_account_id
  FROM tbl_unified_account_map m WHERE q.account_id = m.new_user_id;
UPDATE tbl_agent_user_profile_info a SET user_id = m.old_account_id
  FROM tbl_unified_account_map m WHERE a.user_id = m.new_user_id;
UPDATE tbl_agent_user_memory_info a SET user_id = m.old_account_id
  FROM tbl_unified_account_map m WHERE a.user_id = m.new_user_id;
UPDATE tbl_agent_user_agent_info a SET user_id = m.old_account_id
  FROM tbl_unified_account_map m WHERE a.user_id = m.new_user_id;
UPDATE tbl_data_agent_file f SET creator = m.old_account_id
  FROM tbl_unified_account_map m WHERE f.creator = m.new_user_id;
UPDATE tbl_data_chat_session cs SET user_id = m.old_account_id
  FROM tbl_unified_account_map m WHERE cs.user_id = m.new_user_id;

-- R2 恢复旧表注释
COMMENT ON TABLE tbl_platform_account_info IS '前台账号信息表';

-- R3 清空映射（DDL 回滚会 drop 表；此处先清数据便于复跑演练）
DELETE FROM tbl_unified_account_map;

-- R4 恢复 M8 删除的测试账号（从迁移前备份恢复；样例，按实际备份文件执行）
--   \\copy tbl_platform_account_info FROM 'backup/tbl_platform_account_info.csv' CSV HEADER
--   \\copy tbl_platform_account_group_info FROM 'backup/tbl_platform_account_group_info.csv' CSV HEADER
--   （thinktest 的口令在迁移前已是坏值——那是迁移前的既有状态，不在本次回滚责任范围）

-- R5 对账：反向改写后旧 id 引用应全部回来（打印供人工比对）
DO $$
DECLARE m record; n int;
BEGIN
  FOR m IN SELECT old_account_id, new_user_id FROM tbl_unified_account_map LOOP
    RAISE NOTICE 'R5 回滚映射 old=% new=%', m.old_account_id, m.new_user_id;
  END LOOP;
  SELECT count(*) INTO n FROM tbl_data_chat_session WHERE user_id IN
    (SELECT old_account_id FROM tbl_unified_account_map);
  RAISE NOTICE 'R5 chat_session 已恢复旧 id 行数=%', n;
END $$;

COMMIT;
