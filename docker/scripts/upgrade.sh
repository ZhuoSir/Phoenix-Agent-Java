#!/bin/sh
# 升级（R-13）：放入新 releases/vX.Y.Z/ 与镜像后，重跑 migrator（增量按台账）再滚动 backend
set -eu
cd "$(dirname "$0")/.."
VER="${1:?用法: upgrade.sh <vX.Y.Z>  (需已存在 ../releases/<VER>/sql)}"
[ -d "../releases/${VER}/sql" ] || { echo "[upgrade] 拒绝执行：releases/${VER}/sql 不存在（不造第二套流程）"; exit 1; }
echo "[upgrade] 建议先执行 backup.sh；10 秒后继续（Ctrl-C 取消）"; sleep 10
docker compose up -d --force-recreate --no-deps migrator
docker compose pull 2>/dev/null || true
TAG="${IMAGE_TAG:-v1.2.1}"
docker compose up -d backend nginx
docker compose up -d --force-recreate --no-deps migrator-post
docker compose ps
