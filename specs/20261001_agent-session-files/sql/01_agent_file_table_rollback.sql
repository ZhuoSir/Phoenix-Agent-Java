-- 回滚件：对应 01_agent_file_table.sql（agent-session-files T-01）
-- 作用：drop 登记表（纯新增表，回滚无损既有数据；rel_path 指向的物理文件不物理删，见 plan R-10 首期不 GC）
DROP TABLE IF EXISTS tbl_data_agent_file;
