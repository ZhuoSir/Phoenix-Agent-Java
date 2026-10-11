#!/bin/sh
# 两拍第二拍（plan §3 / R-06）：应用首启自建 tbl_harness_skills 后，
# 无条件重放当前版本 V*01 件（脚本自带存在性容错，幂等 no-op），补 status 列。
set -eu

# ===== [脚本级修复 2026-10-11] 等待 postgres 真正接受 TCP 连接 =====
# 缺因：compose 的健康检查（pg_isready）可经 unix socket 提前通过，而 TCP(5432) 尚未就绪 ⇒
#       首次 psql 连接被拒（exit 2）。此处显式重试，消除与容器启动的时序竞态。
i=0
while ! psql -c 'select 1' >/dev/null 2>&1; do
  i=$((i+1))
  if [ "$i" -gt 60 ]; then echo "[migrator] postgres TCP 5432 未就绪（等待 120s），放弃"; exit 2; fi
  sleep 2
done

VDIR=$(ls -d /releases/v*/sql 2>/dev/null | sort -V | tail -1)
for f in "$VDIR"/V*__*.sql; do
  case "$(basename "$f")" in
    V*_01__*) echo "[migrator-post] 重放 $(basename "$f")"
              psql -v ON_ERROR_STOP=1 -q -f "$f" ;;
  esac
done
echo "[migrator-post] 完成"
