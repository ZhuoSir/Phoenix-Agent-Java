-- ============================================
-- 部署包基线前置（R-05 v1.3.0）：all_data.sql 的 demo 表 CREATE 引用这些序列
-- 但 dump 文件本身不含其 DDL；且其 DROP TABLE 会连带删除 owned 序列，
-- 故必须在 all_data 之前确保它们存在。内容逐条取自 sql/all_schema.sql（BUG-01 修复块）。
-- 幂等：IF NOT EXISTS
-- ============================================
CREATE SEQUENCE IF NOT EXISTS "public"."tbl_data_categories_id_seq"
    INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS "public"."tbl_data_order_items_id_seq"
    INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS "public"."tbl_data_orders_id_seq"
    INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS "public"."tbl_data_products_id_seq"
    INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1;
CREATE SEQUENCE IF NOT EXISTS "public"."tbl_data_users_id_seq"
    INCREMENT 1 START 1 MINVALUE 1 MAXVALUE 9223372036854775807 CACHE 1;
