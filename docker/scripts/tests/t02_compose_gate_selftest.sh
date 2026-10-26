#!/usr/bin/env bash
# T-02 验证：install.sh 前置能力闸（R-03：OSType/context/Compose v2）
# 用法: bash docker/scripts/tests/t02_compose_gate_selftest.sh
# 依赖: 一个已解包的交付载荷（默认 /tmp/fin-arm64/phoenix-2.0.0-arm64，可用 PHX_TEST_PAYLOAD_SRC 覆盖）
# 纪律: 每个用例切换载荷状态前都显式设定并复原（L-102：用例间不得共享可变载荷/环境）
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SRC="${PHX_TEST_PAYLOAD_SRC:-/tmp/fin-arm64/phoenix-2.0.0-arm64}"
[ -d "$SRC" ] || { echo "  ❌ 载荷源不存在: $SRC（先解包交付包或设 PHX_TEST_PAYLOAD_SRC）"; exit 2; }
WORK="$(mktemp -d /tmp/t02gate.XXXXXX)"
PAY="$WORK/pay"; cp -R "$SRC" "$PAY"
cp "$ROOT/docker/scripts/install.sh" "$PAY/install.sh"
mkdir -p "$PAY/docker/scripts/lib"
cp "$ROOT/docker/scripts/lib/common.sh" "$PAY/docker/scripts/lib/common.sh"

HOSTARCH="$(uname -m)"; case "$HOSTARCH" in x86_64) HOSTARCH=amd64;; aarch64|arm64) HOSTARCH=arm64;; esac
if [ "$HOSTARCH" = "arm64" ]; then OTHER=amd64; else OTHER=arm64; fi

set_arch() { # set_arch <arm64|amd64>：改写载荷 manifest 架构字段
  python3 - "$PAY" "$1" <<'PY'
import io,sys,re
p=sys.argv[1]+'/manifest.json'; s=io.open(p,encoding='utf-8').read()
io.open(p,'w',encoding='utf-8').write(re.sub(r'"arch":\s*"[^"]*"','"arch": "%s"'%sys.argv[2],s,count=1))
PY
}
break_sha() { # 破坏首个哈希 ⇒ 步骤 3 必然失败（早停，不做真装）
  python3 - "$PAY" <<'PY'
import io,sys
p=sys.argv[1]+'/SHA256SUMS'; L=io.open(p,encoding='utf-8').read().split('\n')
L[0]=('a' if not L[0].startswith('a') else 'b')+L[0][1:]
io.open(p,'w',encoding='utf-8').write('\n'.join(L))
PY
}
mkdir -p "$WORK/shim"
cat > "$WORK/shim/docker" <<'SH'
#!/bin/sh
case "$1" in
  info) [ "${2:-}" = "--format" ] && case "${3:-}" in *OSType*) echo linux;; *ServerVersion*) echo 20.10.20-stub;; *) echo linux;; esac; exit 0;;
  compose) exit 1;;
  ps) exit 0;;
  *) exit 0;;
esac
SH
printf '#!/bin/sh\necho "docker-compose version 1.29.2, build unknown"\nexit 0\n' > "$WORK/shim/docker-compose"
chmod +x "$WORK/shim/docker"
break_sha
P=0; F=0; ok(){ P=$((P+1)); printf '  ✅ %s\n' "$1"; }; no(){ F=$((F+1)); printf '  ❌ %s — %s\n' "$1" "$2"; }

# 用例1：仅 Compose v1（反例：必须早退、零容器、有指引）
set_arch "$HOSTARCH"; rm -f "$PAY/.phoenix-install.state"; L1="$WORK/v1.log"
( cd "$PAY" && PHX_LOG_FILE="$L1" PATH="$WORK/shim:$PATH" bash install.sh --project phxt02v1 --port 8391 >/dev/null 2>&1 ); RC=$?
[ $RC -ne 0 ] && ok "仅v1：退出码非0（=$RC）" || no "仅v1 退出码" "rc=0"
grep -q "Compose 能力不足" "$L1" && ok "仅v1：检测结论入日志" || no "检测结论" "$(tail -2 "$L1" | head -1 | cut -c1-80)"
grep -q "已创建容器数=0" "$L1" && ok "仅v1：断言容器数=0" || no "容器数" "未出现"
grep -q "处置:" "$L1" && ok "仅v1：含安装指引" || no "指引" "未出现"
grep -q "步骤 3/9" "$L1" && no "仅v1 时序" "进入了步骤3" || ok "仅v1：早于步骤6"
[ "$(docker ps -a --filter 'name=^phxt02v1-' -q 2>/dev/null | wc -l | tr -d ' ')" = "0" ] && ok "仅v1：零残留容器" || no "残留容器" "有"

# 用例2b：架构严格默认（反例：无开关必须拒绝）
set_arch "$OTHER"
L2b="$WORK/arch-strict.log"
( cd "$PAY" && PHX_LOG_FILE="$L2b" bash install.sh --project phxt02b --port 8394 >/dev/null 2>&1 ); RCs=$?
[ $RCs -ne 0 ] && grep -q "架构不符" "$L2b" && ok "严格默认：架构不符被拒（rc=$RCs）" || no "严格默认" "rc=$RCs"

# 用例2：架构不符 + 显式开关（身份：跨架构模拟）→ WARN 放行，步骤1/2 通过、步骤3 早停
set_arch "$OTHER"; rm -f "$PAY/.phoenix-install.state"; L2="$WORK/ok.log"
( cd "$PAY" && PHX_LOG_FILE="$L2" PHOENIX_ALLOW_ARCH_MISMATCH=1 bash install.sh --project phxt02ok --port 8392 >/dev/null 2>&1 ); RC=$?
grep -q "Compose 能力 OK" "$L2" && ok "正例：Compose 能力 OK" || no "Compose OK" "$(tail -1 "$L2" | cut -c1-80)"
grep -q "docker context=" "$L2" && ok "正例：context 已记录" || no "context" "未出现"
grep -q "架构不符但已显式允许" "$L2" && ok "跨架构模拟：WARN 放行" || no "跨架构" "无 WARN"
grep -q "步骤 3/9" "$L2" && [ $RC -ne 0 ] && ok "正例：步骤1/2 通过、步骤3 按预期失败" || no "步骤3早停" "rc=$RC"
[ "$(docker ps -a --filter 'name=^phxt02ok-' -q 2>/dev/null | wc -l | tr -d ' ')" = "0" ] && ok "正例：零容器" || no "容器残留" "有"

# 用例3：重跑幂等（状态机仍生效）
L3="$WORK/rerun.log"
( cd "$PAY" && PHX_LOG_FILE="$L3" PHOENIX_ALLOW_ARCH_MISMATCH=1 bash install.sh --project phxt02ok --port 8392 >/dev/null 2>&1 ); RC=$?
grep -qE "步骤 1/9 跳过（已完成）" "$L3" && grep -qE "步骤 2/9 跳过（已完成）" "$L3" && ok "重跑：步骤1/2 跳过（断点续传）" || no "重跑幂等" "未跳过"
[ $RC -ne 0 ] && ok "重跑：仍被拦住（rc=$RC）" || no "重跑拦截" "rc=0"
printf '\n  ── T-02 断言合计: 通过=%d 失败=%d（工作目录 %s）\n' "$P" "$F" "$WORK"
[ "$F" -eq 0 ]
