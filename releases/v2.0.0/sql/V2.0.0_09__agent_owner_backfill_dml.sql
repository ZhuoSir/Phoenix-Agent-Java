-- =====================================================================
-- 版本: v2.0.0  序号: 09  类型: DML
-- 来源: specs/20261007_user-role-group-model (T-23 / R-17, requirements v2.6.0)
-- 目的: 智能体「创建人」存量回填 —— 把 admin_id 为空的历史智能体归给内置超管账号（admin）
-- 前置: V2.0.0_01~08 已应用；后端须为 v2.6.0 代码（列表已按创建人过滤）
-- 可重入: 是（仅处理 admin_id IS NULL 的行；重复执行影响 0 行）
-- 回滚: rollback/V2.0.0_09__agent_owner_backfill_dml_rollback.sql
-- ---------------------------------------------------------------------
-- 用户口径（2026-10-07，确认人陈卓）：「每个人只能看到自己创建的智能体，然后系统管理员能看到所有人的，
--   系统管理员这个角色也不可以删除」+ 追问裁定「存量无主智能体回填给 admin」
-- 背景（实测）：tbl_data_agent.admin_id 列存在但**创建时从不写入**，活库 19, 20, 21, 23, 24, 25, 30, 33, 36, 37 行全为 NULL；
--   列表接口原不做用户过滤 ⇒ 人人可见全部。
-- 环境无关性（同 L-51）：admin 的 id 逐环境不同（活库 461681072489615360 / 全新库种子 1000000000000000001），
--   故用**子查询动态解析**，不硬编码。
-- =====================================================================
BEGIN;

-- ① 回填：admin_id 为空的历史智能体 → 内置超管账号（admin）
UPDATE tbl_data_agent
   SET admin_id = (
        SELECT u.id::bigint
          FROM tbl_privilege_user u
         WHERE lower(u.username) = 'admin'
           AND coalesce(u.del_flag, 0) = 0
         ORDER BY u.create_time
         LIMIT 1)
 WHERE admin_id IS NULL
   AND EXISTS (SELECT 1 FROM tbl_privilege_user u
                WHERE lower(u.username) = 'admin' AND coalesce(u.del_flag, 0) = 0);

-- ② 自检（环境无关：只断言"有 admin 时不得再有空归属"）
DO $chk$
DECLARE admin_cnt int; left_null int; owned int;
BEGIN
  SELECT count(*) INTO admin_cnt FROM tbl_privilege_user
   WHERE lower(username) = 'admin' AND coalesce(del_flag, 0) = 0;
  SELECT count(*) INTO left_null FROM tbl_data_agent WHERE admin_id IS NULL;
  SELECT count(*) INTO owned FROM tbl_data_agent WHERE admin_id IS NOT NULL;

  IF admin_cnt >= 1 AND left_null <> 0 THEN
    RAISE EXCEPTION '[V2.0.0_09] 仍有 % 个智能体无创建人（admin 存在时应为 0）', left_null;
  END IF;

  RAISE NOTICE '[V2.0.0_09] 自检通过：admin 账号 % 个 / 已归属智能体 % 个 / 无主 % 个',
    admin_cnt, owned, left_null;
END $chk$;

COMMIT;
