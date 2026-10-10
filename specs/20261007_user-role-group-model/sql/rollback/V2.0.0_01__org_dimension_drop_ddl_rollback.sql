-- =====================================================================
-- 版本: v2.0.0  序号: 01  类型: DDL(回滚)
-- 配对正向件: ../V2.0.0_01__org_dimension_drop_ddl.sql
-- 目的: 恢复组织维度（3 表）+ 三方平台配置表 + 各表组织列/免登列，并**保真恢复列值与行数据**
-- 可重入: 是（ADD COLUMN IF NOT EXISTS / CREATE TABLE IF NOT EXISTS / CREATE INDEX IF NOT EXISTS /
--         主键约束 DO 块守卫 / 数据恢复走「临时表 + ON CONFLICT (id) DO NOTHING」）
-- ---------------------------------------------------------------------
-- 恢复口径与证据:
--   1) 结构: 4 张表的 CREATE TABLE / 主键 / 索引 DDL **逐字取自 T-01 备份**
--      backups/pre_v2.0.0_orgdim_20261007_165618.sql（pg_dump 16.15）
--   2) 列值: 9 条 UPDATE 为**正向执行前**从生产库逐行导出的原值
--      （7 行 tbl_privilege_user + 1 行 tbl_privilege_role + 1 行 tbl_platform_account_info）
--   3) 行数据: 内联同一 dump 的 COPY 数据原文 —— 行数基线
--      tbl_privilege_company=4 / tbl_privilege_department=17 / tbl_privilege_employee=14 /
--      tbl_platform_platform_info=0（活库与演练库实测均为 0）
--   4) NOT NULL 按原定义恢复: tbl_privilege_user.company_id / dept_id 先加列、回填后再 SET NOT NULL
--      （直接 ADD COLUMN ... NOT NULL 在非空表上会失败）
-- 本件为**自包含**回滚件，单独执行即可完整回退，无需再跑其它文件。
-- 演练: evidence/T-14_drill.txt
-- =====================================================================
BEGIN;

-- ① 按原定义（可空）加回各列
ALTER TABLE IF EXISTS tbl_privilege_user ADD COLUMN IF NOT EXISTS company_id varchar(255);
ALTER TABLE IF EXISTS tbl_privilege_user ADD COLUMN IF NOT EXISTS dept_id varchar(255);
ALTER TABLE IF EXISTS tbl_privilege_user ADD COLUMN IF NOT EXISTS employee_id varchar(65);
ALTER TABLE IF EXISTS tbl_privilege_role ADD COLUMN IF NOT EXISTS company_id bigint;
ALTER TABLE IF EXISTS tbl_platform_account_info ADD COLUMN IF NOT EXISTS dept_id varchar(64);
ALTER TABLE IF EXISTS tbl_platform_account_info ADD COLUMN IF NOT EXISTS dept_name varchar(128);
ALTER TABLE IF EXISTS tbl_platform_account_info ADD COLUMN IF NOT EXISTS employee_id varchar(64);
ALTER TABLE IF EXISTS tbl_platform_account_info ADD COLUMN IF NOT EXISTS third_party_id varchar(255);
ALTER TABLE IF EXISTS tbl_unified_account_map ADD COLUMN IF NOT EXISTS employee_id varchar(64);

-- ② 回填正向执行前的原值（逐行导出，按主键覆盖）
update tbl_privilege_user set company_id = '428001009954172928', dept_id = '428003302434869248', employee_id = '8602614d117642279f32d4993fd4bf7b' where id = '428011841386577921';
update tbl_privilege_user set company_id = '428001009954172928', dept_id = '431991150494330880', employee_id = '4f1f93ac08b74724ad18c088c3ba5585' where id = '432101006843711488';
update tbl_privilege_user set company_id = '428001009954172928', dept_id = '431991481437499392', employee_id = '485df10644f24289a012449b00ea9aac' where id = '432061200055025664';
update tbl_privilege_user set company_id = '428001009954172928', dept_id = '431990993023381504', employee_id = NULL where id = '431678413494018048';
update tbl_privilege_user set company_id = '428001009954172928', dept_id = '431990993023381504', employee_id = '8602614d117642279f32d4993fd4bf7b' where id = '428011841386577920';
update tbl_privilege_user set company_id = '428001009954172928', dept_id = '431990993023381504', employee_id = '461670914812276736' where id = '461681072489615360';
update tbl_privilege_user set company_id = '428001009954172928', dept_id = '431990993023381504', employee_id = '461670914812276736' where id = '461671036765859840';
update tbl_privilege_role set company_id = '428001009954172928' where id = '428007432736870400';
update tbl_platform_account_info set dept_id = '431990993023381504', dept_name = '党委办公室', employee_id = '461670914812276736', third_party_id = '' where id = '461671714812850176';

-- ③ 恢复原 NOT NULL 约束（须在回填之后）
ALTER TABLE IF EXISTS tbl_privilege_user ALTER COLUMN company_id SET NOT NULL;
ALTER TABLE IF EXISTS tbl_privilege_user ALTER COLUMN dept_id SET NOT NULL;

-- ④ 恢复 4 张表结构（DDL 逐字取自 T-01 pg_dump）
CREATE TABLE IF NOT EXISTS public.tbl_privilege_company (
    id character varying(64) NOT NULL,
    pid character varying(64),
    cname character varying(32),
    ename character varying(32),
    idm_company_id character varying(64),
    short_name character varying(120),
    code character varying(32),
    third_id character varying(255),
    descr character varying(200),
    status integer DEFAULT 1,
    create_by character varying(32) NOT NULL,
    create_time timestamp(6) without time zone NOT NULL,
    update_by character varying(32),
    update_time timestamp(6) without time zone,
    del_flag integer DEFAULT 0 NOT NULL,
    sort smallint DEFAULT 0
);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tbl_privilege_company_pkey') THEN
        ALTER TABLE ONLY public.tbl_privilege_company ADD CONSTRAINT tbl_privilege_company_pkey PRIMARY KEY (id);
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS idx_tbl_privilege_company_code ON public.tbl_privilege_company USING btree (code);
CREATE TABLE IF NOT EXISTS public.tbl_privilege_department (
    id character varying(64) NOT NULL,
    company_id character varying(64) NOT NULL,
    name character varying(100) NOT NULL,
    code character varying(20),
    note character varying(80),
    pid character varying(64),
    order_no integer,
    create_time timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    create_by character varying(32) NOT NULL,
    update_time timestamp(6) without time zone,
    update_by character varying(32),
    del_flag integer DEFAULT 0 NOT NULL,
    leader integer DEFAULT 0 NOT NULL,
    department_type integer NOT NULL,
    status smallint DEFAULT 0,
    nature smallint DEFAULT 0,
    third_id character varying(255)
);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tbl_privilege_department_pkey') THEN
        ALTER TABLE ONLY public.tbl_privilege_department ADD CONSTRAINT tbl_privilege_department_pkey PRIMARY KEY (id);
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS idx_tbl_privilege_department_code ON public.tbl_privilege_department USING btree (code);
CREATE TABLE IF NOT EXISTS public.tbl_privilege_employee (
    id character varying(36) NOT NULL,
    emp_code character varying(255),
    emp_name character varying(255),
    position_code character varying(255),
    job_grade_code character varying(255),
    leader_user_id character varying(255),
    leader_user_name character varying(255),
    company_id character varying(255),
    company_name character varying(255),
    dept_id character varying(255),
    dept_name character varying(255),
    sex integer,
    status integer DEFAULT 1,
    enable_flag integer DEFAULT 3,
    service_date timestamp(6) without time zone,
    leave_date timestamp(6) without time zone,
    third_union_id character varying(255),
    third_open_id character varying(255),
    third_user_id character varying(255),
    avatar_url character varying(255),
    del_flag smallint DEFAULT 0 NOT NULL,
    create_by character varying(32),
    create_time timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP,
    update_by character varying(32),
    update_time timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP,
    mobile character varying(255),
    email character varying(255),
    is_dept_leader character varying(255),
    paths character varying(255)
);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tbl_privilege_employee_pkey') THEN
        ALTER TABLE ONLY public.tbl_privilege_employee ADD CONSTRAINT tbl_privilege_employee_pkey PRIMARY KEY (id);
    END IF;
END $$;
CREATE TABLE IF NOT EXISTS public.tbl_platform_platform_info (
    id character varying(64) NOT NULL,
    type character varying(50) DEFAULT NULL::character varying,
    name character varying(255) DEFAULT NULL::character varying,
    corpid character varying(255) DEFAULT NULL::character varying,
    corpsecret character varying(255) DEFAULT NULL::character varying,
    agentid character varying(100) DEFAULT NULL::character varying,
    app_key character varying(255) DEFAULT NULL::character varying,
    status character varying(2) DEFAULT '0'::character varying,
    create_time timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP,
    creator character varying(255) DEFAULT NULL::character varying,
    update_time timestamp(6) without time zone DEFAULT CURRENT_TIMESTAMP,
    updator character varying(255) DEFAULT NULL::character varying,
    del_flag smallint DEFAULT 1
);
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'tbl_platform_platform_info_pkey') THEN
        ALTER TABLE ONLY public.tbl_platform_platform_info ADD CONSTRAINT tbl_platform_platform_info_pkey PRIMARY KEY (id);
    END IF;
END $$;

-- ⑤ 恢复 4 张表的行数据（内联 T-01 备份 COPY 原文）

-- 数据恢复 tbl_privilege_company（4 行；可重入：先进临时表，再 ON CONFLICT (id) DO NOTHING；转义保持 pg_dump 原文）
CREATE TEMP TABLE tmp_tbl_privilege_company (LIKE public.tbl_privilege_company INCLUDING DEFAULTS) ON COMMIT DROP;
COPY tmp_tbl_privilege_company (id , pid , cname , ename , idm_company_id , short_name , code , third_id , descr , status , create_by , create_time , update_by , update_time , del_flag , sort) FROM stdin;
428001009954172928	\N	初彩科技有限公司	Example Technology Co., Ltd.	\N	初彩科技	GS600001	\N	主营软件开发与技术服务	1	600001	2026-06-26 14:24:12.643	\N	\N	0	0
432017488169111552	\N	兴隆科技有限公司	\N	\N	兴隆科技	BM0221	\N	\N	1	428011841386577920	2026-07-07 16:24:15.839918	\N	2026-07-07 16:24:15.839918	0	1
432023134889279488	\N	测试公司22	\N	\N	测试222	BM665	\N	\N	1	428011841386577920	2026-07-07 16:47:23.736	428011841386577920	2026-07-07 16:47:29.785168	0	2
433179373917511680	\N	测试公司33	\N	\N	测试公司33	csgs3	\N	\N	1	428011841386577920	2026-07-10 21:21:20.799	428011841386577920	2026-07-10 21:21:20.893	0	123
\.
INSERT INTO public.tbl_privilege_company (id , pid , cname , ename , idm_company_id , short_name , code , third_id , descr , status , create_by , create_time , update_by , update_time , del_flag , sort) SELECT id , pid , cname , ename , idm_company_id , short_name , code , third_id , descr , status , create_by , create_time , update_by , update_time , del_flag , sort FROM tmp_tbl_privilege_company ON CONFLICT (id) DO NOTHING;

-- 数据恢复 tbl_privilege_department（17 行；可重入：先进临时表，再 ON CONFLICT (id) DO NOTHING；转义保持 pg_dump 原文）
CREATE TEMP TABLE tmp_tbl_privilege_department (LIKE public.tbl_privilege_department INCLUDING DEFAULTS) ON COMMIT DROP;
COPY tmp_tbl_privilege_department (id , company_id , name , code , note , pid , order_no , create_time , create_by , update_time , update_by , del_flag , leader , department_type , status , nature , third_id) FROM stdin;
433179979998633984	433179373917511680	测试部门-子部门	csbm--zbm	\N	433179696031670272	3	2026-07-10 21:47:02.717	428011841386577920	2026-07-10 21:47:03.417	428011841386577920	0	0	0	0	0	\N
431990929269960704	428001009954172928	综合办公室	ZHBGS	\N	\N	0	2026-07-07 14:38:43.537	428011841386577920	\N	\N	0	0	0	0	0	\N
431990993023381504	428001009954172928	党委办公室	DWBGS	\N	\N	0	2026-07-07 14:38:58.741	428011841386577920	\N	\N	0	0	0	0	0	\N
431991090205405184	428001009954172928	法律合规部	FLHGB	\N	\N	0	2026-07-07 14:39:21.911	428011841386577920	\N	\N	0	0	0	0	0	\N
431991150494330880	428001009954172928	审计部	SJB	\N	\N	0	2026-07-07 14:39:36.284	428011841386577920	\N	\N	0	0	0	0	0	\N
431991223299059712	428001009954172928	品牌市场部	PPSCB	\N	\N	0	2026-07-07 14:39:53.643	428011841386577920	\N	\N	0	0	0	0	0	\N
431991323475816448	428001009954172928	组织人事部	ZHRSB	\N	\N	0	2026-07-07 14:40:17.526	428011841386577920	\N	\N	0	0	0	0	0	\N
431991481437499392	428001009954172928	销售中心	XSZX	\N	\N	0	2026-07-07 14:40:55.188	428011841386577920	\N	\N	0	0	0	0	0	\N
431991577403174912	428001009954172928	科技研发部	KJYFB	\N	\N	0	2026-07-07 14:41:18.068	428011841386577920	\N	\N	0	0	0	0	0	\N
431991691584712704	428001009954172928	生命医学部	SMYXB	\N	\N	0	2026-07-07 14:41:45.291	428011841386577920	\N	\N	0	0	0	0	0	\N
432003167410180096	428001009954172928	生命医学一部门	SMYXYBM	\N	431991691584712704	0	2026-07-07 15:27:21.341	428011841386577920	\N	\N	0	0	0	0	0	\N
432003680381947904	428001009954172928	生命医学二部门	SMYXEBM	\N	431991691584712704	2	2026-07-07 15:29:23.643	428011841386577920	\N	\N	0	0	0	0	0	\N
428003302434869248	428001009954172928	AI智能中心	BM600001	人工智能技术研发中心	\N	1	2026-06-26 14:33:19.216	600001	\N	\N	0	0	0	0	0	\N
432018521494622208	428001009954172928	生命医学三部	SMYXSBM	\N	431991691584712704	3	2026-07-07 16:28:22.199871	428011841386577920	2026-07-07 16:28:22.199871	\N	0	0	0	0	0	\N
432379794237296640	428001009954172928	审计1部	sj1bu	\N	431991150494330880	0	2026-07-08 16:23:56.328	432061200055025664	\N	\N	0	0	0	0	0	\N
433179797445746688	433179373917511680	测试部门-子部门	csbm-zbm	\N	433179696031670300	3	2026-07-10 21:22:58.292	428011841386577920	2026-07-10 21:22:58.332	428011841386577920	0	0	0	0	0	\N
433179696031670272	433179373917511680	测试部门	csbm	\N	\N	1	2026-07-10 21:23:11.994	428011841386577920	2026-07-10 21:23:12.549	428011841386577920	0	0	0	0	0	\N
\.
INSERT INTO public.tbl_privilege_department (id , company_id , name , code , note , pid , order_no , create_time , create_by , update_time , update_by , del_flag , leader , department_type , status , nature , third_id) SELECT id , company_id , name , code , note , pid , order_no , create_time , create_by , update_time , update_by , del_flag , leader , department_type , status , nature , third_id FROM tmp_tbl_privilege_department ON CONFLICT (id) DO NOTHING;

-- 数据恢复 tbl_privilege_employee（14 行；可重入：先进临时表，再 ON CONFLICT (id) DO NOTHING；转义保持 pg_dump 原文）
CREATE TEMP TABLE tmp_tbl_privilege_employee (LIKE public.tbl_privilege_employee INCLUDING DEFAULTS) ON COMMIT DROP;
COPY tmp_tbl_privilege_employee (id , emp_code , emp_name , position_code , job_grade_code , leader_user_id , leader_user_name , company_id , company_name , dept_id , dept_name , sex , status , enable_flag , service_date , leave_date , third_union_id , third_open_id , third_user_id , avatar_url , del_flag , create_by , create_time , update_by , update_time , mobile , email , is_dept_leader , paths) FROM stdin;
14a1a3b5e7c44aecab79bbd1314202da	60006	李四	\N	\N	\N	\N		\N	431249386077605888	测试3	\N	1	1	\N	\N	\N	\N	\N	\N	1	600001	2026-07-07 11:19:52.797107	\N	2026-07-07 11:19:52.797107	123565656	\N	\N	\N
61752be9e46e41bd8823cd4a9aaadb4f	3434	七	\N	\N	\N	\N		\N	431249344986009600	测试2	\N	1	1	\N	\N	\N	\N	\N	\N	1	600001	2026-07-07 13:26:46.515281	\N	2026-07-07 13:26:46.515281	13234	\N	\N	\N
85cc7fdabee84c5f901f3600ff1a127e	1000066	八	\N	\N	\N	\N		初彩科技有限公司	431249344986009600	测试2	\N	1	1	\N	\N	\N	\N	\N	\N	1	600001	2026-07-07 14:26:57.074704	\N	2026-07-07 14:26:57.074704	121212	\N	\N	\N
253e889fb3ca4092a981440f48962edc	60000007	王五	\N	\N	\N	\N		\N	431249344986009600	测试2	\N	1	1	\N	\N	\N	\N	\N	\N	1	600001	2026-07-07 13:24:24.454254	\N	2026-07-07 13:24:24.454254	12122121	\N	\N	\N
8cd8c83ba8304f8c917d0fc004dbc8cc	600005	李四	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	431991223299059712	品牌市场部	\N	1	1	\N	\N	\N	\N	\N	\N	0	600001	2026-07-07 10:44:23.970998	\N	2026-07-07 10:44:23.970998	135555	\N	\N	\N
0698139d1ec84f1bbcfca1ddfb642ad8	60000004545	王五	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	431991481437499392	销售中心	\N	1	1	\N	\N	\N	\N	\N	\N	1	600001	2026-07-07 14:34:07.339149	\N	2026-07-07 14:34:07.339149	13565656	\N	\N	\N
432280557611864064	65555	赵七	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	431991223299059712	品牌市场部	\N	1	1	\N	\N	\N	\N	\N	\N	1	428011841386577920	2026-07-08 09:49:36.448	\N	2026-07-08 09:49:36.449903	12233	\N	\N	\N
432275628411240448	65555	赵四	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	431991090205405184	法律合规部	\N	1	1	\N	\N	\N	\N	\N	\N	1	428011841386577920	2026-07-08 09:30:01.249	\N	2026-07-08 09:30:01.236907	121	\N	\N	\N
d0b474e712dc48eab2abc6d15a74ead0	700000	小七	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	432003167410180096	生命医学一部门	\N	1	1	\N	\N	\N	\N	\N	\N	1	600001	2026-07-07 16:28:49.86725	\N	2026-07-07 16:28:49.86725	366	\N	\N	\N
485df10644f24289a012449b00ea9aac	60000004545	王五	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	431991481437499392	销售中心	\N	1	1	\N	\N	\N	\N	\N	\N	1	600001	2026-07-07 16:27:26.523377	\N	2026-07-07 16:27:26.523377	35656	\N	\N	\N
4f1f93ac08b74724ad18c088c3ba5585	655557	马六	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	431991150494330880	审计部	\N	1	1	\N	\N	\N	\N	\N	\N	1	600001	2026-07-07 16:25:49.417119	\N	2026-07-07 16:25:49.417119	12	\N	\N	\N
7741dcd5152a4d199becd6f7cbfcf535	1000008	军哥	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	428003302434869248	AI智能中心	\N	1	1	\N	\N	\N	\N	\N	\N	0	600001	2026-07-11 10:51:08.331	428011841386577920	2026-07-11 10:51:08.333		\N	\N	\N
8602614d117642279f32d4993fd4bf7b	600001	刘方			\N	\N	428001009954172928	初彩科技有限公司	431991323475816448	组织人事部	1	1	1	\N	\N	\N	\N	\N	\N	0	600001	2026-07-11 12:08:36.819	428011841386577920	2026-07-11 12:08:36.959	13735165741	857484261@qq.com	0	
461670914812276736	1002	陈卓	\N	\N	\N	\N	428001009954172928	初彩科技有限公司	431990993023381504	党委办公室	\N	1	1	\N	\N	\N	\N	\N	\N	0	428011841386577920	2026-09-27 12:16:23.533	\N	2026-09-27 12:16:23.512872	\N	\N	\N	\N
\.
INSERT INTO public.tbl_privilege_employee (id , emp_code , emp_name , position_code , job_grade_code , leader_user_id , leader_user_name , company_id , company_name , dept_id , dept_name , sex , status , enable_flag , service_date , leave_date , third_union_id , third_open_id , third_user_id , avatar_url , del_flag , create_by , create_time , update_by , update_time , mobile , email , is_dept_leader , paths) SELECT id , emp_code , emp_name , position_code , job_grade_code , leader_user_id , leader_user_name , company_id , company_name , dept_id , dept_name , sex , status , enable_flag , service_date , leave_date , third_union_id , third_open_id , third_user_id , avatar_url , del_flag , create_by , create_time , update_by , update_time , mobile , email , is_dept_leader , paths FROM tmp_tbl_privilege_employee ON CONFLICT (id) DO NOTHING;

-- 数据恢复 tbl_platform_platform_info（0 行；可重入：先进临时表，再 ON CONFLICT (id) DO NOTHING；转义保持 pg_dump 原文）
CREATE TEMP TABLE tmp_tbl_platform_platform_info (LIKE public.tbl_platform_platform_info INCLUDING DEFAULTS) ON COMMIT DROP;
COPY tmp_tbl_platform_platform_info (id , type , name , corpid , corpsecret , agentid , app_key , status , create_time , creator , update_time , updator , del_flag) FROM stdin;
\.
INSERT INTO public.tbl_platform_platform_info (id , type , name , corpid , corpsecret , agentid , app_key , status , create_time , creator , update_time , updator , del_flag) SELECT id , type , name , corpid , corpsecret , agentid , app_key , status , create_time , creator , update_time , updator , del_flag FROM tmp_tbl_platform_platform_info ON CONFLICT (id) DO NOTHING;

-- ⑥ 收尾
COMMIT;
