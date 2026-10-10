-- =====================================================================
-- 版本: v2.0.0  序号: 10  类型: DML
-- 来源: specs/20261007_user-role-group-model（BUG-135 修复）
-- 目的: 补齐「存活但 state 为空」菜单的操作权限位掩码，修复角色授权树里这些菜单无法勾选的问题
-- 前置: V2.0.0_01~09 已应用
-- 可重入: 是（仅命中 state 为空的存活行；补过即 0 行）
-- 回滚: rollback/V2.0.0_10__menu_state_backfill_dml_rollback.sql
-- ---------------------------------------------------------------------
-- 根因（实测）：tbl_privilege_module.state 是 varchar 位掩码（'31'=0b11111=查询/新增/编辑/删除/下载）。
--   PrivilegeModuleServiceImpl.tree() 以 `moduleState = state ?: 0` 过滤权限位，
--   再经前端 assign-menu.vue `v-if="data.pvalues?.length"` 渲染复选框。
--   技能管理/插件管理/MCP/知识库 4 个菜单由功能迁移（V1.2.0_02 / V1.3.0_02 / V1.6.0_02）插入时
--   **漏写 state 列** ⇒ state=NULL ⇒ moduleState=0 ⇒ pvalues 为空 ⇒ 整行无复选框 ⇒ 无法分配。
-- 修复：与全站一致补 '31'（不写死 id，兜住同类漏网菜单；仅作用存活行，软删行不动）。
-- =====================================================================
BEGIN;

UPDATE tbl_privilege_module
   SET state = '31', update_time = now()
 WHERE coalesce(del_flag, 0) = 0
   AND (state IS NULL OR state = '');

-- 自检（环境无关：修复后不得再有「存活且 state 空」的菜单）
DO $chk$
DECLARE still_empty int; alive int; fixed int;
BEGIN
  SELECT count(*) INTO alive FROM tbl_privilege_module WHERE coalesce(del_flag, 0) = 0;
  SELECT count(*) INTO still_empty FROM tbl_privilege_module
   WHERE coalesce(del_flag, 0) = 0 AND (state IS NULL OR state = '');
  IF still_empty <> 0 THEN
    RAISE EXCEPTION '[V2.0.0_10] 仍有 % 个存活菜单 state 为空（应为 0）', still_empty;
  END IF;
  RAISE NOTICE '[V2.0.0_10] 自检通过：存活菜单 % 个，state 空 % 个（全部已补齐为 31）', alive, still_empty;
END $chk$;

COMMIT;
