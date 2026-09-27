-- 升级件草案：技能管理菜单注册（T-12）
-- 依赖：01_agent_skill_upgrade.sql（技能域表）
-- 幂等：模块 id 取 md5 常量；ACL 用 NOT EXISTS 防重
-- 说明：ACL 按「智能体列表」模块(f0c0d2d3a7bb452cb5ff98328575b41a)的既有授权角色逐一复制，
--       保证已能看智能体管理的角色同时获得技能管理。

INSERT INTO tbl_privilege_module (id, pid, name, url, sn, component, type, order_no, is_show, status,
                                  create_time, del_flag)
SELECT md5('phoenix-skill-menu'),
       '741103abe63748749f82fbd2b420061c',
       '技能管理',
       '/agent/skill',
       'AgentSkillManagement',
       '#/views/agent/skill/index.vue',
       1, 15, 1, 1, now(), 0
WHERE NOT EXISTS (
    SELECT 1 FROM tbl_privilege_module WHERE url = '/agent/skill' AND del_flag = 0
);

INSERT INTO tbl_privilege_acl (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state,
                              create_time, del_flag)
SELECT gen_random_uuid()::text,
       a.release_id, a.release_sn, a.system_sn,
       md5('phoenix-skill-menu'), a.module_sn, a.acl_state,
       now(), 0
FROM tbl_privilege_acl a
WHERE a.module_id = 'f0c0d2d3a7bb452cb5ff98328575b41a'
  AND a.del_flag = 0
  AND NOT EXISTS (
      SELECT 1 FROM tbl_privilege_acl b
      WHERE b.module_id = md5('phoenix-skill-menu')
        AND b.release_id = a.release_id
        AND b.release_sn = a.release_sn
        AND b.del_flag = 0
  );

-- 回滚：
-- DELETE FROM tbl_privilege_acl WHERE module_id = md5('phoenix-skill-menu');
-- DELETE FROM tbl_privilege_module WHERE id = md5('phoenix-skill-menu');
