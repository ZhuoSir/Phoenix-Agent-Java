#!/usr/bin/env bash
# common.sh 自测（T-01 验证载体）——bash 3.2/5.x 双版本都必须全绿
# shellcheck disable=SC2015,SC1091
#  ↑ SC2015：测试断言惯用法 A&&ok||bad——ok/bad 恒成功不会触发 C 分支误跑，语境安全；
#    SC1091：动态 source 路径 shellcheck 无法跟随，属测试脚本常态
set -u
HERE=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d /tmp/phx-selftest.XXXXXX)
trap '[ -n "${SRV_PID:-}" ] && kill "$SRV_PID" 2>/dev/null; rm -rf "$T"' EXIT

export PHX_LOG_FILE="$T/test.log"
export PHX_STATE_FILE="$T/state"
# shellcheck source=common.sh
. "$HERE/common.sh"
phx_log_init

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); echo "  ✓ $1"; }
bad()  { FAIL=$((FAIL+1)); echo "  ✗ $1"; }
assert_eq() { # assert_eq <期望> <实际> <名>
  if [ "$1" = "$2" ]; then ok "$3"; else bad "$3 (期望[$1] 实际[$2])"; fi
}

echo "== 1. 日志双写 =="
phx_log INFO "selftest-hello" >/dev/null
grep -q 'selftest-hello' "$PHX_LOG_FILE" && ok "日志落盘" || bad "日志落盘"

echo "== 2. 九步状态机 =="
phx_step 1 9 "探测" >/dev/null && ok "首跑步骤1返回0(该干活)" || bad "首跑步骤1返回0"
phx_step_mark 1
phx_step_is_done 1 && ok "标记后 is_done=真" || bad "标记后 is_done=真"
phx_step 1 9 "探测" >/dev/null && bad "重跑步骤1应返回1(跳过)" || ok "重跑步骤1返回1(跳过)"
phx_step_is_done 2 && bad "步骤2不应已完成" || ok "步骤2未完成(位图隔离)"

echo "== 3. phx_fail 失败出口 =="
OUT=$(bash -c ". '$HERE/common.sh'; PHX_LOG_FILE='$T/f.log'; phx_log_init; phx_fail 6 9 '模拟炸了'" 2>&1); RC=$?
assert_eq "1" "$RC" "fail 退出码=1"
echo "$OUT" | grep -q '步骤 6/9 失败: 模拟炸了' && ok "fail 输出含步骤号+原因" || bad "fail 输出格式"
echo "$OUT" | grep -q "$T/f.log" && ok "fail 输出含日志路径" || bad "fail 含日志路径"

echo "== 4. sha256 助手 =="
echo -n "phoenix" > "$T/known.bin"
if command -v shasum >/dev/null 2>&1; then WANT=$(shasum -a 256 "$T/known.bin" | awk '{print $1}'); else WANT=$(sha256sum "$T/known.bin" | awk '{print $1}'); fi
assert_eq "$WANT" "$(phx_sha256 "$T/known.bin")" "phx_sha256 与系统工具一致"
mkdir -p "$T/pkg" && cp "$T/known.bin" "$T/pkg/"
echo "$WANT  known.bin" > "$T/pkg/SHA256SUMS"
phx_sha_check_dir "$T/pkg" "$T/pkg/SHA256SUMS" >/dev/null && ok "完好包校验过" || bad "完好包校验过"
echo "tamper" >> "$T/pkg/known.bin"
BADS=$(phx_sha_check_dir "$T/pkg" "$T/pkg/SHA256SUMS"); RC=$?
assert_eq "1" "$RC" "篡改包校验拒绝"
echo "$BADS" | grep -q 'BAD: known.bin' && ok "篡改包列出坏文件名" || bad "篡改包列名"
rm "$T/pkg/known.bin"
phx_sha_check_dir "$T/pkg" "$T/pkg/SHA256SUMS" | grep -q 'MISSING' && ok "缺文件列 MISSING" || bad "缺文件列 MISSING"

echo "== 5. mirror 竞速 =="
SRV_PID=""
if command -v python3 >/dev/null 2>&1; then
  mkdir -p "$T/srv/v2" && echo '{}' > "$T/srv/v2/index.html"
  ( cd "$T/srv" && python3 -m http.server 18923 >/dev/null 2>&1 & echo $! > "$T/srv.pid" )
  SRV_PID=$(cat "$T/srv.pid")
  for _ in 1 2 3 4 5 6 7 8 9 10; do curl -s -m 2 -o /dev/null "http://127.0.0.1:18923/v2/" && break; sleep 1; done
  PICK=$(phx_mirror_pick "http://127.0.0.1:19999" "http://127.0.0.1:18923")
  assert_eq "http://127.0.0.1:18923" "$PICK" "死口跳过活口选中"
else
  echo "  ⊘ 活口子测跳过（无 python3，非失败）"
fi
phx_mirror_pick "http://127.0.0.1:19998" >/dev/null 2>&1 && bad "全死应返回1" || ok "全死返回1"

echo "== 6. 收据卡 =="
phx_receipt "$T/RECEIPT" "v9.9.9-selftest" "版本" "v9.9.9" "访问地址" "http://localhost:9080" >/dev/null
grep -q '版本: v9.9.9' "$T/RECEIPT" && ok "RECEIPT 文件字段齐" || bad "RECEIPT 字段"
grep -q '访问地址: http://localhost:9080' "$T/RECEIPT" && ok "RECEIPT 第二字段" || bad "RECEIPT 第二字段"

echo "== 7. 命令与磁盘探测 =="
phx_require_cmd bash && ok "require_cmd 命中" || bad "require_cmd 命中"
phx_require_cmd definitely-not-exist-xyz && bad "require_cmd 应miss" || ok "require_cmd miss"
phx_disk_ok "$T" 1 && ok "disk_ok 小需求过" || bad "disk_ok 小需求过"
phx_disk_ok "$T" 999999999 && bad "disk_ok 天文数字应拒" || ok "disk_ok 天文数字拒"
FREE=$(phx_disk_free_mb "$T"); [ -n "$FREE" ] && [ "$FREE" -gt 0 ] && ok "disk_free_mb 出数($FREE)" || bad "disk_free_mb 出数"

echo ""
echo "════ selftest: PASS=$PASS FAIL=$FAIL (bash $BASH_VERSION) ════"
[ "$FAIL" -eq 0 ]
