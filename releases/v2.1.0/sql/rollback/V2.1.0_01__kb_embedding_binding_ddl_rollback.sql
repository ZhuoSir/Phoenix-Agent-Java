-- V2.1.0_01 回滚：撤销知识库的向量模型绑定列与索引
-- 影响：知识库回到"无绑定"状态（platform 侧需回退到旧版本行为）；向量表数据不受影响
DROP INDEX IF EXISTS idx_dkb_embedding_model;
ALTER TABLE IF EXISTS tbl_data_knowledge_base DROP COLUMN IF EXISTS embedding_model_id;
ALTER TABLE IF EXISTS tbl_data_knowledge_base DROP COLUMN IF EXISTS model_space_key;
