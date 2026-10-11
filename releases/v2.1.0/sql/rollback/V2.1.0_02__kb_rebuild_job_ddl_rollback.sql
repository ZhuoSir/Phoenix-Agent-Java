-- V2.1.0_02 回滚：删除重建任务表（只删任务台账，不动任何向量数据）
DROP TABLE IF EXISTS tbl_data_kb_rebuild_job;
