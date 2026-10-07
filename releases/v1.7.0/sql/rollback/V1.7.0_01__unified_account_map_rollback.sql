-- =====================================================================
-- v1.7.0 统一账号中心（一期）· 升级件 01 回滚：迁移映射/审计表
-- 说明：本文件仅回滚 DDL（建表）。
--       数据面回滚（8 类列反向重写 / 删除分支 B 新建行 / 恢复前台密码原值 /
--       恢复 M8 删除的 thinktest 行）在 T-12 随迁移脚本一并补齐并演练，
--       其"事实锚"就是本表——**先备份本表内容再回滚删除**。
-- =====================================================================

-- 回滚前置：备份映射表内容（回滚事实锚，务必先做）
-- \copy (SELECT * FROM tbl_unified_account_map) TO '/tmp/uam_backup.csv' CSV HEADER

DROP INDEX IF EXISTS idx_uamr_new_id;
DROP TABLE IF EXISTS tbl_unified_account_migration_rows;
DROP INDEX IF EXISTS idx_uam_new_user;
DROP TABLE IF EXISTS tbl_unified_account_map;
