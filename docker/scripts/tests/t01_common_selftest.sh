#!/usr/bin/env bash
# T-01 单元自测：common.sh 新增通用函数逐项验证（含反例）
# 用法: bash docker/scripts/tests/t01_common_selftest.sh   （需 docker 在 PATH；退出码 0=全绿）
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"   # 仓库根（本脚本位于 docker/scripts/tests/）
cd "$ROOT" || exit 9
export PHX_LOG_FILE=/tmp/t01_common.log
. docker/scripts/lib/common.sh
: > "$PHX_LOG_FILE"
P=0; F=0
ok(){ P=$((P+1)); printf '  ✅ %s\n' "$1"; }
no(){ F=$((F+1)); printf '  ❌ %s — 实测: %s\n' "$1" "$2"; }

# ① phx_runid 格式
r="$(phx_runid gate)"
case "$r" in gate-[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-[0-9][0-9][0-9][0-9][0-9][0-9]-*) ok "phx_runid 格式 实测=${r}";; *) no "phx_runid 格式" "$r";; esac

# ②③④ 字节/哈希/对账（含反例）
head -c 1024 /dev/zero > /tmp/t01_1k.bin
b="$(phx_bytes /tmp/t01_1k.bin)"; [ "$b" = "1024" ] && ok "phx_bytes=1024" || no "phx_bytes" "$b"
h="$(phx_sha256 /tmp/t01_1k.bin)"; h2="$(shasum -a 256 /tmp/t01_1k.bin | awk '{print $1}')"
[ "$h" = "$h2" ] && ok "phx_sha256 与系统一致 ${h:0:16}…" || no "phx_sha256" "$h"
phx_assert_file_size /tmp/t01_1k.bin 1024 >/dev/null 2>&1 && ok "对账一致→rc0" || no "对账一致" "rc!=0"
phx_assert_file_size /tmp/t01_1k.bin 999 >/dev/null 2>&1; [ $? -eq 1 ] && ok "对账不一致→rc1" || no "对账不一致" "rc!=1"
phx_assert_file_size /tmp/t01_missing.bin 1 >/dev/null 2>&1; [ $? -eq 1 ] && ok "文件不存在→rc1" || no "文件不存在" "rc!=1"

# ⑤ 镜像身份（真实镜像）
ii="$(phx_image_info phoenix-backend:2.0.0)"
echo "$ii" | grep -q 'Id=sha256:' && echo "$ii" | grep -q 'Arch=' && ok "phx_image_info 实测: ${ii:0:60}…" || no "phx_image_info" "$ii"

# ⑥⑦ 正例：本机引擎类型 / Compose v2
phx_ostype_is_linux >/dev/null 2>&1 && ok "phx_ostype_is_linux→rc0（本机 linux）" || no "ostype 正例" "rc!=0"
phx_compose_v2_ok >/dev/null 2>&1 && ok "phx_compose_v2_ok→rc0（本机 v2）" || no "compose 正例" "rc!=0"

# ⑧ 体检三件套
phx_env_checkup >/dev/null 2>&1 && grep -q "环境体检" "$PHX_LOG_FILE" && ok "phx_env_checkup 已记入日志" || no "env_checkup" "无日志"

# ⑨⑩⑪ 门禁框架（含失败计数）
rm -rf /tmp/t01-gate
phx_gate_begin /tmp/t01-gate 2.1.0 arm64 >/dev/null 2>&1 && ok "phx_gate_begin→rc0" || no "gate_begin" "rc!=0"
phx_gate_item "冒烟-真" true >/dev/null 2>&1 && ok "gate_item true→PASS" || no "gate_item true" "rc!=0"
phx_gate_item "冒烟-假" false >/dev/null 2>&1; [ $? -eq 1 ] && ok "gate_item false→FAIL" || no "gate_item false" "rc!=1"
phx_gate_end >/dev/null 2>&1; [ $? -eq 1 ] && ok "gate_end 有失败→rc1" || no "gate_end" "rc!=1"
grep -q "冒烟-假 — FAIL" /tmp/t01-gate/*.md && ok "证据文件含 FAIL 项与命令" || no "证据文件" "缺 FAIL 段"
grep -q "结果=FAIL 通过=1 失败=1" "$PHX_LOG_FILE" && ok "日志含 runId/结果/通过数/失败数" || no "门禁结束日志" "$(grep '门禁结束' "$PHX_LOG_FILE" | tail -1)"

# ⑫⑬ 反例：Windows 引擎 / 仅 Compose v1（PATH shim 模拟）
mkdir -p /tmp/t01-shim
cat > /tmp/t01-shim/docker <<'SH'
#!/bin/sh
if [ "$1" = "info" ]; then echo windows; exit 0; fi
if [ "$1" = "compose" ]; then exit 1; fi
if [ "$1" = "ps" ]; then exit 0; fi
if [ "$1" = "system" ]; then exit 0; fi
exit 1
SH
printf '#!/bin/sh\nexit 0\n' > /tmp/t01-shim/docker-compose; chmod +x /tmp/t01-shim/docker /tmp/t01-shim/docker-compose
( PATH=/tmp/t01-shim:$PATH; . docker/scripts/lib/common.sh; phx_ostype_is_linux >/dev/null 2>&1; exit $? )
[ $? -eq 1 ] && grep -q "Windows 容器引擎" "$PHX_LOG_FILE" && ok "反例：OSType=windows 被拒且有指引" || no "windows 反例" "rc!=1 或无指引"
( PATH=/tmp/t01-shim:$PATH; . docker/scripts/lib/common.sh; phx_compose_v2_ok >/dev/null 2>&1; exit $? )
[ $? -eq 1 ] && grep -q "Compose 能力不足" "$PHX_LOG_FILE" && grep -q "处置:" "$PHX_LOG_FILE" && ok "反例：仅 v1 被拒且给安装指引/用户口径" || no "compose v1 反例" "rc!=1 或无指引"

printf '\n  ── 合计: 通过=%d 失败=%d\n' "$P" "$F"
[ "$F" -eq 0 ]
