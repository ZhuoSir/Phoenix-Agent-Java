-- V2.1.0_03 遗留向量表主键补齐（vector-dim-flexibility R-09 约束项 / T-15·T-16）
-- 目标库：PostgreSQL（phoenix）
-- 背景（实测发现）：从历史 dump（sql/all_data.sql）建出的 3 张向量表只有 `id uuid NOT NULL DEFAULT uuid_generate_v4()`，
--   **没有主键/唯一约束**；升级后的存量现场被 R-09 五项验收的"约束"项判不通过（升级测试实测 FAIL）。
-- 策略：**只加约束、绝不动数据**；若某表存在重复 id ⇒ 该表**告警并跳过**（不静默、不删数据），由运维人工复核去重后重跑本件。
-- 幂等：已有主键则跳过；约束名冲突自动换名；可重复执行。
-- 回滚：见 rollback/V2.1.0_03__vector_tables_pk_ddl_rollback.sql（仅删约束，不动数据）
DO $$
DECLARE
    t            text;
    dup          int;
    has_pk       int;
    name_taken   int;
    cname        text;
BEGIN
    FOREACH t IN ARRAY ARRAY['tbl_vector_store_simple_data', 'tbl_vector_store_rag', 'tbl_vector_store_user_memory']
    LOOP
        IF to_regclass(t) IS NULL THEN
            RAISE NOTICE '[V2.1.0_03] 表不存在，跳过: %', t;
            CONTINUE;
        END IF;

        SELECT count(*) INTO has_pk
          FROM pg_constraint p JOIN pg_class c ON c.oid = p.conrelid
         WHERE c.relname = t AND p.contype = 'p';
        IF has_pk > 0 THEN
            RAISE NOTICE '[V2.1.0_03] 已有主键，跳过: %', t;
            CONTINUE;
        END IF;

        EXECUTE format('SELECT count(*) FROM (SELECT id FROM %I GROUP BY id HAVING count(*) > 1) d', t) INTO dup;
        IF dup > 0 THEN
            RAISE WARNING '[V2.1.0_03] 表 % 存在 % 组重复 id ⇒ 跳过加主键（不删数据）；请人工复核去重后重跑本迁移件', t, dup;
            CONTINUE;
        END IF;

        SELECT count(*) INTO name_taken FROM pg_class WHERE relname = t || '_pkey';
        cname := CASE WHEN name_taken > 0 THEN t || '_pk_v210' ELSE t || '_pkey' END;
        EXECUTE format('ALTER TABLE %I ADD CONSTRAINT %I PRIMARY KEY (id)', t, cname);
        RAISE NOTICE '[V2.1.0_03] 已补主键: % (constraint=%)', t, cname;
    END LOOP;
END $$;
