#!/bin/sh
# 【定位注记(docker-auto-pipeline Q3 决议)】手工链脚本——保留兼容；日常一键交付请用 package.sh(打包)+install.sh/install.ps1(安装)
# 离线交付：六镜像一 tar + sha256（R-03）。前提：已 build.sh --amd64 且 pull 齐基础镜像
set -eu
# docker CLI 自愈：非交互 shell 可能不含 Docker Desktop 路径
if ! command -v docker >/dev/null 2>&1; then
  for d in /usr/local/bin /Applications/Docker.app/Contents/Resources/bin /snap/bin; do
    [ -x "$d/docker" ] && PATH="$d:$PATH" && break
  done
fi
cd "$(dirname "$0")/.."
[ -f .env ] && . ./.env 2>/dev/null || true
TAG="${IMAGE_TAG:-v1.2.0}"; OUT="${1:-phoenix-${TAG}-images.tar.gz}"
docker save "${PGVECTOR_IMAGE:-pgvector/pgvector:pg16}" "${REDIS_IMAGE:-redis:7-alpine}" "${PGCLIENT_IMAGE:-postgres:16-alpine}" "${NGINX_BASE_IMG:-nginx:1.27-alpine}" \
  "phoenix-backend:${TAG}" "phoenix-frontend:${TAG}" | gzip > "$OUT"
shasum -a 256 "$OUT" | tee "${OUT}.sha256"
echo "[save] ${OUT} 完成（约 2~3G）"
