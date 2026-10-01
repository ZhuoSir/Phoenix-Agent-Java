#!/bin/sh
# 备份（R-14）：pg_dump -Fc 落 ./backup/；redis AOF 卷说明见 README
set -eu
# docker CLI 自愈：非交互 shell 可能不含 Docker Desktop 路径
if ! command -v docker >/dev/null 2>&1; then
  for d in /usr/local/bin /Applications/Docker.app/Contents/Resources/bin /snap/bin; do
    [ -x "$d/docker" ] && PATH="$d:$PATH" && break
  done
fi
cd "$(dirname "$0")/.."
STAMP=$(date +%Y%m%d_%H%M%S); mkdir -p backup
docker compose exec -T postgres pg_dump -U phoenix -Fc phoenix > "backup/phx_${STAMP}.dump"
echo "[backup] backup/phx_${STAMP}.dump 完成（恢复: docker compose exec -T postgres pg_restore -U phoenix -d phoenix --clean -e < 文件）"
