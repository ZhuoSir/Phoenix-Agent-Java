-- =====================================================================
-- 版本: v2.0.0  序号: 11  类型: DML
-- 来源: specs/20261007_user-role-group-model（BUG-135 收尾：数据一致性补齐）
-- 目的: 把「存活但 system_id / category_id 为空」的菜单补成全站统一值 110 / 111
-- 前置: V2.0.0_01~10 已应用
-- 可重入: 是（仅命中空值存活行；补过即 0 行）
-- 回滚: rollback/V2.0.0_11__menu_system_category_backfill_dml_rollback.sql
-- ---------------------------------------------------------------------
-- 背景：技能管理/插件管理/MCP/知识库/智能体中心 5 个菜单由功能迁移插入时，
--   INSERT 列清单漏了 system_id / category_id（与 state 同源缺陷，见 BUG-135 / L-59）。
-- 安全性（实测，确认不改鉴权语义）：
--   · 登录期菜单树 LoginServiceImpl 用 privilegeModuleService.list()，**不按 system_id 过滤**；
--   · 角色授权树 / 菜单管理页 getModuleTreeApi() **不带 systemId 参数** → tree(null) 不过滤；
--   · getBySystemId / /module/system/{id} 硬过滤端点**无前端调用方**；
--   · category_id 后端**零引用**。
--   ⇒ 补值对现有可见性/鉴权完全惰性，仅统一数据口径。
-- =====================================================================
BEGIN;

UPDATE tbl_privilege_module
   SET system_id = 110, category_id = 111, update_time = now()
 WHERE coalesce(del_flag, 0) = 0
   AND (system_id IS NULL OR category_id IS NULL);

-- 自检（环境无关：修复后不得再有「存活且 system_id/category_id 空」的菜单）
DO $chk$
DECLARE still_empty int; alive int;
BEGIN
  SELECT count(*) INTO alive FROM tbl_privilege_module WHERE coalesce(del_flag, 0) = 0;
  SELECT count(*) INTO still_empty FROM tbl_privilege_module
   WHERE coalesce(del_flag, 0) = 0 AND (system_id IS NULL OR category_id IS NULL);
  IF still_empty <> 0 THEN
    RAISE EXCEPTION '[V2.0.0_11] 仍有 % 个存活菜单 system_id/category_id 为空（应为 0）', still_empty;
  END IF;
  RAISE NOTICE '[V2.0.0_11] 自检通过：存活菜单 % 个，system_id/category_id 空 % 个（全部已补齐 110/111）',
    alive, still_empty;
END $chk$;

COMMIT;
