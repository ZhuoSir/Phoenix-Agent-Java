-- =====================================================================
-- v1.7.0 升级件 04：智能体中心改为「新窗口菜单」（R-12 / T-22）
-- ---------------------------------------------------------------------
-- 变更内容：tbl_privilege_module 中 sn='AgentChatCenter' 的 url：'/agent/chat' → '#/front/chat'
-- 依据（plan 决策 8）：url 以 `#/` 开头 ⇒ 前端 access.ts 视为**外链菜单**（生成合成 path `/external/<sn>`
--   并写 meta.link），vben 侧栏点击走 `use-navigation.ts` 的 `openWindow(link,'_blank')` ⇒ 新浏览器窗口打开，
--   不再占用 admin 内的页签、不叠加后台外壳。
-- 其余列一律不动：component 仍为 '#/views/front/chat.vue'（记录该菜单指向的页面；外链菜单不渲染 admin 内组件），
--   order_no/is_show/pid/type/state/image/ACL 均保持升级件 03 的结果。
-- 幂等：按 sn 精确定位 + `url <> 目标` 条件 ⇒ 重复执行 0 行受影响。
-- del_flag 语义（L-21，privilege 系）：0=存在，1=已删 —— 本件只动 0。
-- 回滚：rollback/V1.7.0_04__agent_chat_center_new_window_rollback.sql
-- =====================================================================
BEGIN;

-- 前置闸门：目标行必须存在且唯一（防误改 / 多行）
DO $$
DECLARE n int;
BEGIN
  SELECT count(*) INTO n FROM tbl_privilege_module WHERE sn = 'AgentChatCenter' AND del_flag = 0;
  IF n <> 1 THEN RAISE EXCEPTION '菜单行 AgentChatCenter 定位失败（% 行）', n; END IF;
END $$;

UPDATE tbl_privilege_module
   SET url = '#/front/chat', update_time = now(), update_by = 'admin'
 WHERE sn = 'AgentChatCenter'
   AND del_flag = 0
   AND url <> '#/front/chat';

-- 后置闸门：改写后必须恰好 1 行且 url 正确
DO $$
DECLARE v_url text; n int;
BEGIN
  SELECT count(*) INTO n FROM tbl_privilege_module WHERE sn = 'AgentChatCenter' AND url = '#/front/chat';
  SELECT url INTO v_url FROM tbl_privilege_module WHERE sn = 'AgentChatCenter' AND del_flag = 0;
  IF n <> 1 THEN RAISE EXCEPTION 'url 改写失败（当前 url=%）', v_url; END IF;
  RAISE NOTICE 'T-22：智能体中心 url = %（新窗口菜单 ⇒ 侧栏点击 window.open(_blank) 打开独立 chat 页）', v_url;
END $$;

COMMIT;
