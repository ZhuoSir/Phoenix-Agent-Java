-- v1.7.0 升级件 03 回滚：移除「智能体中心」菜单行与其角色授权行
-- 主键/固定 id 定位（不依赖 module_sn —— 该列存在过期标签，见升级件 03 头注④）
BEGIN;

DELETE FROM tbl_privilege_acl    WHERE module_id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
DELETE FROM tbl_privilege_module WHERE id        = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
-- 兜底：防手工按 sn 插入的残留
DELETE FROM tbl_privilege_acl    WHERE module_sn = 'AgentChatCenter';
DELETE FROM tbl_privilege_module WHERE sn        = 'AgentChatCenter';

DO $$
DECLARE n_mod int; n_acl int;
BEGIN
  SELECT count(*) INTO n_mod FROM tbl_privilege_module WHERE sn = 'AgentChatCenter'
     OR id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
  SELECT count(*) INTO n_acl FROM tbl_privilege_acl WHERE module_sn = 'AgentChatCenter'
     OR module_id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
  IF n_mod <> 0 OR n_acl <> 0 THEN
    RAISE EXCEPTION '回滚不干净：菜单 % 行 / ACL % 行', n_mod, n_acl;
  END IF;
  RAISE NOTICE '回滚完成：菜单行与 ACL 行均已清除';
END $$;

COMMIT;
