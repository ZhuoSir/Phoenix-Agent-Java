-- V2.1.0_01 知识库绑定向量模型（vector-dim-flexibility R-01 / R-06）
-- 目标库：PostgreSQL（phoenix）
-- 幂等：ADD COLUMN / CREATE INDEX 均 IF NOT EXISTS；注释段有 to_regclass 守卫；可重跑
-- 顺序：① 知识库加绑定列 ② 注释 ③ 索引 ④ 存量回填口径与判定查询 ⑤ 人工确认绑定模板 ⑥ 核对
-- 回滚：见 rollback/V2.1.0_01__kb_embedding_binding_ddl_rollback.sql
--
-- 语义注记：
--   · embedding_model_id：该知识库绑定的向量模型（对应 tbl_data_model_config.id）；NULL = 未绑定/待重建
--   · model_space_key：向量空间键（provider+model+版本摘要+维度），用于归属与"哪些向量已失效"体检
--   · **存量回填口径（重要）**：默认留 NULL（= 待重建）。原因：平台此前只允许"全局唯一模型"，
--     且现有向量表未记录生成模型（实测 metadata 仅含 knowledgeId/agentId/chunk_index/parent_document_id/source），
--     而不同模型的维度可能相同 ⇒ **无法从数据唯一判定**生成模型。
--     判定方式：① 用下方 ④ 的查询比对维度 ② 现场部署/操作记录；人工确认后按 ⑤ 的模板绑定（R-07a 人工闸）。
--     未确认的库保持 NULL，升级时按 R-07(b) 默认走重建。

-- ① 知识库加绑定列
ALTER TABLE IF EXISTS tbl_data_knowledge_base
    ADD COLUMN IF NOT EXISTS embedding_model_id bigint;
ALTER TABLE IF EXISTS tbl_data_knowledge_base
    ADD COLUMN IF NOT EXISTS model_space_key varchar(128);

-- ② 列注释（表不存在时跳过，保证幂等可重跑）
DO $$
BEGIN
  IF to_regclass('public.tbl_data_knowledge_base') IS NOT NULL THEN
    EXECUTE 'COMMENT ON COLUMN tbl_data_knowledge_base.embedding_model_id IS ''绑定的向量模型 id（tbl_data_model_config.id）；NULL=未绑定/待重建''';
    EXECUTE 'COMMENT ON COLUMN tbl_data_knowledge_base.model_space_key IS ''向量空间键（provider+model+版本摘要+维度），用于归属与失效体检''';
  END IF;
END $$;

-- ③ 绑定查询索引（仅存活库）
CREATE INDEX IF NOT EXISTS idx_dkb_embedding_model
    ON tbl_data_knowledge_base (embedding_model_id) WHERE del_flag = 0;

-- ④ 判定查询（只读，供运维核对"能否绑定某模型"）：每库的向量维度与规模
--    期望：所有库维度一致 ⇒ 可能是同一模型；出现多种维度 ⇒ 必然跨模型（需按库分别判定）
-- SELECT kb.id AS kb_id, kb.name,
--        (SELECT count(DISTINCT a.atttypmod)
--           FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid
--          WHERE c.relname LIKE 'tbl_vector_store%' AND a.attname = 'embedding') IS NOT NULL AS has_vec_tables,
--        (SELECT count(*) FROM tbl_data_agent_knowledge k
--          WHERE k.knowledge_base_id = kb.id AND k.is_deleted = 0) AS docs
--   FROM tbl_data_knowledge_base kb WHERE kb.del_flag = 0 ORDER BY kb.id;

-- ⑤ 人工确认后绑定（示例；执行前必须确认"这批向量确实由该模型生成且维度一致"）
-- UPDATE tbl_data_knowledge_base
--    SET embedding_model_id = <模型id>,
--        model_space_key   = '<provider>-<model>-<版本摘要>-<维度>'
--  WHERE del_flag = 0 AND embedding_model_id IS NULL;

-- ⑥ 核对：绑定分布（期望：全 NULL（待重建）或全部指向同一模型；混合即为未完成迁移）
-- SELECT embedding_model_id, model_space_key, count(*)
--   FROM tbl_data_knowledge_base WHERE del_flag = 0 GROUP BY 1,2 ORDER BY 1 NULLS FIRST;
