#!/bin/sh
# 交付验收断言（R-17/R-01/AC-07 的脚本化）：密钥红线、端口暴露、健康与首登
set -u
# docker CLI 自愈：非交互 shell 可能不含 Docker Desktop 路径
if ! command -v docker >/dev/null 2>&1; then
  for d in /usr/local/bin /Applications/Docker.app/Contents/Resources/bin /snap/bin; do
    [ -x "$d/docker" ] && PATH="$d:$PATH" && break
  done
fi
cd "$(dirname "$0")/.."
fail=0; step(){ printf '%-46s' "$1"; }
ok(){ echo "PASS"; }; no(){ echo "FAIL: $1"; fail=1; }

step "[1] 种子文件无真实密钥(仅占位符/注释)"
bad=$(grep -oE 'sk-[A-Za-z0-9._-]{20,}' ../sql/all_data.sql | grep -vcE '^sk-x+$' || true)
[ "${bad:-0}" = "0" ] && ok || no "发现 $bad 处疑似真实密钥"

step "[2] 当前追踪树+种子无长密钥指纹"
hits=$(cd .. && git grep -lE 'sk-[A-Za-z0-9._-]{24,}' -- ':!docker/scripts/verify.sh' 2>/dev/null | wc -l | tr -d ' ')
[ "${hits:-0}" = "0" ] && ok || no "命中文件数=$hits（历史审计 2026-09-30 全历史 0 命中，结论见 spec changelog）"

step "[3] 仅 nginx 对外(其余服务无宿主端口绑定)"
extra=0
for c in $(docker compose ps -q); do
  ports=$(docker inspect -f '{{range $p,$b := .NetworkSettings.Ports}}{{if $b}}X{{end}}{{end}}' "$c")
  name=$(docker inspect -f '{{index .Config.Labels "com.docker.compose.service"}}' "$c")
  [ "$name" != nginx ] && [ -n "$ports" ] && { echo -n ""; extra=1; }
done
[ "$extra" = "0" ] && ok || no "非 nginx 服务存在宿主端口"

step "[4] compose 全绿 + /echo/ok 经 nginx"
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/echo/ok" || true)
[ "$code" = "200" ] && ok || no "GET /echo/ok -> ${code:-n/a}"

step "[5] 登录（BL-18 根治后单前缀直连 /api/*）"
body=$(curl -s --max-time 10 -X POST -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/privilege/auth/login" || true)
echo "$body" | grep -q '"code":"100"' && ok || no "$(echo $body | head -c 60)"

step "[9] platform 裸域直连（/platform/*）"
b9=$(curl -s --max-time 10 "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/platform/group-info/page?page=1&size=200" | grep -cE '"code":|success' || true)
[ "${b9:-0}" -ge 1 ] && ok || no "platform 域透传未生效"

step "[6] 迁移台账齐(baseline + V 件)"
n=$(docker compose exec -T postgres psql -U phoenix -d phoenix -tAc "select count(*) from tbl_phoenix_release" 2>/dev/null || echo 0)
[ "$n" -ge 2 ] && ok || no "台账行数=$n"

step "[8] 负断言：双前缀 /api/api/* 不再存在折叠依赖"
b8=$(curl -s --max-time 10 -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/api/privilege/auth/login" | grep -c '"code":"100"' || true)
[ "${b8:-0}" = "0" ] && ok || no "折叠规则仍在？BL-18 后应移除"

step "[10] 会话文件面板表与端点在位 (BL-19)"
b10=$(docker compose exec -T postgres psql -U phoenix -d phoenix -tAc "select count(*) from information_schema.tables where table_name='tbl_data_agent_file'" 2>/dev/null || echo 0)
l10=$(curl -s --max-time 10 -H "phoenix-token: $(curl -s --max-time 10 -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/privilege/auth/login" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')" "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/agent/files?sessionId=verify-nonexistent" | grep -cE '"code":|"success":' || true)
[ "${b10:-0}" = "1" ] && [ "${l10:-0}" -ge 1 ] && ok || no "表=$b10 端点信封=$l10（需跑 V1.3.0_01 并部署 T-04）"

step "[12] 知识库模块就位（表×菜单×端点）(knowledge-base)"
kb12=$(docker compose exec -T postgres psql -U phoenix -d phoenix -tAc "select count(*) from information_schema.tables where table_name in ('tbl_data_knowledge_base','tbl_data_agent_kbase_bind','tbl_platform_group_kbase_info')" 2>/dev/null || echo 0)
km12=$(docker compose exec -T postgres psql -U phoenix -d phoenix -tAc "select count(*) from tbl_privilege_module where url='/knowledge-base' and del_flag=0" 2>/dev/null || echo 0)
en12=$(curl -s --max-time 10 -X POST -H "phoenix-token: $(curl -s --max-time 10 -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/privilege/auth/login" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')" -H 'Content-Type: application/json' -d '{"pageNum":1,"pageSize":1}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/knowledge-base/query/page" | grep -cE '"success":true|"message"' || true)
[ "${kb12:-0}" = "3" ] && [ "${km12:-0}" = "1" ] && [ "${en12:-0}" -ge 1 ] && ok || no "表=$kb12 菜单=$km12 端点=$en12（需 V1.3.0_02 + 部署 T-02）"

step "[13] /auth/login 双面共存（GET=SPA 页 / POST=后端信封）(BUG-56 防回归)"
g13=$(curl -s -m 8 "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/auth/login" | grep -ciE '<html|<!doctype' || true)
p13=$(curl -s -m 8 -X POST -H 'Content-Type: application/json' -d '{"username":"verify-probe","password":"x"}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/auth/login" | grep -cE '"success":|"code":' || true)
[ "$g13" -ge 1 ] && [ "$p13" -ge 1 ] && ok || no "GET非SPA=$g13 POST非信封=$p13（nginx 方法分流被改动？）"

step "[14] 断线续传轮次端点在位 (detached-stream)"
tk=$(curl -s --max-time 10 -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/privilege/auth/login" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
t14=$(curl -s --max-time 10 -H "phoenix-token: $tk" "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/admin/harness/turn/status?sessionId=verify-none")
echo "$t14" | grep -q '"code":"100"' && echo "$t14" | grep -q '"data":false' && ok || no "turn/status 信封异常: ${t14:0:70}"

step "[7] harness status 列已由两拍补齐"
c=$(docker compose exec -T postgres psql -U phoenix -d phoenix -tAc "select count(*) from information_schema.columns where table_name='tbl_harness_skills' and column_name='status'" 2>/dev/null || echo 0)
[ "$c" = "1" ] && ok || no "若为 0：应用可能未首启（表未建），启动一轮对话后重跑本断言"

exit $fail
