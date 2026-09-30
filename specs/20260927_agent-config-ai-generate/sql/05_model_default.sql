-- 升级件草案：模型配置「默认模型」标记（agent-config-ai-generate）T-01
-- 目标库：PostgreSQL（phoenix）
-- 幂等：ADD COLUMN IF NOT EXISTS / 仅对"尚无默认"的类型回填 / CREATE UNIQUE INDEX IF NOT EXISTS
-- 顺序：加列 → 回填（每类型至多一条）→ 建部分唯一索引（先收敛后建索引，避免索引因脏数据建不起来）
-- 回滚：见 05_model_default_rollback.sql（只删索引与新列，不动 is_active 与既有数据）
--
-- 背景：现状只有 is_active，且 ModelConfigMapper.deactivateOthers 的 SQL 写反（bugs.md B-20：
-- 注释"设为非启用"实为 SET is_active = true），导致同类型可能已存在多条同时启用。
-- 本需求把语义拆为「启用=可多选集合」+「默认=未显式选择时的加载项」，故：
--   ① 不再依赖 is_active 表达唯一默认；② 回填时按「优先启用项、其次最新一条」为每类型收敛出**唯一**默认。

-- 1) 新增默认标记列
ALTER TABLE tbl_data_model_config
    ADD COLUMN IF NOT EXISTS is_default boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN tbl_data_model_config.is_default IS '该类型的默认模型 0否 1是（每类型至多一条；智能体未显式选择模型时加载它）';

-- 2) 存量回填：仅处理"该类型当前还没有任何默认"的情况（重复执行为 no-op，保证幂等）
WITH target_types AS (
    SELECT DISTINCT m.model_type
      FROM tbl_data_model_config m
     WHERE m.is_deleted = 0
       AND NOT EXISTS (
             SELECT 1 FROM tbl_data_model_config d
              WHERE d.model_type = m.model_type AND d.is_default = true AND d.is_deleted = 0
           )
), picked AS (
    SELECT DISTINCT ON (c.model_type) c.id, c.model_type
      FROM tbl_data_model_config c
      JOIN target_types tt ON tt.model_type = c.model_type
     WHERE c.is_deleted = 0
     ORDER BY c.model_type,
              c.is_active DESC NULLS LAST,
              c.updated_time DESC NULLS LAST,
              c.created_time DESC NULLS LAST,
              c.id DESC
)
UPDATE tbl_data_model_config t
   SET is_default = true
  FROM picked p
 WHERE t.id = p.id;

-- 3) 业务防重的最终防线：每个类型至多一条默认（部分唯一索引，仅约束"是默认且未删"的行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmc_type_default
    ON tbl_data_model_config (model_type) WHERE is_default = true AND is_deleted = 0;

-- 4) 执行后自检（人工核对，注释形态以免升级时产生结果集）
-- select model_type, count(*) filter (where is_default) as defaults,
--        count(*) filter (where is_active) as actives, count(*) as total
--   from tbl_data_model_config where is_deleted = 0 group by model_type order by model_type;
-- 期望：defaults 每类型 ≤ 1（正常应为 1，该类型无任何配置时为 0）
