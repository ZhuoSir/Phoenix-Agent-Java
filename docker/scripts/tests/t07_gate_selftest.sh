#!/usr/bin/env bash
# T-07 验证：gate.sh 四项门禁（R-05）—— 两反例 + 一正例
# 依赖: docker 在 PATH；arm64 交付包 tar（默认 docker/dist/phoenix-2.0.0-arm64.tar.gz，可用 PHX_TEST_PKG 覆盖）
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; cd "$ROOT"
export PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"
for c in docker tar python3; do command -v "$c" >/dev/null 2>&1 || { echo "  ❌ 环境自检失败: $c 不在 PATH（L-102）"; exit 3; }; done
PKG="${PHX_TEST_PKG:-docker/dist/phoenix-2.0.0-arm64.tar.gz}"
[ -f "$PKG" ] || { echo "  ❌ 缺交付包: $PKG"; exit 2; }
W="$(mktemp -d /tmp/t07t.XXXXXX)"; P=0; F=0
ok(){ P=$((P+1)); printf '  ✅ %s\n' "$1"; }; no(){ F=$((F+1)); printf '  ❌ %s — %s\n' "$1" "$2"; }

# 准备：解包 + 覆盖为新脚本 + 重算 SHA256SUMS（模拟重打包后的产物）
tar -xzf "$PKG" -C "$W"
PAY="$W/phoenix-2.0.0-arm64"
cp docker/scripts/install.sh "$PAY/install.sh"
cp docker/scripts/gate.sh "$PAY/docker/scripts/gate.sh" 2>/dev/null || { mkdir -p "$PAY/docker/scripts"; cp docker/scripts/gate.sh "$PAY/docker/scripts/gate.sh"; }
cp docker/scripts/lib/common.sh "$PAY/docker/scripts/lib/common.sh"
( cd "$PAY" && python3 - <<'PY'
import os,hashlib,io
pay=os.getcwd(); rows=[]
for root,dirs,files in os.walk(pay):
    for f in sorted(files):
        fp=os.path.join(root,f); rel=os.path.relpath(fp,pay)
        if rel=='SHA256SUMS' or rel.endswith('.sha256'): continue
        rows.append(f"{hashlib.sha256(open(fp,'rb').read()).hexdigest()}  {rel}")
io.open(os.path.join(pay,'SHA256SUMS'),'w',encoding='utf-8').write('\n'.join(rows)+'\n')
PY
) && echo "  载荷就绪（SHA256SUMS 已重算）"

# 反例A：镜像内 jar 权限 0600（以 10001 运行用户不可读）—— 小镜像 + commit 时显式改 USER
docker rm -f t07tmp >/dev/null 2>&1; docker rmi t07-jar600:test >/dev/null 2>&1
docker run -d --name t07tmp --user root --entrypoint sh redis:7-alpine \
  -c 'mkdir -p /app && echo fakejar > /app/phoenix-admin.jar && chmod 600 /app/phoenix-admin.jar && sleep 60' >/dev/null 2>&1
sleep 4
docker commit --change 'USER 10001' t07tmp t07-jar600:test >/dev/null 2>&1
docker rm -f t07tmp >/dev/null 2>&1
PROBE="$(docker run --rm --entrypoint sh t07-jar600:test -c 'id -u; test -r /app/phoenix-admin.jar && echo READABLE || echo NOT-READABLE' 2>&1 | tr '\n' ' ')"
echo "$PROBE" | grep -q "NOT-READABLE" && ok "前提成立：t07-jar600 内 jar 对 10001 不可读（$PROBE）" || no "前提 jar0600" "$PROBE"
bash docker/scripts/gate.sh --payload "$PAY" --out "$W/gateA" --jar-image t07-jar600:test --skip-install > "$W/gateA.log" 2>&1; RA=$?
[ $RA -ne 0 ] && ok "反例A 门禁整体非 0（=$RA）" || no "反例A rc" "0"
grep -q "门禁 FAIL item=jar-readable" "$W/gateA.log" && ok "反例A 失败项=jar-readable 且含复现信息" || no "反例A 失败项" "$(grep -c '门禁' "$W/gateA.log") 行"
grep -qE "^## jar-readable — FAIL" "$W/gateA"/*.md && ok "反例A 证据文件记录 FAIL 段" || no "反例A 证据" "无 FAIL 段"

# 反例B：SHA256SUMS 被破坏
PAYB="$W/payB"; cp -R "$PAY" "$PAYB"
python3 - "$PAYB" <<'PY'
import io,sys
p=sys.argv[1]+'/SHA256SUMS'; L=io.open(p,encoding='utf-8').read().split('\n')
L[0]=('a' if not L[0].startswith('a') else 'b')+L[0][1:]
io.open(p,'w',encoding='utf-8').write('\n'.join(L))
PY
bash docker/scripts/gate.sh --payload "$PAYB" --out "$W/gateB" --jar-image phoenix-backend:2.0.0 --skip-install > "$W/gateB.log" 2>&1; RB=$?
[ $RB -ne 0 ] && ok "反例B 门禁整体非 0（=$RB）" || no "反例B rc" "0"
grep -q "门禁 FAIL item=sha256sums" "$W/gateB.log" && ok "反例B 失败项=sha256sums" || no "反例B 失败项" "$(grep -c '门禁' "$W/gateB.log") 行"

# 正例：四项全 PASS（含全新空库安装到收据）
bash docker/scripts/gate.sh --payload "$PAY" --out "$W/gateC" --project "phxgate-t07-$$" --port 8191 --timeout 900 > "$W/gateC.log" 2>&1; RCC=$?
[ $RCC -eq 0 ] && ok "正例 门禁四项全 PASS（rc=0）" || no "正例 rc" "$RCC（见 $W/gateC.log）"
grep -q "门禁结束.*结果=PASS 通过=4 失败=0" "$W/gateC.log" && ok "正例 日志含 通过=4 失败=0" || no "正例计数" "$(grep '门禁结束' "$W/gateC.log" | tail -1 | cut -c1-90)"
grep -q "安装成功" "$PAY/RECEIPT" 2>/dev/null && ok "正例 全新空库安装产出收据" || no "正例 收据" "无"
[ "$(docker ps -a --filter 'name=^phxgate-t07' -q | wc -l | tr -d ' ')" = "0" ] && ok "正例 门禁栈已清理（零残留容器）" || no "正例 清理" "有残留"
docker rmi t07-jar600:test >/dev/null 2>&1 || true
printf '\n  ── T-07 断言: 通过=%d 失败=%d（工作目录 %s）\n' "$P" "$F" "$W"; [ "$F" -eq 0 ]
