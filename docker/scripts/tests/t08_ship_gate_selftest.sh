#!/usr/bin/env bash
# T-08 验证：package.sh 出货门禁接线（pkg_ship_gate / pkg_ship_or_skip / 哈希复核）
# 用法: bash docker/scripts/tests/t08_ship_gate_selftest.sh   （用 stub 门禁，无需真打包；退出码 0=全绿）
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; cd "$ROOT"
command -v python3 >/dev/null 2>&1 || { echo "  ❌ 环境自检失败: python3 不在 PATH（L-102）"; exit 3; }
W="$(mktemp -d /tmp/t08t.XXXXXX)"; P=0; F=0
ok(){ P=$((P+1)); printf '  ✅ %s\n' "$1"; }; no(){ F=$((F+1)); printf '  ❌ %s — %s\n' "$1" "$2"; }
# 假 payload 与假 tarball
mkdir -p "$W/pay"; echo hello > "$W/pay/file.txt"; tar -czf "$W/pkg.tar.gz" -C "$W/pay" .
# stub 门禁：PASS / FAIL / PASS但改动产物
printf '#!/usr/bin/env bash\nexit 0\n' > "$W/gate-pass.sh"
printf '#!/usr/bin/env bash\necho "stub gate: FAIL" >&2\nexit 1\n' > "$W/gate-fail.sh"
printf '#!/usr/bin/env bash\necho x >> "%s/pkg.tar.gz"\nexit 0\n' "$W" > "$W/gate-mutate.sh"
chmod +x "$W"/gate-*.sh
run_case(){ # run_case <stub> <期望rc> <期望日志片段> <标签> [SKIP_GATE]
  local stub="$1" want="$2" pat="$3" label="$4" skip="${5:-0}"
  ( export PHX_PKG_LIB_ONLY=1 PHX_LOG_FILE="$W/log-$label" SKIP_GATE="$skip" PHX_GATE_CMD="$stub"
    . docker/scripts/package.sh --version 2.1.0 --arch arm64 --out /tmp/phx-t08 >/dev/null 2>&1
    pkg_ship_or_skip "$W/pay" "$W/pkg.tar.gz" "$W/ev-$label" >/dev/null 2>&1; echo $? > "$W/rc-$label" )
  local rc; rc="$(cat "$W/rc-$label")"
  [ "$rc" = "$want" ] && ok "$label 退出码=$rc" || no "$label 退出码" "期望 $want 实得 $rc"
  grep -q "$pat" "$W/log-$label" && ok "$label 日志含「$pat」" || no "$label 日志" "$(tail -1 "$W/log-$label" | cut -c1-80)"
}
# 用例1：门禁通过 + 哈希复核一致
tar -czf "$W/pkg.tar.gz" -C "$W/pay" .
run_case "$W/gate-pass.sh" 0 "门禁通过且包哈希复核一致" pass
# 用例2：门禁失败
run_case "$W/gate-fail.sh" 1 "出货门禁未通过" fail
# 用例3：门禁期间产物被改动 ⇒ 哈希复核拒绝
tar -czf "$W/pkg.tar.gz" -C "$W/pay" .
run_case "$W/gate-mutate.sh" 1 "门禁后包哈希变化" mutate
# 用例4：--skip-gate（仅调试）
run_case "$W/gate-fail.sh" 0 "禁止用于出货" skip 1
# 用例5：--skip-gate 时不调用门禁（stub 不会被触发：用必然失败的 stub 也返回 0）
printf '#!/usr/bin/env bash\nexit 9\n' > "$W/gate-boom.sh"; chmod +x "$W/gate-boom.sh"
run_case "$W/gate-boom.sh" 0 "已跳过出货门禁" skipnoverify 1
printf '\n  ── T-08 断言: 通过=%d 失败=%d\n' "$P" "$F"; [ "$F" -eq 0 ]
