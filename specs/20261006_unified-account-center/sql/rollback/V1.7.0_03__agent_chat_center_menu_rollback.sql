-- v1.7.0 升级件 03 回滚：移除「智能体中心」菜单与其 ACL
BEGIN;
DELETE FROM tbl_privilege_acl    WHERE module_sn = 'AgentChatCenter';
DELETE FROM tbl_privilege_module WHERE sn        = 'AgentChatCenter';
COMMIT;
