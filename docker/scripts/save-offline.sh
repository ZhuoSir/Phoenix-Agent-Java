#!/bin/sh
# 离线交付：六镜像一 tar + sha256（R-03）。前提：已 build.sh --amd64 且 pull 齐基础镜像
set -eu
cd "$(dirname "$0")/.."
TAG="${IMAGE_TAG:-v1.2.1}"; OUT="${1:-phoenix-${TAG}-images.tar.gz}"
docker save pgvector/pgvector:pg16 redis:7-alpine postgres:16-alpine nginx:1.27-alpine \
  "phoenix-backend:${TAG}" "phoenix-frontend:${TAG}" | gzip > "$OUT"
shasum -a 256 "$OUT" | tee "${OUT}.sha256"
echo "[save] ${OUT} 完成（约 2~3G）"
