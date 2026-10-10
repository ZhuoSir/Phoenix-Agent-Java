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
DECLARE alive int; bad int; chen_role int; has_admin boolean; has_chen boolean;
BEGIN
  SELECT count(*) INTO alive FROM tbl_privilege_user WHERE coalesce(del_flag, 0) = 0;
  -- BUG-171（BUG-151 同族再犯）：原断言写死 alive=2 且要求 admin+chenzhuo 同时在——那是**开发数据集**口径；
  -- 全新安装库只种一个 admin（alive=1）⇒ 断言触发、整迁移事务回滚、migrator exit 3、整栈起不来。
  -- 按本文件自身声明的原则（L-51：只校验结构不变量、不硬编码环境相关绝对数）改为分档：
  --   0 = 空库（跳过）/ 1 = 仅 admin 的全新库（通过）/ 2 = 开发库 admin+chenzhuo（通过）/ >2 才异常
  IF alive = 0 THEN
    RAISE NOTICE '[V2.0.0_07] 跳过：库中无存活账号（全新未初始化库）';
    RETURN;
  END IF;
  IF alive > 2 THEN RAISE EXCEPTION '[V2.0.0_07] 存活账号 % > 2，疑似清理未生效', alive; END IF;
  SELECT EXISTS (SELECT 1 FROM tbl_privilege_user WHERE username = 'admin' AND coalesce(del_flag,0)=0) INTO has_admin;
  SELECT EXISTS (SELECT 1 FROM tbl_privilege_user WHERE username = 'chenzhuo' AND coalesce(del_flag,0)=0) INTO has_chen;
  IF NOT has_admin THEN RAISE EXCEPTION '[V2.0.0_07] 存活账号中缺 admin'; END IF;
  IF alive = 2 AND NOT has_chen THEN RAISE EXCEPTION '[V2.0.0_07] 存活账号为 2 但不是 admin + chenzhuo'; END IF;

  SELECT count(*) INTO bad FROM (
    SELECT 1 FROM tbl_privilege_user WHERE id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
    UNION ALL SELECT 1 FROM tbl_privilege_user_role WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
    UNION ALL SELECT 1 FROM tbl_agent_user_agent_info WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
    UNION ALL SELECT 1 FROM tbl_data_chat_session WHERE user_id IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
    UNION ALL SELECT 1 FROM tbl_vector_store_user_memory WHERE metadata->>'userId' IN ('428011841386577920', '431678413494018048', '428011841386577921', '432101006843711488', '432061200055025664', '433383317100486656')
  ) x;
  IF bad <> 0 THEN RAISE EXCEPTION '[V2.0.0_07] 仍残留待删账号的账号域数据 % 行', bad; END IF;

  -- chenzhuo 的角色绑定：仅当 chenzhuo 存活时校验（全新库无此账号属正常，不再硬失败）
  IF has_chen THEN
    SELECT count(*) INTO chen_role FROM tbl_privilege_user_role
     WHERE user_id = '461671036765859840' AND coalesce(del_flag, 0) = 0;
    IF chen_role < 1 THEN RAISE EXCEPTION '[V2.0.0_07] chenzhuo 的角色绑定丢失'; END IF;
  ELSE
    chen_role := 0;
  END IF;

  RAISE NOTICE '[V2.0.0_07] 自检通过：存活账号 %（admin=% / chenzhuo=%）/ 残留 % 行 / chenzhuo 角色绑定 % 条',
    alive, has_admin, has_chen, bad, chen_role;
END $chk$;

COMMIT;
