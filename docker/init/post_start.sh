#!/bin/sh
# 两拍第二拍（plan §3 / R-06）：应用首启自建 tbl_harness_skills 后，
# 无条件重放当前版本 V*01 件（脚本自带存在性容错，幂等 no-op），补 status 列。
set -eu
VDIR=$(ls -d /releases/v*/sql 2>/dev/null | sort -V | tail -1)
for f in "$VDIR"/V*__*.sql; do
  case "$(basename "$f")" in
    V*_01__*) echo "[migrator-post] 重放 $(basename "$f")"
              psql -v ON_ERROR_STOP=1 -q -f "$f" ;;
  esac
done
echo "[migrator-post] 完成"
