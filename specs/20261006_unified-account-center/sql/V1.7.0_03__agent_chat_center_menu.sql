-- =====================================================================
-- v1.7.0 升级件 03：智能体中心并入 admin（R-11 / T-18）—— 一级菜单行 + 角色授权行
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
--      定位菜单一律用 tbl_privilege_module.url（本脚本按 url 定位母版行）。
--   ⑤ 层级：本菜单为**一级菜单**（pid=''、type='1'）并置顶（order_no=-1）——用户 2026-10-06 指定
--      「放在一级目录，最上面」。注意 type 必须是 '1'：前端 access.ts 只在 type==='1' 时取 component，
--      type='0'（目录）会得到空组件 ⇒ 页面空白。
-- 幂等：先按固定 id / sn 清理再插；回滚见 rollback/V1.7.0_03__agent_chat_center_menu_rollback.sql
-- =====================================================================
BEGIN;

-- 幂等清理（固定 id + sn 双保险，防上次手工插入残留）
DELETE FROM tbl_privilege_acl    WHERE module_id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
DELETE FROM tbl_privilege_module WHERE id        = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
DELETE FROM tbl_privilege_acl    WHERE module_sn = 'AgentChatCenter';
DELETE FROM tbl_privilege_module WHERE sn        = 'AgentChatCenter';

-- ① 菜单行：一级菜单 + 置顶，字段形状继承现有一级菜单「知识库」(url='/knowledge-base')：
--    pid=''、type='1'、system_id/category_id 为空 —— 与既有约定同源，避免手写字段漂移（L-01）。
--    image='lucide:message-square'（iconify 名，与既有菜单同格式）—— v2 追加：用户实测反馈"没有图标"；
--    首版置 NULL 是漏看 image 列（现网 24/27 行都有图标），非设计取舍。
INSERT INTO tbl_privilege_module
  (id, name, url, sn, state, component, system_id, status, image, order_no, is_show,
   create_time, create_by, del_flag, pid, category_id, type)
SELECT 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70', '智能体中心', '/agent/chat', 'AgentChatCenter',
       COALESCE(m.state, '31'), '#/views/front/chat.vue', m.system_id, 1, 'lucide:message-square', -1, 1,
       now(), 'admin', 0, COALESCE(m.pid, ''), m.category_id, m.type
  FROM tbl_privilege_module m
 WHERE m.url = '/knowledge-base';

-- ② 角色授权行：每个角色一行（唯一键 (release_id, module_id) 天然去重）
--    acl_state 继承「知识库」在系统管理员角色下的授权位掩码（31=全权限位），无母版时兜底 '31'。
INSERT INTO tbl_privilege_acl
  (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state, create_time, del_flag)
SELECT replace(gen_random_uuid()::text, '-', ''), r.id, 'role',
       (SELECT a.system_sn FROM tbl_privilege_acl a
         WHERE a.release_id = '428007432736870400' AND a.system_sn IS NOT NULL LIMIT 1),
       'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70', 'AgentChatCenter',
       COALESCE((SELECT a.acl_state FROM tbl_privilege_acl a
                  WHERE a.release_id = '428007432736870400'
                    AND a.module_id = (SELECT id FROM tbl_privilege_module WHERE url = '/knowledge-base')
                  LIMIT 1), '31'),
       now(), 0
  FROM tbl_privilege_role r;

-- ③ 闸门：母版可定位、菜单行恰好 1 行且为一级菜单、ACL 行数 = 角色数
DO $$
DECLARE n_mod int; n_acl int; n_role int; n_master int; n_root int;
BEGIN
  SELECT count(*) INTO n_master FROM tbl_privilege_module WHERE url = '/knowledge-base';
  SELECT count(*) INTO n_mod    FROM tbl_privilege_module WHERE id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
  SELECT count(*) INTO n_root   FROM tbl_privilege_module
    WHERE id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70' AND COALESCE(pid, '') = '' AND type = '1';
  SELECT count(*) INTO n_role   FROM tbl_privilege_role;
  SELECT count(*) INTO n_acl    FROM tbl_privilege_acl WHERE module_id = 'a9c1e7d4f0b34c8e9a1d2b3c4e5f6a70';
  IF n_master <> 1 THEN RAISE EXCEPTION '母版行「知识库」定位失败（% 行）', n_master; END IF;
  IF n_mod <> 1 THEN RAISE EXCEPTION '菜单行插入失败（%）', n_mod; END IF;
  IF n_root <> 1 THEN RAISE EXCEPTION '菜单行不是一级菜单（pid/type 不符）'; END IF;
  IF n_acl <> n_role THEN RAISE EXCEPTION 'ACL 行数 % <> 角色数 %', n_acl, n_role; END IF;
  RAISE NOTICE 'T-18：一级菜单行 1（智能体中心 /agent/chat，置顶）+ 角色 ACL % 行（角色数 %）', n_acl, n_role;
END $$;

COMMIT;
