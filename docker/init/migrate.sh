#!/bin/sh
# Phoenix 交付包初始化/增量迁移器（plan §3）
# 首启：基线 schema+data+admin 种子；此后按 releases 序号增量执行并记台账 tbl_phoenix_release
set -eu
PSQL="psql -v ON_ERROR_STOP=1 -q"

# ===== [脚本级修复 2026-10-11] 等待 postgres 真正接受 TCP 连接 =====
# 缺因：compose 的健康检查（pg_isready）可经 unix socket 提前通过，而 TCP(5432) 尚未就绪 ⇒
#       首次 psql 连接被拒（exit 2）。此处显式重试，消除与容器启动的时序竞态。
i=0
while ! psql -c 'select 1' >/dev/null 2>&1; do
  i=$((i+1))
  if [ "$i" -gt 60 ]; then echo "[migrator] postgres TCP 5432 未就绪（等待 120s），放弃"; exit 2; fi
  sleep 2
done


FIRST=$(psql -tAc "SELECT 1 FROM information_schema.tables WHERE table_name='tbl_phoenix_release'") || FIRST=""
if [ -z "$FIRST" ]; then
  echo "[migrator] 首次初始化(R-05 v1.3.0)：00 前置序列 → all_data 基线 → 10_seed_admin"
  $PSQL -f /init/00_baseline_sequences.sql
  $PSQL -f /sql/all_data.sql
  $PSQL -f /init/10_seed_admin.sql
  $PSQL -f /init/20_seed_runtime_agents.sql
  $PSQL -c "CREATE TABLE IF NOT EXISTS tbl_phoenix_release (seq text PRIMARY KEY, file text NOT NULL, applied_at timestamptz DEFAULT now())"
  $PSQL -c "INSERT INTO tbl_phoenix_release(seq,file) VALUES('0000_baseline','sequences+data+admin+runtime_agents') ON CONFLICT DO NOTHING"
else
  echo "[migrator] 检测到哨兵表，非首次初始化，跳过基线（R-08）"
fi

# ===== [脚本级修复 2026-10-11] 预建"应用自建表" =====
# 缺因：tbl_harness_skills 等由应用启动时 AgentScope PostgresSkillRepository.createIfNotExist 创建，
# 而 compose 让 backend 依赖 migrator 成功 ⇒ 空库首启时 V2.0.0_13/14 必然 "relation does not exist" ⇒ exit 3 ⇒ 死锁。
# 对策（仅脚本层）：在跑发布件之前幂等预建这些表；应用启动后的 createIfNotExist 遇到已存在表为 no-op。
if [ "$(psql -tAc "select to_regclass('public.tbl_harness_skills') is null")" = "t" ]; then
$PSQL <<'APPTABLES'
SET search_path TO public;
-- PostgreSQL database dump


-- Dumped from database version 16.15 (Debian 16.15-1.pgdg12+2)
-- Dumped by pg_dump version 16.15 (Debian 16.15-1.pgdg12+2)




-- Name: tbl_harness_skill_resources; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.tbl_harness_skill_resources (
    id bigint NOT NULL,
    resource_path character varying(500) NOT NULL,
    resource_content text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


-- Name: tbl_harness_skills; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.tbl_harness_skills (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    description text NOT NULL,
    skill_content text NOT NULL,
    source character varying(255) NOT NULL,
    metadata_json text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    status character varying(16) DEFAULT 'draft'::character varying NOT NULL,
    creator character varying(64)
);


-- Name: COLUMN tbl_harness_skills.status; Type: COMMENT; Schema: public; Owner: -

COMMENT ON COLUMN public.tbl_harness_skills.status IS '发布状态 draft=草稿 published=已发布；覆盖上传后回 draft';


-- Name: tbl_harness_skills_id_seq; Type: SEQUENCE; Schema: public; Owner: -

CREATE SEQUENCE public.tbl_harness_skills_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


-- Name: tbl_harness_skills_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -

ALTER SEQUENCE public.tbl_harness_skills_id_seq OWNED BY public.tbl_harness_skills.id;


-- Name: tbl_harness_store_state; Type: TABLE; Schema: public; Owner: -

CREATE TABLE public.tbl_harness_store_state (
    session_id character varying(255) NOT NULL,
    state_key character varying(255) NOT NULL,
    item_index integer DEFAULT 0 NOT NULL,
    state_data text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);


-- Name: tbl_harness_skills id; Type: DEFAULT; Schema: public; Owner: -

ALTER TABLE ONLY public.tbl_harness_skills ALTER COLUMN id SET DEFAULT nextval('public.tbl_harness_skills_id_seq'::regclass);


-- Name: tbl_harness_skill_resources tbl_harness_skill_resources_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tbl_harness_skill_resources
    ADD CONSTRAINT tbl_harness_skill_resources_pkey PRIMARY KEY (id, resource_path);


-- Name: tbl_harness_skills tbl_harness_skills_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tbl_harness_skills
    ADD CONSTRAINT tbl_harness_skills_pkey PRIMARY KEY (id);


-- Name: tbl_harness_store_state tbl_harness_store_state_pkey; Type: CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tbl_harness_store_state
    ADD CONSTRAINT tbl_harness_store_state_pkey PRIMARY KEY (session_id, state_key, item_index);


-- Name: tbl_harness_skills_name_creator_key; Type: INDEX; Schema: public; Owner: -

CREATE UNIQUE INDEX tbl_harness_skills_name_creator_key ON public.tbl_harness_skills USING btree (name, COALESCE(creator, ''::character varying));


-- Name: tbl_harness_skill_resources tbl_harness_skill_resources_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -

ALTER TABLE ONLY public.tbl_harness_skill_resources
    ADD CONSTRAINT tbl_harness_skill_resources_id_fkey FOREIGN KEY (id) REFERENCES public.tbl_harness_skills(id) ON DELETE CASCADE;


-- PostgreSQL database dump complete
APPTABLES
fi

for vdir in $(ls -d /releases/v*/sql 2>/dev/null | sort -V); do
  for f in "$vdir"/V*.sql; do
    [ -e "$f" ] || continue
    seq=$(basename "$f" .sql)
    done_flag=$(psql -tAc "SELECT 1 FROM tbl_phoenix_release WHERE seq='$seq'") || done_flag=""
    if [ -n "$done_flag" ]; then
      echo "[migrator] skip $seq（已应用）"
      continue
    fi
    echo "[migrator] apply $seq"
    $PSQL -1 -f "$f"
    $PSQL -c "INSERT INTO tbl_phoenix_release(seq,file) VALUES('$seq','$f') ON CONFLICT DO NOTHING"
  done
done
echo "[migrator] 完成"
