#!/usr/bin/env bash
# Phoenix 服务控制一条入口（start/stop/restart/down/status/logs/verify/purge）
# 用法: phoenix-ctl.sh <命令> [服务名] [--project 名] [--dir docker目录]
#   start    起服务（compose up -d，等 backend healthy）
#   stop     暂停（容器保留，最快恢复）      down   撤容器（数据卷保留）
#   restart  重启全部或指定服务              status 状态+健康+端口
#   logs [svc]  跟日志(--tail 100)          verify  跑 13 项断言
#   purge    撤容器+删数据卷（毁灭性，需输入 yes 确认）
# 项目名解析优先级: --project > 环境 PHOENIX_COMPOSE_PROJECT > 包根 .phoenix-project 文件
#                > 从运行中容器名推断（phoenix*/phx-* 前缀）> compose 默认解析（name: 字段）
set -uo pipefail
CMD="${1:-help}"; [ $# -gt 0 ] && shift
PROJECT="${PHOENIX_COMPOSE_PROJECT:-}"; DIR=""; SVC=""
while [ $# -gt 0 ]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2;;
    --dir) DIR="$2"; shift 2;;
    *) SVC="$1"; shift;;
  esac
done
# 定位 docker 目录（含 docker-compose.yaml）
if [ -z "$DIR" ]; then
  PAYLOAD_DIR=$(find "$HOME/phoenix-install" -maxdepth 2 -type d -name docker 2>/dev/null | sort -r | head -1)
  for d in "$PWD" "$(cd "$(dirname "$0")/.." 2>/dev/null && pwd)" ${PAYLOAD_DIR:-}; do
    [ -f "$d/docker-compose.yaml" ] && { DIR="$d"; break; }
  done
fi
[ -n "$DIR" ] && [ -f "$DIR/docker-compose.yaml" ] || { echo "找不到 docker-compose.yaml（用 --dir 指定）" >&2; exit 2; }
cd "$DIR" || { echo "进不去 $DIR" >&2; exit 2; }
# 项目名推断
if [ -z "$PROJECT" ] && [ -f "../.phoenix-project" ]; then PROJECT=$(cat "../.phoenix-project"); fi
if [ -z "$PROJECT" ]; then
  PROJECT=$(docker ps --format '{{.Names}}' 2>/dev/null | sed -n 's/^\(.*\)-\(backend\|nginx\)-1$/\1/p' | head -1)
fi
DC="docker compose"; [ -n "$PROJECT" ] && DC="docker compose -p $PROJECT"
PORT=$(grep '^PHOENIX_HTTP_PORT=' .env 2>/dev/null | head -1 | cut -d= -f2 | awk '{print $1}'); PORT="${PORT:-9080}"
wait_healthy() {
  i=0
  while [ $i -lt 60 ]; do
    ST=$(docker inspect --format '{{.State.Health.Status}}' "${PROJECT:-phoenix-release}-backend-1" 2>/dev/null || echo waiting)
    [ "$ST" = healthy ] && { echo "backend healthy（${i}0s 内）"; return 0; }
    i=$((i+1)); sleep 10
  done
  echo "backend 600s 未 healthy——docker logs ${PROJECT:-phoenix-release}-backend-1 排查" >&2; return 1
}
case "$CMD" in
  start)   $DC up -d ${SVC:+"$SVC"} && { [ -z "$SVC" ] && wait_healthy; $DC ps; echo "访问: http://localhost:$PORT"; } ;;
  stop)    $DC stop ${SVC:+"$SVC"} && echo "已暂停（start 秒级恢复；数据无虞）" ;;
  down)    $DC down --remove-orphans && echo "容器已撤（数据卷保留；start 重建）" ;;
  restart) $DC restart ${SVC:+"$SVC"} && { [ -z "$SVC" ] && wait_healthy; } ;;
  status)  $DC ps; echo "── 端口: $PORT ──"; curl -s -m 5 -o /dev/null -w "首页HTTP:%{http_code}\n" "http://127.0.0.1:$PORT/" 2>/dev/null || echo "首页不可达" ;;
  logs)    $DC logs -f --tail 100 ${SVC:+"$SVC"} ;;
  verify)  PHOENIX_HTTP_PORT="$PORT" ${PROJECT:+PHOENIX_COMPOSE_PROJECT="$PROJECT"} sh scripts/verify.sh ;;
  purge)   echo "⚠ 将删除容器与全部数据卷（数据库/上传文件全没）。输入 yes 确认:"; read -r A; [ "$A" = "yes" ] && $DC down -v --remove-orphans && echo "已清除" || echo "取消" ;;
  *)       grep '^#' "$0" | sed -n '2,12p' ;;
esac
