-- =====================================================================
-- 版本: v2.0.0  序号: 08  类型: DDL(回滚)
-- 配对正向件: ../V2.0.0_08__usertype_code_drop_ddl.sql
-- 目的: 恢复 tbl_privilege_user 的 code/user_type/it_user_id/it_user_name 列、
--       tbl_privilege_user_role.user_no 列及其原值；组归属 account_name 还原为工号
-- 可重入: 是（ADD COLUMN IF NOT EXISTS + 按主键/外键 UPDATE）
-- 环境无关性: 用户列按 id、绑定列按 user_id、组归属按 account_id 还原（不依赖具体行 id）
-- 数据来源: 正向执行前由生产库逐行导出（字段级保真）
-- =====================================================================
BEGIN;

-- ① 恢复列（先允许 NULL）
ALTER TABLE IF EXISTS tbl_privilege_user ADD COLUMN IF NOT EXISTS code varchar(64);
ALTER TABLE IF EXISTS tbl_privilege_user ADD COLUMN IF NOT EXISTS user_type int4;
ALTER TABLE IF EXISTS tbl_privilege_user ADD COLUMN IF NOT EXISTS it_user_id varchar(128);
ALTER TABLE IF EXISTS tbl_privilege_user ADD COLUMN IF NOT EXISTS it_user_name varchar(128);
ALTER TABLE IF EXISTS tbl_privilege_user_role ADD COLUMN IF NOT EXISTS user_no varchar(64);

-- ② 还原用户列原值（按主键；原值取自正向执行前的生产库）
UPDATE tbl_privilege_user SET code = '1002', user_type = 0, it_user_id = NULL, it_user_name = NULL WHERE id = '461671036765859840';
UPDATE tbl_privilege_user SET code = '1001', user_type = 0, it_user_id = NULL, it_user_name = NULL WHERE id = '461681072489615360';
UPDATE tbl_privilege_user SET code = '10001', user_type = 0, it_user_id = NULL, it_user_name = NULL WHERE id = '465417639332724736';

-- ③ 还原角色绑定冗余工号（**按 user_id 还原**，不依赖具体绑定行 id ⇒ 任何环境成立）
UPDATE tbl_privilege_user_role SET user_no = '1002' WHERE user_id = '461671036765859840';
UPDATE tbl_privilege_user_role SET user_no = '1001' WHERE user_id = '461681072489615360';
UPDATE tbl_privilege_user_role SET user_no = '10001' WHERE user_id = '465417639332724736';

-- ④ 恢复 NOT NULL 约束（原 DDL：code NOT NULL；需先保证无 NULL）
UPDATE tbl_privilege_user SET code = '' WHERE code IS NULL;
ALTER TABLE IF EXISTS tbl_privilege_user ALTER COLUMN code SET NOT NULL;

-- ⑤ 组归属 account_name 还原为工号（按 account_id 关联该用户的原工号）
UPDATE tbl_platform_account_group_info SET account_name = '1002' WHERE account_id = '461671036765859840';
UPDATE tbl_platform_account_group_info SET account_name = '1001' WHERE account_id = '461681072489615360';
UPDATE tbl_platform_account_group_info SET account_name = '10001' WHERE account_id = '465417639332724736';

COMMIT;
