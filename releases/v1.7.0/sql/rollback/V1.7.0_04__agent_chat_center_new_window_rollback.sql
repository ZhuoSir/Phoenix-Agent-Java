-- v1.7.0 升级件 04 回滚：智能体中心 url 恢复为 admin 内路由 '/agent/chat'（撤销"新窗口菜单"）
BEGIN;

UPDATE tbl_privilege_module
   SET url = '/agent/chat', update_time = now(), update_by = 'admin'
 WHERE sn = 'AgentChatCenter'
   AND del_flag = 0
   AND url <> '/agent/chat';

DO $$
DECLARE v_url text;
BEGIN
  SELECT url INTO v_url FROM tbl_privilege_module WHERE sn = 'AgentChatCenter' AND del_flag = 0;
  IF v_url <> '/agent/chat' THEN
    RAISE EXCEPTION '回滚不干净：url=%', v_url;
  END IF;
  RAISE NOTICE '回滚完成：智能体中心 url 已恢复 /agent/chat（admin 内页签形态）';
END $$;

COMMIT;
