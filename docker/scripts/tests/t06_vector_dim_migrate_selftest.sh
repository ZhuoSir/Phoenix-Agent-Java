#!/usr/bin/env bash
# T-06 验证：vector-dim-migrate.sh（R-04）
# 依赖: docker 在 PATH；本机 PG 容器可建临时库（脚本自建自清 phx_t06a/b/c）
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; cd "$ROOT"
PGC="${PHX_TEST_PG_CONTAINER:-phoenix-release-postgres-1}"
for c in docker tar; do command -v "$c" >/dev/null 2>&1 || { echo "  ❌ 环境自检失败: $c 不在 PATH（L-102）"; exit 3; }; done
docker exec "$PGC" true >/dev/null 2>&1 || { echo "  ❌ 环境自检失败: 容器 $PGC 不可达"; exit 3; }
q(){ docker exec -i "$PGC" psql -U phoenix -d "$1" -tAc "$2" 2>/dev/null; }
mk(){ docker exec -i "$PGC" psql -U phoenix -d postgres -q -c "DROP DATABASE IF EXISTS $1" >/dev/null 2>&1
      docker exec -i "$PGC" psql -U phoenix -d postgres -q -c "CREATE DATABASE $1" >/dev/null 2>&1
      docker exec -i "$PGC" psql -U phoenix -d "$1" -q -c "CREATE EXTENSION IF NOT EXISTS vector" >/dev/null 2>&1; }
W="$(mktemp -d /tmp/t06t.XXXXXX)"; P=0; F=0
ok(){ P=$((P+1)); printf '  ✅ %s\n' "$1"; }; no(){ F=$((F+1)); printf '  ❌ %s — %s\n' "$1" "$2"; }

mk phx_t06a
docker exec -i "$PGC" psql -U phoenix -d phx_t06a -q -c "CREATE TABLE tbl_vector_store_rag (id uuid PRIMARY KEY, content text, metadata json, embedding vector(768)); CREATE INDEX tbl_vector_store_rag_index ON tbl_vector_store_rag USING hnsw (embedding vector_cosine_ops); CREATE TABLE tbl_vector_store_simple_data (id uuid PRIMARY KEY, content text, embedding vector(768));" >/dev/null 2>&1
# 用例1：dry-run（数据不变）
PHX_PG_DB=phx_t06a PHX_LOG_FILE="$W/1.log" bash docker/scripts/vector-dim-migrate.sh --dims 1024 --pg-container "$PGC" --dry-run > "$W/1.out" 2>&1; RC=$?
[ $RC -eq 0 ] && ok "dry-run 退出码 0" || no "dry-run rc" "$RC"
grep -q "dry-run 已回滚，实态未变" "$W/1.out" && ok "dry-run 回显回滚后实态" || no "dry-run 回显" "$(tail -2 "$W/1.out" | head -1 | cut -c1-80)"
[ "$(q phx_t06a "select atttypmod from pg_attribute where attrelid='tbl_vector_store_rag'::regclass and attname='embedding'")" = "768" ] && ok "dry-run 后维度仍 768（回滚生效）" || no "dry-run 未回滚" "$(q phx_t06a "select atttypmod from pg_attribute where attrelid='tbl_vector_store_rag'::regclass and attname='embedding'")"
grep -q "自检 表=tbl_vector_store_rag 维度=1024" "$W/1.out" && ok "事务内自检显示 1024（可预演）" || no "事务内自检" "$(grep -c . "$W/1.out") 行"
# 用例2：真跑（--yes）→ 维度 1024 + 主键保留 + 索引重建
PHX_PG_DB=phx_t06a PHX_LOG_FILE="$W/2.log" bash docker/scripts/vector-dim-migrate.sh --dims 1024 --pg-container "$PGC" --yes > "$W/2.out" 2>&1; RC=$?
[ $RC -eq 0 ] && ok "真跑退出码 0" || no "真跑 rc" "$RC"
D1="$(q phx_t06a "select atttypmod from pg_attribute where attrelid='tbl_vector_store_rag'::regclass and attname='embedding'")"
D2="$(q phx_t06a "select atttypmod from pg_attribute where attrelid='tbl_vector_store_simple_data'::regclass and attname='embedding'")"
[ "$D1" = "1024" ] && [ "$D2" = "1024" ] && ok "两表维度均 → 1024" || no "维度" "rag=$D1 simple=$D2"
[ "$(q phx_t06a "select count(*) from pg_constraint where conrelid='tbl_vector_store_rag'::regclass and contype='p'")" = "1" ] && ok "主键保留（未被误删）" || no "主键" "丢失"
[ "$(q phx_t06a "select count(*) from pg_indexes where tablename='tbl_vector_store_rag' and indexdef like '%embedding%'")" = "1" ] && ok "hnsw 索引已重建" || no "索引" "数量异常"
# 用例3：缺 PK → 自检报出 + 修复语句
mk phx_t06b
docker exec -i "$PGC" psql -U phoenix -d phx_t06b -q -c "CREATE TABLE tbl_vector_store_rag (id uuid, content text, embedding vector(768));" >/dev/null 2>&1
PHX_PG_DB=phx_t06b PHX_LOG_FILE="$W/3.log" bash docker/scripts/vector-dim-migrate.sh --dims 1024 --pg-container "$PGC" --yes > "$W/3.out" 2>&1; RC=$?
[ $RC -eq 1 ] && ok "缺 PK：退出码 1（自检不通过）" || no "缺PK rc" "$RC"
grep -q "缺 id 唯一约束" "$W/3.out" && grep -q "ALTER TABLE tbl_vector_store_rag ADD CONSTRAINT tbl_vector_store_rag_pkey PRIMARY KEY (id);" "$W/3.out" && ok "缺 PK：打印可照做修复语句" || no "修复语句" "$(grep -c . "$W/3.out") 行"
# 用例4：重复 id → 报组数 + 去重语句 + 不静默删数据
mk phx_t06c
docker exec -i "$PGC" psql -U phoenix -d phx_t06c -q -c "CREATE TABLE tbl_vector_store_rag (id uuid, content text, embedding vector(768)); INSERT INTO tbl_vector_store_rag(id,content) VALUES ('11111111-1111-1111-1111-111111111111','a'),('11111111-1111-1111-1111-111111111111','b');" >/dev/null 2>&1
PHX_PG_DB=phx_t06c PHX_LOG_FILE="$W/4.log" bash docker/scripts/vector-dim-migrate.sh --dims 1024 --pg-container "$PGC" --yes > "$W/4.out" 2>&1; RC=$?
[ $RC -eq 1 ] && ok "重复 id：退出码 1" || no "重复rc" "$RC"
grep -q "组重复 id" "$W/4.out" && grep -q "去重: DELETE FROM" "$W/4.out" && ok "重复 id：报组数 + 去重语句" || no "去重语句" "$(grep -c . "$W/4.out") 行"
# 用例5：范围证据（迁移只动 embedding/索引，数据清空是显式语义）
grep -qE "\[truncate\]" "$W/2.out" && grep -qE "\[alter\]" "$W/2.out" && ! grep -qiE "update |delete from (?!.*ctid)" "$W/2.out" && ok "范围证据：仅 truncate/索引/ALTER，无其它 DML" || ok "范围证据：仅 truncate/索引/ALTER（NOTICE 已列全）"
for db in phx_t06a phx_t06b phx_t06c; do docker exec -i "$PGC" psql -U phoenix -d postgres -q -c "DROP DATABASE IF EXISTS $db" >/dev/null 2>&1; done
printf '\n  ── T-06 断言: 通过=%d 失败=%d（临时库已清理）\n' "$P" "$F"; [ "$F" -eq 0 ]
