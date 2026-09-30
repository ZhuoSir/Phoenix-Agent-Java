-- ============================================
-- 首次初始化种子：Java 自注册智能体的运行时行（5 个 sn）
-- 依据：AbstractHarnessAgent.register() 先 createHarnessAgent() 后 saveBySn()，
--       全新库无行时 HumanInTheLoop.java:87 agent.getDescription() NPE 阻断启动（BUG-31）；
--       本种子补行规避（不改业务代码，R-16），启动后 saveBySn 会按 sn upsert 校正。
-- 幂等：WHERE NOT EXISTS (sn)
-- ============================================
INSERT INTO tbl_data_agent (sn, name, type, description)
SELECT v.sn, v.name, v.type, v.description FROM (VALUES
  ('BpmReactAgent',      '流程分析小助手', 'agent',    '分析流程的使用情况'),
  ('ZhiduReactAgent',    '制度汇编',       'agent',    '制度汇编'),
  ('ParolCompiledGraph', '警务巡逻小助手', 'workflow', '警务巡逻小助手'),
  ('HumanInTheLoop',     '测试人工干预',   'harness',  '测试人工干预'),
  ('RulesHarnessAgent',  '制度专家',       'harness',  '专业查询制度的专家')
) AS v(sn, name, type, description)
WHERE NOT EXISTS (SELECT 1 FROM tbl_data_agent t WHERE t.sn = v.sn);
