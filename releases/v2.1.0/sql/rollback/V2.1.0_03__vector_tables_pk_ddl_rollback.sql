-- V2.1.0_03 回滚：仅删除本件添加的主键约束（不动任何向量数据）
DO $$
DECLARE
    t text;
BEGIN
    FOREACH t IN ARRAY ARRAY['tbl_vector_store_simple_data', 'tbl_vector_store_rag', 'tbl_vector_store_user_memory']
    LOOP
        IF to_regclass(t) IS NULL THEN
            CONTINUE;
        END IF;
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I', t, t || '_pkey');
        EXECUTE format('ALTER TABLE %I DROP CONSTRAINT IF EXISTS %I', t, t || '_pk_v210');
    END LOOP;
END $$;
