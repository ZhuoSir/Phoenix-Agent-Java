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

step "[5] admin/123456 首登返回成功"
body=$(curl -s --max-time 10 -X POST -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/privilege/auth/login" || true)
echo "$body" | grep -q '"success": *true' && ok || no "$body"

step "[6] 迁移台账齐(baseline + V 件)"
n=$(docker compose exec -T postgres psql -U phoenix -d phoenix -tAc "select count(*) from tbl_phoenix_release" 2>/dev/null || echo 0)
[ "$n" -ge 2 ] && ok || no "台账行数=$n"

step "[8] 双重前缀登录可用(nginx 折叠在位, BUG-33)"
b8=$(curl -s --max-time 10 -X POST -H 'Content-Type: application/json' -d '{"username":"admin","password":"123456"}' "http://127.0.0.1:${PHOENIX_HTTP_PORT:-9080}/api/api/privilege/auth/login" | grep -c '"code":"100"' || true)
[ "${b8:-0}" = "1" ] && ok || no "nginx /api 折叠规则缺失？见 BUG-33"

step "[7] harness status 列已由两拍补齐"
c=$(docker compose exec -T postgres psql -U phoenix -d phoenix -tAc "select count(*) from information_schema.columns where table_name='tbl_harness_skills' and column_name='status'" 2>/dev/null || echo 0)
[ "$c" = "1" ] && ok || no "若为 0：应用可能未首启（表未建），启动一轮对话后重跑本断言"

exit $fail
