#!/bin/sh
# 【定位注记(docker-auto-pipeline Q3 决议)】手工链脚本——保留兼容；日常一键交付请用 package.sh(打包)+install.sh/install.ps1(安装)
# Phoenix 镜像构建。默认 thin 模式（plan D7 应急通道=本机实测通路）：
#   host 产出 jar/dist → docker/.stage → 薄镜像组装（构建零外网依赖）
# --multistage：容器内多阶段（交付机可出网时用）
set -eu
# docker CLI 自愈：非交互 shell 可能不含 Docker Desktop 路径
if ! command -v docker >/dev/null 2>&1; then
  for d in /usr/local/bin /Applications/Docker.app/Contents/Resources/bin /snap/bin; do
    [ -x "$d/docker" ] && PATH="$d:$PATH" && break
  done
fi
cd "$(dirname "$0")/.."
[ -f .env ] && . ./.env 2>/dev/null || true
TAG="${IMAGE_TAG:-v1.2.0}"
JAR_SRC="../phoenix-admin/phoenix-admin-manager/target/phoenix-admin.jar"
STAGE=.stage; mkdir -p "$STAGE"

if [ "${1:-}" = "--multistage" ]; then
  echo "[build] multistage（需出网）"
  docker build -t "phoenix-backend:${TAG}" -f Dockerfile.backend.multistage ..
  docker build -t "phoenix-frontend:${TAG}" -f Dockerfile.frontend.multistage ..
else
  echo "[build] thin 模式"
  # 1) jar：缺失或过旧则 host mvn 重编（JAVA_HOME/MVN 可用环境变量指定）
  if [ ! -f "$JAR_SRC" ]; then
    echo "[build] 无 jar，执行 host mvn package（设 JAVA_HOME 与 mvn 可用）"
    (cd .. && "${MVN:-mvn}" -q package -DskipTests -Dspring-javaformat.skip=true \
       -pl phoenix-admin/phoenix-admin-manager -am ${MVN_REPO_LOCAL:+-Dmaven.repo.local="$MVN_REPO_LOCAL"})
  fi
  NEWER=$(find ../phoenix-admin/../ -name '*.java' -newer "$JAR_SRC" 2>/dev/null | head -1) || true
  [ -n "${NEWER:-}" ] && echo "[build] ⚠️ 存在比 jar 新的源码（如 $NEWER），建议重编后重试或确认无碍" || true
  cp "$JAR_SRC" "$STAGE/phoenix-admin.jar"
  # 2) dist：host pnpm 构建
  if [ "${SKIP_WEB:-0}" != "1" ]; then
    (cd ../web-frontend && [ -d node_modules ] || pnpm install --prefer-offline)
    (cd ../web-frontend && pnpm -F @vben/web-ele build)
    rm -rf "$STAGE/dist"; cp -r ../web-frontend/apps/admin-ui/dist "$STAGE/dist"
  fi
  docker build --build-arg JRE_BASE_IMG="${JRE_BASE_IMG:-eclipse-temurin:21-jre-jammy}" \
    -t "phoenix-backend:${TAG}" -f Dockerfile.backend ..
  docker build --build-arg NGINX_BASE_IMG="${NGINX_BASE_IMG:-nginx:latest}" \
    -t "phoenix-frontend:${TAG}" -f Dockerfile.frontend ..
fi
for img in "${PGVECTOR_IMAGE:-pgvector/pgvector:pg16}" "${REDIS_IMAGE:-redis:7-alpine}" "${PGCLIENT_IMAGE:-postgres:16-alpine}"; do
  docker image inspect "$img" >/dev/null 2>&1 || docker pull "$img"
done
echo "[build] 完成：phoenix-backend:${TAG} phoenix-frontend:${TAG} + 基础镜像就绪"
