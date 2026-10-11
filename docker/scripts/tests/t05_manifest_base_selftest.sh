#!/usr/bin/env bash
# T-05 验证：package.sh 底座差异记录（--base-image / manifest.base_images / equivalence_note）
# 用法: bash docker/scripts/tests/t05_manifest_base_selftest.sh   （无需 docker；退出码 0=全绿）
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"; cd "$ROOT"
command -v python3 >/dev/null 2>&1 || { echo "  ❌ 环境自检失败: python3 不在 PATH（L-102）"; exit 3; }
W="$(mktemp -d /tmp/t05t.XXXXXX)"
P=0; F=0; ok(){ P=$((P+1)); printf '  ✅ %s\n' "$1"; }; no(){ F=$((F+1)); printf '  ❌ %s — %s\n' "$1" "$2"; }
jget(){ python3 -c "import json,sys;d=json.load(open('$1'));print(eval('d'+sys.argv[1]))" "$2" 2>/dev/null; }

# 用例A：参考底座（未替代）
( export PHX_PKG_LIB_ONLY=1 PHX_LOG_FILE="$W/a.log"; . docker/scripts/package.sh >/dev/null 2>&1
  pkg_manifest_write "$W/a.json" 2.1.0 arm64 "v2.0.0-3-gabc" abc123 '"phoenix-backend:2.1.0","phoenix-frontend:2.1.0"' )
[ "$(jget "$W/a.json" "['base_images']['jre_substituted']")" = "False" ] && ok "A 未替代 ⇒ jre_substituted=false" || no "A substituted" "$(jget "$W/a.json" "['base_images']['jre_substituted']")"
jget "$W/a.json" "['equivalence_note']" | grep -q "与参考底座一致" && ok "A note 说明无影响" || no "A note" "$(jget "$W/a.json" "['equivalence_note']" | cut -c1-60)"
for k in product version arch engine_min images base_images; do jget "$W/a.json" "['$k']" >/dev/null && :; done
python3 -c "import json;d=json.load(open('$W/a.json'));assert all(k in d for k in ('product','version','arch','git','built_at','engine_min','base_images','equivalence_note','images')),d.keys()" && ok "A 既有字段齐全（含新增两字段）" || no "A 字段" "缺字段"

# 用例B：替代底座（差异四要素 + 影响）
( export PHX_PKG_LIB_ONLY=1 PHX_LOG_FILE="$W/b.log" JRE_BASE_IMG=eclipse-temurin:21-jre-jammy JAVA_BIN_ARG=java
  . docker/scripts/package.sh >/dev/null 2>&1
  pkg_manifest_write "$W/b.json" 2.1.0 amd64 "v2.0.0-3-gabc" abc123 '"phoenix-backend:2.1.0"' )
[ "$(jget "$W/b.json" "['base_images']['jre_substituted']")" = "True" ] && ok "B 替代 ⇒ jre_substituted=true" || no "B substituted" "$(jget "$W/b.json" "['base_images']['jre_substituted']")"
[ "$(jget "$W/b.json" "['base_images']['jre']")" = "eclipse-temurin:21-jre-jammy" ] && ok "B 记录实际底座" || no "B jre 记录" "$(jget "$W/b.json" "['base_images']['jre']")"
[ "$(jget "$W/b.json" "['base_images']['jre_reference']")" = "docker.elastic.co/elasticsearch/elasticsearch:9.2.5" ] && ok "B 记录参考底座" || no "B ref" "$(jget "$W/b.json" "['base_images']['jre_reference']")"
N="$(jget "$W/b.json" "['equivalence_note']")"
echo "$N" | grep -q "架构=amd64" && echo "$N" | grep -q "JRE底座=eclipse-temurin:21-jre-jammy" && echo "$N" | grep -qE "JAVA_BIN=[^ ]+" && echo "$N" | grep -q "是否替代=true" && echo "$N" | grep -q "各自 install.sh 端到端验证" && ok "B note 含差异四要素+影响" || no "B note 要素" "$N"

# 用例C：--base-image 参数生效
( export PHX_PKG_LIB_ONLY=1 PHX_LOG_FILE="$W/c.log"
  . docker/scripts/package.sh --base-image eclipse-temurin:21-jre-jammy >/dev/null 2>&1
  printf '%s' "$JRE_BASE" > "$W/c.val" )
[ "$(cat "$W/c.val")" = "eclipse-temurin:21-jre-jammy" ] && ok "C --base-image 生效（JRE_BASE 被覆盖）" || no "C 参数" "$(cat "$W/c.val")"

# 用例D：既有参数回归（--version/--arch/--out 不受影响）
( export PHX_PKG_LIB_ONLY=1 PHX_LOG_FILE="$W/d.log"
  . docker/scripts/package.sh --version 2.1.0 --arch amd64 --out /tmp/x >/dev/null 2>&1
  printf '%s|%s|%s' "$VERSION" "$ARCH" "$OUTDIR" > "$W/d.val" )
[ "$(cat "$W/d.val")" = "2.1.0|amd64|/tmp/x" ] && ok "D 既有参数解析零回归（version/arch/out）" || no "D 回归" "$(cat "$W/d.val")"
printf '\n  ── T-05 断言: 通过=%d 失败=%d\n' "$P" "$F"; [ "$F" -eq 0 ]
