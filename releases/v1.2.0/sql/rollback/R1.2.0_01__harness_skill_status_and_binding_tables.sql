-- ============================================
-- 回滚件：对应 V1.2.0 同名序号正向脚本
-- 作用: 回滚 V1.2.0_01（drop 两张绑定/授权表 + drop status 列）
-- ============================================

-- 回滚件：智能体技能管理（对应 01_agent_skill_upgrade.sql）
-- 逆序执行。数据丢弃说明：status 列与两张关联表内容不保留。

DROP TABLE IF EXISTS tbl_platform_group_skill_info;
DROP TABLE IF EXISTS tbl_data_agent_skill_info;
-- M3 汇总时补存在性容错（与草案的唯一差异）：tbl_harness_skills 由应用首启创建，
-- 从未启动过应用的环境里该表不存在，直接 ALTER 会报错中断回滚链
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_tables WHERE tablename = 'tbl_harness_skills') THEN
        ALTER TABLE tbl_harness_skills DROP COLUMN IF EXISTS status;
        RAISE NOTICE '已删除 tbl_harness_skills.status 列';
    ELSE
        RAISE NOTICE 'tbl_harness_skills 不存在，无需回滚 status 列';
    END IF;
END $$;
