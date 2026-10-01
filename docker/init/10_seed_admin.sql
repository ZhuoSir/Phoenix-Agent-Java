-- ============================================
-- 首次初始化种子：初始管理员 admin / 123456
-- 哈希 = SecureUtil.md5("phoenix" + "123456")（LoginConstant.PASSWORD_SALT="phoenix"，T-01 实测）
-- 列值参照 all_data.sql 既有用户行（company_id/dept_id 为 NOT NULL）
-- 幂等：WHERE NOT EXISTS
-- ============================================
INSERT INTO tbl_privilege_user (id, code, real_name, username, password, company_id, dept_id, status, del_flag, pwd_init)
SELECT '1000000000000000001', '1099', '初始管理员', 'admin',
       'f1c457c84af9bc85acaeb64bee218755',
       '428001009954172928', '428003302434869248', 0, 0, 1
WHERE NOT EXISTS (
    SELECT 1 FROM tbl_privilege_user
     WHERE username = 'admin' AND del_flag = 0
);
