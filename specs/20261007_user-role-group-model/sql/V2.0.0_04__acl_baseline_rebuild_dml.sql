-- =====================================================================
-- 版本: v2.0.0  序号: 04  类型: DML
-- 来源: specs/20261007_user-role-group-model (T-06)
-- 目的: R-05 —— 重建「角色 → 菜单」授权基线，使菜单过滤启用后可用
-- 前置: V2.0.0_02（补角色）、V2.0.0_03（组织菜单清理）
-- 可重入: 是（备份表 ON CONFLICT DO NOTHING + 目标对先清后插 + 确定性主键）
-- 预估: 百行量级，<1s
-- 回滚: rollback/V2.0.0_04__acl_baseline_rebuild_dml_rollback.sql
-- ---------------------------------------------------------------------
-- 背景（活库实测，见 evidence/T-01_gate-stats.txt）:
--   acl 存活 61 行：30 行 module_id 落空、36 行挂在不存在的 release_id
--   `71f2934c-…`（该 id 根本不是角色）；超管可用菜单 24/27；普通角色仅 1 行，
--   而名下 5 个存活用户 ⇒ 直接启用过滤会「只剩 1 个菜单且落地页 /agent/list 未授权」
--   ⇒ 登录即 404/白屏。本件即为此重建基线。
--
-- 关键设计（防线上翻车）:
--   ① **唯一索引 idx_tbl_privilege_acl_release_module (release_id, module_id) 覆盖墓碑行**
--      （实测本库有 14 行 del_flag≠0 的墓碑占着键位）⇒ 若只判"活跃行不存在"就插入，
--      生产库一旦有同角色+同菜单的墓碑，INSERT 会撞唯一键**中止整个迁移**。
--      因此本件对「将要写入的目标对」先**留档 + 删除全部冲突行（含墓碑）**，再纯 INSERT。
--   ② 脏数据清理只针对**活跃行**（del_flag=0），墓碑行一概不碰（最小footprint）。
--   ③ 所有被删除的行（脏数据 + 目标对冲突行）都进 JSONB 备份表 tbl_privilege_acl_bak_v200，
--      回滚可 1:1 还原。
--
-- 三段动作:
--   ① 脏数据（活跃且 module 落空 / release 非角色）留档 + 清理
--   ② 超管（sn=ROLE_ADMIN）：授予**全部存活菜单**
--   ③ 普通角色（sn=COMMON）：授予**非管理类基线菜单**（url 白名单）
--
-- ⚠️ 普通角色基线为 agent 依「非管理类 + 必须覆盖落地页 /agent/list」拟定，属**可调参数**：
--    增删菜单只改 ③ 的白名单后重跑本件（幂等）。该集合决定 5 个普通用户的可见面，
--    如需调整请在 M3 汇总前提出。
-- =====================================================================
BEGIN;

-- ① 备份表 + 脏数据留档（仅活跃行）
CREATE TABLE IF NOT EXISTS tbl_privilege_acl_bak_v200 (
  id       varchar(200) PRIMARY KEY,
  row_json jsonb        NOT NULL,
  bak_at   timestamptz  DEFAULT now()
);

INSERT INTO tbl_privilege_acl_bak_v200 (id, row_json)
SELECT a.id, to_jsonb(a)
  FROM tbl_privilege_acl a
 WHERE coalesce(a.del_flag, 0) = 0
   AND (NOT EXISTS (SELECT 1 FROM tbl_privilege_module m WHERE m.id = a.module_id)
        OR (a.release_sn = 'role'
            AND NOT EXISTS (SELECT 1 FROM tbl_privilege_role r WHERE r.id = a.release_id)))
ON CONFLICT (id) DO NOTHING;

DELETE FROM tbl_privilege_acl a
 WHERE coalesce(a.del_flag, 0) = 0
   AND a.id IN (SELECT b.id FROM tbl_privilege_acl_bak_v200 b);

-- ②③ 目标对计算 → 冲突留档清除 → 纯插入
DO $$
DECLARE
  v_admin   varchar(64);
  v_common  varchar(64);
  v_alive   int;
  v_targets int;
  v_admin_n int;
  v_common_n int;
BEGIN
  SELECT id INTO v_admin  FROM tbl_privilege_role
   WHERE upper(sn) = 'ROLE_ADMIN' AND coalesce(del_flag,0) = 0 ORDER BY id LIMIT 1;
  SELECT id INTO v_common FROM tbl_privilege_role
   WHERE upper(sn) = 'COMMON'    AND coalesce(del_flag,0) = 0 ORDER BY id LIMIT 1;
  IF v_admin IS NULL THEN RAISE EXCEPTION '[V2.0.0_04] 未找到 sn=ROLE_ADMIN 的启用角色，拒绝继续'; END IF;
  IF v_common IS NULL THEN RAISE EXCEPTION '[V2.0.0_04] 未找到 sn=COMMON 的启用角色，拒绝继续'; END IF;

  SELECT count(*) INTO v_alive FROM tbl_privilege_module WHERE coalesce(del_flag,0) = 0;

  -- 目标对：超管=全部存活菜单；普通角色=白名单
  CREATE TEMP TABLE IF NOT EXISTS tmp_v200_acl_targets (
    release_id varchar(200) NOT NULL,
    module_id  varchar(200) NOT NULL,
    PRIMARY KEY (release_id, module_id)
  ) ON COMMIT DROP;
  TRUNCATE tmp_v200_acl_targets;

  INSERT INTO tmp_v200_acl_targets (release_id, module_id)
  SELECT v_admin, m.id FROM tbl_privilege_module m WHERE coalesce(m.del_flag,0) = 0
  UNION
  SELECT v_common, m.id FROM tbl_privilege_module m
   WHERE coalesce(m.del_flag,0) = 0
     AND m.url IN ('/agent-manager',    -- 目录：智能体管理
                   '/agent/list',       -- 落地页（homePath 默认，必须授权）
                   '/agent/:id',        -- 列表 → 编辑
                   '/agent/:id/run',    -- 列表 → 运行
                   '#/front/chat',      -- 智能体中心（实测其 url 带 # 前缀，非 /agent/chat！）
                   '/agent/chat',       -- 兼容写法（若某库如此存）
                   '/knowledge-base',   -- 知识库
                   '/profile');         -- 个人中心

  SELECT count(*) INTO v_targets FROM tmp_v200_acl_targets;

  -- 冲突行（含墓碑！）先留档再删 —— 唯一索引覆盖墓碑，不先清会撞键中止迁移
  -- 注意：**不归档本件自己插入的行**（create_by='V2.0.0_04'）；否则幂等复跑会把自己上一轮的行
  --       也存进备份，造成同 (release_id, module_id) 多行 ⇒ 回滚还原时撞唯一键（已实测踩中）。
  INSERT INTO tbl_privilege_acl_bak_v200 (id, row_json)
  SELECT a.id, to_jsonb(a)
    FROM tbl_privilege_acl a
    JOIN tmp_v200_acl_targets t
      ON t.release_id = a.release_id AND t.module_id = a.module_id
   WHERE coalesce(a.create_by, '') <> 'V2.0.0_04'
  ON CONFLICT (id) DO NOTHING;

  -- 删除全部冲突行（含本件自己上一轮插入的，保证随后纯插入不撞键）
  DELETE FROM tbl_privilege_acl a
   USING tmp_v200_acl_targets t
   WHERE a.release_id = t.release_id AND a.module_id = t.module_id;

  -- 纯插入（此时目标对已被清空，不可能撞唯一键）
  INSERT INTO tbl_privilege_acl
        (id, release_id, release_sn, system_sn, module_id, module_sn, acl_state,
         create_time, create_by, update_time, update_by, del_flag)
  SELECT md5('v2.0.0_acl:' || t.release_id || ':' || t.module_id),
         t.release_id, 'role', NULL, t.module_id, m.sn, '31',
         now(), 'V2.0.0_04', now(), 'V2.0.0_04', 0
    FROM tmp_v200_acl_targets t
    JOIN tbl_privilege_module m ON m.id = t.module_id;

  -- 自检
  SELECT count(*) INTO v_admin_n  FROM tbl_privilege_acl
   WHERE release_id = v_admin  AND coalesce(del_flag,0) = 0
     AND module_id IN (SELECT id FROM tbl_privilege_module WHERE coalesce(del_flag,0) = 0);
  SELECT count(*) INTO v_common_n FROM tbl_privilege_acl
   WHERE release_id = v_common AND coalesce(del_flag,0) = 0
     AND module_id IN (SELECT id FROM tbl_privilege_module WHERE coalesce(del_flag,0) = 0);

  IF v_admin_n <> v_alive THEN
    RAISE EXCEPTION '[V2.0.0_04] 自检失败：超管可用菜单 % ≠ 存活菜单 %', v_admin_n, v_alive;
  END IF;
  IF v_common_n = 0 THEN
    RAISE EXCEPTION '[V2.0.0_04] 自检失败：普通角色可用菜单为 0（白名单可能全部失配）';
  END IF;

  RAISE NOTICE '[V2.0.0_04] 基线重建完成：存活菜单 %；目标对 %；超管 %；普通角色 %（角色 % / %）',
               v_alive, v_targets, v_admin_n, v_common_n, v_admin, v_common;
END $$;

-- 终检：任何存活角色可用菜单为 0 都应被拦下（上线门禁的库内版）
DO $$
DECLARE r record; v_zero text := '';
BEGIN
  FOR r IN SELECT id, name FROM tbl_privilege_role WHERE coalesce(del_flag,0) = 0 LOOP
    IF NOT EXISTS (SELECT 1 FROM tbl_privilege_acl a
                    JOIN tbl_privilege_module m ON m.id = a.module_id AND coalesce(m.del_flag,0) = 0
                   WHERE a.release_id = r.id AND coalesce(a.del_flag,0) = 0) THEN
      v_zero := v_zero || r.name || '(' || r.id || ') ';
    END IF;
  END LOOP;
  IF v_zero <> '' THEN
    RAISE EXCEPTION '[V2.0.0_04] 终检失败：以下角色可用菜单为 0 → %', v_zero;
  END IF;
  RAISE NOTICE '[V2.0.0_04] 终检通过：无「可用菜单为 0」的角色';
END $$;

COMMIT;
