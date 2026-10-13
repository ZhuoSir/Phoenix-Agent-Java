#!/usr/bin/env bash
# T-04 验证：install.sh 步骤 2 离线引擎落地（R-02）
# 依赖: docker 在 PATH；静态件缓存 /tmp/engine-cache/docker-<ver>-<arch>.tgz（可用 PHX_TEST_ENGINE_DIR 覆盖）
# 纪律: 每个场景使用**独立载荷副本**并清空状态文件（L-102）
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
CACHE="${PHX_TEST_ENGINE_DIR:-/tmp/engine-cache}"
IMG="${PHX_TEST_IMAGE:-pgvector/pgvector:pg16}"
# L-102 ①：先做环境自检，缺失即报真因（避免"看起来像断言失败"）
for _c in docker tar; do command -v "$_c" >/dev/null 2>&1 || { echo "  ❌ 环境自检失败: $_c 不在 PATH（L-102）"; exit 3; }; done
AMB="$(ls "$CACHE"/docker-*-amd64.tgz 2>/dev/null | head -1)"
[ -n "$AMB" ] || { echo "  ❌ 缺 amd64 静态件缓存（$CACHE）"; exit 2; }
WORK="$(mktemp -d /tmp/t04t.XXXXXX)"
mkpayload() { # mkpayload <名> <manifestArch> <engine文件>
  local d="$WORK/$1"; mkdir -p "$d/engine" "$d/docker/scripts/lib"
  printf '{\n  "product": "Phoenix-Agent-Java",\n  "version": "2.1.0",\n  "arch": "%s"\n}\n' "$2" > "$d/manifest.json"
  cp "$ROOT/docker/scripts/install.sh" "$d/install.sh"
  cp "$ROOT/docker/scripts/lib/common.sh" "$d/docker/scripts/lib/common.sh"
  [ -n "$3" ] && cp "$3" "$d/engine/"
}
P=0; F=0; ok(){ P=$((P+1)); printf '  ✅ %s\n' "$1"; }; no(){ F=$((F+1)); printf '  ❌ %s — %s\n' "$1" "$2"; }

# 场景A：本机已有 Docker ⇒ 必须跳过（用独立副本，无状态文件）
mkpayload A arm64 "$AMB"; LA="$WORK/a.log"
( cd "$WORK/A" && PHX_LOG_FILE="$LA" bash install.sh --project t04a --port 8395 --timeout 60 >/dev/null 2>&1 )
grep -q "引擎已可用，跳过安装" "$LA" && ok "A 跳过分支命中" || no "A 跳过" "$(grep -c . "$LA") 行"
grep -q "静态二进制落地" "$LA" && no "A 不应走落地" "却走了" || ok "A 未触发落地分支"

# 场景B：容器内（无 docker CLI）真落地——落地与校验放在**同一次会话**
mkpayload B amd64 "$AMB"; LB="$WORK/b.log"
docker run --rm --privileged -v "$WORK/B:/payload" -w /payload "$IMG" bash -c '
  bash install.sh --project t04b --port 8395 --timeout 60 2>&1 | tail -12
  echo "=== 落地核对 ==="
  ls -l /usr/local/bin/dockerd /usr/local/bin/docker /usr/local/bin/containerd 2>&1
  stat -c "%a %n" /usr/local/bin/dockerd 2>/dev/null
  echo "=== dockerd 日志尾 ==="; tail -3 /var/log/dockerd.log 2>/dev/null || echo "（无 dockerd 日志）"
' > "$LB" 2>&1
grep -q "静态二进制落地" "$LB" && ok "B 走离线落地分支" || no "B 落地分支" "$(tail -2 "$LB" | head -1 | cut -c1-90)"
grep -q "二进制已落地 数量=8" "$LB" && ok "B 8 个二进制全部落地" || no "B 落地数量" "$(grep -o '数量=[0-9]*' "$LB" | head -1)"
grep -qE "^755 /usr/local/bin/dockerd" "$LB" && ok "B dockerd 权限 0755" || no "B 权限" "$(grep dockerd "$LB" | tail -1 | cut -c1-80)"
grep -q "无 systemd" "$LB" && ok "B 无 systemd ⇒ 显式降级告知（未假装自启成功）" || no "B 降级告知" "未出现"
grep -qE "引擎可达|引擎装后仍不可达" "$LB" && ok "B 给出明确的守护进程结论（如实，不隐瞒）" || no "B 结论" "无"

# 场景C：架构不匹配（包架构匹配宿主=amd64，但包内引擎件是 arm64）⇒ 必须明确停止
ARM="$(ls "$CACHE"/docker-*-arm64.tgz 2>/dev/null | head -1)"
[ -n "$ARM" ] || { echo "  ❌ 缺 arm64 静态件缓存"; exit 2; }
mkpayload C amd64 "$ARM"; LC="$WORK/c.log"
docker run --rm --privileged -v "$WORK/C:/payload" -w /payload "$IMG" bash install.sh --project t04c --port 8396 --timeout 60 > "$LC" 2>&1; RCC=$?
[ $RCC -ne 0 ] && ok "C 架构不匹配退出码非0（=$RCC）" || no "C 退出码" "rc=0"
grep -q "架构不匹配" "$LC" && ok "C 日志含「架构不匹配」并列出包内件" || no "C 报错" "$(tail -3 "$LC" | head -1 | cut -c1-90)"
printf '\n  ── T-04 断言: 通过=%d 失败=%d（工作目录 %s）\n' "$P" "$F" "$WORK"
[ "$F" -eq 0 ]
