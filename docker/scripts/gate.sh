#!/usr/bin/env bash
# Phoenix 出货门禁（R-05 · v2.1.0 release-delivery-hardening）
# 用法: gate.sh --payload <解包目录> [--out <证据目录>] [--jar-image IMG] [--skip-install]
#               [--project phxgate] [--port 8190] [--timeout 900] [--allow-arch-mismatch]
# 四项自检: ①SHA256SUMS 全量 ②镜像内应用 jar 对运行用户可读 ③前置(架构/Compose v2)与声明一致 ④全新空库安装到收据
# 退出码: 0=四项全 PASS；非 0=有 FAIL（证据与失败项见日志/证据文件）
set -uo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/lib/common.sh"
phx_log_init

PAYLOAD=""; OUT="docker/dist/gate-$(date +%Y%m%d-%H%M%S)"; JAR_IMG=""; SKIP_INSTALL=0
PROJECT="phxgate-$(date +%H%M%S)-$$"; PORT=8190; TIMEOUT=900; ALLOW_MISMATCH=0
while [ $# -gt 0 ]; do
  case "$1" in
    --payload) PAYLOAD="$2"; shift 2;;
    --out) OUT="$2"; shift 2;;
    --jar-image) JAR_IMG="$2"; shift 2;;
    --skip-install) SKIP_INSTALL=1; shift;;
    --project) PROJECT="$2"; shift 2;;
    --port) PORT="$2"; shift 2;;
    --timeout) TIMEOUT="$2"; shift 2;;
    --allow-arch-mismatch) ALLOW_MISMATCH=1; shift;;
    -h|--help) sed -n '2,9p' "$0"; exit 0;;
    *) phx_log ERROR "未知参数: $1"; exit 2;;
  esac
done
[ -d "$PAYLOAD" ] || { phx_log ERROR "payload 目录不存在: ${PAYLOAD}"; exit 2; }
PAYLOAD="$(cd "$PAYLOAD" && pwd)"
VERSION="$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' "$PAYLOAD/manifest.json" | head -1)"
PKGARCH="$(sed -n 's/.*"arch": *"\([^"]*\)".*/\1/p' "$PAYLOAD/manifest.json" | head -1)"
[ -n "$VERSION" ] && [ -n "$PKGARCH" ] || { phx_log ERROR "manifest 字段解析失败: ${PAYLOAD}/manifest.json"; exit 2; }
[ -n "$JAR_IMG" ] || JAR_IMG="phoenix-backend:${VERSION}"

phx_gate_begin "$OUT" "$VERSION" "$PKGARCH"
phx_log INFO "门禁参数 payload=${PAYLOAD} jar-image=${JAR_IMG} project=${PROJECT} port=${PORT} skip-install=${SKIP_INSTALL}"

# 门禁前环境体检（L-04：先把环境事实落日志，避免把"磁盘满"误判成门禁失败）
phx_env_checkup

# ① SHA256SUMS 全量校验
phx_gate_item "sha256sums" bash -c "cd '$PAYLOAD' && if command -v sha256sum >/dev/null 2>&1; then sha256sum -c SHA256SUMS; else shasum -a 256 -c SHA256SUMS; fi" || true   # 结果由 phx_gate_item 记录并打印（不抑制，便于出货时直接看到失败项）

# ② 镜像内应用 jar 对运行用户可读（L-101：以"运行用户能否读"为判据，而非宿主权限）
phx_gate_item "jar-readable" docker run --rm --entrypoint sh "$JAR_IMG" -c 'id -u; ls -l /app/phoenix-admin.jar; test -r /app/phoenix-admin.jar' || true

# ③ 前置声明与实测一致（架构匹配 + Compose v2）
HOSTARCH="$(uname -m)"; case "$HOSTARCH" in x86_64) HOSTARCH=amd64;; aarch64|arm64) HOSTARCH=arm64;; esac
prereq() {
  if [ "$HOSTARCH" != "$PKGARCH" ] && [ "$ALLOW_MISMATCH" -ne 1 ]; then
    phx_log ERROR "架构不符 包=${PKGARCH} 本机=${HOSTARCH}（需 --allow-arch-mismatch 才放行模拟）"; return 1
  fi
  phx_compose_v2_ok || return 1
  phx_log INFO "前置一致 包架构=${PKGARCH} 本机=${HOSTARCH} Compose=v2"
}
phx_gate_item "prereq-match" prereq || true

# ④ 全新空库安装到收据（独立项目名 + 独立端口；结束只清本项目，绝不触碰其它栈）
fresh_install() {
  local envflag=""
  # 装前预清：同名项目的残留容器会让 compose recreate 撞上竞态（实测 No such container）
  ( cd "$PAYLOAD/docker" 2>/dev/null && IMAGE_TAG="$VERSION" docker compose -p "$PROJECT" down -v >/dev/null 2>&1 ) || true
  [ "$ALLOW_MISMATCH" -eq 1 ] && envflag="PHOENIX_ALLOW_ARCH_MISMATCH=1"
  ( cd "$PAYLOAD" && rm -f .phoenix-install.state && \
    env $envflag PHX_LOG_FILE="$OUT/install-${PROJECT}.log" bash install.sh --project "$PROJECT" --port "$PORT" --timeout "$TIMEOUT" ) \
    >>"$OUT/install-console.log" 2>&1 || { phx_log ERROR "install.sh 失败（console 见 ${OUT}/install-console.log）"; return 1; }
  [ -f "$PAYLOAD/RECEIPT" ] || { phx_log ERROR "未生成收据 RECEIPT"; return 1; }
  grep -q "安装成功" "$PAYLOAD/RECEIPT" || { phx_log ERROR "收据未含'安装成功'"; return 1; }
  phx_log INFO "全新空库安装成功（收据已生成）"
  return 0
}
cleanup_project() {
  local d="$PAYLOAD/docker"
  [ -d "$d" ] || return 0
  ( cd "$d" && IMAGE_TAG="$VERSION" docker compose -p "$PROJECT" down -v >/dev/null 2>&1 ) || true
  phx_log INFO "已清理门禁栈 project=${PROJECT}（仅本项目）"
}
if [ "$SKIP_INSTALL" -eq 1 ]; then
  phx_log WARN "已跳过 ④ 全新空库安装（--skip-install 仅用于局部验证，出货禁用）"
else
  phx_gate_item "fresh-db-install" fresh_install || true
  cleanup_project
fi

if phx_gate_end; then exit 0; else phx_log ERROR "门禁存在失败项；证据=${PHX_GATE_EVIDENCE}"; exit 1; fi
