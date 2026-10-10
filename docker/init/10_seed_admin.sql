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

-- BUG-185：初始管理员必须绑「超级管理员」角色（ROLE_ADMIN）。原种子只建用户、不建角色绑定，
-- 全新装出来的 admin 无角色 ⇒ 被 V2.0.0_02 的角色回填兜底补成 COMMON ⇒ 超管登录后只剩普通用户菜单（用户实测）。
-- 幂等：仅当 admin 尚无有效角色绑定、且 ROLE_ADMIN 角色存在时插入；本语句为纯 SQL（不在 PL/pgSQL 块内）。
INSERT INTO tbl_privilege_user_role (id, user_id, role_id, end_date, valid_month,
                                     create_time, create_by, update_time, update_by, del_flag)
SELECT md5('seed_admin_role:1000000000000000001'), u.id, r.id, NULL, NULL,
       now(), 'seed', now(), 'seed', 0
  FROM tbl_privilege_user u
  JOIN tbl_privilege_role r ON r.sn = 'ROLE_ADMIN' AND coalesce(r.del_flag, 0) = 0
 WHERE u.username = 'admin' AND coalesce(u.del_flag, 0) = 0
   AND NOT EXISTS (SELECT 1 FROM tbl_privilege_user_role ur
                    WHERE ur.user_id = u.id AND coalesce(ur.del_flag, 0) = 0);