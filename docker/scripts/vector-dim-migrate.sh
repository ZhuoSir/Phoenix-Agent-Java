#!/usr/bin/env bash
# Phoenix 向量维度迁移（独立运维脚本 · R-04 / v2.1.0 release-delivery-hardening）
# 用法:
#   vector-dim-migrate.sh --dims 1024 [--pg-container phoenix-postgres-1] [--table T] [--dry-run] [--yes]
# 语义:
#   1) 迁移前打印快照（表 / 维度 / 行数）
#   2) 逐表：删除 embedding 上的**性能索引**（排除承载主键/唯一约束的索引）→ TRUNCATE 旧向量 → ALTER TYPE vector(N) → 重建同名索引
#   3) 迁移后自检：所有含 embedding 列的表维度 == 目标维度，且每表 id 上有唯一约束；不通过则打印可照做的修复语句
#   4) --dry-run：整体包在事务里并 ROLLBACK（数据不变，仅验证）
#   5) 非 dry-run 必须显式 --yes（TRUNCATE 会清空旧向量——换模型后旧向量语义作废）
# 退出码: 0=成功且自检通过 / 1=自检不通过（已打印修复语句） / 2=用法或环境错误
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
[ -f "$HERE/lib/common.sh" ] && . "$HERE/lib/common.sh"
PHX_LOG_FILE="${PHX_LOG_FILE:-$HOME/.phoenix/vector-dim-migrate-$(date +%Y%m%d-%H%M%S).log}"
log(){ printf '[%s] [%s] %s\n' "$(date '+%F %T')" "$1" "$2" | tee -a "$PHX_LOG_FILE" >&2; }
die(){ log ERROR "$2"; exit "${1:-2}"; }

DIMS=""; DRY=0; YES=0; PGC="${PHX_PG_CONTAINER:-phoenix-postgres-1}"; ONLY=""
PGUSER="${PHX_PG_USER:-phoenix}"; PGDB="${PHX_PG_DB:-phoenix}"
while [ $# -gt 0 ]; do
  case "$1" in
    --dims) DIMS="$2"; shift 2;;
    --pg-container) PGC="$2"; shift 2;;
    --table) ONLY="$2"; shift 2;;
    --dry-run) DRY=1; shift;;
    --yes) YES=1; shift;;
    -h|--help) sed -n '2,14p' "$0"; exit 0;;
    *) die 2 "未知参数: $1（--help 看用法）";;
  esac
done
case "$DIMS" in ''|*[!0-9]*) die 2 "--dims 必须为正整数（如 --dims 1024）";; esac
[ "$DRY" -eq 1 ] || [ "$YES" -eq 1 ] || die 2 "非 dry-run 必须显式 --yes（TRUNCATE 会清空旧向量）"

command -v docker >/dev/null 2>&1 || die 2 "环境自检失败: docker 不在 PATH"
docker exec "$PGC" true >/dev/null 2>&1 || die 2 "环境自检失败: 容器不可达 pg-container=${PGC}"
PSQL(){ docker exec -i "$PGC" psql -U "$PGUSER" -d "$PGDB" -v ON_ERROR_STOP=1 -q "$@"; }

TABLES_FILTER=""
[ -n "$ONLY" ] && TABLES_FILTER=" AND c.relname = '$ONLY'"
log INFO "迁移开始 目标维度=${DIMS} dry-run=${DRY} 容器=${PGC} 库=${PGDB} 范围=${ONLY:-全部含embedding列的表}"

# ---------- 1) 迁移前快照 ----------
log INFO "迁移前快照:"
PSQL -tAc "SELECT '  表='||c.relname||' 维度='||a.atttypmod||' 行数='||(xpath('/row/c/text()', query_to_xml('SELECT count(*) c FROM '||quote_ident(c.relname), false, true, '')))[1]::text
           FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid
           WHERE c.relkind='r' AND a.attname='embedding'${TABLES_FILTER} ORDER BY 1" | tee -a "$PHX_LOG_FILE" >&2

# ---------- 1b) 迁移前预检：重复 id（迁移会 TRUNCATE，之后无从查起；且缺失唯一约束会引发 ON CONFLICT 报错 BUG-195）----------
DUP_BAD=0
for T in $(docker exec -i "$PGC" psql -U "$PGUSER" -d "$PGDB" -tAc "SELECT c.relname FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid WHERE c.relkind='r' AND a.attname='embedding'${TABLES_FILTER} ORDER BY 1"); do
  DN="$(docker exec -i "$PGC" psql -U "$PGUSER" -d "$PGDB" -tAc "SELECT count(*) FROM (SELECT id FROM ${T} GROUP BY id HAVING count(*)>1) d" 2>/dev/null || echo 0)"
  if [ "${DN:-0}" != "0" ]; then
    log ERROR "迁移前预检: 表=${T} 有 ${DN} 组重复 id（未静默删数据）⇒ 查重: SELECT id,count(*) FROM ${T} GROUP BY id HAVING count(*)>1; 去重: DELETE FROM ${T} a USING ${T} b WHERE a.id=b.id AND a.ctid>b.ctid;"
    DUP_BAD=1
  fi
done

# ---------- 2) 迁移 SQL（dry-run 时包事务并回滚） ----------
SQLF="$(mktemp "${TMPDIR:-/tmp}/phx-vdm-XXXXXX")"   # 注意：模板必须以 XXXXXX 结尾（macOS mktemp 不允许后缀，L-103）
[ -n "$SQLF" ] || die 2 "临时 SQL 文件创建失败"
{
  [ "$DRY" -eq 1 ] && echo "BEGIN;"
  cat <<SQL
DO \$\$
DECLARE d int := ${DIMS}; t text; i text; idx text[];
BEGIN
  FOR t IN SELECT c.relname FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid
           WHERE c.relkind='r' AND a.attname='embedding'${TABLES_FILTER} ORDER BY 1 LOOP
    SELECT array_agg(ic.relname) INTO idx
      FROM pg_index x JOIN pg_class ic ON ic.oid = x.indexrelid
      JOIN pg_attribute a2 ON a2.attrelid = x.indrelid AND a2.attnum = ANY(x.indkey)
     WHERE x.indrelid = t::regclass AND a2.attname = 'embedding'
       AND NOT EXISTS (SELECT 1 FROM pg_constraint con WHERE con.conindid = ic.oid)
       AND ic.relname NOT LIKE '%_pkey' AND ic.relname NOT LIKE '%_key';
    IF idx IS NOT NULL THEN
      FOREACH i IN ARRAY idx LOOP
        RAISE NOTICE '[drop idx] %.%', t, i;
        EXECUTE format('DROP INDEX IF EXISTS %I', i);
      END LOOP;
    END IF;
    RAISE NOTICE '[truncate] %', t;
    EXECUTE format('TRUNCATE TABLE %I', t);
    EXECUTE format('ALTER TABLE %I ALTER COLUMN embedding TYPE vector(%s)', t, d);
    RAISE NOTICE '[alter] % -> vector(%)', t, d;
    IF idx IS NOT NULL THEN
      FOREACH i IN ARRAY idx LOOP
        EXECUTE format('CREATE INDEX IF NOT EXISTS %I ON %I USING hnsw (embedding vector_cosine_ops)', i, t);
        RAISE NOTICE '[create idx] %.% (hnsw)', t, i;
      END LOOP;
    END IF;
  END LOOP;
END \$\$;
-- 自检输出（供脚本判定；格式: 表|维度|id是否有唯一约束）
SELECT c.relname || '|' || a.atttypmod || '|' ||
       CASE WHEN EXISTS (SELECT 1 FROM pg_index x JOIN pg_attribute a2 ON a2.attrelid=x.indrelid AND a2.attnum=ANY(x.indkey)
                         WHERE x.indrelid=c.oid AND x.indisunique AND a2.attname='id') THEN 't' ELSE 'f' END
FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid
WHERE c.relkind='r' AND a.attname='embedding'${TABLES_FILTER} ORDER BY 1;
SQL
  [ "$DRY" -eq 1 ] && echo "ROLLBACK;"
} > "$SQLF"
OUT="$(PSQL -tA -f - < "$SQLF" 2>&1 | tee -a "$PHX_LOG_FILE")"   # -tA: 自检行必须是裸 "表|维度|t/f"，便于判定

# ---------- 3) 自检判定 + 修复语句（依据【迁移事务内】的自检输出；dry-run 回滚后实态仍是旧值，不能用它判定）----------
BAD=0
CHECK="$(printf '%s\n' "$OUT" | grep -E '^[A-Za-z0-9_]+\|[0-9]+\|[tf]$' || true)"
[ -n "$CHECK" ] || { log ERROR "未取得自检输出（psql 异常？）"; exit 2; }
while IFS='|' read -r T D U; do
  [ -n "${T:-}" ] || continue
  log INFO "自检 表=${T} 维度=${D} id唯一约束=${U}"
  if [ "$D" != "$DIMS" ]; then log ERROR "自检不通过: 表=${T} 维度=${D} 期望=${DIMS} ⇒ 该表未迁移成功"; BAD=1; fi
  if [ "$U" != "t" ]; then
    log ERROR "自检不通过: 表=${T} 缺 id 唯一约束 ⇒ 修复语句: ALTER TABLE ${T} ADD CONSTRAINT ${T}_pkey PRIMARY KEY (id);"
    BAD=1
  fi
done <<EOF
$CHECK
EOF
rm -f "$SQLF"
if [ "$DRY" -eq 1 ]; then
  REAL="$(docker exec -i "$PGC" psql -U "$PGUSER" -d "$PGDB" -tAc "SELECT string_agg(c.relname||'='||a.atttypmod, ', ') FROM pg_class c JOIN pg_attribute a ON a.attrelid=c.oid WHERE c.relkind='r' AND a.attname='embedding'${TABLES_FILTER}")"
  log INFO "dry-run 已回滚，实态未变: ${REAL}"
fi
if [ "${DUP_BAD:-0}" -ne 0 ]; then BAD=1; fi
if [ "$BAD" -eq 0 ]; then
  log INFO "迁移完成且自检通过 目标维度=${DIMS} 模式=$([ "$DRY" -eq 1 ] && echo dry-run || echo 已执行)"
  exit 0
fi
log ERROR "迁移自检存在不通过项（见上方修复语句）；日志=${PHX_LOG_FILE}"; exit 1
