#!/usr/bin/env bash
# Phoenix 一键打包（docker-auto-pipeline T-02）
# 用法: package.sh [--arch amd64|arm64] [--version X.Y.Z] [--overseas] [--mirror <前缀>] [--out <目录>]
#        [--engine-tgz <本地路径|URL>]  # R-02：通用静态二进制离线引擎件（含字节对账/tar 完整性校验）
#        [--engine-bytes <N>]           # 可选：期望字节数（L-73 对账）
#        [--skip-gate]                  # 仅调试：跳过出货门禁（R-05；禁止用于出货）
# 产物: <out>/phoenix-<ver>-<arch>.tar.gz（自包含：镜像+compose资产+安装脚本+manifest+sha256）
# 兼容契约: bash 3.2（L-05）；失败出口唯一 phx_fail（L-02/L-03）
# shellcheck disable=SC1091  # 动态 source 路径，shellcheck 无法跟随
set -uo pipefail
HERE=$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)   # 兼容 source（$0 在被 source 时不是本脚本）
REPO=$(cd "$HERE/../.." && pwd)
# shellcheck source=lib/common.sh
. "$HERE/lib/common.sh"

TOTAL=8
ARCH=""; VERSION=""; OVERSEAS=0; MIRROR=""; OUTDIR="$REPO/docker/dist"
JRE_BASE="${JRE_BASE_IMG:-docker.elastic.co/elasticsearch/elasticsearch:9.2.5}"
JRE_BASE_REF="${PHX_JRE_BASE_REF:-docker.elastic.co/elasticsearch/elasticsearch:9.2.5}"   # R-01：参考底座（判定"是否替代"）
JAVA_BIN_ARG="${JAVA_BIN:-/usr/share/elasticsearch/jdk/bin/java}"
NGINX_BASE="${NGINX_BASE_IMG:-nginx:latest}"
while [ $# -gt 0 ]; do
  case "$1" in
    --arch) ARCH="$2"; shift 2;;
    --version) VERSION="$2"; shift 2;;
    --overseas) OVERSEAS=1; shift;;
    --mirror) MIRROR="$2"; shift 2;;
    --out) OUTDIR="$2"; shift 2;;
    --with-engine-debs) WITH_DEBS=1; shift;;
    --base-image) JRE_BASE_IMG="$2"; JRE_BASE="$2"; shift 2;;
    --skip-gate) SKIP_GATE=1; shift;;               # 仅调试：跳过出货门禁（禁止用于出货）   # R-01：覆盖 JRE 底座（JRE_BASE 已在其前定值，须同步覆盖）
    --engine-tgz) ENGINE_TGZ="$2"; shift 2;;        # R-02：通用静态二进制（本地路径或 URL）
    --engine-bytes) ENGINE_BYTES="$2"; shift 2;;    # 可选：期望字节数（L-73 对账）
    -h|--help) grep '^#' "$0" | head -6; exit 0;;
    *) echo "未知参数: $1" >&2; exit 2;;
  esac
done
WITH_DEBS="${WITH_DEBS:-0}"
SKIP_GATE="${SKIP_GATE:-0}"
ENGINE_TGZ="${ENGINE_TGZ:-}"
ENGINE_BYTES="${ENGINE_BYTES:-}"

NATIVE=$(uname -m)
case "$NATIVE" in x86_64) NATIVE=amd64;; aarch64|arm64) NATIVE=arm64;; *) NATIVE="";; esac
[ -z "$ARCH" ] && ARCH="$NATIVE"
case "$ARCH" in amd64|arm64) ;; *) phx_log ERROR "arch 仅支持 amd64/arm64（收到: ${ARCH}）"; exit 2;; esac
[ -z "$VERSION" ] && VERSION=$(git -C "$REPO" describe --tags 2>/dev/null | sed 's/^v//')
[ -z "$VERSION" ] && { phx_log ERROR "无法从 git tag 推断版本，请 --version 指定"; exit 2; }
PKGNAME="phoenix-${VERSION}-${ARCH}"

export PHX_LOG_FILE="${PHX_LOG_FILE:-$HOME/.phoenix/package-${VERSION}-${ARCH}-$(date +%Y%m%d-%H%M%S).log}"
phx_log_init
export PHX_STATE_FILE="$OUTDIR/.package-${VERSION}-${ARCH}.state"
mkdir -p "$OUTDIR"
WORK="$OUTDIR/.work-$PKGNAME"
pkg_manifest_write() { # pkg_manifest_write <out> <ver> <arch> <gitdesc> <githash> <imgjson>
  # R-01（v2.1.0）：manifest 记录逐架构底座与差异说明，供两架构一致性核对
  local out="$1" ver="$2" arch="$3" gd="$4" gh="$5" imgs="$6" sub note
  if [ "$JRE_BASE" = "$JRE_BASE_REF" ]; then sub=false; else sub=true; fi
  note="架构=${arch} JRE底座=${JRE_BASE} 参考底座=${JRE_BASE_REF} JAVA_BIN=${JAVA_BIN_ARG:-java} 是否替代=${sub}"
  if [ "$sub" = "true" ]; then
    note="${note}；影响=未使用参考底座，两架构镜像层不一致，出货前必须各自 install.sh 端到端验证（到收据）"
  else
    note="${note}；影响=无（与参考底座一致）"
  fi
  printf '{\n  "product": "Phoenix-Agent-Java",\n  "version": "%s",\n  "arch": "%s",\n  "git": "%s (%s)",\n  "built_at": "%s",\n  "engine_min": "20.10",\n  "base_images": {\n    "jre": "%s",\n    "jre_reference": "%s",\n    "jre_substituted": %s,\n    "nginx": "%s"\n  },\n  "equivalence_note": "%s",\n  "images": [%s]\n}\n' \
    "$ver" "$arch" "$gd" "$gh" "$(date '+%F %T')" "$JRE_BASE" "$JRE_BASE_REF" "$sub" "$NGINX_BASE" "$note" "$imgs" > "$out"
}

pkg_ship_gate() { # pkg_ship_gate <解包payload> <tarball> <证据目录> → 0=门禁通过且哈希复核一致
  local pay="$1" tar="$2" gout="$3" before after gcmd
  gcmd="${PHX_GATE_CMD:-$HERE/gate.sh}"
  before="$(phx_sha256 "$tar")"
  local flag=""
  [ "${PHX_ALLOW_ARCH_MISMATCH:-0}" = "1" ] && flag="--allow-arch-mismatch"
  phx_log INFO "出货门禁开始 payload=${pay} 证据=${gout}"
  bash "$gcmd" --payload "$pay" --out "$gout" --project "phxgate-pkg-$$" \
       --port "${PHX_GATE_PORT:-8190}" --timeout "${PHX_GATE_TIMEOUT:-900}" $flag \
    || { phx_log ERROR "出货门禁未通过 证据=${gout}（修复后重跑；--skip-gate 仅调试，禁止出货）"; return 1; }
  after="$(phx_sha256 "$tar")"
  if [ "$before" != "$after" ]; then
    phx_log ERROR "门禁后包哈希变化 before=${before} after=${after}（门禁期间产物被改动，拒绝出货）"; return 1
  fi
  phx_log INFO "门禁通过且包哈希复核一致 sha256=${after} 证据=${gout}"
  return 0
}

pkg_ship_or_skip() { # pkg_ship_or_skip <解包payload> <tarball> <证据目录> —— 步骤7 用；尊重 SKIP_GATE
  if [ "${SKIP_GATE:-0}" -eq 1 ]; then
    phx_log WARN "--skip-gate：已跳过出货门禁（仅调试用途，禁止用于出货）"
    return 0
  fi
  pkg_ship_gate "$1" "$2" "$3"
}

if [ "${PHX_PKG_LIB_ONLY:-0}" = "1" ]; then return 0; fi   # 仅测试：source 后加载函数即返回（T-05 验证用）

phx_log INFO "打包开始: $PKGNAME (native=$NATIVE overseas=$OVERSEAS) 日志=$PHX_LOG_FILE"

# ref()/ref_any(): 镜像加 mirror 前缀（--overseas/手工 mirror 时直通）
# MP=探活 URL(带 scheme, curl 用)；MPH=镜像引用前缀(必须无 scheme——docker 拒收 https:// 开头的 stage name，首跑真抓)
# ref=library 官方镜像；ref_any=分型（含 / 的第三方镜像不加 /library/，plan v1.0.1）
MP=""; MPH=""
ref() { if [ -n "$MPH" ]; then echo "$MPH/library/$1"; else echo "$1"; fi; }
ref_any() { case "$1" in */*) if [ -n "$MPH" ]; then echo "$MPH/$1"; else echo "$1"; fi;; *) ref "$1";; esac; }
mph_from() { MPH="${1#https://}"; MPH="${MPH#http://}"; MPH="${MPH%/}"; }
# 基础运行时三件（compose 默认引用；plan v1.0.1 五镜像化——T-03 演练实证的 R-06 缺口）
BASE_IMGS="redis:7-alpine pgvector/pgvector:pg16 postgres:16-alpine"

# 设计规则（二跑教训）：步骤1/2 为探测/产值步——每次必跑，不入状态机；
# 状态机只记昂贵且幂等的副作用步（3编译/4组装/5payload/6tar/7自校验/8收据）
phx_log INFO "====> 步骤 1/$TOTAL: 环境自检"
phx_require_cmd docker || phx_fail 1 $TOTAL "缺 docker CLI"
docker info >/dev/null 2>&1 || phx_fail 1 $TOTAL "docker daemon 不可达"
phx_disk_ok "$OUTDIR" 10000 || phx_fail 1 $TOTAL "$OUTDIR 可用空间不足 10GB"
[ -n "$NATIVE" ] || phx_log WARN "非常见架构 uname=${NATIVE}，编译阶段将不加 --platform"

phx_log INFO "====> 步骤 2/$TOTAL: 镜像源选优"
if true; then
  if [ "$OVERSEAS" -eq 1 ]; then
    MP=""; MPH=""; phx_log INFO "--overseas：全走官方源"
  elif [ -n "$MIRROR" ]; then
    MP="${MIRROR%/}"; mph_from "$MP"; phx_log INFO "手工指定 mirror: $MP (镜像前缀 $MPH)"
  else
    # tr -d '\r' 前置：mirrors.list 若是 CRLF（Windows 检出常见），token 尾部会带 \r
    # 导致 curl 对每个源都返回 000（瞬时判死、连 8s 超时都用不上）——实测复现见 BUG-147。
    CANDS=$(tr -d '\r' < "$HERE/mirrors.list" | grep -v '^#' | grep -v '^[[:space:]]*$' | tr '\n' ' ')
    # shellcheck disable=SC2086
    MP=$(phx_mirror_pick $CANDS) || phx_fail 2 $TOTAL "候选 mirror 全不可达（已重试 2 轮，见 mirrors.list）——先查该文件行尾（CRLF 会让每个源都 000）与网络，再重跑；仍失败用 --mirror <源> 手工指定，或 --overseas"
    mph_from "$MP"
  fi
fi


phx_step 3 $TOTAL "容器内编译 jar+dist（native，产物架构无关）" && {
  rm -rf "$WORK/artifacts" && mkdir -p "$WORK/artifacts"
  if [ "$OVERSEAS" -eq 1 ]; then SETTINGS_ARG="docker/maven/settings.default.xml"; NPM_ARG="https://registry.npmjs.org"
  else SETTINGS_ARG="docker/maven/settings.aliyun.xml"; NPM_ARG="https://registry.npmmirror.com"; fi
  PLAT="" ; [ -n "$NATIVE" ] && PLAT="--platform linux/$NATIVE"
  # 构建基础镜像预拉取 + 完整性校验（BUG-148 加固 / BUG-176）：坏 blob 不再拖到构建深处才炸
  PHX_PREFETCH_PLATFORM=""; [ -n "$NATIVE" ] && PHX_PREFETCH_PLATFORM="linux/$NATIVE" || true
  phx_prefetch_images "$(ref maven:3.9-eclipse-temurin-21)" "$(ref node:22-bookworm)" \
    || phx_fail 3 $TOTAL "构建基础镜像预拉取/校验失败（镜像源传输质量差，换 --mirror 重跑）"
  PHX_PREFETCH_PLATFORM=""
  phx_log INFO "backend 编译（MAVEN_IMAGE=$(ref maven:3.9-eclipse-temurin-21) settings=${SETTINGS_ARG}）"
  # shellcheck disable=SC2086
  docker build $PLAT -f "$REPO/docker/Dockerfile.backend.multistage" --target jar \
    --build-arg "MAVEN_IMAGE=$(ref maven:3.9-eclipse-temurin-21)" \
    --build-arg "MAVEN_SETTINGS=$SETTINGS_ARG" \
    -t "phx-tmp-jar:$VERSION" "$REPO" >>"$PHX_LOG_FILE" 2>&1 \
    || phx_fail 3 $TOTAL "backend 编译失败（详见日志尾部）"
  phx_log INFO "frontend 编译（NODE_IMAGE=$(ref node:22-bookworm) npm=${NPM_ARG}）"
  # shellcheck disable=SC2086
  docker build $PLAT -f "$REPO/docker/Dockerfile.frontend.multistage" --target dist \
    --build-arg "NODE_IMAGE=$(ref node:22-bookworm)" \
    --build-arg "NPM_REGISTRY=$NPM_ARG" \
    -t "phx-tmp-dist:$VERSION" "$REPO" >>"$PHX_LOG_FILE" 2>&1 \
    || phx_fail 3 $TOTAL "frontend 编译失败（详见日志尾部）"
  CID=$(docker create "phx-tmp-jar:$VERSION") && docker cp "$CID:/out/phoenix-admin.jar" "$WORK/artifacts/phoenix-admin.jar" && docker rm "$CID" >/dev/null
  CID=$(docker create "phx-tmp-dist:$VERSION") && docker cp "$CID:/out/dist" "$WORK/artifacts/dist" && docker cp "$CID:/out/dist-mobile" "$WORK/artifacts/dist-mobile" && docker rm "$CID" >/dev/null
  docker rmi "phx-tmp-jar:$VERSION" "phx-tmp-dist:$VERSION" >/dev/null 2>&1
  [ -s "$WORK/artifacts/phoenix-admin.jar" ] && [ -f "$WORK/artifacts/dist/index.html" ] && [ -f "$WORK/artifacts/dist-mobile/index.html" ] \
    || phx_fail 3 $TOTAL "工件提取核验失败（jar / dist/index.html / dist-mobile/index.html 有缺）"
  phx_log INFO "工件就绪: jar=$(du -m "$WORK/artifacts/phoenix-admin.jar" | awk '{print $1}')MB dist=$(du -sm "$WORK/artifacts/dist" | awk '{print $1}')MB mobile=$(du -sm "$WORK/artifacts/dist-mobile" | awk '{print $1}')MB"
  phx_step_mark 3
}

phx_step 4 $TOTAL "组装双侧镜像 + 基础运行时三件备齐（--platform linux/${ARCH}）" && {
  mkdir -p "$REPO/docker/.stage"
  cp "$WORK/artifacts/phoenix-admin.jar" "$REPO/docker/.stage/phoenix-admin.jar"
  rm -rf "$REPO/docker/.stage/dist" && cp -r "$WORK/artifacts/dist" "$REPO/docker/.stage/dist"
  # BUG-180: mobile 产物也要 staging —— Dockerfile.frontend:7 会 COPY docker/.stage/dist-mobile
  rm -rf "$REPO/docker/.stage/dist-mobile" && cp -r "$WORK/artifacts/dist-mobile" "$REPO/docker/.stage/dist-mobile"
  # 组装用基础镜像预拉取 + 完整性校验（BUG-176）：nginx / JRE 基座 / 三件基础运行时
  PHX_PREFETCH_PLATFORM="linux/$ARCH"
  # shellcheck disable=SC2046
  phx_prefetch_images "$(ref "$NGINX_BASE")" "$JRE_BASE" $(for _pi in $BASE_IMGS; do ref_any "$_pi"; done) \
    || phx_fail 4 $TOTAL "组装用基础镜像预拉取/校验失败（镜像源传输质量差，换 --mirror 重跑）"
  PHX_PREFETCH_PLATFORM=""
  docker build --platform "linux/$ARCH" -f "$REPO/docker/Dockerfile.backend" \
    --build-arg "JRE_BASE_IMG=$JRE_BASE" --build-arg "JAVA_BIN=$JAVA_BIN_ARG" \
    -t "phoenix-backend:$VERSION" "$REPO" >>"$PHX_LOG_FILE" 2>&1 \
    || phx_fail 4 $TOTAL "backend 镜像组装失败"
  docker build --platform "linux/$ARCH" -f "$REPO/docker/Dockerfile.frontend" \
    --build-arg "NGINX_BASE_IMG=$(ref "$NGINX_BASE")" \
    -t "phoenix-frontend:$VERSION" "$REPO" >>"$PHX_LOG_FILE" 2>&1 \
    || phx_fail 4 $TOTAL "frontend 镜像组装失败"
  BARCH=$(docker image inspect "phoenix-backend:$VERSION" --format '{{.Architecture}}')
  FARCH=$(docker image inspect "phoenix-frontend:$VERSION" --format '{{.Architecture}}')
  [ "$BARCH" = "$ARCH" ] && [ "$FARCH" = "$ARCH" ] \
    || phx_fail 4 $TOTAL "架构核验不符（期望 $ARCH 实得 backend=$BARCH frontend=${FARCH}）"
  # 基础三件：本地缓存架构不符即按目标架构重拉（mirror 前缀 pull + retag 回裸名，compose 零改动）
  for img in $BASE_IMGS; do
    CURA=$(docker image inspect "$img" --format '{{.Architecture}}' 2>/dev/null || echo none)
    if [ "$CURA" != "$ARCH" ]; then
      SRC=$(ref_any "$img")
      phx_log INFO "基础镜像备齐: $img ($SRC, linux/$ARCH)"
      docker pull --platform "linux/$ARCH" "$SRC" >>"$PHX_LOG_FILE" 2>&1 || phx_fail 4 $TOTAL "基础镜像拉取失败: ${img}（源 ${SRC}；可 --mirror 换源或 --overseas）"
      if [ "$SRC" != "$img" ]; then docker tag "$SRC" "$img"; fi
    else
      phx_log INFO "基础镜像已在（$img, ${ARCH}）"
    fi
  done
  phx_step_mark 4
}

phx_step 5 $TOTAL "payload 组装（deploy 资产+脚本+manifest+SHA256SUMS）" && {
  PAY="$WORK/$PKGNAME"
  rm -rf "$PAY" && mkdir -p "$PAY/images" "$PAY/docs"
  # 仓库同构布局：docker/（compose 工作目录）+ sql/ + releases/（compose 的 ../ 引用零改动）
  mkdir -p "$PAY/docker"
  cp "$REPO/docker/docker-compose.yaml" "$PAY/docker/"
  cp "$REPO/docker/.env.example" "$PAY/docker/"
  cp -r "$REPO/docker/init" "$PAY/docker/init"
  cp -r "$REPO/docker/nginx" "$PAY/docker/nginx"
  cp -r "$REPO/docker/scripts" "$PAY/docker/scripts"
  cp -r "$REPO/sql" "$PAY/sql"
  cp -r "$REPO/releases" "$PAY/releases"
  # 安装脚本（T-03/T-05 交付后自动入包；缺席时手动链兜底并如实警告）
  # upgrade.sh 与 install.sh 同级入包根 = 客户机一键升级入口（自动探测旧项目名/旧 .env 后转调 install.sh）
  for f in install.sh upgrade.sh install.ps1; do
    if [ -f "$HERE/$f" ]; then cp "$HERE/$f" "$PAY/$f"; chmod +x "$PAY/$f" 2>/dev/null || true
    else phx_log WARN "$f 尚未交付（T-03/T-05）——本包暂以 docs 手动链安装"; fi
  done
  [ -d "$REPO/docker/payload-docs" ] && cp -r "$REPO/docker/payload-docs/." "$PAY/docs/"
  if [ ! -f "$PAY/docs/INSTALL-QUICK.md" ]; then
    cat > "$PAY/docs/INSTALL-QUICK.md" <<'DOCEOF'
# 手动链安装（install.sh 交付前的兜底路径）
1. `docker load -i images/phoenix-images.tar`
2. `cd docker && cp .env.example .env`，改 `IMAGE_TAG`（=包版本）与 `PG_PASSWORD`
3. `docker compose up -d`，等 backend healthy
4. `sh scripts/verify.sh` 全 PASS 即成；浏览器开 `http://localhost:9080`（admin/123456，首登改密）
DOCEOF
  fi
  # shellcheck disable=SC2086
  docker save "phoenix-backend:$VERSION" "phoenix-frontend:$VERSION" $BASE_IMGS > "$PAY/images/phoenix-images.tar" \
    || phx_fail 5 $TOTAL "docker save 失败（五镜像）"
  GITDESC=$(git -C "$REPO" describe --tags --always 2>/dev/null || echo unknown)
  GITHASH=$(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo unknown)
    if [ "$JRE_BASE" != "$JRE_BASE_REF" ]; then
      phx_log WARN "底座差异: 本包 JRE 底座=${JRE_BASE} 参考=${JRE_BASE_REF} JAVA_BIN=${JAVA_BIN_ARG:-java}（已记入 manifest.base_images/equivalence_note；R-01：两架构各自端到端验证后方可出货）"
    fi
  IMGJSON="\"phoenix-backend:$VERSION\", \"phoenix-frontend:$VERSION\""
  for img in $BASE_IMGS; do IMGJSON="$IMGJSON, \"$img\""; done
    pkg_manifest_write "$PAY/manifest.json" "$VERSION" "$ARCH" "$GITDESC" "$GITHASH" "$IMGJSON" \
      || phx_fail 5 $TOTAL "manifest 写出失败"
  python3 -c "import json;json.load(open('$PAY/manifest.json'))" 2>/dev/null || phx_log WARN "manifest.json 未过 JSON 解析自检（目标机无 python3 不影响安装，仅记录）"
  # R-02（v2.1.0）：通用静态二进制离线引擎件——**在 SHA256SUMS 之前**入包，确保安装侧完整性校验覆盖它
  if [ -n "$ENGINE_TGZ" ]; then
    mkdir -p "$PAY/engine"
    STAGED="$(phx_engine_tgz_stage "$ENGINE_TGZ" "$PAY/engine" "$ARCH" "$ENGINE_BYTES")" \
      || phx_fail 5 $TOTAL "离线引擎件入包失败（来源不可达/截断/损坏；见日志）"
    phx_log INFO "engine static 已入包 path=${STAGED}（随 SHA256SUMS 入册）"
  fi
  # SHA256SUMS（两段式：清单先落盘再逐一算，无管道读写竞态；相对路径兼容 phx_sha_check_dir）
  ( cd "$PAY" && find . -type f ! -name SHA256SUMS | sed 's|^\./||' | sort > "$WORK/.filelist" )
  ( cd "$PAY" && while read -r f; do
      if command -v sha256sum >/dev/null 2>&1; then H=$(sha256sum "$f" | awk '{print $1}'); else H=$(shasum -a 256 "$f" | awk '{print $1}'); fi
      echo "$H  $f"
    done < "$WORK/.filelist" > "$WORK/.sums" && cp "$WORK/.sums" "$PAY/SHA256SUMS" )
  if [ "$WITH_DEBS" -eq 1 ]; then
    phx_log INFO "--with-engine-debs：容器内下载 Ubuntu 22.04 ($ARCH) 引擎离线 deb 组"
    mkdir -p "$PAY/engine"
    docker run --rm --platform "linux/$ARCH" -v "$PAY/engine:/debs" "$(ref ubuntu:22.04)" bash -c \
      'sed -i "s|archive.ubuntu.com|mirrors.aliyun.com|g; s|security.ubuntu.com|mirrors.aliyun.com|g" /etc/apt/sources.list \
       && apt-get update -qq \
       && apt-get install -y -qq --download-only -o Dir::Cache::archives=/debs docker.io docker-compose-v2' \
      >>"$PHX_LOG_FILE" 2>&1 \
      && [ -n "$(ls "$PAY/engine"/*.deb 2>/dev/null)" ] \
      || phx_log WARN "engine debs 下载失败/为空——安装侧将按在线或降级路径（不阻塞打包）"
    DEBSIZE=$(du -sm "$PAY/engine" 2>/dev/null | awk '{print $1}')
    phx_log INFO "engine debs: ${DEBSIZE:-0}MB（清单已随 SHA256SUMS 入册）"
  fi
  phx_step_mark 5
}

phx_step 6 $TOTAL "打包 tar.gz" && {
  TAR="$OUTDIR/$PKGNAME.tar.gz"
  rm -f "$TAR"
  ( cd "$WORK" && tar -czf "$TAR" "$PKGNAME" ) || phx_fail 6 $TOTAL "tar 失败"
  phx_step_mark 6
}

phx_step 7 $TOTAL "自校验（解包+sha 全量+manifest 复算）" && {
  VT=$(mktemp -d /tmp/phx-verify.XXXXXX)
  tar -xzf "$OUTDIR/$PKGNAME.tar.gz" -C "$VT" || phx_fail 7 $TOTAL "解包失败"
  BADS=$(phx_sha_check_dir "$VT/$PKGNAME" "$VT/$PKGNAME/SHA256SUMS"); RC=$?
  [ $RC -eq 0 ] || phx_fail 7 $TOTAL "sha 校验失败: $BADS"
  grep -q "\"version\": \"$VERSION\"" "$VT/$PKGNAME/manifest.json" || phx_fail 7 $TOTAL "manifest 版本不符"
  grep -q "\"arch\": \"$ARCH\"" "$VT/$PKGNAME/manifest.json" || phx_fail 7 $TOTAL "manifest 架构不符"
  pkg_ship_or_skip "$VT/$PKGNAME" "$OUTDIR/$PKGNAME.tar.gz" "$OUTDIR/gate-${VERSION}-${ARCH}-$(date +%Y%m%d-%H%M%S)" \
    || phx_fail 7 $TOTAL "出货门禁未通过（证据见 $OUTDIR/gate-*；修复后重跑）"
  rm -rf "$VT"
  phx_step_mark 7
}

phx_step 8 $TOTAL "出货收据" && {
  TARSHA=$(phx_sha256 "$OUTDIR/$PKGNAME.tar.gz")
  echo "$TARSHA  $PKGNAME.tar.gz" > "$OUTDIR/$PKGNAME.tar.gz.sha256"
  phx_receipt "$OUTDIR/$PKGNAME.RECEIPT" "打包完成" \
    "包文件" "$OUTDIR/$PKGNAME.tar.gz" \
    "体积" "$(du -h "$OUTDIR/$PKGNAME.tar.gz" | awk '{print $1}')" \
    "sha256" "$TARSHA" \
    "版本/架构" "$VERSION / $ARCH" \
    "日志" "$PHX_LOG_FILE" >/dev/null
  cat "$OUTDIR/$PKGNAME.RECEIPT"
  phx_step_mark 8
}
rm -rf "$WORK" "$PHX_STATE_FILE"
phx_log INFO "打包完成: $OUTDIR/$PKGNAME.tar.gz"
