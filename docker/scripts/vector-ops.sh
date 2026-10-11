#!/usr/bin/env bash
# vector-ops.sh —— 向量运维工具箱（vector-dim-flexibility R-08/R-09，T-14）
#
# 设计原则：**不依赖平台升级**（现场可能今天升级完就不再升级）⇒ 纯 shell + psql 直连数据库，
# 只要能连到 PG 就能体检/迁移/出报告；可选调用后端 API 做抽样检索。
#
# 用法：
#   vector-ops.sh --mode audit                      # 归属体检：模型↔表↔库 清单 + 异常告警
#   vector-ops.sh --mode report [--api URL --token T]  # 验收报告（R-09 五项）+ 失败项修复提示
#   vector-ops.sh --mode migrate --kb ID --to-model MID [--dry-run]  # 表-模型迁移（默认不重嵌）
#
# 连接方式（二选一）：
#   --pg-container NAME   # docker exec 进容器执行 psql（推荐，无需暴露端口）
#   或设置 PGHOST/PGPORT/PGUSER/PGPASSWORD/PGDATABASE 直连
#
# 退出码：0=通过/成功；2=报告判不通过；其它=参数/执行错误
set -uo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
# shellcheck source=lib/common.sh
[ -f "$HERE/lib/common.sh" ] && . "$HERE/lib/common.sh"

MODE=""; PG_CONTAINER=""; PGUSER="${PGUSER:-phoenix}"; PGDATABASE="${PGDATABASE:-phoenix}"
KB=""; TO_MODEL=""; DRY_RUN=0; API=""; TOKEN=""; AGENT="20"
RUNID="$(date +%Y%m%d-%H%M%S)-$$"

log()  { printf '[%s] %s\n' "$(date +%H:%M:%S)" "$*" >&2; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }
usage(){ sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'; exit 0; }

while [ $# -gt 0 ]; do
  case "$1" in
    --mode) MODE="${2:-}"; shift 2;;
    --pg-container) PG_CONTAINER="${2:-}"; shift 2;;
    --pg-user) PGUSER="${2:-}"; shift 2;;
    --pg-db) PGDATABASE="${2:-}"; shift 2;;
    --kb) KB="${2:-}"; shift 2;;
    --to-model) TO_MODEL="${2:-}"; shift 2;;
    --agent) AGENT="${2:-}"; shift 2;;
    --api) API="${2:-}"; shift 2;;
    --token) TOKEN="${2:-}"; shift 2;;
    --dry-run) DRY_RUN=1; shift;;
    -h|--help) usage;;
    *) die "未知参数：$1（--help 查看用法）";;
  esac
done
[ -n "$MODE" ] || die "必须指定 --mode audit|report|migrate|rollback"

# ---------- psql 通道 ----------
q() { # q <sql> → stdout（-tA 纯值）
  if [ -n "$PG_CONTAINER" ]; then
    docker exec -i "$PG_CONTAINER" psql -U "$PGUSER" -d "$PGDATABASE" -tAc "$1"
  else
    psql -U "$PGUSER" -d "$PGDATABASE" -tAc "$1"
  fi
}
qfile() { # qfile <file> [额外 psql 参数...] —— 事务执行
  if [ -n "$PG_CONTAINER" ]; then
    docker exec -i "$PG_CONTAINER" psql -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 "$@" -f -
  else
    psql -U "$PGUSER" -d "$PGDATABASE" -v ON_ERROR_STOP=1 "$@" -f -
  fi
}
# 前置自检：连接通道所需命令必须存在（环境三对齐，L-102）
if [ -n "$PG_CONTAINER" ]; then
  command -v docker >/dev/null 2>&1 || die "选择了 --pg-container 但 PATH 中没有 docker（macOS 提示：export PATH=\"/Applications/Docker.app/Contents/Resources/bin:\$PATH\"）"
else
  command -v psql >/dev/null 2>&1 || die "未选择 --pg-container 且 PATH 中没有 psql；请安装 psql 或改用 --pg-container"
fi

need() { need_v="$1"; have="$(q "select $need_v")"; [ -n "$have" ] || die "数据库不可达或查询失败：$need_v"; }

# 与 Java 端 ModelVectorStoreFactory 完全同口径：sha256(provider|modelName) 前 6 字节 = 12 hex
model_key_hash() { printf '%s|%s' "$1" "$2" | sha256sum | cut -c1-12; }
table_of() { # table_of <base> <modelId> → 表名（默认模型=基表）
  mid="$2"; [ -z "$mid" ] && { printf '%s' "$1"; return; }
  row="$(q "select provider||'|'||model_name||'|'||coalesce(is_default::text,'false') from tbl_data_model_config where id=$mid")"
  [ -z "$row" ] && { printf '%s' "$1"; return; }
  [ "$(echo "$row" | cut -d'|' -f3)" = "true" ] && { printf '%s' "$1"; return; }
  printf '%s__%s' "$1" "$(model_key_hash "$(echo "$row" | cut -d'|' -f1)" "$(echo "$row" | cut -d'|' -f2)")"
}

# ---------- audit：归属体检 ----------
do_audit() {
  need "1"
  printf '\n== 归属体检（模型 ↔ 表 ↔ 库）==\n'
  printf '%-6s %-28s %-8s %-26s %-34s %-7s %s\n' KBID 名称 模型ID 模型 空间键 文档 状态
  q "select kb.id||'|'||kb.name||'|'||coalesce(kb.embedding_model_id::text,'-')||'|'||coalesce(mc.model_name,'-')||'|'||coalesce(kb.model_space_key,'-')||'|'||(select count(*) from tbl_data_agent_knowledge k where k.knowledge_base_id=kb.id and k.is_deleted=0)||'|'||case when kb.embedding_model_id is null then '未绑定(待重建/搬迁)' when mc.id is null then '绑定模型已不存在' when mc.is_active is not true then '绑定模型已停用' when kb.model_space_key is null then '空间键缺失' else 'OK' end from tbl_data_knowledge_base kb left join tbl_data_model_config mc on mc.id=kb.embedding_model_id where kb.del_flag=0 order by kb.id" \
  | while IFS='|' read -r id name mid mname skey docs st; do
      printf '%-6s %-28s %-8s %-26s %-34s %-7s %s\n' "$id" "$name" "$mid" "$mname" "$skey" "$docs" "$st"
    done
  printf '\n== 向量表清单（行数 / 维度 / hnsw 索引）==\n'
  TABLES="$(q "select c.relname||'|'||coalesce(a.atttypmod::text,'-')||'|'||coalesce((select count(*) from pg_indexes p where p.tablename=c.relname and p.indexdef ilike '%hnsw%'),0)::text from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relkind='r' and a.attname='embedding' order by c.relname")"
  printf '%s\n' "$TABLES" | while IFS='|' read -r t dims hnsw; do
      [ -z "$t" ] && continue
      n="$(q "select count(*) from \"$t\"")"
      owner=""
      MODELS="$(q "select id||'|'||provider||'|'||model_name||'|'||coalesce(is_default::text,'false') from tbl_data_model_config where model_type='EMBEDDING' and coalesce(is_deleted,0)=0 order by id")"
      printf '%s\n' "$MODELS" | while IFS='|' read -r mid prov mname isdef; do
        [ -z "$mid" ] && continue
        if [ "$isdef" = "true" ]; then exp="tbl_vector_store_simple_data"; else exp="tbl_vector_store_simple_data__$(model_key_hash "$prov" "$mname")"; fi
        [ "$exp" = "$t" ] && printf '%s:%s' "$mid" "$mname" > /tmp/.phx_owner_$$
      done
      owner="$(cat /tmp/.phx_owner_$$ 2>/dev/null)"; rm -f /tmp/.phx_owner_$$
      printf '  %-46s 行=%-6s 维度=%-6s hnsw=%-2s 归属模型=%s\n' "$t" "$n" "$dims" "$hnsw" "${owner:-（无对应模型配置 ⇒ 孤儿表）}"
    done
  printf '\n== 专属表与模型映射核对 ==\n'
  q "select id||'|'||provider||'|'||model_name from tbl_data_model_config where model_type='EMBEDDING' and coalesce(is_deleted,0)=0 order by id" \
  | while IFS='|' read -r id prov mname; do
      if [ "$(q "select is_default from tbl_data_model_config where id=$id")" = "t" ]; then
        printf '  模型 %-5s %-8s %-28s → 基表 tbl_vector_store_simple_data\n' "$id" "$prov" "$mname"
      else
        printf '  模型 %-5s %-8s %-28s → tbl_vector_store_simple_data__%s\n' "$id" "$prov" "$mname" "$(model_key_hash "$prov" "$mname")"
      fi
    done
}

# ---------- migrate：表-模型迁移（不重嵌） ----------
do_migrate() {
  [ -n "$KB" ] || die "--mode migrate 需要 --kb"
  [ -n "$TO_MODEL" ] || die "--mode migrate 需要 --to-model"
  base="tbl_vector_store_simple_data"
  from_bind="$(q "select embedding_model_id from tbl_data_knowledge_base where id=$KB and del_flag=0")"
  [ -n "$(q "select 1 from tbl_data_knowledge_base where id=$KB and del_flag=0")" ] || die "知识库不存在：$KB"
  [ -n "$(q "select 1 from tbl_data_model_config where id=$TO_MODEL")" ] || die "目标模型不存在：$TO_MODEL"
  [ -z "$from_bind" ] && from_bind="$(q "select id from tbl_data_model_config where model_type='EMBEDDING' and is_default limit 1")"
  from_table="$(table_of "$base" "$from_bind")"
  to_table="$(table_of "$base" "$TO_MODEL")"
  ids="$(q "select string_agg(id::text,',' order by id) from tbl_data_agent_knowledge where knowledge_base_id=$KB and is_deleted=0")"
  src_rows=0; [ -n "$ids" ] && src_rows="$(q "select count(*) from \"$from_table\" where metadata->>'agentKnowledgeId' in ($(echo "$ids" | sed "s/[^,]*/'&'/g"))" 2>/dev/null || echo 0)"
  to_dims="$(q "select coalesce((select a.atttypmod::text from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relname='$to_table' and a.attname='embedding'),'-')")"
  src_dims="$(q "select coalesce((select a.atttypmod::text from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relname='$from_table' and a.attname='embedding'),'-')")"
  space_key="$(q "select provider||'-'||model_name||'-'||coalesce((select a.atttypmod::text from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relname='$to_table' and a.attname='embedding'),'?') from tbl_data_model_config where id=$TO_MODEL")"

  printf '\n== 迁移计划 ==\n'
  printf '  KB=%-6s 来源表=%-46s 维度=%s 行数=%s\n' "$KB" "$from_table" "$src_dims" "$src_rows"
  [ "$to_dims" = "-" ] && to_dims="$src_dims"   # 目标表未建 ⇒ 按源表维度建（逐行拷贝，同一向量空间）
  [ "$src_dims" = "-" ] && die "源表 $from_table 不存在或无数值维度，无法搬迁"
  space_key="$(q "select provider||'-'||model_name||'-'||'$to_dims' from tbl_data_model_config where id=$TO_MODEL")"
  printf '  目标模型=%-6s 目标表=%-46s 维度=%s 空间键=%s\n' "$TO_MODEL" "$to_table" "$to_dims" "$space_key"
  printf '  条目数=%s 不重嵌（逐行原样拷贝 embedding）\n' "$(echo "$ids" | awk -F, '{print NF}')"
  if [ "$to_dims" != "-" ] && [ "$src_dims" != "-" ] && [ "$to_dims" != "$src_dims" ]; then
    die "维度不一致（来源 $src_dims / 目标 $to_dims）⇒ 应走重建，不可搬迁"
  fi
  if [ "$DRY_RUN" = "1" ]; then
    printf '\n  [--dry-run] 仅打印，不执行任何写操作。将执行：\n'
    printf '    ① CREATE TABLE IF NOT EXISTS "%s" (id uuid DEFAULT uuid_generate_v4() PRIMARY KEY, content text, metadata json, embedding vector(%s)) + hnsw 索引\n' "$to_table" "${to_dims:--}"
    printf '    ② INSERT INTO "%s" (id,content,metadata,embedding) SELECT uuid_generate_v4(),content,metadata,embedding FROM "%s" WHERE metadata->>%s IN (…)\n' "$to_table" "$from_table" "'agentKnowledgeId'"
    printf '    ③ UPDATE tbl_data_knowledge_base SET embedding_model_id=%s, model_space_key=%s WHERE id=%s\n' "$TO_MODEL" "'$space_key'" "$KB"
    return 0
  fi
  # 真实执行（单事务；mktemp 采用 L-103 修正写法：模板以 XXXXXX 结尾 + 非空守卫）
  SQLF="$(mktemp "${TMPDIR:-/tmp}/phx-vector-ops.XXXXXX")"
  [ -n "$SQLF" ] || die "mktemp 失败"
  trap 'rm -f "$SQLF"' EXIT
  {
    echo "BEGIN;"
    echo "CREATE TABLE IF NOT EXISTS \"$to_table\" (id uuid DEFAULT uuid_generate_v4() PRIMARY KEY, content text, metadata json, embedding vector(${to_dims:--}));"
    echo "CREATE INDEX IF NOT EXISTS \"${to_table}_index\" ON \"$to_table\" USING hnsw (embedding vector_cosine_ops);"
    [ -n "$ids" ] && echo "INSERT INTO \"$to_table\" (id,content,metadata,embedding) SELECT uuid_generate_v4(),content,metadata,embedding FROM \"$from_table\" WHERE metadata->>'agentKnowledgeId' IN ($(echo "$ids" | sed "s/[^,]*/'&'/g"));"
    echo "UPDATE tbl_data_knowledge_base SET embedding_model_id=$TO_MODEL, model_space_key='$space_key', update_time=now() WHERE id=$KB;"
    echo "COMMIT;"
  } > "$SQLF"
  [ -s "$SQLF" ] || die "临时 SQL 生成失败（空文件）"
  # 注意：qfile 用 `-f -` 从 **stdin** 读脚本，必须重定向文件；否则会把文件名当参数、SQL 静默不执行（实测踩坑）
  out="$(qfile < "$SQLF" 2>&1)" || { printf '%s\n' "$out" >&2; die "迁移执行失败"; }
  printf '%s\n' "$out" | grep -qE "COMMIT" || die "迁移未提交（输出异常）：$(printf '%s' "$out" | head -c 200)"
  new_bind="$(q "select embedding_model_id from tbl_data_knowledge_base where id=$KB")"
  new_rows="$(q "select count(*) from \"$to_table\" where metadata->>'agentKnowledgeId' in ($(echo "$ids" | sed "s/[^,]*/'&'/g"))" 2>/dev/null || echo 0)"
  printf '\n== 迁移完成 ==\n  绑定=%s 空间键=%s 目标表行数=%s（来源 %s）\n' "$new_bind" "$(q "select model_space_key from tbl_data_knowledge_base where id=$KB")" "$new_rows" "$src_rows"
  [ "$new_rows" = "$src_rows" ] || die "对账失败：目标 $new_rows ≠ 来源 $src_rows"
  printf '  对账通过（行数一致），**未发生嵌入调用**（纯搬运）\n'
}

# ---------- rollback：回滚到旧表/旧绑定（回滚窗口，T-16） ----------
# 语义：迁移/重建**从不删除旧表旧行** ⇒ 回滚 = 把绑定改回旧模型并核对旧表数据仍在；
#       目标表数据默认保留（--prune-target 才清理），便于二次核对与再迁移。
do_rollback() {
  [ -n "$KB" ] || die "--mode rollback 需要 --kb"
  [ -n "$TO_MODEL" ] || die "--mode rollback 需要 --to-model（回滚目标＝旧模型）"
  base="tbl_vector_store_simple_data"
  [ -n "$(q "select 1 from tbl_data_knowledge_base where id=$KB and del_flag=0")" ] || die "知识库不存在：$KB"
  [ -n "$(q "select 1 from tbl_data_model_config where id=$TO_MODEL")" ] || die "回滚目标模型不存在：$TO_MODEL"
  cur="$(q "select coalesce(embedding_model_id::text,'-') from tbl_data_knowledge_base where id=$KB")"
  old_table="$(table_of "$base" "$TO_MODEL")"
  ids="$(q "select string_agg(id::text,',' order by id) from tbl_data_agent_knowledge where knowledge_base_id=$KB and is_deleted=0")"
  rows=0; [ -n "$ids" ] && rows="$(q "select count(*) from \"$old_table\" where metadata->>'agentKnowledgeId' in ($(echo "$ids" | sed "s/[^,]*/'&'/g"))" 2>/dev/null || echo 0)"
  space_key="$(q "select provider||'-'||model_name||'-'||coalesce((select a.atttypmod::text from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relname='$old_table' and a.attname='embedding'),'?') from tbl_data_model_config where id=$TO_MODEL")"
  printf '\n== 回滚计划 ==\n  当前绑定=%s → 回滚到模型 %s（表 %s）\n  旧表本库行数=%s（迁移/重建不删旧行 ⇒ 回滚窗口有效）\n' "$cur" "$TO_MODEL" "$old_table" "$rows"
  if [ "$rows" = "0" ]; then printf '  警告：旧表无该库向量 ⇒ 回滚后检索可能命中 0（请确认是否应改走重建）\n'; fi
  if [ "$DRY_RUN" = "1" ]; then printf '\n  [--dry-run] 仅打印。将执行：UPDATE tbl_data_knowledge_base SET embedding_model_id=%s, model_space_key=%s WHERE id=%s;\n' "$TO_MODEL" "'$space_key'" "$KB"; return 0; fi
  q "update tbl_data_knowledge_base set embedding_model_id=$TO_MODEL, model_space_key='$space_key', update_time=now() where id=$KB" >/dev/null
  now="$(q "select coalesce(embedding_model_id::text,'-')||'|'||coalesce(model_space_key,'-') from tbl_data_knowledge_base where id=$KB")"
  printf '\n== 回滚完成 ==\n  绑定=%s 旧表行数=%s\n' "$now" "$rows"
  printf '  目标表数据默认保留（如需清理：手工 DROP/清行；本脚本不做破坏性操作）\n'
}

# ---------- report：验收报告（R-09 五项） ----------
do_report() {
  need "1"
  fails=0
  printf '\n== 向量迁移/重建验收报告（R-09）==\n  run=%s\n' "$RUNID"
  printf '\n[1/5] 维度\n'
  bad="$(q "select c.relname||'='||a.atttypmod from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relkind='r' and a.attname='embedding'")"
  printf '%s\n' "$bad" | while IFS= read -r line; do [ -n "$line" ] && printf '  %s\n' "$line"; done
  orphan=0
  MODELS="$(q "select provider||'|'||model_name from tbl_data_model_config where model_type='EMBEDDING' and coalesce(is_deleted,0)=0")"
  for t in $(q "select relname from pg_class where c.relkind='r' and relname like 'tbl_vector_store%\_\_%'" 2>/dev/null || q "select relname from pg_class where relkind='r' and relname like 'tbl_vector_store%\_\_%'"); do
    [ -z "$t" ] && continue
    hit=0
    for m in $MODELS; do
      prov="$(printf '%s' "$m" | cut -d'|' -f1)"; mname="$(printf '%s' "$m" | cut -d'|' -f2)"
      h="$(model_key_hash "$prov" "$mname")"
      case "$t" in *"__$h") hit=1;; esac
    done
    [ "$hit" = "0" ] && orphan=$((orphan+1))
  done
  [ "${orphan:-0}" = "0" ] && printf '  孤儿专属表=0 ✅\n' || { printf '  孤儿专属表=%s ❌（无对应模型配置）\n' "$orphan"; fails=$((fails+1)); printf '    修复：DROP TABLE 该表，或恢复对应模型配置\n'; }

  printf '\n[2/5] 约束（id 主键/非空）\n'
  miss="$(q "select count(*) from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relkind='r' and a.attname='embedding' and not exists (select 1 from pg_constraint p where p.conrelid=c.oid and p.contype='p')")"
  if [ "${miss:-0}" = "0" ]; then printf '  所有向量表均有主键 ✅\n'; else
    printf '  缺主键的表数=%s ❌\n' "$miss"; fails=$((fails+1))
    for t in $(q "select c.relname from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relkind='r' and a.attname='embedding' and not exists (select 1 from pg_constraint p where p.conrelid=c.oid and p.contype='p')"); do
      printf '    修复：ALTER TABLE "%s" ADD PRIMARY KEY (id);\n' "$t"
    done
  fi

  printf '\n[3/5] 索引（hnsw）\n'
  noidx="$(q "select count(*) from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relkind='r' and a.attname='embedding' and not exists (select 1 from pg_indexes p where p.tablename=c.relname and p.indexdef ilike '%hnsw%')")"
  if [ "${noidx:-0}" = "0" ]; then printf '  所有向量表均有 hnsw 索引 ✅\n'; else
    printf '  缺 hnsw 的表数=%s ❌\n' "$noidx"; fails=$((fails+1))
    for t in $(q "select c.relname from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relkind='r' and a.attname='embedding' and not exists (select 1 from pg_indexes p where p.tablename=c.relname and p.indexdef ilike '%hnsw%')"); do
      printf '    修复：CREATE INDEX IF NOT EXISTS "%s_index" ON "%s" USING hnsw (embedding vector_cosine_ops);\n' "$t" "$t"
    done
  fi

  printf '\n[4/5] 重嵌完成率（有向量条目 / 条目总数，按库）\n'
  KBS="$(q "select kb.id||'|'||kb.name||'|'||(select count(*) from tbl_data_agent_knowledge k where k.knowledge_base_id=kb.id and k.is_deleted=0) from tbl_data_knowledge_base kb where kb.del_flag=0 order by kb.id")"
  for row in $KBS; do
      kid="$(printf '%s' "$row" | cut -d'|' -f1)"; kname="$(printf '%s' "$row" | cut -d'|' -f2)"; total="$(printf '%s' "$row" | cut -d'|' -f3)"
      # 跨表 **distinct 并集**（同一向量可能存在于多张表，重复计数会出现 >100% —— 实测踩坑）
      VTABS="$(q "select c.relname from pg_class c join pg_attribute a on a.attrelid=c.oid join information_schema.columns i on i.table_name=c.relname and i.column_name='metadata' where c.relkind='r' and a.attname='embedding'")"
      union=""
      for t in $VTABS; do
        [ -z "$t" ] && continue
        part="select metadata->>'agentKnowledgeId' as akid from \"$t\""
        if [ -z "$union" ]; then union="$part"; else union="$union union $part"; fi
      done
      withvec=0
      if [ -n "$union" ]; then
        withvec="$(q "select count(*) from ($union) u join tbl_data_agent_knowledge k on k.id::text = u.akid and k.knowledge_base_id=$kid and k.is_deleted=0" 2>/dev/null || echo 0)"
      fi
      pct=0; [ "${total:-0}" -gt 0 ] && pct=$((withvec * 100 / total))
      printf '  库 %-5s %-26s 有向量 %s/%-4s (%s%%)\n' "$kid" "$kname" "$withvec" "$total" "$pct"
  done
  jfail="$(q "select count(*) from tbl_data_kb_rebuild_job where status in ('FAILED','PARTIAL')")"
  [ "${jfail:-0}" = "0" ] && printf '  重建任务失败/部分失败=0 ✅\n' || printf '  重建任务失败/部分失败=%s ⚠️（见 tbl_data_kb_rebuild_job）\n' "$jfail"

  DIFF="$(q "select string_agg(c.relname, ', ') from pg_class c join pg_attribute a on a.attrelid=c.oid where c.relkind='r' and a.attname='embedding' and not exists (select 1 from information_schema.columns i where i.table_name=c.relname and i.column_name='metadata')")"
  [ -n "$DIFF" ] && printf '  说明：%s 结构独立（无 metadata 列），不参与条目级重嵌率统计\n' "$DIFF"

  printf '\n[5/5] 抽样检索\n'
  if [ -n "$API" ] && [ -n "$TOKEN" ]; then
    resp="$(curl -s -m 60 -X POST "$API/api/knowledge-base/retrieval-probe" -H "Content-Type: application/json" \
        -H "phoenix-token: $TOKEN" -d "{\"agentId\":\"$AGENT\",\"docVectorType\":\"agentKnowledge\",\"query\":\"验收抽样\",\"topK\":5}" || true)"
    hits="$(printf '%s' "$resp" | sed -n 's/.*"hitCount":\([0-9-]*\).*/\1/p')"
    if [ -n "$hits" ] && [ "$hits" -ge 0 ]; then printf '  agent=%s 命中=%s（API 实测）✅\n' "$AGENT" "$hits"
    else printf '  抽样检索调用失败或未返回 hitCount ❌ 响应=%s\n' "$(printf '%s' "$resp" | head -c 160)"; fails=$((fails+1)); fi
  else
    printf '  SKIP（未提供 --api/--token；本机可用：--api http://127.0.0.1:8066 --token <admin token>）\n'
    printf '  说明：报告其余四项为纯 SQL 判定，**不依赖平台升级**（R-08）\n'
  fi

  printf '\n== 结论 ==\n'
  if [ "$fails" = "0" ]; then printf '  PASS（可判定项全绿）\n'; return 0
  else printf '  FAIL（%s 项不通过，修复语句见上）\n' "$fails"; return 2; fi
}

case "$MODE" in
  audit)   do_audit;;
  migrate) do_migrate;;
  rollback) do_rollback;;
  report)  do_report || exit 2;;
  *) die "未知模式：$MODE";;
esac
