#!/bin/sh
# 【定位注记(docker-auto-pipeline Q3 决议)】手工链脚本——保留兼容；日常一键交付请用 package.sh(打包)+install.sh/install.ps1(安装)
# 目标机离线入口：导入镜像 tar → compose up（R-03）。用法：sh load-and-run.sh <images.tar.gz>
set -eu
# docker CLI 自愈：非交互 shell 可能不含 Docker Desktop 路径
if ! command -v docker >/dev/null 2>&1; then
  for d in /usr/local/bin /Applications/Docker.app/Contents/Resources/bin /snap/bin; do
    [ -x "$d/docker" ] && PATH="$d:$PATH" && break
  done
fi
cd "$(dirname "$0")/.."
TAR="${1:?用法: load-and-run.sh <images.tar.gz>}"
[ -f "${TAR}.sha256" ] && shasum -a 256 -c "${TAR}.sha256"
gunzip -c "$TAR" | docker load
[ -f .env ] || cp .env.example .env
docker compose up -d
echo "[load-and-run] 已启动；健康确认: docker compose ps；验证: sh scripts/verify.sh"
