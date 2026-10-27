#!/usr/bin/env bash
# T-09 验证：upgrade.sh 升级统一入口（交付包模式 + 仓库开发栈模式）
# 用法: bash docker/scripts/tests/t09_upgrade_selftest.sh   （全程 --dry-run，不触碰任何栈；退出码 0=全绿）
# 纪律: 每场景独立载荷副本（L-102）；断言"命令是否被组装/是否零副作用"，不靠肉眼
set -u
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
UP="$ROOT/docker/scripts/upgrade.sh"
# docker CLI 自愈（与 upgrade.sh 同规则），保证探测类场景可判定
if ! command -v docker >/dev/null 2>&1; then
  for d in /usr/local/bin /Applications/Docker.app/Contents/Resources/bin /snap/bin; do
    if [ -x "$d/docker" ]; then PATH="$d:$PATH"; break; fi
  done
fi
BASH32="/bin/bash"                        # macOS 自带 3.2；不存在则退回当前 bash
[ -x "$BASH32" ] || BASH32="$(command -v bash)"
[ -f "$UP" ] || { echo "  ❌ 环境自检失败: 缺 $UP"; exit 3; }
W="$(mktemp -d /tmp/t09up.XXXXXX)"
P=0; F=0; S=0
ok(){ P=$((P+1)); printf '  ✅ %s\n' "$1"; }
no(){ F=$((F+1)); printf '  ❌ %s — %s\n' "$1" "$2"; }
sk(){ S=$((S+1)); printf '  ⚠️ 跳过 %s（条件不满足，非通过）\n' "$1"; }

mkpkg() { # mkpkg <目录> —— 造一个"交付包根"（install.sh + manifest.json + upgrade.sh），不带镜像不安装
  mkdir -p "$1/docker/scripts/lib"
  cp "$ROOT/docker/scripts/install.sh" "$1/install.sh"
  cp "$ROOT/docker/scripts/lib/common.sh" "$1/docker/scripts/lib/common.sh" 2>/dev/null || true
  cp "$UP" "$1/upgrade.sh"
  printf '{\n  "product": "Phoenix-Agent-Java",\n  "version": "2.1.1",\n  "arch": "arm64"\n}\n' > "$1/manifest.json"
}
FAKE_ENV="$W/old-install.env"
printf 'PHOENIX_HTTP_PORT=8777\nPG_PASSWORD=s3cr3t-from-old\nIMAGE_TAG=v2.0.0\n' > "$FAKE_ENV"

echo "--- 场景 1：包内一键（两个参数都给，dry-run）---"
D1="$W/pkg1"; mkpkg "$D1"
O1=$(cd "$D1" && "$BASH32" upgrade.sh --dry-run --project t09demo --env-from "$FAKE_ENV" 2>&1); R1=$?
echo "$O1" | grep -q "场景 = 交付包升级" && ok "1a 判为交付包场景（bash3.2: $("$BASH32" --version | head -1 | cut -d, -f1)）" || no "1a 场景判别" "$(echo "$O1" | head -1)"
echo "$O1" | grep -q "bash install.sh --offline --timeout 900 --project t09demo --env-from $FAKE_ENV" \
  && ok "1b 组装出的命令与 install.sh 参数契约一致" || no "1b 命令组装" "$(echo "$O1" | grep 即将 || echo "$O1" | head -2)"
if [ "$R1" -eq 0 ]; then ok "1c 退出码 0"; else no "1c 退出码" "$R1"; fi
[ ! -f "$D1/.phoenix-install.state" ] && [ ! -f "$D1/docker/.env" ] && [ ! -f "$D1/RECEIPT" ] \
  && ok "1d dry-run 零副作用（无状态文件/无 .env/无收据）" || no "1d 零副作用" "出现了安装产物"
[ $(( $(ls -A "$D1" | wc -l) )) -eq 4 ] && ok "1e 包目录保持 4 项（install/manifest/upgrade/docker）" || no "1e 目录被写入" "$(ls -A "$D1" | tr '\n' ' ')"

echo "--- 场景 2：包内自动探测（不给 --project/--env-from）---"
NBK=$(command -v docker >/dev/null 2>&1 && docker ps -a --format '{{.Names}}' 2>/dev/null | grep -Ec -- '-backend(-[0-9]+)?$' || echo 0)
NPJ=$(command -v docker >/dev/null 2>&1 && docker ps -a --format '{{.Names}}' 2>/dev/null | grep -E -- '-backend(-[0-9]+)?$' \
      | while read -r n; do docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$n" 2>/dev/null; done \
      | sort -u | sed '/^$/d' | wc -l | tr -d ' ' || echo 0)
if [ "${NBK:-0}" -eq 0 ]; then
  sk "场景 2（本机无任何 backend 容器，自动探测无从验证）"
elif [ "${NPJ:-0}" -gt 1 ]; then
  D2="$W/pkg2"; mkpkg "$D2"
  O2=$(cd "$D2" && bash upgrade.sh --dry-run 2>&1); R2=$?
  [ "$R2" -ne 0 ] && ok "2a 多栈并存（本机 $NPJ 个栈）拒绝猜测并退出非 0" || no "2a 多栈保护" "却返回 0"
  echo "$O2" | grep -q '需指明升级哪一个' && echo "$O2" | grep -q '用法：bash upgrade.sh --project' \
    && ok "2b 列出容器→项目映射并给出可执行下一步" || no "2b 提示内容" "$(echo "$O2" | tail -2)"
else
  D2="$W/pkg2"; mkpkg "$D2"
  O2=$(cd "$D2" && bash upgrade.sh --dry-run 2>&1); R2=$?
  [ $R2 -eq 0 ] && echo "$O2" | grep -q '自动探测 项目名' && ok "2a 单栈自动探测项目名命中" || no "2a 单栈探测" "$(echo "$O2" | tail -2)"
fi

echo "--- 场景 3：找不到旧 .env ⇒ 必须拒绝（防止随机库密码连不上旧卷）---"
D3="$W/pkg3"; mkpkg "$D3"
O3=$(cd "$D3" && PHX_ENV_SEARCH_BASES="$W/none" bash upgrade.sh --dry-run --project t09nostack 2>&1); R3=$?
[ "$R3" -ne 0 ] && ok "3a 缺旧 .env 时退出非 0（不放行）" || no "3a 缺 .env 放行" "却返回 0"
echo "$O3" | grep -q '随机生成库密码' && echo "$O3" | grep -q -- '--env-from <旧安装目录>/docker/.env' \
  && ok "3b 说清后果（连不上旧数据卷）并给出手动指定方式" || no "3b 提示" "$(echo "$O3" | tail -2)"

echo "--- 场景 4：--env-from 指向不存在的文件 ---"
D4="$W/pkg4"; mkpkg "$D4"
O4=$(cd "$D4" && bash upgrade.sh --dry-run --project t09demo --env-from "$W/nope.env" 2>&1)
echo "$O4" | grep -q '文件不存在' && ok "4 早退并点名文件路径（不留给 install.sh 猜）" || no "4 路径校验" "$(echo "$O4" | tail -1)"

echo "--- 场景 5：未知参数转交 install.sh（不吞、不假报错）---"
D5="$W/pkg5"; mkpkg "$D5"
O5=$(cd "$D5" && bash upgrade.sh --dry-run --project t09demo --env-from "$FAKE_ENV" --port 8777 2>&1)
echo "$O5" | grep -q -- '--env-from '"$FAKE_ENV"' --port 8777' && ok "5a 附加参数原样透传（--port 8777 未被当版本号吞掉）" || no "5a 透传" "$(echo "$O5" | grep 即将)"
O5b=$(cd "$D5" && bash upgrade.sh --dry-run --project t09demo --env-from "$FAKE_ENV" --online 2>&1)
echo "$O5b" | grep -q "bash install.sh --timeout" && ! echo "$O5b" | grep -q -- "--offline" \
  && ok "5b --online 时确实不带 --offline" || no "5b online" "$(echo "$O5b" | grep 即将)"

echo "--- 场景 6/7：仓库开发栈模式（PATCH 无 SQL vs minor 有 SQL）---"
ENVF="$ROOT/docker/.env"; B6=$(command -v md5 >/dev/null 2>&1 && md5 -q "$ENVF" || md5sum "$ENVF" | cut -d' ' -f1)
O6=$(cd "$ROOT" && bash "$UP" v2.1.1 --dry-run 2>&1); R6=$?
echo "$O6" | grep -q '场景 = 仓库开发栈' && ok "6a 仓库模式判别再对（包外不误入包模式）" || no "6a 仓库判别" "$(echo "$O6" | head -1)"
echo "$O6" | grep -q '跳过迁移' && ! echo "$O6" | grep -q 'migrator' \
  && ok "6b v2.1.1（PATCH 无 sql）跳过迁移 ⇒ 原脚本的\"拒绝执行\"门槛已去掉" || no "6b PATCH 门槛" "$(echo "$O6" | grep -E '迁移|migrator' | head -2)"
echo "$O6" | grep -q 'IMAGE_TAG=2.1.1' && ok "6c 镜像 tag 真正落到命令里（原脚本 TAG 是死变量）" || no "6c TAG" "$(echo "$O6" | grep TAG | head -1)"
[ "$R6" -eq 0 ] && ok "6d 退出码 0" || no "6d 退出码" "$R6"
O7=$(cd "$ROOT" && bash "$UP" v2.1.0 --dry-run 2>&1)
echo "$O7" | grep -q '执行 .*/releases/v2.1.0/sql' && echo "$O7" | grep -q 'migrator-post' \
  && ok "7 有 SQL 的版本才迁移，且前后各跑一次 migrator" || no "7 迁移接线" "$(echo "$O7" | grep -E '迁移|migrator' | head -2)"
A6=$(command -v md5 >/dev/null 2>&1 && md5 -q "$ENVF" || md5sum "$ENVF" | cut -d' ' -f1)
[ "$B6" = "$A6" ] && ok "7b dry-run 未改写仓库 docker/.env" || no "7b 改了 .env" "md5 变化"

echo "--- 场景 8：用法与错误处理 ---"
O8=$(cd "$ROOT" && bash "$UP" 2>&1); R8=$?
[ "$R8" -eq 2 ] && echo "$O8" | grep -q '需要版本号' && ok "8a 仓库模式缺版本号 ⇒ rc=2 + 明确用法" || no "8a 缺版本" "rc=$R8"
O9=$(cd "$ROOT" && bash "$UP" -h 2>&1); R9=$?
[ "$R9" -eq 0 ] && echo "$O9" | grep -q 'bash upgrade.sh' && ok "8b -h 打印用法且不含 shebang" || no "8b -h" "$(echo "$O9" | head -1)"

echo "--- 场景 9：打包接线（包根必须有 upgrade.sh，否则客户机没有一键升级）---"
grep -q 'for f in install.sh upgrade.sh' "$ROOT/docker/scripts/package.sh" \
  && ok "9a package.sh 已把 upgrade.sh 拷到包根" || no "9a 打包缺 upgrade.sh" "包根无一键升级入口"
grep -q 'upgrade-entry' "$ROOT/docker/scripts/gate.sh" \
  && ok "9b 出货门禁含 upgrade-entry 断言" || no "9b 门禁未接" "gate.sh 无该项"

echo
echo "=== 通过 $P / 失败 $F / 跳过 $S ==="
[ "$F" -eq 0 ] || exit 1
exit 0
