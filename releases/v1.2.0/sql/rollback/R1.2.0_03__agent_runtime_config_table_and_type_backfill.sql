-- ============================================
-- 回滚件：对应 V1.2.0 同名序号正向脚本
-- 作用: 回滚 V1.2.0_03（drop 运行配置表 + 撤销 type 默认值；NULL→harness 回填不可逆，文末注明）
-- ============================================

-- 回滚件：对话智能体运行配置（对应 03_agent_runtime_config.sql）
-- ⚠️ 不可逆项：type 为 NULL 的存量行已回填为 'harness'，回滚不还原（无法区分回填与原生 harness）；
--    如需还原请以业务口径人工确认后执行 UPDATE。

ALTER TABLE tbl_data_agent ALTER COLUMN type DROP DEFAULT;
DROP TABLE IF EXISTS tbl_data_agent_runtime_config;
