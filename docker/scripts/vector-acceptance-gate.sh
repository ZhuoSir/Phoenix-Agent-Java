#!/usr/bin/env bash
# vector-acceptance-gate.sh —— 向量迁移/重建效果验收门禁（R-09 五项，T-15）
#
# 作用：调用 vector-ops.sh --mode report，落一份**验收回执**（含运行命令/五项结果/判定），并按判定返回退出码。
#   退出码：0=PASS；2=FAIL（附修复语句）；3=SKIP（数据库不可达 —— 出货流程在无库环境下不阻断）
#
# 用法：vector-acceptance-gate.sh [--pg-container NAME | PG* 环境直连] [--api URL --token T] [--receipt-dir DIR]
set -uo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
ROOT="$(cd "$HERE/../.." && pwd)"
OPS="$HERE/vector-ops.sh"
PG_ARGS=(); API_ARGS=(); RECEIPT_DIR="${ROOT}/releases/v2.1.0/acceptance"
while [ $# -gt 0 ]; do
  case "$1" in
    --pg-container|--pg-user|--pg-db) PG_ARGS+=("$1" "${2:-}"); shift 2;;
    --api|--token) API_ARGS+=("$1" "${2:-}"); shift 2;;
    --receipt-dir) RECEIPT_DIR="${2:-}"; shift 2;;
    *) printf 'ERROR: 未知参数 %s\n' "$1" >&2; exit 1;;
  esac
done

RUNID="$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$RECEIPT_DIR" 2>/dev/null || true
RECEIPT="$RECEIPT_DIR/RECEIPT-vector-acceptance-${RUNID}.txt"

CMD="vector-ops.sh --mode report ${PG_ARGS[*]:-} ${API_ARGS[*]:-}"
# 注意：set -u 下空数组展开会报 unbound（实测踩坑）⇒ 用安全展开形式
report="$("$OPS" --mode report ${PG_ARGS[@]+"${PG_ARGS[@]}"} ${API_ARGS[@]+"${API_ARGS[@]}"} 2>&1)"; rc=$?
printf '%s\n' "$report"

verdict="UNKNOWN"
case "$rc" in 0) verdict="PASS";; 2) verdict="FAIL";; *) verdict="SKIP";; esac

{
  echo "向量迁移/重建效果验收回执（R-09 五项）"
  echo "run_id      : $RUNID"
  echo "generated_at: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "command     : $CMD"
  echo "verdict     : $verdict (exit=$rc)"
  echo "spec        : specs/20261011_vector-dim-flexibility (T-15 / R-09)"
  echo "----------------------------------------------------------------"
  printf '%s\n' "$report"
} > "$RECEIPT" 2>/dev/null || { printf '[WARN] 回执写入失败：%s\n' "$RECEIPT" >&2; }

printf '\n[gate] 判定=%s 退出码=%s 回执=%s\n' "$verdict" "$rc" "$RECEIPT"
exit "$rc"
