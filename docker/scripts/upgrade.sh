#!/usr/bin/env bash
# 升级统一入口（两种场景自动判别，不造第二套流程）
#
#   A) 交付包内升级（客户机用这个）：解包后 cd 进包目录，一条命令
#        bash upgrade.sh                # 自动探测旧项目名 + 旧 .env，再调 install.sh
#        bash upgrade.sh --dry-run      # 只打印将执行什么，不动系统
#        bash upgrade.sh --project phoenix --env-from /旧安装/phoenix-2.0.0-arm64/docker/.env
#        bash upgrade.sh --online       # 目标机能出外网时不带 --offline
#      判据：包根目录存在 install.sh + manifest.json；未识别的参数原样转给 install.sh
#
#   B) 仓库开发栈升级（原有用法）：仓库任意位置执行
#        bash docker/scripts/upgrade.sh v2.1.1              # 有 releases/v2.1.1/sql 才迁移
#        bash docker/scripts/upgrade.sh v2.1.1 --no-migrate # PATCH 版无 SQL 时显式跳过
#      区别：交付包只带当前版本的 releases；开发栈用仓库 releases/ 判断
#
# 兼容：bash 3.2（L-05）；失败即退出并给出可执行的下一步提示。
set -euo pipefail

TIMEOUT=900; DRY=0; PROJ=""; ENV_FROM=""; NO_MIGRATE=0; OFFLINE=1
EXTRA=""
VER=""; MODE=""; PKG_DIR=""

# ---- docker CLI 自愈（非交互 shell 可能没有 Docker Desktop 路径）----
if ! command -v docker >/dev/null 2>&1; then
  for d in /usr/local/bin /Applications/Docker.app/Contents/Resources/bin /snap/bin; do
    if [ -x "$d/docker" ]; then PATH="$d:$PATH"; break; fi
  done
fi

run() { if [ "$DRY" = 1 ]; then printf '  [dry-run] %s\n' "$*"; else printf '  $ %s\n' "$*"; "$@"; fi; }

# ---- 参数解析 ----
SEEN_VER=0
while [ $# -gt 0 ]; do
  case "$1" in
    --project)    PROJ="${2:-}"; shift 2;;
    --env-from)   ENV_FROM="${2:-}"; shift 2;;
    --timeout)    TIMEOUT="${2:-}"; shift 2;;
    --dry-run)    DRY=1; shift;;
    --no-migrate) NO_MIGRATE=1; shift;;
    --online)     OFFLINE=0; shift;;          # 不带 --offline（目标机可出外网时的常规安装）
    -h|--help)    awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"; exit 0;;
    v[0-9]*|[0-9]*) [ "$SEEN_VER" = 0 ] && { VER="$1"; SEEN_VER=1; }; shift;;
    # 未知选项：原样连同其取值转给 install.sh（不吞参数，也不假装是版本号）
    -*)           EXTRA="$EXTRA $1"; shift
                  if [ -n "${1:-}" ] && [ "${1#-}" = "$1" ]; then EXTRA="$EXTRA $1"; shift; fi;;
    *)            echo "[upgrade] 未知参数: $1（-h 看用法）" >&2; exit 2;;
  esac
done

# ---- 场景判别 ----
is_pkg() { [ -f "$1/install.sh" ] && [ -f "$1/manifest.json" ]; }
if is_pkg "."; then
  MODE="package"; PKG_DIR="."
elif is_pkg "$(cd "$(dirname "$0")" && pwd)"; then
  MODE="package"; PKG_DIR="$(cd "$(dirname "$0")" && pwd)"
else
  MODE="repo"
fi

# 从容器（含已停止的）反查 compose 项目名 / compose 文件路径
# 传项目名时只在该项目内找，避免多栈并存拿错对象
backend_of() {
  if [ -n "${1:-}" ]; then
    docker ps -a --filter "label=com.docker.compose.project=$1" --format '{{.Names}}' 2>/dev/null \
      | grep -E -- '-backend(-[0-9]+)?$' | head -1 || true
  else
    docker ps -a --format '{{.Names}}' 2>/dev/null | grep -E -- '-backend(-[0-9]+)?$' | head -1 || true
  fi
}
proj_of()  { docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$1" 2>/dev/null || true; }
cf_of()    { docker inspect --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}' "$1" 2>/dev/null || true; }

if [ "$MODE" = package ]; then
  cd "$PKG_DIR"
  echo "[upgrade] 场景 = 交付包升级（包目录 $PKG_DIR，版本 $(grep -o '"version"[^,]*' manifest.json 2>/dev/null | head -1 | tr -d '" ' )）"

  # ① 项目名：多栈并存必须显式指定，否则会升错对象
  if [ -z "$PROJ" ]; then
    NPROJ=$(docker ps -a --format '{{.Names}}' 2>/dev/null | grep -E -- '-backend(-[0-9]+)?$' \
            | while read -r n; do proj_of "$n"; done | sort -u | sed '/^$/d' | wc -l | tr -d ' ' || true)
    if [ "${NPROJ:-0}" -gt 1 ]; then
      echo "[upgrade] ❌ 机器上有多套栈，需指明升级哪一个：" >&2
      docker ps -a --format '{{.Names}}' 2>/dev/null | grep -E -- '-backend(-[0-9]+)?$' | while read -r n; do
        echo "    容器 $n → 项目 $(proj_of "$n")" >&2
      done
      echo "    用法：bash upgrade.sh --project <项目名>" >&2
      exit 1
    fi
    C=$(backend_of "")
    if [ -z "$C" ]; then
      echo "[upgrade] ❌ 没找到已安装的 backend 容器，无法自动探测项目名。" >&2
      echo "    手动指定：bash upgrade.sh --project <项目名>" >&2
      echo "    查询：docker ps -a --format '{{.Names}}' | grep backend   （如 phoenix-backend-1 → phoenix）" >&2
      exit 1
    fi
    PROJ=$(proj_of "$C")
    [ -n "$PROJ" ] || { echo "[upgrade] ❌ 容器 $C 没有 compose 项目标签，请用 --project 指定" >&2; exit 1; }
    echo "[upgrade] 自动探测 项目名 = $PROJ（来自容器 $C）"
  fi

  # ② 旧 .env：承接它才不会换库密码/端口（丢了就连不上旧数据卷）
  if [ -z "$ENV_FROM" ]; then
    CF=$(cf_of "$(backend_of "$PROJ")")
    if [ -n "$CF" ] && [ -f "$(dirname "$CF")/.env" ]; then
      ENV_FROM="$(dirname "$CF")/.env"
    else
      # 只在常见安装位置搜（可用 PHX_ENV_SEARCH_BASES 覆盖）；跳过 git 工作树内的 .env（开发栈密码与目标环境无关）
      for base in ${PHX_ENV_SEARCH_BASES:-/opt /srv /data "$HOME"}; do
        [ -d "$base" ] || continue
        ENV_FROM=$(find "$base" -maxdepth 6 -name .env -path '*docker*' 2>/dev/null \
                   | while read -r f; do
                       git -C "$(dirname "$f")" rev-parse --is-inside-work-tree >/dev/null 2>&1 && continue
                       grep -q '^PG_PASSWORD=' "$f" 2>/dev/null && { echo "$f"; break; }
                     done | head -1 || true)
        [ -n "$ENV_FROM" ] && break
      done
    fi
    if [ -z "$ENV_FROM" ]; then
      echo "[upgrade] ❌ 没找到旧安装的 docker/.env。" >&2
      echo "    不给它，安装器会**随机生成库密码** ⇒ 新容器连不上旧数据卷（等于数据「丢了」）。" >&2
      echo "    手动指定：bash upgrade.sh --env-from <旧安装目录>/docker/.env" >&2
      echo "    查询：find / -name .env -path '*docker*' 2>/dev/null | head" >&2
      exit 1
    fi
    echo "[upgrade] 自动探测 旧 .env = $ENV_FROM"
    if [ -n "$CF" ] && [ "$(dirname "$CF")" != "$(dirname "$ENV_FROM")" ]; then
      echo "[upgrade] ⚠️ 该 .env 不在旧安装的 compose 目录（$CF）同层，请确认是目标环境的库密码；" >&2
      echo "          不确定就显式给：--env-from <旧安装目录>/docker/.env" >&2
    fi
  fi
  [ -f "$ENV_FROM" ] || { echo "[upgrade] ❌ --env-from 文件不存在：$ENV_FROM" >&2; exit 1; }

  # ③ 调 install.sh（迁移/换镜像/健康等待/verify 都在它内部，不另造流程）
  OFF_ARG="--offline "; [ "$OFFLINE" = 1 ] || OFF_ARG=""
  PLAN="bash install.sh ${OFF_ARG}--timeout $TIMEOUT --project $PROJ --env-from $ENV_FROM$EXTRA"
  echo "[upgrade] 即将执行：$PLAN"
  if [ "$DRY" = 1 ]; then
    printf '  [dry-run] %s\n' "$PLAN"
    exit 0
  fi
  # shellcheck disable=SC2086
  bash install.sh $OFF_ARG --timeout "$TIMEOUT" --project "$PROJ" --env-from "$ENV_FROM" $EXTRA || {
    echo "[upgrade] ❌ install.sh 失败；详见其日志（安装器幂等，修好问题后可直接重跑 bash upgrade.sh）" >&2
    exit 1
  }

  # ④ 收尾自检
  echo
  echo "[upgrade] 升级后自检（安装器第 8 步已跑过全量 verify，这里只给速览）："
  PORT=$(grep -m1 '^PHOENIX_HTTP_PORT=' "./docker/.env" 2>/dev/null | cut -d= -f2 | tr -d '[:space:]' || true)
  PORT=${PORT:-9080}
  CODE=$(curl -s -o /dev/null -m 10 -w '%{http_code}' -X POST "http://127.0.0.1:$PORT/api/privilege/auth/login" \
         -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' 2>/dev/null || echo 000)
  echo "  地址   http://127.0.0.1:$PORT"
  echo "  登录   HTTP=$CODE（200=默认口令可用；若已改密，非 200 属正常，请用实际账号登录复验）"
  docker ps --filter "name=${PROJ}-" --format '  容器   {{.Names}} | {{.Image}} | {{.Status}}'
  echo "  台账   docker exec ${PROJ}-postgres-1 psql -U phoenix -d phoenix -tAc \"select seq from tbl_phoenix_release order by seq\""
  exit 0
fi

# ---- 场景 B：仓库开发栈 ----
[ -n "$VER" ] || { echo "[upgrade] ❌ 仓库模式需要版本号：bash docker/scripts/upgrade.sh v2.1.1（-h 看用法）" >&2; exit 2; }
cd "$(dirname "$0")/.."
REPO_ROOT=$(cd ../ && pwd)
echo "[upgrade] 场景 = 仓库开发栈，版本 = $VER"
[ -n "$EXTRA" ] && echo "[upgrade] ⚠️ 仓库模式不识别这些参数，已忽略:$EXTRA"

TAG="${IMAGE_TAG:-${VER#v}}"
SQL_DIR="$REPO_ROOT/releases/$VER/sql"
if [ -d "$SQL_DIR" ] && [ "$NO_MIGRATE" = 0 ]; then
  echo "[upgrade] 迁移：执行 $SQL_DIR/V*.sql（幂等，按台账 tbl_phoenix_release 跳过已应用）"
  run docker compose -p "${COMPOSE_PROJECT_NAME:-phx210}" up -d --force-recreate --no-deps migrator
else
  echo "[upgrade] 跳过迁移（releases/$VER/sql 不存在或 --no-migrate；PATCH 版通常无新增 SQL）"
fi

# 原脚本在这里给 TAG 赋值后从未使用 ⇒ 切版本不生效，已修
if [ -f .env ] && grep -q '^IMAGE_TAG=' .env; then
  echo "[upgrade] 镜像 tag 写入 docker/.env：IMAGE_TAG=$TAG"
  run sed -i.bak "s|^IMAGE_TAG=.*|IMAGE_TAG=$TAG|" .env
  run rm -f .env.bak
else
  echo "[upgrade] 镜像 tag 本次注入：IMAGE_TAG=$TAG（docker/.env 无该项，不改文件）"
  export IMAGE_TAG="$TAG"
fi

run docker compose -p "${COMPOSE_PROJECT_NAME:-phx210}" up -d --force-recreate backend nginx
if [ -d "$SQL_DIR" ] && [ "$NO_MIGRATE" = 0 ]; then
  run docker compose -p "${COMPOSE_PROJECT_NAME:-phx210}" up -d --force-recreate --no-deps migrator-post
fi
run docker compose -p "${COMPOSE_PROJECT_NAME:-phx210}" ps
echo "[upgrade] 完成。核对镜像：docker ps --format '{{.Names}} | {{.Image}}' | grep backend"
