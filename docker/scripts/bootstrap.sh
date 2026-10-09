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
# 强制 bootstrap 前缀（source common 后再设会被其默认值占位——命名小bug同修）；拆分声明避开 SC2155
PHX_LOG_FILE="$HOME/.phoenix/bootstrap-$(date +%Y%m%d-%H%M%S).log"
export PHX_LOG_FILE
phx_log_init
phx_log INFO "bootstrap 开始: src=$SRC version=$VERSION arch=$ARCH project=$PROJECT port=${PORT:-默认}"

# ── A/4 引擎就位（与 install.sh 步骤2 同款逻辑，薄复制——见 plan §1.6 被拒案） ──
SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"
if docker info >/dev/null 2>&1; then
  phx_log INFO "[A/4] 引擎已在，跳过"
  phx_engine_autostart "$SUDO"   # BUG-165: 引擎已在也要核实开机自启 + 讲清重启/休眠语义
elif [ "$(uname -s)" = "Darwin" ]; then
  phx_fail 1 4 "mac 不代装引擎（A-3）：请装 Docker Desktop/Colima 后重跑"
else
  # BUG-164: WSL 导入法建的发行版没有 /etc/wsl.conf ⇒ systemd 未启用 ⇒ 引擎装完也起不来
  # （systemctl enable --now docker 失败、dockerd 无守护 ⇒ 随后 docker info 判定"引擎装后仍不可达"）。
  # 这里先探测，非 systemd 且处于 WSL 时给出明确引导并停下，避免白装一遍引擎。
  if [ "$(ps -p 1 -o comm= 2>/dev/null)" != "systemd" ]; then
    if grep -qi microsoft /proc/version 2>/dev/null; then
      phx_log WARN "检测到 systemd 未启用（PID1 非 systemd）——WSL 导入法建的发行版默认如此，引擎将无法作为服务启动"
      if [ ! -f /etc/wsl.conf ]; then
        printf '[boot]\nsystemd=true\n' > /etc/wsl.conf
        phx_log INFO "已创建 /etc/wsl.conf 开启 systemd"
      elif ! grep -q 'systemd=true' /etc/wsl.conf 2>/dev/null; then
        phx_log WARN "/etc/wsl.conf 已存在但未开 systemd——请手工在其 [boot] 段加入 systemd=true"
      fi
      phx_fail 1 4 "需先启用 systemd 并重启发行版：Windows 侧执行 wsl --terminate <发行版名> 后重跑本脚本（断点续接）"
    fi
  fi
  phx_log INFO "[A/4] 引擎安装（get.docker.com --mirror Aliyun；拉不到则回退国内源）"
  # 注意：--mirror Aliyun 只管 deb 包源，安装脚本本体仍在 get.docker.com（境外）；
  # 国内受限网络 curl 会被 reset，故此处必须带国内回退（BUG-145）。
  if curl -fsSL -m 30 https://get.docker.com -o /tmp/phx-getdocker.sh 2>/dev/null; then
    phx_log INFO "  提示：官方脚本会静默下载引擎包（apt 输出被它丢进 /dev/null），通常 2-5 分钟无输出属正常（BUG-146）"
    # shellcheck disable=SC2086
    $SUDO sh /tmp/phx-getdocker.sh --mirror Aliyun >>"$PHX_LOG_FILE" 2>&1 || phx_fail 1 4 "引擎安装失败（见日志）"
  else
    phx_log WARN "get.docker.com 不可达（受限网络）——回退国内源（阿里云→清华→中科大，多源有序回退）"
    phx_install_docker_aliyun || phx_fail 1 4 "国内源装引擎失败（见日志 $PHX_LOG_FILE）"
  fi
  phx_engine_autostart "$SUDO"   # BUG-165: 设置并核实开机自启（原为静默 || true，失败了也无人知道）
  # shellcheck disable=SC2086
  [ -n "$SUDO" ] && $SUDO usermod -aG docker "$(id -un)" >/dev/null 2>&1
  docker info >/dev/null 2>&1 || phx_fail 1 4 "引擎装后仍不可达（重开终端使 docker 组生效或查 systemctl status docker）"
fi

# ── B/4 本机打包（native 架构，国内源默认） ──
phx_log INFO "[B/4] 源码打包 package.sh（--arch $ARCH --version $VERSION）"
[ "$OFFLINE" -eq 1 ] && phx_log WARN "--offline 仅作用于安装段；打包段需外网（镜像源/依赖源）"
PKG_MIRROR_ARG=""; [ -n "$MIRROR_OPT" ] && PKG_MIRROR_ARG="--mirror $MIRROR_OPT"
# 打包 40-70 分钟，全程静默会让人以为卡死（BUG-146）：日志照存、明细仍只进日志，
# 但把所有 phx_log 行（含 `====> 步骤 N/8` 标记与 INFO/WARN/ERROR）透传到屏幕。
# grep 无匹配也要算成功（否则 pipefail 会把它当失败），故 || true；package.sh 的失败仍会被捕获。
# shellcheck disable=SC2086
bash "$SRC/docker/scripts/package.sh" --arch "$ARCH" --version "$VERSION" $PKG_MIRROR_ARG 2>&1 \
  | tee -a "$PHX_LOG_FILE" \
  | { grep --line-buffered -E '\[(INFO|WARN|ERROR)\]' || true; } \
  || phx_fail 2 4 "打包失败（明细见日志尾部: tail -n 50 $PHX_LOG_FILE）"
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
