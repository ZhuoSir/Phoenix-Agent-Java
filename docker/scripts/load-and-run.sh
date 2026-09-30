#!/bin/sh
# 目标机离线入口：导入镜像 tar → compose up（R-03）。用法：sh load-and-run.sh <images.tar.gz>
set -eu
cd "$(dirname "$0")/.."
TAR="${1:?用法: load-and-run.sh <images.tar.gz>}"
[ -f "${TAR}.sha256" ] && shasum -a 256 -c "${TAR}.sha256"
gunzip -c "$TAR" | docker load
[ -f .env ] || cp .env.example .env
docker compose up -d
echo "[load-and-run] 已启动；健康确认: docker compose ps；验证: sh scripts/verify.sh"
