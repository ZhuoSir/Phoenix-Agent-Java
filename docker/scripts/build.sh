#!/bin/sh
# 构建两个自研镜像。默认本机架构（验证用）；交付构建：--amd64（buildx linux/amd64）
set -eu
cd "$(dirname "$0")/.."
TAG="${IMAGE_TAG:-$(grep -m1 '^IMAGE_TAG' .env 2>/dev/null | cut -d= -f2 || echo v1.2.1)}"
SETTINGS_ARG=""
[ -f maven/settings.xml ] && SETTINGS_ARG="--build-arg MAVEN_SETTINGS_FILE=docker/maven/settings.xml --secret id=none"
# settings 注入方式：直接放进构建上下文（maven/settings.xml），Dockerfile 里可选 COPY
if [ "${1:-}" = "--amd64" ]; then
  echo "[build] linux/amd64（跨架构，qemu 下耗时分钟级→十分钟级，plan 风险⑥）"
  docker buildx build --platform linux/amd64 -t "phoenix-backend:${TAG}" -f Dockerfile.backend --load ..
  docker buildx build --platform linux/amd64 -t "phoenix-frontend:${TAG}" -f Dockerfile.frontend --load ..
else
  echo "[build] 本机架构验证构建"
  docker build -t "phoenix-backend:${TAG}" -f Dockerfile.backend ..
  docker build -t "phoenix-frontend:${TAG}" -f Dockerfile.frontend ..
fi
docker pull pgvector/pgvector:pg16 >/dev/null && docker pull redis:7-alpine >/dev/null \
  && docker pull postgres:16-alpine >/dev/null && echo "[build] 基础镜像就绪；完成"
