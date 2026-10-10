-- =====================================================================
-- 版本: v2.0.0  序号: 07  类型: DML(回滚)
-- 配对正向件: ../V2.0.0_07__account_prune_dml.sql
-- 目的: 保真重建被删的 5 个账号及其账号域数据（角色绑定 / 登录日志 / 智能体绑定 /
--       聊天会话与消息 / 向量记忆）
-- 可重入: 是（INSERT ... ON CONFLICT (id) DO NOTHING）
-- 数据来源: 正向执行前由生产库逐行导出（字段级保真，未改写任何列值）
-- =====================================================================
BEGIN;

-- tbl_privilege_user：5 行
INSERT INTO tbl_privilege_user (id, code, real_name, username, password, tel, phone, mobile, email, image, it_user_id, it_user_name, is_leader, sex, address, fax, create_time, create_by, update_time, fail_month, failure_time, acl_timestamp, pwd_ftime, pwd_init, update_by, del_flag, user_type, status) VALUES ('428011841386577921', '600002', 'XTJ', 'xtj', '149d8a33536a282608c620f2e4dc5851', NULL, NULL, '', '', NULL, NULL, NULL, 0, 1, NULL, NULL, '2026-06-26T15:07:15.059', '600001', NULL, NULL, NULL, NULL, NULL, 0, NULL, 0, 1, 0) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user (id, code, real_name, username, password, tel, phone, mobile, email, image, it_user_id, it_user_name, is_leader, sex, address, fax, create_time, create_by, update_time, fail_month, failure_time, acl_timestamp, pwd_ftime, pwd_init, update_by, del_flag, user_type, status) VALUES ('432101006843711488', '655557', '马六', 'maliu', '149d8a33536a282608c620f2e4dc5851', NULL, NULL, '12', NULL, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-07T21:56:08.211', '432061200055025664', '2026-07-08T15:34:36.646', NULL, NULL, NULL, NULL, 0, '428011841386577920', 1, 0, 0) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user (id, code, real_name, username, password, tel, phone, mobile, email, image, it_user_id, it_user_name, is_leader, sex, address, fax, create_time, create_by, update_time, fail_month, failure_time, acl_timestamp, pwd_ftime, pwd_init, update_by, del_flag, user_type, status) VALUES ('432061200055025664', '60000004545', '王五', 'wangwu', '149d8a33536a282608c620f2e4dc5851', NULL, NULL, '35656', NULL, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-07T19:17:57.529', '432055603565846528', '2026-07-08T15:31:53.771', NULL, NULL, NULL, NULL, 1, '428011841386577920', 1, 0, 0) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user (id, code, real_name, username, password, tel, phone, mobile, email, image, it_user_id, it_user_name, is_leader, sex, address, fax, create_time, create_by, update_time, fail_month, failure_time, acl_timestamp, pwd_ftime, pwd_init, update_by, del_flag, user_type, status) VALUES ('431678413494018048', '1000008', '军哥', 'lwj', '149d8a33536a282608c620f2e4dc5851', NULL, NULL, '', NULL, NULL, NULL, NULL, 0, 0, NULL, NULL, '2026-07-10T22:16:40.194', NULL, '2026-07-10T22:16:40.208', NULL, NULL, NULL, NULL, 0, '428011841386577920', 0, 0, 0) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user (id, code, real_name, username, password, tel, phone, mobile, email, image, it_user_id, it_user_name, is_leader, sex, address, fax, create_time, create_by, update_time, fail_month, failure_time, acl_timestamp, pwd_ftime, pwd_init, update_by, del_flag, user_type, status) VALUES ('428011841386577920', '600001', '刘方', 'liufang', '149d8a33536a282608c620f2e4dc5851', NULL, NULL, '', '', NULL, NULL, NULL, 0, 1, NULL, NULL, '2026-07-11T11:42:42.748', '600001', '2026-07-11T11:42:42.894', NULL, NULL, NULL, NULL, 0, '428011841386577920', 0, 0, 0) ON CONFLICT (id) DO NOTHING;
-- tbl_privilege_user_role：9 行
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('428013125460164608', '428011841386577920', NULL, '428007432736870400', NULL, NULL, '2026-06-26T15:12:21.204', NULL, '2026-06-26T15:12:21.416456', NULL, 0) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('432101007938424832', '432101006843711488', NULL, '431285032083144704', NULL, NULL, '2026-07-07T21:56:08.546', '432061200055025664', '2026-07-07T21:56:08.371258', NULL, 1) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('432386698498007040', '432101006843711488', NULL, '431285032083144704', NULL, NULL, '2026-07-08T16:51:22.384', '432061200055025664', '2026-07-08T16:51:22.531378', NULL, 1) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('432398378778996736', '432101006843711488', NULL, '431285032083144704', NULL, NULL, '2026-07-08T17:37:47.203', '432061200055025664', '2026-07-08T17:37:47.085539', NULL, 1) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('432398380247003136', '432101006843711488', NULL, '428007432736870400', NULL, NULL, '2026-07-08T17:37:47.522', '432061200055025664', '2026-07-08T17:37:47.438589', NULL, 1) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('432398402007052288', '432101006843711488', NULL, '431285032083144704', NULL, NULL, '2026-07-08T17:37:52.713', '432061200055025664', '2026-07-08T17:37:52.623655', NULL, 1) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('432061201283956736', '432061200055025664', NULL, '431285032083144704', NULL, NULL, '2026-07-07T19:17:57.885', '432055603565846528', '2026-07-07T19:17:57.69266', NULL, 1) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('2562b99c2c3cecd8c748a5629aa5cd03', '428011841386577921', 'xtj', '431285032083144704', NULL, NULL, '2026-10-07T18:34:56.206443', 'V2.0.0_02', '2026-10-07T18:34:56.206443', 'V2.0.0_02', 0) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_user_role (id, user_id, user_no, role_id, end_date, valid_month, create_time, create_by, update_time, update_by, del_flag) VALUES ('f97a3ac3008a505329d2d979938a0953', '431678413494018048', 'lwj', '431285032083144704', NULL, NULL, '2026-10-07T18:34:56.206443', 'V2.0.0_02', '2026-10-07T18:34:56.206443', 'V2.0.0_02', 0) ON CONFLICT (id) DO NOTHING;
-- tbl_privilege_login_log：14 行
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461664528111902720, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T11:51:00.61518', 0, '428011841386577920', '2026-09-27T11:51:00.819', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461670700084883456, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T12:15:32.195617', 0, '428011841386577920', '2026-09-27T12:15:32.336', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461671323190685696, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T12:18:00.81805', 0, '428011841386577920', '2026-09-27T12:18:00.891', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461672442889412608, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T12:22:27.648028', 0, '428011841386577920', '2026-09-27T12:22:27.85', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461673132206497792, 428011841386577921, '0:0:0:0:0:0:0:1', 'xtj', 'XTJ', '用户登录', '2026-09-27T12:25:12.173749', 0, '428011841386577921', '2026-09-27T12:25:12.192', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461680928641765376, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T12:56:10.922236', 0, '428011841386577920', '2026-09-27T12:56:11.009', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461699059875565568, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T14:08:13.633075', 0, '428011841386577920', '2026-09-27T14:08:13.838', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461699289769484288, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T14:09:08.446969', 0, '428011841386577920', '2026-09-27T14:09:08.644', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461699467431829504, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T14:09:50.809965', 0, '428011841386577920', '2026-09-27T14:09:51.002', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461699893501767680, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T14:11:32.402345', 0, '428011841386577920', '2026-09-27T14:11:32.586', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461700574078029824, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T14:14:14.639252', 0, '428011841386577920', '2026-09-27T14:14:14.847', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461700821562834944, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T14:15:13.688295', 0, '428011841386577920', '2026-09-27T14:15:13.852', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461703239885373440, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T14:24:50.198517', 0, '428011841386577920', '2026-09-27T14:24:50.43', NULL, NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_privilege_login_log (id, operation_id, ip, operation_username, operation_person, operation_content, operation_time, del_flag, create_by, create_time, update_by, update_time) VALUES (461725828959571968, 428011841386577920, '0:0:0:0:0:0:0:1', 'liufang', '刘方', '用户登录', '2026-09-27T15:54:35.915674', 0, '428011841386577920', '2026-09-27T15:54:36.081', NULL, NULL) ON CONFLICT (id) DO NOTHING;
-- tbl_agent_user_agent_info：2 行
INSERT INTO tbl_agent_user_agent_info (id, user_id, agent_sn, action_count, last_date) VALUES ('433380097808588800', '428011841386577920', 'BpmReactAgent', 1, '2026-07-11T10:38:47.129') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_agent_user_agent_info (id, user_id, agent_sn, action_count, last_date) VALUES ('433380878112710656', '428011841386577920', 'ZhiduReactAgent', 2, '2026-09-27T12:12:44.298') ON CONFLICT (id) DO NOTHING;
-- tbl_data_chat_session：5 行
INSERT INTO tbl_data_chat_session (id, agent_id, title, status, is_pinned, user_id, create_time, update_time) VALUES ('93e56f1b-514d-4d8b-a74f-a359b17072ed', 22, '分析销售额', 'active', false, '428011841386577920', '2026-07-11T10:27:38.979798', '2026-07-11T10:29:15.442725') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_session (id, agent_id, title, status, is_pinned, user_id, create_time, update_time) VALUES ('390af8ae-3939-405f-b615-adbcddb37cc0', 19, '分析本月流程审批量按天统计', 'active', false, '428011841386577920', '2026-07-11T10:36:39.212005', '2026-07-11T10:38:58.495592') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_session (id, agent_id, title, status, is_pinned, user_id, create_time, update_time) VALUES ('0e1a3407-6753-4411-ab4b-ed84809f52d4', 23, '报诗', 'active', false, '428011841386577920', '2026-09-27T12:09:53.29468', '2026-09-27T12:09:58.745916') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_session (id, agent_id, title, status, is_pinned, user_id, create_time, update_time) VALUES ('b5fdeb5f-aa23-4e85-be9c-8a08aa1c24ee', 20, '请假流程', 'active', false, '428011841386577920', '2026-07-11T10:41:53.1275', '2026-09-27T12:13:04.120927') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_session (id, agent_id, title, status, is_pinned, user_id, create_time, update_time) VALUES ('c9e3b744-ef6a-4b6b-b6f0-7669b4d2900e', 24, '报诗', 'active', false, '428011841386577920', '2026-09-27T12:10:24.519679', '2026-09-27T12:28:44.421441') ON CONFLICT (id) DO NOTHING;
-- tbl_data_chat_message：32 行
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3756, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'user', '帮我分析一下销售额', 'text', NULL, '2026-07-11T10:27:38.979798') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3757, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">意图识别</div>
      <div class="agent-response-content">正在进行意图识别...<br><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">json</span><span hidden>{"classification": "《可能的数据分析请求》"}</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs json"><span class="hljs-punctuation">{</span><span class="hljs-attr">&quot;classification&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-string">&quot;《可能的数据分析请求》&quot;</span><span class="hljs-punctuation">}</span></code></pre><br>意图识别完成！<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:27:40.816632') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3758, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">执行证据召回流程</div>
      <div class="agent-response-content">正在查询重写以更好召回evidence...<br><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">json</span><span hidden>{"standalone_query": "分析销售额"}</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs json"><span class="hljs-punctuation">{</span><span class="hljs-attr">&quot;standalone_query&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-string">&quot;分析销售额&quot;</span><span class="hljs-punctuation">}</span></code></pre><br>查询重写完成！<br>重写后查询：<br>分析销售额<br>正在获取证据...未找到证据！<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:27:42.210763') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3759, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">业务翻译</div>
      <div class="agent-response-content">正在进行问题增强...<br><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">json</span><span hidden>{
  "canonical_query": "分析销售额数据",
  "expanded_queries": [
    "请帮我查看当前的销售总额情况",
    "对最近的销售额进行统计分析",
    "分析一下整体的销售业绩"
  ]
}</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs json"><span class="hljs-punctuation">{</span>
  <span class="hljs-attr">&quot;canonical_query&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-string">&quot;分析销售额数据&quot;</span><span class="hljs-punctuation">,</span>
  <span class="hljs-attr">&quot;expanded_queries&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-punctuation">[</span>
    <span class="hljs-string">&quot;请帮我查看当前的销售总额情况&quot;</span><span class="hljs-punctuation">,</span>
    <span class="hljs-string">&quot;对最近的销售额进行统计分析&quot;</span><span class="hljs-punctuation">,</span>
    <span class="hljs-string">&quot;分析一下整体的销售业绩&quot;</span>
  <span class="hljs-punctuation">]</span>
<span class="hljs-punctuation">}</span></code></pre><br>问题增强完成！<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:27:47.376926') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3760, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">召回数据库结构</div>
      <div class="agent-response-content">开始初步召回Schema信息...<br>初步表信息召回完成，数量: 3，表名: tbl_tmp_products, tbl_tmp_orders, tbl_tmp_users<br>初步Schema信息召回完成.<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:27:47.405695') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3761, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">表关系推理</div>
      <div class="agent-response-content">开始构建初始Schema...<br>初始Schema构建完成.<br>正在选择合适的数据表...<br><br><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">json</span><span hidden>["tbl_tmp_orders"]</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs json"><span class="hljs-punctuation">[</span><span class="hljs-string">&quot;tbl_tmp_orders&quot;</span><span class="hljs-punctuation">]</span></code></pre><br><br>选择数据表完成。<br>开始处理Schema选择...<br>Schema选择处理完成.<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:27:50.344924') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3762, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">可行性评估</div>
      <div class="agent-response-content">正在进行可行性评估...<br>【需求类型】：《数据分析》  <br>【语种类型】：《中文》  <br>【需求内容】：分析销售额数据可行性评估完成！<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:27:55.673332') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3769, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">执行SQL</div>
      <div class="agent-response-content">开始执行SQL...<br>执行SQL查询：<br><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">sql</span><span hidden>SELECT dimension, month, status, sales, order_count FROM ( SELECT CASE WHEN GROUPING(TO_CHAR(order_date, ''YYYY-MM'')) = 0 AND GROUPING(status) = 1 THEN ''monthly'' WHEN GROUPING(status) = 0 AND GROUPING(TO_CHAR(order_date, ''YYYY-MM'')) = 1 THEN ''status'' ELSE ''total'' END as dimension, TO_CHAR(order_date, ''YYYY-MM'') as month, status, SUM(CASE WHEN status = ''completed'' THEN total_amount ELSE 0 END) as sales, COUNT(*) as order_count FROM tbl_tmp_orders GROUP BY GROUPING SETS ( (), (TO_CHAR(order_date, ''YYYY-MM'')), (status) ) ) sub ORDER BY CASE dimension WHEN ''total'' THEN 0 WHEN ''monthly'' THEN 1 ELSE 2 END, month NULLS LAST, status NULLS LAST
</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs sql"><span class="hljs-keyword">SELECT</span> dimension, <span class="hljs-keyword">month</span>, status, sales, order_count <span class="hljs-keyword">FROM</span> ( <span class="hljs-keyword">SELECT</span> <span class="hljs-keyword">CASE</span> <span class="hljs-keyword">WHEN</span> <span class="hljs-keyword">GROUPING</span>(TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)) <span class="hljs-operator">=</span> <span class="hljs-number">0</span> <span class="hljs-keyword">AND</span> <span class="hljs-keyword">GROUPING</span>(status) <span class="hljs-operator">=</span> <span class="hljs-number">1</span> <span class="hljs-keyword">THEN</span> <span class="hljs-string">&#x27;monthly&#x27;</span> <span class="hljs-keyword">WHEN</span> <span class="hljs-keyword">GROUPING</span>(status) <span class="hljs-operator">=</span> <span class="hljs-number">0</span> <span class="hljs-keyword">AND</span> <span class="hljs-keyword">GROUPING</span>(TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)) <span class="hljs-operator">=</span> <span class="hljs-number">1</span> <span class="hljs-keyword">THEN</span> <span class="hljs-string">&#x27;status&#x27;</span> <span class="hljs-keyword">ELSE</span> <span class="hljs-string">&#x27;total&#x27;</span> <span class="hljs-keyword">END</span> <span class="hljs-keyword">as</span> dimension, TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>) <span class="hljs-keyword">as</span> <span class="hljs-keyword">month</span>, status, <span class="hljs-built_in">SUM</span>(<span class="hljs-keyword">CASE</span> <span class="hljs-keyword">WHEN</span> status <span class="hljs-operator">=</span> <span class="hljs-string">&#x27;completed&#x27;</span> <span class="hljs-keyword">THEN</span> total_amount <span class="hljs-keyword">ELSE</span> <span class="hljs-number">0</span> <span class="hljs-keyword">END</span>) <span class="hljs-keyword">as</span> sales, <span class="hljs-built_in">COUNT</span>(<span class="hljs-operator">*</span>) <span class="hljs-keyword">as</span> order_count <span class="hljs-keyword">FROM</span> tbl_tmp_orders <span class="hljs-keyword">GROUP</span> <span class="hljs-keyword">BY</span> <span class="hljs-keyword">GROUPING SETS</span> ( (), (TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)), (status) ) ) sub <span class="hljs-keyword">ORDER</span> <span class="hljs-keyword">BY</span> <span class="hljs-keyword">CASE</span> dimension <span class="hljs-keyword">WHEN</span> <span class="hljs-string">&#x27;total&#x27;</span> <span class="hljs-keyword">THEN</span> <span class="hljs-number">0</span> <span class="hljs-keyword">WHEN</span> <span class="hljs-string">&#x27;monthly&#x27;</span> <span class="hljs-keyword">THEN</span> <span class="hljs-number">1</span> <span class="hljs-keyword">ELSE</span> <span class="hljs-number">2</span> <span class="hljs-keyword">END</span>, <span class="hljs-keyword">month</span> <span class="hljs-keyword">NULLS LAST</span>, status <span class="hljs-keyword">NULLS LAST</span>
</code></pre>执行SQL完成<br>SQL查询结果：<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:29:05.183719') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3763, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">执行计划</div>
      <div class="agent-response-content"><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">json</span><span hidden>{
  "thought_process": "用户请求分析销售额数据。我检查了提供的schema，只有一张表tbl_tmp_orders，包含total_amount、order_date、status、user_id等字段。没有地区或渠道维度。因此，我将计划从该表中提取基本销售额汇总，包括总销售额、按时间（月度）趋势、按订单状态分类统计，最后生成总结报告。",
  "execution_plan": [
    {
      "step": 1,
      "tool_to_use": "SQL_GENERATE_NODE",
      "tool_parameters": {
        "instruction": "从 tbl_tmp_orders 表中查询销售额分析核心数据：1. 总销售额（total_amount之和）；2. 按月份（从order_date提取年-月）分组的月度销售额；3. 按status分组的销售额和订单数。只包含状态为''completed''的订单用于销售额统计，但订单数包含所有状态以了解整体结构。"
      }
    },
    {
      "step": 2,
      "tool_to_use": "REPORT_GENERATOR_NODE",
      "tool_parameters": {
        "summary_and_recommendations": "基于步骤1提取的销售额数据，总结总销售额、月度趋势（是否有明显波动或增长/下降）、各订单状态的分布情况。给出商业建议：例如，如果月度销售额持续增长，建议保持当前策略；如果存在季节性波动，建议提前备货或调整营销活动。"
      }
    }
  ]
}</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs json"><span class="hljs-punctuation">{</span>
  <span class="hljs-attr">&quot;thought_process&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-string">&quot;用户请求分析销售额数据。我检查了提供的schema，只有一张表tbl_tmp_orders，包含total_amount、order_date、status、user_id等字段。没有地区或渠道维度。因此，我将计划从该表中提取基本销售额汇总，包括总销售额、按时间（月度）趋势、按订单状态分类统计，最后生成总结报告。&quot;</span><span class="hljs-punctuation">,</span>
  <span class="hljs-attr">&quot;execution_plan&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-punctuation">[</span>
    <span class="hljs-punctuation">{</span>
      <span class="hljs-attr">&quot;step&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-number">1</span><span class="hljs-punctuation">,</span>
      <span class="hljs-attr">&quot;tool_to_use&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-string">&quot;SQL_GENERATE_NODE&quot;</span><span class="hljs-punctuation">,</span>
      <span class="hljs-attr">&quot;tool_parameters&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-punctuation">{</span>
        <span class="hljs-attr">&quot;instruction&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-string">&quot;从 tbl_tmp_orders 表中查询销售额分析核心数据：1. 总销售额（total_amount之和）；2. 按月份（从order_date提取年-月）分组的月度销售额；3. 按status分组的销售额和订单数。只包含状态为&#x27;completed&#x27;的订单用于销售额统计，但订单数包含所有状态以了解整体结构。&quot;</span>
      <span class="hljs-punctuation">}</span>
    <span class="hljs-punctuation">}</span><span class="hljs-punctuation">,</span>
    <span class="hljs-punctuation">{</span>
      <span class="hljs-attr">&quot;step&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-number">2</span><span class="hljs-punctuation">,</span>
      <span class="hljs-attr">&quot;tool_to_use&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-string">&quot;REPORT_GENERATOR_NODE&quot;</span><span class="hljs-punctuation">,</span>
      <span class="hljs-attr">&quot;tool_parameters&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-punctuation">{</span>
        <span class="hljs-attr">&quot;summary_and_recommendations&quot;</span><span class="hljs-punctuation">:</span> <span class="hljs-string">&quot;基于步骤1提取的销售额数据，总结总销售额、月度趋势（是否有明显波动或增长/下降）、各订单状态的分布情况。给出商业建议：例如，如果月度销售额持续增长，建议保持当前策略；如果存在季节性波动，建议提前备货或调整营销活动。&quot;</span>
      <span class="hljs-punctuation">}</span>
    <span class="hljs-punctuation">}</span>
  <span class="hljs-punctuation">]</span>
<span class="hljs-punctuation">}</span></code></pre></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:27:58.53487') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3764, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">生成SQL</div>
      <div class="agent-response-content">开始生成SQL...<br><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">sql</span><span hidden>SELECT
    CASE
        WHEN GROUPING(TO_CHAR(order_date, ''YYYY-MM'')) = 0 AND GROUPING(status) = 1 THEN ''monthly''
        WHEN GROUPING(status) = 0 AND GROUPING(TO_CHAR(order_date, ''YYYY-MM'')) = 1 THEN ''status''
        ELSE ''total''
    END as dimension,
    TO_CHAR(order_date, ''YYYY-MM'') as month,
    status,
    SUM(CASE WHEN status = ''completed'' THEN total_amount ELSE 0 END) as sales,
    COUNT(*) as order_count
FROM tbl_tmp_orders
GROUP BY GROUPING SETS (
    (),
    (TO_CHAR(order_date, ''YYYY-MM'')),
    (status)
)
ORDER BY
    CASE dimension
        WHEN ''total'' THEN 0
        WHEN ''monthly'' THEN 1
        ELSE 2
    END,
    month NULLS LAST,
    status NULLS LAST</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs sql"><span class="hljs-keyword">SELECT</span>
    <span class="hljs-keyword">CASE</span>
        <span class="hljs-keyword">WHEN</span> <span class="hljs-keyword">GROUPING</span>(TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)) <span class="hljs-operator">=</span> <span class="hljs-number">0</span> <span class="hljs-keyword">AND</span> <span class="hljs-keyword">GROUPING</span>(status) <span class="hljs-operator">=</span> <span class="hljs-number">1</span> <span class="hljs-keyword">THEN</span> <span class="hljs-string">&#x27;monthly&#x27;</span>
        <span class="hljs-keyword">WHEN</span> <span class="hljs-keyword">GROUPING</span>(status) <span class="hljs-operator">=</span> <span class="hljs-number">0</span> <span class="hljs-keyword">AND</span> <span class="hljs-keyword">GROUPING</span>(TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)) <span class="hljs-operator">=</span> <span class="hljs-number">1</span> <span class="hljs-keyword">THEN</span> <span class="hljs-string">&#x27;status&#x27;</span>
        <span class="hljs-keyword">ELSE</span> <span class="hljs-string">&#x27;total&#x27;</span>
    <span class="hljs-keyword">END</span> <span class="hljs-keyword">as</span> dimension,
    TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>) <span class="hljs-keyword">as</span> <span class="hljs-keyword">month</span>,
    status,
    <span class="hljs-built_in">SUM</span>(<span class="hljs-keyword">CASE</span> <span class="hljs-keyword">WHEN</span> status <span class="hljs-operator">=</span> <span class="hljs-string">&#x27;completed&#x27;</span> <span class="hljs-keyword">THEN</span> total_amount <span class="hljs-keyword">ELSE</span> <span class="hljs-number">0</span> <span class="hljs-keyword">END</span>) <span class="hljs-keyword">as</span> sales,
    <span class="hljs-built_in">COUNT</span>(<span class="hljs-operator">*</span>) <span class="hljs-keyword">as</span> order_count
<span class="hljs-keyword">FROM</span> tbl_tmp_orders
<span class="hljs-keyword">GROUP</span> <span class="hljs-keyword">BY</span> <span class="hljs-keyword">GROUPING SETS</span> (
    (),
    (TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)),
    (status)
)
<span class="hljs-keyword">ORDER</span> <span class="hljs-keyword">BY</span>
    <span class="hljs-keyword">CASE</span> dimension
        <span class="hljs-keyword">WHEN</span> <span class="hljs-string">&#x27;total&#x27;</span> <span class="hljs-keyword">THEN</span> <span class="hljs-number">0</span>
        <span class="hljs-keyword">WHEN</span> <span class="hljs-string">&#x27;monthly&#x27;</span> <span class="hljs-keyword">THEN</span> <span class="hljs-number">1</span>
        <span class="hljs-keyword">ELSE</span> <span class="hljs-number">2</span>
    <span class="hljs-keyword">END</span>,
    <span class="hljs-keyword">month</span> <span class="hljs-keyword">NULLS LAST</span>,
    status <span class="hljs-keyword">NULLS LAST</span></code></pre>SQL生成完成，准备执行<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:28:37.286022') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3765, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">验证SQL</div>
      <div class="agent-response-content">开始语义一致性校验<br>通过语义一致性校验完成<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:28:44.659978') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3766, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">执行SQL</div>
      <div class="agent-response-content">开始执行SQL...<br>执行SQL查询：<br><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">sql</span><span hidden>SELECT
    CASE
        WHEN GROUPING(TO_CHAR(order_date, ''YYYY-MM'')) = 0 AND GROUPING(status) = 1 THEN ''monthly''
        WHEN GROUPING(status) = 0 AND GROUPING(TO_CHAR(order_date, ''YYYY-MM'')) = 1 THEN ''status''
        ELSE ''total''
    END as dimension,
    TO_CHAR(order_date, ''YYYY-MM'') as month,
    status,
    SUM(CASE WHEN status = ''completed'' THEN total_amount ELSE 0 END) as sales,
    COUNT(*) as order_count
FROM tbl_tmp_orders
GROUP BY GROUPING SETS (
    (),
    (TO_CHAR(order_date, ''YYYY-MM'')),
    (status)
)
ORDER BY
    CASE dimension
        WHEN ''total'' THEN 0
        WHEN ''monthly'' THEN 1
        ELSE 2
    END,
    month NULLS LAST,
    status NULLS LAST
</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs sql"><span class="hljs-keyword">SELECT</span>
    <span class="hljs-keyword">CASE</span>
        <span class="hljs-keyword">WHEN</span> <span class="hljs-keyword">GROUPING</span>(TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)) <span class="hljs-operator">=</span> <span class="hljs-number">0</span> <span class="hljs-keyword">AND</span> <span class="hljs-keyword">GROUPING</span>(status) <span class="hljs-operator">=</span> <span class="hljs-number">1</span> <span class="hljs-keyword">THEN</span> <span class="hljs-string">&#x27;monthly&#x27;</span>
        <span class="hljs-keyword">WHEN</span> <span class="hljs-keyword">GROUPING</span>(status) <span class="hljs-operator">=</span> <span class="hljs-number">0</span> <span class="hljs-keyword">AND</span> <span class="hljs-keyword">GROUPING</span>(TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)) <span class="hljs-operator">=</span> <span class="hljs-number">1</span> <span class="hljs-keyword">THEN</span> <span class="hljs-string">&#x27;status&#x27;</span>
        <span class="hljs-keyword">ELSE</span> <span class="hljs-string">&#x27;total&#x27;</span>
    <span class="hljs-keyword">END</span> <span class="hljs-keyword">as</span> dimension,
    TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>) <span class="hljs-keyword">as</span> <span class="hljs-keyword">month</span>,
    status,
    <span class="hljs-built_in">SUM</span>(<span class="hljs-keyword">CASE</span> <span class="hljs-keyword">WHEN</span> status <span class="hljs-operator">=</span> <span class="hljs-string">&#x27;completed&#x27;</span> <span class="hljs-keyword">THEN</span> total_amount <span class="hljs-keyword">ELSE</span> <span class="hljs-number">0</span> <span class="hljs-keyword">END</span>) <span class="hljs-keyword">as</span> sales,
    <span class="hljs-built_in">COUNT</span>(<span class="hljs-operator">*</span>) <span class="hljs-keyword">as</span> order_count
<span class="hljs-keyword">FROM</span> tbl_tmp_orders
<span class="hljs-keyword">GROUP</span> <span class="hljs-keyword">BY</span> <span class="hljs-keyword">GROUPING SETS</span> (
    (),
    (TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)),
    (status)
)
<span class="hljs-keyword">ORDER</span> <span class="hljs-keyword">BY</span>
    <span class="hljs-keyword">CASE</span> dimension
        <span class="hljs-keyword">WHEN</span> <span class="hljs-string">&#x27;total&#x27;</span> <span class="hljs-keyword">THEN</span> <span class="hljs-number">0</span>
        <span class="hljs-keyword">WHEN</span> <span class="hljs-string">&#x27;monthly&#x27;</span> <span class="hljs-keyword">THEN</span> <span class="hljs-number">1</span>
        <span class="hljs-keyword">ELSE</span> <span class="hljs-number">2</span>
    <span class="hljs-keyword">END</span>,
    <span class="hljs-keyword">month</span> <span class="hljs-keyword">NULLS LAST</span>,
    status <span class="hljs-keyword">NULLS LAST</span>
</code></pre>SQL执行失败: ERROR: column "dimension" does not exist<br>  位置：545<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:28:44.687583') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3767, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">生成SQL</div>
      <div class="agent-response-content">检测到SQL执行异常，开始重新生成SQL...<br><pre><div style="display: flex; justify-content: space-between; align-items: center; background: #f8f9fa; padding: 8px 12px; border-bottom: none; font-family: system-ui, sans-serif; font-size: 14px;"><span style="color: #666;">sql</span><span hidden>SELECT dimension, month, status, sales, order_count FROM ( SELECT CASE WHEN GROUPING(TO_CHAR(order_date, ''YYYY-MM'')) = 0 AND GROUPING(status) = 1 THEN ''monthly'' WHEN GROUPING(status) = 0 AND GROUPING(TO_CHAR(order_date, ''YYYY-MM'')) = 1 THEN ''status'' ELSE ''total'' END as dimension, TO_CHAR(order_date, ''YYYY-MM'') as month, status, SUM(CASE WHEN status = ''completed'' THEN total_amount ELSE 0 END) as sales, COUNT(*) as order_count FROM tbl_tmp_orders GROUP BY GROUPING SETS ( (), (TO_CHAR(order_date, ''YYYY-MM'')), (status) ) ) sub ORDER BY CASE dimension WHEN ''total'' THEN 0 WHEN ''monthly'' THEN 1 ELSE 2 END, month NULLS LAST, status NULLS LAST</span><button onclick=''copyTextToClipboard(this)'' style="background: #f8f9fa; border: none; padding: 4px 12px; border-radius: 12px; font-size: 13px; cursor: pointer; transition: background 0.2s;">复制</button></div><code class="hljs sql"><span class="hljs-keyword">SELECT</span> dimension, <span class="hljs-keyword">month</span>, status, sales, order_count <span class="hljs-keyword">FROM</span> ( <span class="hljs-keyword">SELECT</span> <span class="hljs-keyword">CASE</span> <span class="hljs-keyword">WHEN</span> <span class="hljs-keyword">GROUPING</span>(TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)) <span class="hljs-operator">=</span> <span class="hljs-number">0</span> <span class="hljs-keyword">AND</span> <span class="hljs-keyword">GROUPING</span>(status) <span class="hljs-operator">=</span> <span class="hljs-number">1</span> <span class="hljs-keyword">THEN</span> <span class="hljs-string">&#x27;monthly&#x27;</span> <span class="hljs-keyword">WHEN</span> <span class="hljs-keyword">GROUPING</span>(status) <span class="hljs-operator">=</span> <span class="hljs-number">0</span> <span class="hljs-keyword">AND</span> <span class="hljs-keyword">GROUPING</span>(TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)) <span class="hljs-operator">=</span> <span class="hljs-number">1</span> <span class="hljs-keyword">THEN</span> <span class="hljs-string">&#x27;status&#x27;</span> <span class="hljs-keyword">ELSE</span> <span class="hljs-string">&#x27;total&#x27;</span> <span class="hljs-keyword">END</span> <span class="hljs-keyword">as</span> dimension, TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>) <span class="hljs-keyword">as</span> <span class="hljs-keyword">month</span>, status, <span class="hljs-built_in">SUM</span>(<span class="hljs-keyword">CASE</span> <span class="hljs-keyword">WHEN</span> status <span class="hljs-operator">=</span> <span class="hljs-string">&#x27;completed&#x27;</span> <span class="hljs-keyword">THEN</span> total_amount <span class="hljs-keyword">ELSE</span> <span class="hljs-number">0</span> <span class="hljs-keyword">END</span>) <span class="hljs-keyword">as</span> sales, <span class="hljs-built_in">COUNT</span>(<span class="hljs-operator">*</span>) <span class="hljs-keyword">as</span> order_count <span class="hljs-keyword">FROM</span> tbl_tmp_orders <span class="hljs-keyword">GROUP</span> <span class="hljs-keyword">BY</span> <span class="hljs-keyword">GROUPING SETS</span> ( (), (TO_CHAR(order_date, <span class="hljs-string">&#x27;YYYY-MM&#x27;</span>)), (status) ) ) sub <span class="hljs-keyword">ORDER</span> <span class="hljs-keyword">BY</span> <span class="hljs-keyword">CASE</span> dimension <span class="hljs-keyword">WHEN</span> <span class="hljs-string">&#x27;total&#x27;</span> <span class="hljs-keyword">THEN</span> <span class="hljs-number">0</span> <span class="hljs-keyword">WHEN</span> <span class="hljs-string">&#x27;monthly&#x27;</span> <span class="hljs-keyword">THEN</span> <span class="hljs-number">1</span> <span class="hljs-keyword">ELSE</span> <span class="hljs-number">2</span> <span class="hljs-keyword">END</span>, <span class="hljs-keyword">month</span> <span class="hljs-keyword">NULLS LAST</span>, status <span class="hljs-keyword">NULLS LAST</span></code></pre>SQL生成完成，准备执行<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:28:55.498123') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3768, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">验证SQL</div>
      <div class="agent-response-content">开始语义一致性校验<br>通过语义一致性校验完成<br></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:29:02.162322') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3770, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">执行SQL</div>
      <div class="agent-response-content"></div>
    </div>
  ', 'html', NULL, '2026-07-11T10:29:05.242361') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3771, '93e56f1b-514d-4d8b-a74f-a359b17072ed', 'assistant', '# 销售额分析报告

## 1. 执行摘要

- **总销售额**：11,142.00 元（仅统计已完成订单）
- **数据时间范围**：仅有 2025 年 6 月的数据，无跨月趋势
- **订单状态分布**：已完成订单 6 笔，占总订单数 60%；取消订单 1 笔，待处理 3 笔
- **核心结论**：当前数据量有限，无法判断月度趋势或季节性波动。已完成订单贡献了全部销售额，取消与待处理订单均无销售金额。

## 2. 分析背景与用户诉求

用户需求为“分析销售额数据”。由于数据源 `tbl_tmp_orders` 仅包含 `total_amount`、`order_date`、`status`、`user_id` 等字段，且无地区或渠道维度，分析聚焦于：
- 整体销售额规模
- 按月销售额变化（但因仅有一个月数据，实际无法分析趋势）
- 按订单状态分类的销售额与订单数分布

## 3. 分析过程与数据

### 3.1 数据查询

执行 SQL 查询，从 `tbl_tmp_orders` 中提取总销售额、月度销售额、状态分类统计。仅 `status = ''completed''` 的订单计入销售额，订单数统计所有状态。

### 3.2 执行结果

| 维度 | 月份 | 状态 | 销售额（元） | 订单数 |
|------|------|------|-------------|--------|
| total |      |      | 11,142.00   | 10     |
| monthly | 2025-06 |      | 11,142.00   | 10     |
| status |      | cancelled | 0           | 1      |
| status |      | completed | 11,142.00   | 6      |
| status |      | pending   | 0           | 3      |

*注：月度数据仅 2025 年 6 月，总销售额与该月度数值一致，说明整个数据集仅包含该月订单。*

## 4. 结果解读与洞察

### 4.1 总体销售情况

- 总销售额 11,142.00 元，订单总数 10 笔，平均客单价约 1,114.20 元（基于已完成的 6 笔订单计算，若包含所有订单则更低）。
- 已完成订单 6 笔，销售额 11,142.00 元，说明完全由已完成订单贡献；取消和待处理订单均无销售金额，表明这些状态下的订单未产生收入。

### 4.2 订单状态分布（无金额）

```echarts
{
    "title": { "text": "订单数按状态分布" },
    "tooltip": { "trigger": "item", "formatter": "{b}: {c} 单 ({d}%)" },
    "series": [
        {
            "type": "pie",
            "radius": ["40%", "70%"],
            "center": ["50%", "50%"],
            "data": [
                { "name": "已完成", "value": 6 },
                { "name": "取消", "value": 1 },
                { "name": "待处理", "value": 3 }
            ],
            "label": { "show": true, "formatter": "{b}: {c}" }
        }
    ]
}
```

- 已完成订单占比 60%，取消 10%，待处理 30%。有 3 笔待处理订单未转化，需关注后续跟进或催付流程。

### 4.3 月度趋势

由于仅有一个月（2025 年 6 月）的数据，无法计算变化率或判断增长/下降趋势。若未来补充多个月度数据，可绘制趋势图。

## 5. 建议与后续行动

1. **补充数据**：建议提取连续 3 个月以上的完整数据，以识别月度销售趋势和季节性波动。
2. **优化订单转化**：针对 3 笔待处理订单，分析原因（如库存不足、支付流程卡顿等），提升转化率。
3. **监控客单价**：当前平均客单价约 1,114 元，可进一步拆解商品品类，评估定价策略。
4. **扩展分析维度**：若存在用户 ID，可进行用户复购分析；若后续增加渠道或地区字段，可细化归因。

---

报告生成时间：2025-07-11， 生成人：军哥', 'markdown-report', NULL, '2026-07-11T10:29:15.440695') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3772, '390af8ae-3939-405f-b615-adbcddb37cc0', 'user', '帮我分析一下本月的流程审批量  按天统计', 'text', NULL, '2026-07-11T10:36:39.212005') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3773, '390af8ae-3939-405f-b615-adbcddb37cc0', 'user', '帮我分析一下本月的流程审批量 按天统计', 'text', NULL, '2026-07-11T10:38:46.976881') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3774, '390af8ae-3939-405f-b615-adbcddb37cc0', 'assistant', '好的，我来统计本月按天统计的流程发起量（即本月每天启动了多少个流程实例）。好的，以下是本月（2026年7月）按天统计的流程审批发起量：

## 📊 本月流程审批量统计（按天）

| 日期 | 流程发起数 |
|:---:|:--------:|
| 7月1日 | 1 |
| 7月2日 | **15** 🔥 |
| 7月3日 | 4 |
| 7月9日 | 9 |
| 7月10日 | 5 |

**📈 简要分析：**
- **本月累计发起流程数**：**34个**
- **日均发起量**：约 4.9 个/天（按有数据的日期计）
- **峰值日**：**7月2日**（15个），是其他日期的数倍，可能为批量提交或业务高峰期
- **无数据日期**：7月4日~7月8日（可能为周末或无流程发起）

> 如果需要换成统计**已完成**的流程实例（即 `END_TIME_` 不为空），或者想加上**按周 / 按小时**的更细粒度分析，也可以告诉我！', 'markdown-report', NULL, '2026-07-11T10:38:58.493121') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3775, 'b5fdeb5f-aa23-4e85-be9c-8a08aa1c24ee', 'user', '请假流程', 'text', NULL, '2026-07-11T10:41:53.1275') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3776, 'b5fdeb5f-aa23-4e85-be9c-8a08aa1c24ee', 'assistant', '好的，我先为您查询公司的请假制度规定。已查询到公司《员工休假管理规定》中的请假流程，为您梳理如下：

---

## 📋 请假通用流程

### 1️⃣ 基本原则
> 所有休假均**必须提前办理请假手续**，经批准后方可休假。如因特殊情况在外地不便办理，应**先电话请假，后补办手续**。

### 2️⃣ 审批权限
| 请假天数 | 审批人 |
|---------|--------|
| **1天及以内** | **班长**审批 |
| **1天以上** | **分公司（部门）经理**审批 |

### 3️⃣ 请假类型及对应规定
| 假别 | 核心要点 |
|------|---------|
| **带薪年休假** | 工龄满1年可休，满1年≤10年→5天；满10年≤20年→10天；20年以上→15天 |
| **婚假** | 3天 |
| **丧假** | 直系亲属去世→3天 |
| **产假** | 基础90天+奖励60天；男方护理假20天 |
| **病假** | 需凭医院证明 |
| **事假** | 每年累计不超过90天 |

> ⚠️ 除带薪年休假外，其他休假均连续计算（含法定节假日）。

---

### 🔹 关于领导人员请假（补充说明）
如果您是中层及以上领导，请假1天以上或离开工作区域，还需向相应主管领导报告并履行网上审批流程。

---

请问您具体想请哪种类型的假？我可以进一步帮您确认具体所需材料和操作步骤 👇

[查看年休假详情] [查看病事假规定] [申请请假（转工单）] [转接人工咨询]', 'markdown-report', NULL, '2026-07-11T10:42:02.142969') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3805, '0e1a3407-6753-4411-ab4b-ed84809f52d4', 'user', '报诗', 'text', NULL, '2026-09-27T12:09:53.29468') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3806, '0e1a3407-6753-4411-ab4b-ed84809f52d4', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">Harness</div>
      <div class="agent-response-content"><div class="markdown-report"><p>The user''s message is "报诗" which is... odd. It seems like a typo or meaningless input? "报诗" could be a typo of "报时" (tell the time)? Or "保时"? Or maybe it means "报诗" = recite a poem?</p>
<p>The rules say: if user input is ambiguous, ask first. Also nonsense input like "wwwwwwwww" should be politely redirected.</p>
<p>"报诗" - literally "report poem" / "recite poem". It''s ambiguous. Given the persona is a customer service assistant, the appropriate response is to politely ask for clarification, or possibly interpret it as "请报诗" = recite a poem?</p>
<p>Hmm. This is likely a test of the "ambiguous input → clarify" rule. But it could also be a request to recite a poem. "报诗" is not a standard phrase. Possibly typo for "报时"?</p>
<p>Best: friendly clarification, offering options: 查询订单/退款/ recite a poem. Keep it short, Chinese.</p>
<p>I shouldn''t call any tools. Also maybe memory_search? Not needed — no prior work question. Actually the instructions say to search memory before questions about prior work — this isn''t that.</p>
<p>I could optionally search memory for "报诗" preference... It might be a recurring thing in prior sessions. Hmm, could be worth a quick memory_search in case the user has a preference for poetry. Let me do a quick memory_search for "诗" to check. That''s cheap and could be relevant. But guidance says avoid unnecessary. I''ll do one quick search — it''s plausible the user previously asked for poems.</p>
<p>Actually, let''s just do it: memory_search "诗" — if nothing, respond with clarification.</p>
</div></div>
    </div>
  ', 'html', NULL, '2026-09-27T12:09:55.943183') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3807, '0e1a3407-6753-4411-ab4b-ed84809f52d4', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">Harness</div>
      <div class="agent-response-content"><div class="markdown-report"><p>No memory. So respond with clarification. "报诗" ambiguous — ask what the user needs, offer options: 订单查询、退款、或者想让我念首诗？</p>
<p>Keep natural, friendly, Chinese. Don''t mention tool names.您好，我这边没太理解「报诗」的意思，不敢贸然操作，想先跟您确认一下 😊</p>
<p>您是想让我：</p>
<ol>
<li><strong>念/推荐一首诗</strong> —— 如果是这个，请告诉我想要古诗还是现代诗，我就为您读一段；</li>
<li><strong>查询订单</strong>（订单状态、物流进度）—— 请把订单号发我；</li>
<li><strong>申请退款/退货</strong> —— 请把订单号发我，我为您走退款流程；</li>
<li>还是「报时」之类的其他需求？直接说一声就好。</li>
</ol>
<p>麻烦您补充一下具体想做什么，我马上为您处理～</p>
</div></div>
    </div>
  ', 'html', NULL, '2026-09-27T12:09:58.724786') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3808, 'c9e3b744-ef6a-4b6b-b6f0-7669b4d2900e', 'user', '报诗', 'text', NULL, '2026-09-27T12:10:24.519679') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3809, 'c9e3b744-ef6a-4b6b-b6f0-7669b4d2900e', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">Harness</div>
      <div class="agent-response-content"><div class="markdown-report"><p>The user said "报诗" which triggers the skill phx-poem-weather.</p>
<p>Let me load the skill.【SKILL_OK_2026】天气如诗：春风又绿江南岸，明月何时照我还。</p>
</div></div>
    </div>
  ', 'html', NULL, '2026-09-27T12:10:26.364012') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3810, 'b5fdeb5f-aa23-4e85-be9c-8a08aa1c24ee', 'user', '你能做些什么 ？', 'text', NULL, '2026-09-27T12:12:43.945053') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3811, 'b5fdeb5f-aa23-4e85-be9c-8a08aa1c24ee', 'assistant', '我是爱力优公司的**制度服务智能体**，专门帮你处理公司制度相关的事务。简单说，我能做这几类事：

**1. 制度查询**
- 年假、调休、考勤、报销、绩效等各类规章制度，我都能帮你查（会实时检索公司文档库，不凭记忆瞎说）

**2. 业务办理引导**
- 报销申请、申诉发起、审批/特批流程等，我帮你调取对应表单并引导填写

**3. 争议与违规处理**
- 如果你被处罚想申诉，我可以调取记录、判断能否自动处理；涉及**开除、降薪等红线事项**，我会直接帮你转接专员，不自行处理

**4. 数据查询（需权限校验）**
- 员工档案、考勤记录、薪酬明细等

**5. 转人工**
- 遇到复杂或情绪化的问题，我会把对话记录打包，无缝交接给 HR / 法务 / 业务 BP

---

你可以直接告诉我你的诉求，比如：
- “年假有几天？”
- “我要申请报销”
- “我对这个处罚有异议，想申诉”

[查年假制度] [发起报销] [发起申诉] [转接人工]

请问你现在想处理什么事？', 'markdown-report', NULL, '2026-09-27T12:12:46.545225') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3812, 'b5fdeb5f-aa23-4e85-be9c-8a08aa1c24ee', 'user', '年假有几天', 'text', NULL, '2026-09-27T12:12:58.983545') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3813, 'b5fdeb5f-aa23-4e85-be9c-8a08aa1c24ee', 'assistant', '我帮你查一下公司的年假制度，稍等～工具调用失败了，我重试一次～抱歉，公司制度库的检索工具这次连不上——我已经自动重试过一次，仍然失败，所以**目前我无法确认年假的具体天数**。

按照流程，我不能凭记忆给你一个数字（避免信息不准确），这种情况我会帮你转人工核实。下面是本次的结构化摘要：

---

**📋 待人工处理摘要**
- **用户诉求**：查询公司年假天数
- **已尝试动作**：调用制度检索工具 2 次（含 1 次自动重试），均返回执行错误
- **卡点原因**：制度库检索服务暂时不可用，无法获取权威数据
- **处理建议**：请 HR 专员核实《员工手册》/《考勤与休假管理办法》中的年假条款后回复

---

**说明一下**：本次转人工的工单我暂时无法在系统内正常生成（创建工单的工具此刻同样不可用），所以**暂时没有工单编号**。等工具恢复后，我会第一时间为你补建。

你可以选择：
- **稍后再试**：过几分钟我再帮你查一次
- **直接转接人工**：我把上面的记录打包发给 HR 专员跟进

[稍后重试] [转接人工]

你希望怎么处理？', 'markdown-report', NULL, '2026-09-27T12:13:04.116451') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3814, 'c9e3b744-ef6a-4b6b-b6f0-7669b4d2900e', 'user', '斐波那契验证', 'text', NULL, '2026-09-27T12:28:32.715634') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_data_chat_message (id, session_id, role, content, message_type, metadata, create_time) VALUES (3815, 'c9e3b744-ef6a-4b6b-b6f0-7669b4d2900e', 'assistant', '
    <div class="agent-response-block" style="display: block !important; width: 100% !important;">
      <div class="agent-response-title">Harness</div>
      <div class="agent-response-content"><div class="markdown-report"><p>The user says "斐波那契验证" which triggers the py-fib-demo skill. Let me load it.Load the script resource.Write to workspace scripts/fib.py and run python3 scripts/fib.py. Working directory for shell is project dir. Let''s write to workspace? Skill says write to workspace file scripts/fib.py. Shell pwd is the project directory. Relative path scripts/fib.py would resolve under... ambiguous. I''ll write to the workspace path and run with absolute path? Skill says execute python3 scripts/fib.py. Simplest: write file at /Users/bryanchen/Documents/work/ai_workspace/Phoenix-Agent-Java/.agentscope/workspace/scripts/fib.py and run <code>python3 scripts/fib.py</code> with working_directory set to workspace? The execute tool''s working_directory is "relative to workspace root". Hmm, shell pwd is project dir by default. Let me set working_directory to ".agentscope/workspace" — that resolves under project. Then run python3 scripts/fib.py.</p>
<p>Write the file with write_file using relative path "scripts/fib.py" (resolves under workspace). Then execute with working_directory ".agentscope/workspace".</p>
<p>Wait, working_directory is relative to workspace root per description. That could be ambiguous. Let''s just try.The file was written where? write_file resolved relative to workspace... but maybe it wrote to the project? Let''s check.Hmm, so where did write_file write? Maybe write_file wrote to workspace root with path scripts/fib.py... it said "Written to scripts/fib.py". Let''s find it.</p>
<p>Note working_directory was "." in the shell — but pwd is the project dir. Both dirs don''t have scripts/. Maybe working_directory "." resolved relative to workspace root, which is .agentscope/workspace... then .agentscope/workspace/scripts would be workspace/.agentscope/workspace/scripts. Confusing. Let me search.The write_file wrote to a sandboxed subdir: workspace/461671036765859840/scripts/fib.py (probably a session-scoped sandbox). And shell working_directory "." = project dir.</p>
<p>Simplest: copy it via shell to project scripts/fib.py? Better: run python3 with the actual found path. But the skill says run <code>python3 scripts/fib.py</code>. I''ll materialize scripts/fib.py in the project dir by writing via shell (heredoc) — but the write_file tool is sandboxed. Actually the sandbox root appears to be .../workspace/. Shell cwd is project dir. So I can copy the file into the project dir with shell, then run python3 scripts/fib.py relative to project dir. That satisfies the literal command.</p>
<p>Alternatively just run absolute path of the sandboxed file. To keep the command as <code>python3 scripts/fib.py</code> per skill, copy first.</p>
<p>Hmm, but writing into the project source tree may be undesirable. The skill''s intent is just to run the script. I''ll do: mkdir -p scripts &amp;&amp; cp from sandbox, then python3 scripts/fib.py in project dir. Let me do that.已按 <code>py-fib-demo</code> 技能执行完毕：</p>
<ol>
<li>加载技能脚本资源 <code>scripts/fib.py</code>（来自 skill 缓存目录）</li>
<li>将脚本原样落盘为 <code>scripts/fib.py</code>（实际写入沙箱路径 <code>.agentscope/workspace/461671036765859840/scripts/fib.py</code>，再复制到项目根 <code>scripts/fib.py</code>）</li>
<li>执行 <code>python3 scripts/fib.py</code></li>
</ol>
<p>脚本标准输出（原样）：</p>
<pre><code>FIB_SKILL_RAN=6765
</code></pre>
<p><code>fib(20) = 6765</code>，校验通过。</p>
</div></div>
    </div>
  ', 'html', NULL, '2026-09-27T12:28:44.414081') ON CONFLICT (id) DO NOTHING;
-- tbl_vector_store_user_memory：3 行
INSERT INTO tbl_vector_store_user_memory (id, content, metadata, embedding) VALUES ('df43ee7e-2b72-4b9e-9676-37e852599901', '请求按天统计本月流程审批量', '{"userId": "428011841386577920", "agentSn": "BpmReactAgent", "createdAt": 1783737532114}', '[-0.06755071,-0.0067379167,-0.03949353,0.014648059,0.00765759,-0.0013973791,0.03623417,0.05413206,0.045021098,0.09072838,0.022396186,0.09537917,0.039950985,0.027275696,-0.057601087,0.023234852,-0.010321306,-0.09774268,-0.050358064,0.085696384,0.053788967,-0.0044149077,0.015886998,0.030935328,0.08699251,0.032117084,-0.07307828,0.015896527,0.025960516,0.008777399,-0.03066848,-0.06995235,0.018155206,-0.017087813,0.04040844,0.020756977,-0.016582709,0.045554798,0.08562014,0.009706602,-0.01270388,0.0113886995,0.04971,0.0072239614,0.026665756,0.042924434,-0.08988971,-0.046889037,-0.015038801,-0.09964874,0.012284546,-0.043191284,-0.07056228,0.025045607,-0.016887678,-0.0352049,-0.007995915,-0.0039360104,0.038578622,0.00766712,0.054360785,0.094769225,-0.0115602445,-0.035986383,0.012027228,0.040980257,0.0024921715,0.004719877,-0.011607896,-0.05489448,0.004793737,0.02352076,-0.020852279,0.01631586,0.022948943,0.058020417,-0.009215793,0.06263308,0.0063757654,-0.07936827,-0.037454046,0.01961334,-0.03514772,0.060612656,0.08279918,0.06133696,-0.022453368,-0.07010483,0.066978894,0.11070387,0.0048104147,0.00018107555,-0.012932606,-0.011693669,0.04437304,-0.029543906,-0.020623552,0.0352049,-0.039722256,0.06419605,-0.046469703,0.048375763,-0.011893804,0.00028575986,0.027847514,-0.008877467,-0.0057181753,0.018183798,-0.010321306,0.049061943,0.011703199,-0.051387336,0.0018750849,0.032059904,-0.015372361,0.022739276,-0.010168822,0.012379849,0.017383253,0.027675968,-0.037739955,0.015057862,0.013447242,-0.041628316,0.0061136824,-0.015886998,-0.0042052413,-0.049328793,0.01925119,-0.0034690264,-0.08066439,-0.030191965,-0.026894484,-0.027923755,0.023806669,0.08790741,0.053369638,0.0041385293,-0.09736147,0.02020422,0.014209665,-0.05066303,0.03941729,-0.052340366,0.04269571,0.027942816,-0.06991422,-0.042886313,-0.019708645,-0.016039481,-0.05123485,-0.014428862,-0.042314496,0.067245744,0.017383253,-0.052759696,0.005255956,0.000721324,-0.030801903,0.027980939,-0.003309394,0.010187882,0.05588563,-0.033832535,0.03127842,-0.027161332,0.066978894,-0.0060850917,-0.025064668,-0.05843975,0.004162355,-0.004445881,-0.07860585,-0.028609937,0.044563647,0.042543225,0.048756976,0.045173585,0.012484683,-0.10269843,0.019784886,0.00797209,-0.063052416,0.024664396,0.0064329472,-0.055313814,0.061641928,-0.00042796967,-0.02352076,0.061908778,0.0022777398,0.0855439,0.02676106,0.00359292,-0.06259496,-0.012761061,-0.06964737,0.08973723,0.15858406,0.046774674,0.036939412,-0.005494213,-0.00439823,0.008038801,0.057829812,0.0028471749,0.08455275,0.004307692,0.038845472,-0.023120489,0.009025186,-0.012665758,-0.004877127,0.052378487,-0.004591218,0.0033546628,-0.038025867,0.025102789,-0.04605037,0.041552074,0.032479234,0.09919128,-0.034213748,0.011312457,-0.026646696,-0.00554663,-0.032498296,0.051082365,0.0019989787,-0.020509189,0.02220558,-0.016706603,-0.074869975,0.018174266,0.020108916,-0.082113,0.02542682,0.034137506,-0.066445194,0.044868615,0.0054036756,0.038654864,-0.017726343,-0.004369639,-0.0068046288,-0.0035166778,-0.027523484,0.037987743,0.04037032,0.026932605,-0.10338461,0.04010347,-0.01272294,0.04925255,-0.0044554113,-0.04277195,0.017754935,-0.034537777,0.052835938,0.042162012,-0.06255684,0.04707964,0.023044247,0.026189243,-0.049519397,0.05253097,0.018098025,0.029296119,0.02540776,-0.011626956,-0.006666439,-0.04037032,0.01502927,0.004877127,-0.048413884,0.02150034,-0.067703195,0.019279782,0.017392782,-0.008305649,-0.06335738,0.0009202688,-0.020471068,-0.06831314,-0.01761198,0.05382709,0.047117766,-0.004274336,0.016687542,0.011360108,-0.0081484,0.000873213,-0.03627229,0.0014938733,-0.006599727,-0.015076922,0.048795097,-0.005165418,-0.009968686,0.01239891,-0.03587202,-0.054551393,-0.05706739,0.0074240975,0.015381891,0.07147719,-0.0016392103,-0.13189924,-0.0379115,-0.079063304,-0.003197413,-0.06347174,0.0880599,0.0054751527,0.019203538,-0.111390054,0.04971,0.011550714,-0.017106874,-0.03783526,-0.08165554,0.016716132,0.015763104,0.025484001,0.039531652,-0.007085772,0.025884273,0.023768548,-0.017726343,0.026074879,-0.04925255,0.008977535,0.03263172,-0.0063233487,-0.03619605,0.03686317,-0.010626276,0.020776037,-0.019651463,0.05843975,-0.020375764,0.1527134,0.038063988,-0.018822327,0.04921443,0.02841933,0.038388018,-0.008696391,-0.035033353,-0.0067140907,0.025693668,-0.016801905,-0.03129748,0.021767188,0.0036524844,0.019279782,0.0005057011,0.0053274333,-0.05904969,-0.009253914,0.031812117,-0.01793601,-0.08348536,0.057372358,0.047117766,-0.07803403,0.037168138,0.051997274,-0.033985022,-0.01502927,0.045745403,-0.051463578,-0.023311095,0.026417969,-0.07056228,-0.031831175,0.009482641,-0.06583526,0.010912185,0.016258677,0.06991422,0.059773993,0.029791692,-0.017459495,0.032784205,-0.030001359,-0.079520755,0.017726343,-0.027123211,-0.014991149,0.022758337,0.0030616063,0.0378162,-0.006742682,-0.038254593,0.030382572,0.02315861,-0.009015656,-0.045936007,-0.052797817,-0.020013614,-0.036748808,0.00432437,0.05344588,0.03686317,-0.038597684,0.047117766,-0.019308371,0.0023885295,0.00212049,-0.102393456,-0.023215791,0.09210074,0.03819741,-0.021081006,0.049748126,-0.03591014,-0.011188563,-0.040865894,-0.05962151,-0.06259496,0.04864261,-0.025045607,0.031049691,-0.06228999,0.28636622,0.017497616,-0.07662355,0.0035857724,-0.029124573,-0.029410481,-0.004436351,-0.01791695,0.04269571,0.057296116,-0.008477195,0.038597684,-0.043572497,0.0109503055,0.02222464,-0.02575085,0.018860448,0.025617424,0.021938732,-0.035929203,0.0855439,0.012189244,-0.0033213068,0.03169775,-0.008682096,-0.04971,-0.036348533,0.025007486,-0.033775356,-0.010216474,0.03368005,-0.049519397,-0.015257997,-0.0087535735,0.05577127,0.0489857,-0.017249828,-0.0024540501,0.0058230083,-0.05256909,0.03842614,0.016811436,-0.009882913,-0.019746765,0.005060585,0.029067392,-0.010149761,-0.031430904,-0.034423415,0.015048332,0.033394143,-0.025064668,0.08729748,-0.0009143124,0.02148128,0.037168138,-0.026646696,-0.056991145,0.033470385,-0.0659115,-0.09728522,0.007938733,-0.005508509,0.07189652,-0.02352076,0.0022634445,-0.0397985,0.03162151,0.05081552,-0.027504424,0.031507146,0.012017698,0.062785566,0.033260718,-0.014876786,0.008620149,0.019651463,-0.01698298,0.012846834,0.0039765146,0.0025398228,0.07601361,0.06396732,-0.045745403,-0.043610618,-0.09964874,0.017440435]') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_vector_store_user_memory (id, content, metadata, embedding) VALUES ('5899d3c9-9ec2-479a-b5fc-b459b7408a63', '用户询问请假流程', '{"userId": "428011841386577920", "agentSn": "ZhiduReactAgent", "createdAt": 1783737715703}', '[-0.041335408,-0.08306981,0.055260174,-0.031420495,-0.032278325,-0.007042181,0.081872836,0.105253674,-0.06938443,0.11419106,0.008239152,0.15193556,0.066990495,0.015351157,0.005186876,-0.030941708,-0.02214397,-0.010553297,-0.050911177,0.06838696,0.037644748,0.06471625,0.04333036,0.04293137,0.08809709,0.09376275,-0.085623346,-0.025276044,0.00531156,-0.0089972345,0.011371227,-0.11339308,0.07289555,-0.012418577,0.031161152,0.01772515,-0.009700455,0.020787401,0.046522286,0.017445857,0.010019647,0.0015036952,0.020099143,-0.018493207,0.0075957803,0.006478607,-0.07393292,-0.07664606,-0.024019225,-0.0049923677,0.0395998,0.05749452,0.040158387,0.01502199,0.0035036346,-0.018912146,-0.04883643,0.000621864,0.012687895,-0.011750268,0.019071743,0.073134944,0.007865099,0.020149017,0.0013777638,0.07552889,0.039140962,-0.044846524,-0.014273883,-0.05131017,0.020867199,0.03503136,0.017206462,0.075927876,0.0017318678,-0.020398386,-0.03920081,0.008603231,-0.006817749,-0.037644748,0.01567035,0.08833648,-0.006368885,0.04428794,0.06750918,0.08825668,-0.018114166,-0.0013229026,0.008533408,0.1707679,0.028906856,0.001311681,-0.024517963,0.010443575,0.0708607,-0.028228574,0.039001316,0.037425302,-0.016598001,0.087219305,-0.08793749,-0.023580335,-0.02874726,-0.003139556,0.0500733,0.06463645,-0.02214397,0.02796923,-0.017814923,-0.005256699,-0.023580335,-0.068865746,-0.051110674,0.03878187,0.046123292,0.11099914,-0.010104433,0.015570601,0.03092176,0.061005637,0.020328563,0.0032941648,-0.025914429,-0.02794928,0.029006604,-0.028487917,0.023979325,-0.04632279,0.03002403,0.024857104,-0.028428068,-0.04464703,-0.0068376986,-0.004555972,0.013186634,0.10908398,0.05047229,0.051788956,-0.075608686,-0.05374401,0.021106593,-0.041455105,0.011610622,0.013565674,-0.053185426,0.052467242,-0.050631884,-0.0018390965,-0.011989662,0.026093975,-0.076406665,-0.018872248,-0.011969713,0.0083389,0.011919839,-0.0793193,-0.013735246,0.05577886,-0.008508471,0.056856137,-0.028727312,-0.010712893,0.037205856,-0.015151662,-0.025315942,-0.029944232,0.052347545,0.008697991,0.061564222,-0.016727673,-0.019420858,-0.019769976,-0.009101969,0.054901082,0.04831774,0.024039173,-0.0024812217,0.02788943,-0.058731392,-0.08378799,0.007835175,0.01959043,0.029525291,0.009510934,0.04376925,-0.043051068,0.024956852,-0.019470733,0.104455695,0.064317256,-0.017635377,0.14634968,0.002382721,-0.00020089792,0.06048695,-0.026492964,-0.04412834,0.05952937,0.13964665,0.012807593,0.0002196006,0.00066519185,-0.05673644,-0.021365937,0.007381323,-0.029385645,0.08506476,-0.015869845,0.018742576,-0.004321565,-0.027330844,-0.0019301162,0.056457147,0.00896731,-0.011052035,0.0113612525,-0.06120513,-0.02040836,-0.027191197,-0.03259752,0.0056457147,-0.0010866255,0.002800414,0.026133873,-0.0011701641,0.054462194,-0.040776823,0.026652562,0.015650399,0.013755195,0.024936901,0.03425333,-0.03870207,0.023281092,-0.030762162,-0.045804102,-0.013635498,0.025455588,-0.017016942,0.031041455,-0.012348753,0.04253238,0.01083259,0.008628168,0.0020348511,-0.03832303,-0.019829825,0.015181586,-0.0110221105,0.0014513277,-0.058651593,0.045325313,0.0012399874,0.05577886,-0.01210936,0.004418819,0.017775023,0.014433479,0.015929693,0.03291671,-0.06004806,0.03539045,-0.016867321,-0.013216558,-0.028986655,0.016687775,0.021685129,0.07193797,0.0047679357,-0.030383121,0.0010274004,-0.01273777,0.021844726,-0.0028403131,-0.003782928,0.016328683,-0.014822494,-0.011790168,0.0056806263,-0.023660133,0.014652924,-0.013336255,0.018822374,-0.0069923075,-0.018852297,0.06782837,0.011281454,0.017276285,0.08151375,0.02834827,-0.004553478,0.056018256,-0.034831863,0.024817204,0.022622757,0.07193797,0.022323515,0.0144235045,-0.014782595,-0.01022413,-0.06631221,-0.00083912676,-0.111158736,-0.026832106,0.031161152,0.072177365,-0.01936101,-0.09982741,-0.001851565,-0.06491575,-0.06491575,-0.033455346,0.031240951,0.037744496,0.0026607674,-0.06124503,0.021046745,-0.022024272,0.028507866,0.018004443,-0.16502245,-0.06523494,-0.0020797376,0.03539045,-0.008488521,0.013675397,0.015490804,0.010363776,-0.022163918,-0.02017894,-0.05306573,-0.011111883,-0.0017642858,-0.014672873,0.01313676,0.043529857,0.05326522,0.06519504,-0.041255612,0.0010461031,-0.009091995,0.107408226,0.04959451,-0.04787885,0.00052990916,0.013096861,0.020627806,-0.01921139,-0.062362205,0.019849773,0.058172803,-0.011610622,-0.0008497249,0.044367734,0.012099384,0.019889673,0.032757115,0.0019338568,-0.030363172,-0.007042181,-0.041893996,-0.014313782,-0.1043759,0.011211631,0.045245513,-0.086102135,-0.01583992,-0.018832348,0.0013428521,-0.03205888,0.010892439,-0.017356083,-0.0031445434,0.030363172,-0.026213672,0.030682364,0.082192026,-0.0050422414,0.049554612,-0.02870736,0.010059546,-0.020089168,0.0333157,-0.017994469,0.044008646,0.012119334,0.020508109,-0.018114166,-0.01644838,0.028148774,0.023201294,0.0750501,0.0014051944,-0.0045584654,0.108365804,0.0048552146,0.010274003,-0.054940984,0.0025049117,-0.031380598,4.0795214e-05,0.02044826,-6.230329e-05,0.06882585,-0.028886907,0.02792933,0.036108635,0.040477578,0.0074461587,0.06910514,-0.07117989,-0.029285898,0.047719255,0.09296477,0.037285656,0.0041744374,-0.0029525291,-0.0076007675,-0.070262216,0.0042292983,-0.08362839,0.08362839,-0.018712651,-0.001955053,-0.0710203,0.18481237,-0.0018016911,0.050711684,-0.018553056,0.029984131,-0.019999396,-0.00894736,0.056497045,0.029325796,0.030961657,-0.028088925,-0.023700032,-0.108206205,-0.023959376,-0.03002403,-0.082351625,0.041534904,0.03385434,-0.051908653,0.024358366,0.10541327,0.0015872339,-0.034093734,0.02794928,0.001336618,-0.016667826,-0.01479257,0.0142439585,0.035091206,-0.016179062,0.025196245,0.042811673,-0.021784877,-0.011490924,0.059090484,0.021026796,-0.023021748,-0.021405837,0.06216271,0.007002282,0.027630087,0.07050161,0.0034786977,-0.021525534,0.026133873,0.01669775,0.04237278,-0.032697264,-0.037285656,0.024298517,0.0040522465,-0.050192997,0.08785769,-0.013705322,-0.038522527,0.010822616,0.0060147806,-0.055220276,-0.007226714,-0.046881374,-0.03822328,0.061923314,0.03618843,0.06048695,-0.06363897,0.09240618,-0.070302114,-0.027171249,0.048477337,-0.00822419,-0.0005835235,-0.0084336605,0.06802787,0.030941708,0.04508592,0.017585503,0.018991945,0.008563332,0.017445857,-0.009012196,0.015969591,0.019540556,0.03790409,-0.037644748,-0.018702677,-0.046961173,0.03173969]') ON CONFLICT (id) DO NOTHING;
INSERT INTO tbl_vector_store_user_memory (id, content, metadata, embedding) VALUES ('1bfddbad-dd6d-4ac7-b439-7e22651850b9', '用户提及请假流程', '{"userId": "433383317100486656", "agentSn": "ZhiduReactAgent", "createdAt": 1783738400416}', '[-0.03789281,-0.07799059,0.05704338,-0.05745171,-0.0290321,-0.021478038,0.08860712,0.1161284,-0.03593284,0.11955836,-0.0063699125,0.13883142,0.060595833,0.02852169,0.004654936,-0.016067695,-0.03203331,-0.019814102,-0.042833578,0.059697513,0.040996104,0.068762384,0.06578159,0.04332357,0.08060389,0.07182485,-0.09056709,-0.009085292,0.0050632637,-0.0019076561,-0.006773136,-0.086483814,0.07541813,-0.020988045,0.022498857,0.018915782,0.0043231696,0.0188137,0.047611013,0.03752532,0.0062474143,0.012607118,0.009457891,-0.02727629,0.020079516,0.0170681,-0.08191054,-0.07068153,-0.03072666,-0.007732706,0.037770312,0.04197609,0.028848354,0.033176627,0.004067965,0.0014955002,-0.029848756,-0.0021296842,0.0218047,0.016649563,0.01751726,0.07741894,-0.0017264606,-0.018915782,-0.0033687036,0.06439328,-0.006410745,-0.04405856,-0.020181598,-0.060555,0.0035856278,0.039117794,-0.005349093,0.06925238,-0.0028455337,-0.02615339,-0.017772464,0.04246608,-0.005241907,-0.047039352,0.016853727,0.06737407,0.0035014101,0.04875433,0.04895849,0.0993053,-0.0015618536,-0.013893351,0.016026862,0.17819421,0.023356345,0.0033151107,-0.017466217,0.02586756,0.04806017,-0.010335796,0.021518871,0.03005292,-0.014842712,0.10632854,-0.07696977,-0.0064311614,-0.048631832,-0.0010520819,0.06439328,0.07684728,-0.03242122,0.034156613,-0.015271457,-0.014301678,-0.02215178,-0.06202498,-0.042956077,0.02260094,0.04818267,0.11972169,-2.7673774e-05,0.022968434,0.031482067,0.07815392,0.015220416,-0.006027938,-0.031869978,-0.032625385,0.0060585625,-0.019671187,0.016302483,-0.057533376,0.034769107,0.03478952,-0.03113499,-0.04740685,0.0013589656,-0.004244056,0.0058901273,0.08182888,0.074928135,0.03772948,-0.08378885,-0.05773754,0.017741838,-0.04401773,0.020804297,0.014311886,-0.02688838,0.02858294,-0.078725584,-0.0012422095,-0.027602954,0.02445883,-0.060473334,-0.020202015,-0.020681798,0.022886768,0.027419206,-0.07803143,-0.014209804,0.046671856,-0.0120865,0.05377676,-0.02807253,-0.025785895,0.025295902,-0.023928003,-0.027317123,-0.046181865,0.029420013,0.03372787,0.056063395,-0.012106917,0.0013615177,0.009136332,-0.014077098,0.052510943,0.059289183,0.04405856,0.0066404296,0.021661784,-0.067129076,-0.06480161,0.029787507,0.012300872,0.020651175,0.012882739,0.027072128,-0.046467695,0.025683813,-0.0035881798,0.097426996,0.075499795,-0.03231914,0.12698992,0.0036723975,0.0255409,0.027215043,-0.05626756,-0.05198012,0.050918467,0.12756158,0.02247844,0.009565077,0.004591135,-0.0604325,-0.0093302885,0.0069415714,-0.016629146,0.074315645,-0.041445263,0.025949227,-0.007043653,-0.015220416,-0.007508126,0.06259664,0.015189791,-0.006732303,0.010065278,-0.06520993,-0.031339154,-0.032155808,-0.04875433,-0.0020148421,0.019875351,0.0105756875,0.0052138343,-0.0014457353,0.035095766,-0.0340137,0.027786702,0.030093752,0.00050881464,0.025152987,0.054144256,-0.022110946,0.0017813296,-0.036790326,-0.056594223,0.0015133646,0.0011235393,-0.032053724,0.043037742,-0.03258455,0.038607385,0.007890933,0.007564271,0.007421356,-0.007099798,-0.057655875,0.020263262,0.011249429,0.0019204163,-0.07047737,0.046059366,0.03362579,0.029787507,-0.0049050367,0.0029323034,0.016251443,0.02039597,0.040975686,0.025112154,-0.057410877,0.029011684,-0.019313902,-0.022580523,-0.040036533,0.00015647245,0.013035863,0.061494153,0.00028981696,-0.044589385,0.0023019474,0.0056298184,0.017282471,0.011719005,0.0024218939,0.02570423,-0.016077904,-0.0025099395,0.004749362,0.004956078,0.0084728,-0.0067884484,0.04117985,-0.024581328,-0.016761852,0.048591,0.034667023,0.017894963,0.08252303,0.031767897,-0.0059105437,0.05377676,-0.03597367,0.0075897914,-0.0011803223,0.06300496,0.040873606,0.024887575,0.0058288784,0.010667562,-0.07292733,-0.01384231,-0.10812518,-0.010779852,0.03242122,0.06859905,-0.010065278,-0.12698992,0.035116185,-0.05671672,-0.04015903,-0.03299288,0.062923305,0.053817593,0.014975419,-0.066965744,0.026847547,-0.017946003,0.03654533,0.022417191,-0.14756964,-0.06614909,0.019640563,0.033666622,-0.0088402955,0.030644994,0.022049697,-0.004119006,-0.02253969,-0.02756212,-0.060595833,-0.0066710543,-0.0097284075,-0.01417918,0.014801879,0.055491738,0.037484486,0.08713713,-0.046059366,0.026765881,0.017721422,0.10257192,0.058840025,-0.05026514,0.008238012,0.027419206,0.0154756205,-0.027541704,-0.07178401,0.020140765,0.059003357,-0.018027669,-0.007686769,0.061820816,0.015496037,0.008625923,0.024765076,0.010310275,-0.03350329,-7.727921e-05,-0.033850368,0.004279785,-0.09358871,0.008171658,0.047937673,-0.09995863,-0.02643922,-4.4939974e-05,-0.0078041633,-0.048101004,0.0398732,-0.018384956,0.01344419,0.032870382,-0.025010072,0.048386835,0.092853725,-0.0024946271,0.07884808,-0.021968031,-0.01237233,-0.011943585,0.021886365,-0.00094872393,0.042547747,0.0014916722,0.020314304,-0.033237875,-0.019630356,0.02756212,0.026990462,0.06226998,0.0038714572,-0.007421356,0.087300465,-0.005142377,-0.010412357,-0.041200265,0.003756615,-0.0051117525,-0.010274546,0.013689186,-0.011310678,0.05892169,-0.020273471,0.02931793,0.045651037,0.027500872,0.011821087,0.0508368,-0.06410745,-0.034564942,0.03672908,0.08901544,0.01824204,0.01214775,0.00488462,-0.0067578238,-0.06133082,-0.008610611,-0.0702732,0.0752548,-0.04197609,-0.01090235,-0.08378885,0.22523357,0.0027970448,0.0338912,-0.017078307,0.02260094,-0.03868905,-0.012586702,0.047243517,0.039281126,0.037770312,-0.029807923,-0.008437071,-0.096855335,-0.043037742,-0.032400806,-0.061412487,0.03740282,0.05173512,-0.03491202,0.019722229,0.11751672,0.0020531227,-0.024438415,0.031094156,-0.017803088,0.0022368704,-0.026357554,0.026541302,0.016394358,0.0015439892,0.015843116,0.035259098,-0.016231027,0.0040934854,0.06684325,0.023111349,-0.02253969,-0.025969643,0.061208326,0.0045171254,0.031890396,0.062923305,0.008539153,-0.026582135,-0.006089187,0.0140056405,0.014863129,-0.007987911,-0.041649427,0.027072128,0.015496037,-0.042506915,0.08133888,-0.00994278,-0.016414775,0.002746004,0.01378106,-0.06341329,0.0068190726,-0.06365829,-0.020202015,0.04271108,0.0255409,0.062514976,-0.0580642,0.10240859,-0.070518196,-0.040016115,0.044875216,-0.01571041,0.017782671,-0.0012001006,0.044752717,0.017660175,0.040649023,0.019181194,0.02349926,0.004244056,0.008457487,-0.026867963,0.010667562,0.022131363,0.022886768,-0.032564137,-0.0116475485,-0.045161046,0.028562523]') ON CONFLICT (id) DO NOTHING;

COMMIT;
