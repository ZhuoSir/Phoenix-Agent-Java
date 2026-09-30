-- ============================================
-- 回滚件：对应 V1.2.0 同名序号正向脚本
-- 作用: 回滚 V1.2.0_05（drop uk_dmc_type_default + drop is_default 列；不动 is_active 与数据）
-- ============================================

-- 回滚件：撤销模型配置「默认模型」标记（agent-config-ai-generate）T-01
-- 说明：只撤销本升级件新增的对象（索引 + 列），不触碰 is_active / is_deleted / 业务数据；
--       回滚后行为退回本需求之前（按「该类型唯一启用」取模型）。
--       注意：回滚会丢失"哪条被设为默认"的信息（列值随列一起消失），重新升级时按
--       05 的回填规则重新收敛，可能与回滚前的默认不是同一条。

DROP INDEX IF EXISTS uk_dmc_type_default;

ALTER TABLE tbl_data_model_config
    DROP COLUMN IF EXISTS is_default;
