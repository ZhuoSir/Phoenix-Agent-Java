-- 一次性存量孤儿清理（BUG-14 运维脚本，非 schema 升级件；先干跑后执行）
-- 干跑清单：
--  select 'runtime孤儿='||count(*) from tbl_data_agent_runtime_config r where r.del_flag=0 and not exists (select 1 from tbl_data_agent a where a.id=r.agent_id );
--  select 'skill孤儿='||count(*) from tbl_data_agent_skill_info s where s.del_flag=0 and not exists (select 1 from tbl_data_agent a where a.id=s.agent_id );
--  select 'group孤儿='||count(*) from tbl_platform_group_agent_info g where g.del_flag=0 and not exists (select 1 from tbl_data_agent a where a.id::text=g.agent_id );
--  select 'kbase孤儿='||count(*) from tbl_data_agent_kbase_bind b where not exists (select 1 from tbl_data_agent a where a.id=b.agent_id );
--  select 'ds孤儿='||count(*) from tbl_data_agent_datasource d where not exists (select 1 from tbl_data_agent a where a.id=d.agent_id );
-- 执行（软删优先，绑定物理删）：
UPDATE tbl_data_agent_runtime_config r SET del_flag = 1 WHERE r.del_flag = 0 AND NOT EXISTS (SELECT 1 FROM tbl_data_agent a WHERE a.id = r.agent_id );
UPDATE tbl_data_agent_skill_info s SET del_flag = 1 WHERE s.del_flag = 0 AND NOT EXISTS (SELECT 1 FROM tbl_data_agent a WHERE a.id = s.agent_id );
UPDATE tbl_platform_group_agent_info g SET del_flag = 1 WHERE g.del_flag = 0 AND NOT EXISTS (SELECT 1 FROM tbl_data_agent a WHERE a.id::text = g.agent_id);
DELETE FROM tbl_data_agent_kbase_bind b WHERE NOT EXISTS (SELECT 1 FROM tbl_data_agent a WHERE a.id = b.agent_id );
DELETE FROM tbl_data_agent_datasource d WHERE NOT EXISTS (SELECT 1 FROM tbl_data_agent a WHERE a.id = d.agent_id );
