-- =====================================================================
-- 版本: v2.0.0  序号: 09  类型: DML(回滚)
-- 配对正向件: ../V2.0.0_09__agent_owner_backfill_dml.sql
-- 目的: 把本件回填过的智能体 admin_id 复位置空
-- 范围: 仅限**正向执行前 admin_id 为空**的那些 id（已逐个记录），不影响后续新建的智能体
-- 可重入: 是（幂等）
-- =====================================================================
BEGIN;

UPDATE tbl_data_agent
   SET admin_id = NULL
 WHERE id IN (19, 20, 21, 23, 24, 25, 30, 33, 36, 37)
   AND admin_id = (SELECT u.id::bigint FROM tbl_privilege_user u
                    WHERE lower(u.username) = 'admin' AND coalesce(u.del_flag, 0) = 0
                    ORDER BY u.create_time LIMIT 1);

COMMIT;
