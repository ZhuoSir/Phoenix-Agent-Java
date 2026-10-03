#!/usr/bin/env bash
# Phoenix 一键安装（docker-auto-pipeline T-03）——Linux/mac 目标机，九步状态机
# 用法: 解包后在包根目录执行 bash install.sh [--timeout 秒] [--project 名] [--offline]
# 幂等: 重跑自动跳过已完成步（.phoenix-install.state）；.env 已存在绝不覆盖（R-07）
# 兼容契约: bash 3.2（L-05）；失败出口唯一 phx_fail；日志全程落盘（R-09）
# shellcheck disable=SC1091
set -uo pipefail
PAYLOAD=$(cd "$(dirname "$0")" && pwd)
LIB="$PAYLOAD/docker/scripts/lib/common.sh"
[ -f "$LIB" ] || { echo "缺 lib/common.sh——请在解包后的包根目录运行本脚本" >&2; exit 2; }
# shellcheck source=lib/common.sh disable=SC1090
. "$LIB"

TOTAL=9
TIMEOUT=300; PROJECT=phoenix; OFFLINE=0
while [ $# -gt 0 ]; do
  case "$1" in
    --timeout) TIMEOUT="$2"; shift 2;;
    --project) PROJECT="$2"; shift 2;;
    --offline) OFFLINE=1; shift;;
    --from-wsl) shift;;   # install.ps1 传入：语义与 Linux 相同（引擎装在 WSL Ubuntu 内）
    -h|--help) grep '^#' "$0" | head -5; exit 0;;
    *) echo "未知参数: $1" >&2; exit 2;;
  esac
done

export PHX_LOG_FILE="${PHX_LOG_FILE:-$HOME/.phoenix/install-$(date +%Y%m%d-%H%M%S).log}"
phx_log_init
export PHX_STATE_FILE="$PAYLOAD/.phoenix-install.state"
phx_log INFO "Phoenix 安装开始（payload=$PAYLOAD project=$PROJECT timeout=${TIMEOUT}s offline=$OFFLINE）"

# ---- manifest 读数（无 jq 依赖，sed 抽字段） ----
[ -f "$PAYLOAD/manifest.json" ] || phx_fail 1 $TOTAL "manifest.json 缺失（不是合法安装包？）"
VERSION=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$PAYLOAD/manifest.json" | head -1)
PKGARCH=$(sed -n 's/.*"arch": *"\([^"]*\)".*/\1/p' "$PAYLOAD/manifest.json" | head -1)
[ -n "$VERSION" ] && [ -n "$PKGARCH" ] || phx_fail 1 $TOTAL "manifest 字段解析失败"

SUDO=""
port_of_env() { # 读 .env 端口（容忍行尾注释）；无则默认 9080
  local p=""
  [ -f "$PAYLOAD/docker/.env" ] && p=$(grep '^PHOENIX_HTTP_PORT=' "$PAYLOAD/docker/.env" | head -1 | cut -d= -f2 | awk '{print $1}')
  echo "${p:-9080}"
}

phx_step 1 $TOTAL "环境探测（三查制度化，L-12）" && {
  MARCH=$(uname -m)
  case "$MARCH" in x86_64) MARCH=amd64;; aarch64|arm64) MARCH=arm64;; esac
  [ "$MARCH" = "$PKGARCH" ] || phx_fail 1 $TOTAL "架构不符：包=$PKGARCH 本机=$MARCH（换对应 --arch 的包）"
  if [ "$(id -u)" -ne 0 ]; then
    command -v sudo >/dev/null 2>&1 || phx_fail 1 $TOTAL "非 root 且无 sudo"
    SUDO="sudo"
  fi
  phx_disk_ok "$PAYLOAD" 8000 || phx_log WARN "磁盘可用不足 8GB，load/up 可能失败（继续，失败时回看此处）"
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    OST=$(docker info --format '{{.OSType}}' 2>/dev/null || echo unknown)
    [ "$OST" = "windows" ] && phx_fail 1 $TOTAL "检测到 Windows 原生容器引擎——跑不了 Linux 镜像，请走 install.ps1 的 WSL2 路线"
    phx_log INFO "引擎已在: OSType=$OST Server=$(docker info --format '{{.ServerVersion}}' 2>/dev/null)"
  else
    phx_log INFO "引擎未检测到（步骤 2 处理）"
  fi
  PORT=$(port_of_env)
  phx_step_mark 1
}

phx_step 2 $TOTAL "引擎就位" && {
  if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
    phx_log INFO "引擎已可用，跳过安装"
  elif [ "$(uname -s)" = "Darwin" ]; then
    phx_fail 2 $TOTAL "mac 不代装引擎（A-3）：请安装 Docker Desktop 或 Colima 后重跑本脚本"
  elif [ "$OFFLINE" -eq 1 ] && [ -d "$PAYLOAD/engine" ] && ls "$PAYLOAD/engine"/*.deb >/dev/null 2>&1; then
    phx_log INFO "离线模式：dpkg 本地安装 engine/*.deb"
    # shellcheck disable=SC2086
    $SUDO dpkg -i "$PAYLOAD"/engine/*.deb >>"$PHX_LOG_FILE" 2>&1 || phx_fail 2 $TOTAL "deb 本地安装失败（依赖不齐？见日志）"
  elif [ "$OFFLINE" -eq 1 ]; then
    phx_fail 2 $TOTAL "离线且无 engine/*.deb——参见 docs/OFFLINE-ENGINE.md 手工装引擎后重跑"
  else
    phx_log INFO "在线安装 Docker Engine（get.docker.com --mirror Aliyun）"
    if curl -fsSL -m 30 https://get.docker.com -o /tmp/phx-getdocker.sh; then
      # shellcheck disable=SC2086
      $SUDO sh /tmp/phx-getdocker.sh --mirror Aliyun >>"$PHX_LOG_FILE" 2>&1 || phx_fail 2 $TOTAL "引擎安装脚本失败（见日志；断网请加 --offline 并备 engine/*.deb）"
    else
      phx_fail 2 $TOTAL "get.docker.com 不可达（网络受限？）——加 --offline 走 deb/文档路线"
    fi
    $SUDO systemctl enable --now docker >/dev/null 2>&1 || $SUDO service docker start >/dev/null 2>&1 || true
    $SUDO usermod -aG docker "$(id -un)" >/dev/null 2>&1 || true
    docker info >/dev/null 2>&1 || phx_fail 2 $TOTAL "引擎装后仍不可达（重开终端使 docker 组生效，或检查 systemctl status docker）"
  fi
  phx_step_mark 2
}

phx_step 3 $TOTAL "完整性校验（SHA256SUMS 全量）" && {
  BADS=$(phx_sha_check_dir "$PAYLOAD" "$PAYLOAD/SHA256SUMS"); RC=$?
  [ $RC -eq 0 ] || phx_fail 3 $TOTAL "包损坏，拒装。坏文件: $BADS"
  phx_step_mark 3
}

phx_step 4 $TOTAL "镜像 load" && {
  if docker image inspect "phoenix-backend:$VERSION" >/dev/null 2>&1 && docker image inspect "phoenix-frontend:$VERSION" >/dev/null 2>&1; then
    phx_log INFO "双镜像已在（同 tag），跳过 load"
  else
    docker load -i "$PAYLOAD/images/phoenix-images.tar" >>"$PHX_LOG_FILE" 2>&1 || phx_fail 4 $TOTAL "docker load 失败（见日志）"
    docker image inspect "phoenix-backend:$VERSION" >/dev/null 2>&1 || phx_fail 4 $TOTAL "load 后仍缺 phoenix-backend:$VERSION"
  fi
  phx_step_mark 4
}

phx_step 5 $TOTAL ".env 生成/保留" && {
  ENVF="$PAYLOAD/docker/.env"
  if [ -f "$ENVF" ]; then
    # R-07 保留原值，但 IMAGE_TAG 必须跟随包版本（升级主键——否则旧 tag 起旧镜像，测试前自检抓出的真缺陷）
    CUR=$(grep '^IMAGE_TAG=' "$ENVF" | head -1 | cut -d= -f2 | awk '{print $1}')
    if [ "$CUR" != "$VERSION" ]; then
      sed -i.bak "s|^IMAGE_TAG=.*|IMAGE_TAG=$VERSION|" "$ENVF" && rm -f "$ENVF.bak"
      phx_log INFO ".env 已存在：IMAGE_TAG $CUR→$VERSION 更新，其余键保留（密码/端口不动）"
    else
      phx_log INFO ".env 已存在且 tag 一致——原值全保留（R-07）"
    fi
  else
    cp "$PAYLOAD/docker/.env.example" "$ENVF"
    if command -v openssl >/dev/null 2>&1; then PGPW=$(openssl rand -hex 16)
    else PGPW=$(od -An -tx1 -N16 /dev/urandom | tr -d ' \n'); fi
    sed -i.bak "s|^IMAGE_TAG=.*|IMAGE_TAG=$VERSION|" "$ENVF"
    sed -i.bak "s|^PG_PASSWORD=.*|PG_PASSWORD=$PGPW|" "$ENVF"
    rm -f "$ENVF.bak"; chmod 600 "$ENVF"
    echo "$PGPW" > "$PAYLOAD/.phoenix-pgpassword"; chmod 600 "$PAYLOAD/.phoenix-pgpassword"
    phx_log INFO ".env 已生成（IMAGE_TAG=$VERSION，PG_PASSWORD 随机——仅收据卡回显一次）"
  fi
  phx_step_mark 5
}

phx_step 6 $TOTAL "compose up（项目名 $PROJECT）" && {
  if docker compose version >/dev/null 2>&1; then DC="docker compose"
  elif command -v docker-compose >/dev/null 2>&1; then DC="docker-compose"
  else phx_fail 6 $TOTAL "无 docker compose 插件也无 docker-compose——装 compose 后重跑"; fi
  ( cd "$PAYLOAD/docker" && $DC -p "$PROJECT" up -d ) >>"$PHX_LOG_FILE" 2>&1 || phx_fail 6 $TOTAL "compose up 失败（见日志）"
  phx_step_mark 6
}

phx_step 7 $TOTAL "等待 healthy（${TIMEOUT}s 上限，5s 轮询——L-08 确定性等待）" && {
  i=0
  while [ $i -lt "$TIMEOUT" ]; do
    ST=$(docker inspect --format '{{.State.Health.Status}}' "${PROJECT}-backend-1" 2>/dev/null || echo missing)
    [ "$ST" = "healthy" ] && break
    i=$((i+5)); sleep 5
  done
  [ "$ST" = "healthy" ] || phx_fail 7 $TOTAL "backend 未在 ${TIMEOUT}s 内 healthy（当前=$ST；docker logs ${PROJECT}-backend-1 看详情）"
  phx_step_mark 7
}

phx_step 8 $TOTAL "verify 全断言（失败=安装失败，Q2 决议）" && {
  PORT=$(port_of_env)
  ( cd "$PAYLOAD/docker" && PHOENIX_HTTP_PORT="$PORT" sh scripts/verify.sh ) >>"$PHX_LOG_FILE" 2>&1 \
    || phx_fail 8 $TOTAL "verify 存在失败断言（详见日志，检索 PASS/FAIL）"
  phx_step_mark 8
}

phx_step 9 $TOTAL "收据卡" && {
  PORT=$(port_of_env)
  PWD_LINE="见 docker/.env 的 PG_PASSWORD"
  if [ -f "$PAYLOAD/.phoenix-pgpassword" ]; then PWD_LINE="$(cat "$PAYLOAD/.phoenix-pgpassword")（仅此一次，已存 docker/.env）"; rm -f "$PAYLOAD/.phoenix-pgpassword"; fi
  phx_receipt "$PAYLOAD/RECEIPT" "安装成功" \
    "版本" "$VERSION" \
    "架构" "$PKGARCH" \
    "访问地址" "http://localhost:$PORT" \
    "管理端账号" "admin / 123456（首登立即改密）" \
    "库密码" "$PWD_LINE" \
    "数据目录" "docker 卷 ${PROJECT}_*（/var/lib/docker/volumes/）" \
    "日志" "$PHX_LOG_FILE" >/dev/null
  cat "$PAYLOAD/RECEIPT"
  phx_log INFO "下一步: ①浏览器打开上方地址并改密 ②模型管理配置真实 API key ③日常运维 cd docker && docker compose -p $PROJECT ps/logs"
  phx_step_mark 9
}
phx_log INFO "安装完成: $PROJECT @ http://localhost:$(port_of_env)"
