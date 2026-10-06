-- =====================================================================
-- v1.7.0 智能体中心并入 admin（R-11 / T-18）：菜单行 + 角色 ACL
-- 做法：复制「智能体列表」菜单行的字段形状与它的 ACL 授权面（数据驱动，避免手写字段错漏）
-- 幂等：先删同名行再插；回滚见 rollback/V1.7.0_03__..._rollback.sql
-- =====================================================================
BEGIN;
DELETE FROM tbl_privilege_acl    WHERE module_sn = 'AgentChatCenter';
DELETE FROM tbl_privilege_module WHERE sn        = 'AgentChatCenter';

INSERT INTO tbl_privilege_module
  (id, name, url, sn, state, component, system_id, status, image, order_no, is_show,
   create_time, create_by, del_flag, pid, category_id, type)
SELECT 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70', '智能体中心', '/agent/chat', 'AgentChatCenter',
       m.state, '#/views/front/chat.vue', m.system_id, 1, 'meteor-icons:chat',
       m.order_no + 1, 1, now(), 'admin', 0, m.pid, m.category_id, m.type
  FROM tbl_privilege_module m WHERE m.sn = 'agentManagerIndex';

INSERT INTO tbl_privilege_acl
  (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, del_flag)
SELECT replace(gen_random_uuid()::text, '-', ''), a.release_id, a.release_sn, a.system_sn,
       (SELECT id FROM tbl_privilege_module WHERE sn = 'AgentChatCenter'),
       'AgentChatCenter', a.acl_state, now(), 0
  FROM tbl_privilege_acl a WHERE a.module_sn = 'agentManagerIndex';

DO $$
DECLARE n_mod int; n_acl int;
BEGIN
  SELECT count(*) INTO n_mod FROM tbl_privilege_module WHERE sn = 'AgentChatCenter';
  SELECT count(*) INTO n_acl FROM tbl_privilege_acl    WHERE module_sn = 'AgentChatCenter';
  IF n_mod <> 1 THEN RAISE EXCEPTION '菜单行插入失败（%）', n_mod; END IF;
  IF n_acl < 1  THEN RAISE EXCEPTION 'ACL 未插入'; END IF;
  RAISE NOTICE 'T-18：菜单行 1 + ACL % 行（复制自 agentManagerIndex）', n_acl;
END $$;
COMMIT;
