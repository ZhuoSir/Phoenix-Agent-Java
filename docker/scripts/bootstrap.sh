#!/usr/bin/env bash
# Phoenix 一键 bootstrap（R-10/T-11）：源码 → 引擎 → 打包 → 安装 → 收据，单机全链
# 用法: cd <源码根> && bash docker/scripts/bootstrap.sh [--version V] [--project P] [--port N] [--timeout S] [--offline]
# 幂等: 重跑自动续接（package/install 内部状态机各自断点续传；引擎段探测跳过）
# 兼容契约: bash 3.2；失败出口唯一 phx_fail
# shellcheck disable=SC1091
set -uo pipefail
SRC=$(cd "$(dirname "$0")/../.." && pwd)
. "$SRC/docker/scripts/lib/common.sh"

VERSION=""; PROJECT=phoenix; PORT=""; TIMEOUT=300; OFFLINE=0; MIRROR_OPT=""
while [ $# -gt 0 ]; do
  case "$1" in
    --version) VERSION="$2"; shift 2;;
    --project) PROJECT="$2"; shift 2;;
    --port) PORT="$2"; shift 2;;
    --timeout) TIMEOUT="$2"; shift 2;;
    --offline) OFFLINE=1; shift;;
    --mirror) MIRROR_OPT="$2"; shift 2;;   # 透传 package.sh：竞速只测HEAD不测带宽，慢源可手工指定（bs演练实证）
    -h|--help) grep '^#' "$0" | head -5; exit 0;;
    *) echo "未知参数: $1" >&2; exit 2;;
  esac
done
if [ -z "$VERSION" ]; then VERSION=$(git -C "$SRC" describe --tags 2>/dev/null | sed 's/^v//'); fi
[ -z "$VERSION" ] && VERSION="1.5.0-local"
ARCH=$(uname -m); case "$ARCH" in x86_64) ARCH=amd64;; aarch64|arm64) ARCH=arm64;; esac
export PHX_LOG_FILE="$HOME/.phoenix/bootstrap-$(date +%Y%m%d-%H%M%S).log"   # 强制 bootstrap 前缀（source common 后再设会被其默认值占位——命名小bug同修）
phx_log_init
phx_log INFO "bootstrap 开始: src=$SRC version=$VERSION arch=$ARCH project=$PROJECT port=${PORT:-默认}"

# ── A/4 引擎就位（与 install.sh 步骤2 同款逻辑，薄复制——见 plan §1.6 被拒案） ──
if docker info >/dev/null 2>&1; then
  phx_log INFO "[A/4] 引擎已在，跳过"
elif [ "$(uname -s)" = "Darwin" ]; then
  phx_fail 1 4 "mac 不代装引擎（A-3）：请装 Docker Desktop/Colima 后重跑"
else
  phx_log INFO "[A/4] 引擎安装（get.docker.com --mirror Aliyun）"
  SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"
  curl -fsSL -m 30 https://get.docker.com -o /tmp/phx-getdocker.sh || phx_fail 1 4 "get.docker.com 不可达（网络受限？）"
  # shellcheck disable=SC2086
  $SUDO sh /tmp/phx-getdocker.sh --mirror Aliyun >>"$PHX_LOG_FILE" 2>&1 || phx_fail 1 4 "引擎安装失败（见日志）"
  $SUDO systemctl enable --now docker >/dev/null 2>&1 || $SUDO service docker start >/dev/null 2>&1 || true
  # shellcheck disable=SC2086
  [ -n "$SUDO" ] && $SUDO usermod -aG docker "$(id -un)" >/dev/null 2>&1
  docker info >/dev/null 2>&1 || phx_fail 1 4 "引擎装后仍不可达（重开终端使 docker 组生效或查 systemctl status docker）"
fi

# ── B/4 本机打包（native 架构，国内源默认） ──
phx_log INFO "[B/4] 源码打包 package.sh（--arch $ARCH --version $VERSION）"
[ "$OFFLINE" -eq 1 ] && phx_log WARN "--offline 仅作用于安装段；打包段需外网（镜像源/依赖源）"
PKG_MIRROR_ARG=""; [ -n "$MIRROR_OPT" ] && PKG_MIRROR_ARG="--mirror $MIRROR_OPT"
# shellcheck disable=SC2086
bash "$SRC/docker/scripts/package.sh" --arch "$ARCH" --version "$VERSION" $PKG_MIRROR_ARG >>"$PHX_LOG_FILE" 2>&1 || phx_fail 2 4 "打包失败（见日志尾部）"
TAR="$SRC/docker/dist/phoenix-${VERSION}-${ARCH}.tar.gz"
[ -f "$TAR" ] || phx_fail 2 4 "打包产物缺失: $TAR"
phx_log INFO "[B/4] 产包就绪: $TAR ($(du -h "$TAR" | awk '{print $1}'))——可拷贝至其它机器离线安装"

# ── C/4 解包 ──
DEST="$HOME/phoenix-install"
phx_log INFO "[C/4] 解包 → $DEST"
mkdir -p "$DEST"
tar -xzf "$TAR" -C "$DEST" || phx_fail 3 4 "解包失败"
PAYDIR="$DEST/phoenix-${VERSION}-${ARCH}"
[ -f "$PAYDIR/install.sh" ] || phx_fail 3 4 "包结构异常: $PAYDIR/install.sh 缺失"

# ── D/4 九步安装 ──
phx_log INFO "[D/4] install.sh 九步安装（project=$PROJECT timeout=${TIMEOUT}s）"
ARGS="--project $PROJECT --timeout $TIMEOUT"
[ -n "$PORT" ] && ARGS="$ARGS --port $PORT"
[ "$OFFLINE" -eq 1 ] && ARGS="$ARGS --offline"
# shellcheck disable=SC2086
bash "$PAYDIR/install.sh" $ARGS || phx_fail 4 4 "安装失败（install.sh 输出见上/日志）"
phx_log INFO "bootstrap 全链完成 ✓ 复用包保留: $TAR"
