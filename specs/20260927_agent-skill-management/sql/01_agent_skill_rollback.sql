-- 回滚件：智能体技能管理（对应 01_agent_skill_upgrade.sql）
-- 逆序执行。数据丢弃说明：status 列与两张关联表内容不保留。

DROP TABLE IF EXISTS tbl_platform_group_skill_info;
DROP TABLE IF EXISTS tbl_data_agent_skill_info;
ALTER TABLE tbl_harness_skills DROP COLUMN IF EXISTS status;
