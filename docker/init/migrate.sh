#!/bin/sh
# Phoenix 交付包初始化/增量迁移器（plan §3）
# 首启：基线 schema+data+admin 种子；此后按 releases 序号增量执行并记台账 tbl_phoenix_release
set -eu
PSQL="psql -v ON_ERROR_STOP=1 -q"

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
