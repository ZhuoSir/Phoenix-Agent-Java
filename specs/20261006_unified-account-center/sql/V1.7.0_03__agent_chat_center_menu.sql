-- =====================================================================
-- v1.7.0 升级件 03：智能体中心并入 admin（R-11 / T-18）—— 菜单行 + 角色授权行
-- ---------------------------------------------------------------------
-- 机制结论（2026-10-06 只读调查；证据 evidence/T-18_menu-mechanism.txt）
--   ① 菜单可见性的数据源 = tbl_privilege_module（一行 = 一个目录/菜单）
--   ② 角色授权数据源 = tbl_privilege_acl，唯一键 (release_id, module_id)，release_sn='role'
--      module_id 指向 tbl_privilege_module.id（**不是 pvalue id** —— 旧脚本在此误判，
--      且把「智能体列表」的 4 行 ACL 原样复制 ⇒ 4 行同 release_id + 同新 module_id ⇒ 撞唯一键）
--   ③ 运行时现状：LoginServiceImpl.getUserMenus() 为**开发期全放开** ——
--      `getModelTreeByUserId(...)` 调用被注释，改为 `list()` 全量 + buildAdminAclMap 授予全部权限位。
--      ⇒ 本期实际可见性 = 任一登录用户（ACL 行不参与过滤）；ACL 行是"二期启用过滤即生效"的正确预置数据。
--   ④ 数据坑：tbl_privilege_acl.module_sn 是**过期标签** ——「智能体列表/技能管理/MCP/插件管理」4 行
--      的 module_sn 全部写着 'agentManagerIndex'，因此**禁止用 module_sn 反查菜单**，
--      定位菜单一律用 tbl_privilege_module.url（本脚本按 url='/agent/list' 定位母版行）。
-- 幂等：先按固定 id / sn 清理再插；回滚见 rollback/V1.7.0_03__agent_chat_center_menu_rollback.sql
-- =====================================================================
BEGIN;

-- 幂等清理（固定 id + sn 双保险，防上次手工插入残留）
DELETE FROM tbl_privilege_acl    WHERE module_id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
DELETE FROM tbl_privilege_module WHERE id        = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
DELETE FROM tbl_privilege_acl    WHERE module_sn = 'AgentChatCenter';
DELETE FROM tbl_privilege_module WHERE sn        = 'AgentChatCenter';

-- ① 菜单行：挂在「智能体管理」(pid) 下，排在组内最前（order_no=2 < 请求管理 3）
--    字段形状继承母版行「智能体列表」(url='/agent/list')：state/system_id/category_id/type/pid 全部同源，
--    避免手写字段与既有约定漂移（L-01）。image 保持 NULL —— 现网 26 行菜单均无图标。
INSERT INTO tbl_privilege_module
  (id, name, url, sn, state, component, system_id, status, image, order_no, is_show,
   create_time, create_by, del_flag, pid, category_id, type)
SELECT 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70', '智能体中心', '/agent/chat', 'AgentChatCenter',
       m.state, '#/views/front/chat.vue', m.system_id, 1, NULL, 2, 1,
       now(), 'admin', 0, m.pid, m.category_id, m.type
  FROM tbl_privilege_module m
 WHERE m.url = '/agent/list';

-- ② 角色授权行：每个角色一行（唯一键 (release_id, module_id) 天然去重）
--    acl_state 继承「智能体列表」在系统管理员角色下的授权位掩码（31=全权限位），
--    无母版授权时兜底 '31'（与该菜单自身 state 一致）。
INSERT INTO tbl_privilege_acl
  (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, del_flag)
SELECT replace(gen_random_uuid()::text, '-', ''), r.id, 'role',
       (SELECT a.system_sn FROM tbl_privilege_acl a
         WHERE a.release_id = '428007432736870400' AND a.system_sn IS NOT NULL LIMIT 1),
       'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70', 'AgentChatCenter',
       COALESCE((SELECT a.acl_state FROM tbl_privilege_acl a
                  WHERE a.release_id = '428007432736870400'
                    AND a.module_id = (SELECT id FROM tbl_privilege_module WHERE url = '/agent/list')
                  LIMIT 1), '31'),
       now(), 0
  FROM tbl_privilege_role r;

-- ③ 闸门：菜单行必须恰好 1 行；ACL 行数必须等于角色数
DO $$
DECLARE n_mod int; n_acl int; n_role int; n_url int;
BEGIN
  SELECT count(*) INTO n_mod  FROM tbl_privilege_module WHERE id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
  SELECT count(*) INTO n_url  FROM tbl_privilege_module WHERE url = '/agent/list';
  SELECT count(*) INTO n_role FROM tbl_privilege_role;
  SELECT count(*) INTO n_acl  FROM tbl_privilege_acl WHERE module_id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
  IF n_url <> 1 THEN RAISE EXCEPTION '母版行「智能体列表」定位失败（% 行）', n_url; END IF;
  IF n_mod <> 1 THEN RAISE EXCEPTION '菜单行插入失败（%）', n_mod; END IF;
  IF n_acl <> n_role THEN RAISE EXCEPTION 'ACL 行数 % <> 角色数 %', n_acl, n_role; END IF;
  RAISE NOTICE 'T-18：菜单行 1（智能体中心 /agent/chat）+ 角色 ACL % 行（角色数 %）', n_acl, n_role;
END $$;

COMMIT;
