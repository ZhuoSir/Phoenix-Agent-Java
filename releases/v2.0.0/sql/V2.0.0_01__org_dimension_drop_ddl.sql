-- =====================================================================
-- 版本: v2.0.0  序号: 01  类型: DDL
-- 来源: specs/20261007_user-role-group-model (T-14)
-- 目的: R-01/R-02/R-11 —— 组织维度（企业/部门/员工）与三方平台配置整体下线
-- 前置: 无（最小序号，先于 02~04 执行）
-- 可重入: 是（DROP ... IF EXISTS + ALTER TABLE IF EXISTS，重复执行影响 0 对象）
-- 实测注记（全新库重放发现）: `tbl_unified_account_map` **不在基线 sql/all_schema.sql 中**
--   （由另一处迁移创建）⇒ 必须写成 `ALTER TABLE IF EXISTS ... DROP COLUMN IF EXISTS ...`：
--   仅给列加 IF EXISTS 不能覆盖"表本身不存在"的情况，会以
--   `ERROR: relation "tbl_unified_account_map" does not exist` 中断整个升级件。
-- 回滚: rollback/V2.0.0_01__org_dimension_drop_ddl_rollback.sql（结构 + 列值保真恢复）
-- ---------------------------------------------------------------------
-- 影响面（活库实测 2026-10-07，非推断）:
--   DROP TABLE   tbl_privilege_company(4 行) / tbl_privilege_department(17 行) /
--                tbl_privilege_employee(14 行) / tbl_platform_platform_info(0 行)
--   DROP COLUMN  tbl_privilege_user.company_id(varchar(255) NOT NULL) / .dept_id(varchar(255) NOT NULL)
--                / .employee_id(varchar(65))
--                tbl_privilege_role.company_id(bigint)
--                tbl_platform_account_info.dept_id(varchar(64)) / .dept_name(varchar(128))
--                / .employee_id(varchar(64)) / .third_party_id(varchar(255))
--                tbl_unified_account_map.employee_id(varchar(64))
--   外键: 0 条（实测 pg_constraint 无 FK 指向上述表）⇒ 无需 CASCADE，删除顺序无关
-- ---------------------------------------------------------------------
-- 应用侧同批改动（T-14 代码面，已 BUILD SUCCESS）:
--   1) 删除死 XML mapper：PrivilegeUserMapper.xml / PrivilegeRoleMapper.xml
--      （其 selectPageByQuery/countPageByQuery JOIN 组织表；角色侧还引用根本不存在的 r.dept_id，
--        且这两个语句全仓 0 调用 —— 服务层早已改走 QueryWrapper）
--   2) 实体去组织字段：PrivilegeUser(companyId/deptId/employeeId/companyName/deptName)、
--      PrivilegeRole(companyId/companyName/deptName)、AccountInfo(employeeId/deptId/deptName)
--   3) PrivilegeRoleVO / PrivilegeRoleQuery 去组织字段；移除 GET /company/{companyId}
--      端点与 IPrivilegeRoleService.getByCompanyId
--   4) **关键**：解除 tbl_privilege_user.company_id / dept_id 的 NOT NULL ——
--      否则去掉组织字段后的建号会被数据库层拦下（T-13 探针实测
--      `null value in column "company_id" violates not-null constraint`），
--      直接阻断 R-02「只填账号 + 勾 1 角色即创建成功」
-- =====================================================================
BEGIN;

-- ① 用户侧组织列（company_id / dept_id 原为 NOT NULL —— 本版建号路径的硬阻塞点）
ALTER TABLE IF EXISTS tbl_privilege_user DROP COLUMN IF EXISTS company_id;
ALTER TABLE IF EXISTS tbl_privilege_user DROP COLUMN IF EXISTS dept_id;
ALTER TABLE IF EXISTS tbl_privilege_user DROP COLUMN IF EXISTS employee_id;

-- ② 角色侧组织列（PrivilegeRole 实体对应的 company_id；companyName/deptName 本就无对应列）
ALTER TABLE IF EXISTS tbl_privilege_role DROP COLUMN IF EXISTS company_id;

-- ③ 前台账号侧组织列 + 三方免登列（R-11：免登下线）
ALTER TABLE IF EXISTS tbl_platform_account_info DROP COLUMN IF EXISTS dept_id;
ALTER TABLE IF EXISTS tbl_platform_account_info DROP COLUMN IF EXISTS dept_name;
ALTER TABLE IF EXISTS tbl_platform_account_info DROP COLUMN IF EXISTS employee_id;
ALTER TABLE IF EXISTS tbl_platform_account_info DROP COLUMN IF EXISTS third_party_id;

-- ④ 统一账号映射表的人员列
ALTER TABLE IF EXISTS tbl_unified_account_map DROP COLUMN IF EXISTS employee_id;

-- ⑤ 组织维度三张表（先删叶子后删主表；无外键，顺序仅为可读性）
DROP TABLE IF EXISTS tbl_privilege_employee;
DROP TABLE IF EXISTS tbl_privilege_department;
DROP TABLE IF EXISTS tbl_privilege_company;

-- ⑥ 三方平台配置表（R-11：平台配置下线）
DROP TABLE IF EXISTS tbl_platform_platform_info;

COMMIT;
