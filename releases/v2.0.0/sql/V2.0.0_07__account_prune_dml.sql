-- =====================================================================
-- 版本: v2.0.0  序号: 07  类型: DML
-- 来源: specs/20261007_user-role-group-model (T-19 / R-13, requirements v2.2.0)
-- 目的: 账号集合收敛 —— 只保留 admin 与 chenzhuo；其余账号及其账号域数据全部删除
-- 前置: V2.0.0_01~06 已应用
-- 可重入: 是（按固定 id 删除；重复执行影响 0 行）
-- 回滚: rollback/V2.0.0_07__account_prune_dml_rollback.sql（保真重建 5 账号及其关联，含聊天与向量记忆）
-- ---------------------------------------------------------------------
-- 用户口令（2026-10-07，确认人陈卓）：只保留 admin 和 chenzhuo 两个账号，其他的全部删掉，包括初始化数据也要改
--   + 追问裁定「被删账号的聊天数据一并删除」
-- 待删账号（实测 id）:
--   liufang 428011841386577920 / lwj 431678413494018048 / xtj 428011841386577921（存活）
--   maliu   432101006843711488 / wangwu 432061200055025664（已软删，物理删）
--   另：lwj 的前台账号 id 433383317100486656（出现在向量记忆 metadata.userId 中）
-- 删除范围: 账号域（角色绑定/登录日志/智能体绑定）+ 聊天会话与消息 + 向量记忆
-- 保留: admin（init 种子）与 chenzhuo 的全部数据；其他行的审计列（create_by/update_by）不改写
-- =====================================================================
BEGIN;

-- ① 先删从属数据（聊天消息 → 会话 → 向量记忆 → 登录日志 → 角色绑定 → 智能体绑定）
DELETE FROM tbl_data_chat_message WHERE session_id IN (SELECT id FROM tbl_data_chat_session WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656'));
DELETE FROM tbl_data_chat_session WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656');
DELETE FROM tbl_vector_store_user_memory WHERE metadata->>'userId' IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656');
DELETE FROM tbl_privilege_login_log WHERE operation_id::text IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656') OR coalesce(create_by,'') IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656');
DELETE FROM tbl_privilege_user_role WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656');
DELETE FROM tbl_agent_user_agent_info WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656');
DELETE FROM tbl_privilege_user WHERE id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656');

-- ② 自检（L-51：只校验结构不变量，不硬编码环境相关绝对数）
DO $chk$
DECLARE alive int; bad int; chen_role int;
BEGIN
  SELECT count(*) INTO alive FROM tbl_privilege_user WHERE coalesce(del_flag, 0) = 0;
  IF alive <> 2 THEN RAISE EXCEPTION '[V2.0.0_07] 存活账号 % <> 2', alive; END IF;
  IF NOT EXISTS (SELECT 1 FROM tbl_privilege_user WHERE username = 'admin' AND coalesce(del_flag,0)=0)
     OR NOT EXISTS (SELECT 1 FROM tbl_privilege_user WHERE username = 'chenzhuo' AND coalesce(del_flag,0)=0) THEN
    RAISE EXCEPTION '[V2.0.0_07] 存活账号不是 admin + chenzhuo';
  END IF;

  SELECT count(*) INTO bad FROM (
    SELECT 1 FROM tbl_privilege_user WHERE id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
    UNION ALL SELECT 1 FROM tbl_privilege_user_role WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
    UNION ALL SELECT 1 FROM tbl_agent_user_agent_info WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
    UNION ALL SELECT 1 FROM tbl_data_chat_session WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
    UNION ALL SELECT 1 FROM tbl_vector_store_user_memory WHERE metadata->>'userId' IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
  ) x;
  IF bad <> 0 THEN RAISE EXCEPTION '[V2.0.0_07] 仍残留待删账号的账号域数据 % 行', bad; END IF;

  SELECT count(*) INTO chen_role FROM tbl_privilege_user_role
   WHERE user_id = '461671036765859840' AND coalesce(del_flag, 0) = 0;
  IF chen_role < 1 THEN RAISE EXCEPTION '[V2.0.0_07] chenzhuo 的角色绑定丢失'; END IF;

  RAISE NOTICE '[V2.0.0_07] 自检通过：存活账号 %（admin+chenzhuo）/ 残留 % 行 / chenzhuo 角色绑定 % 条',
    alive, bad, chen_role;
END $chk$;

COMMIT;
