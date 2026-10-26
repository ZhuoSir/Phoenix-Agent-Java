#!/usr/bin/env bash
# Phoenix 交付流水线共用基座（docker-auto-pipeline T-01）
# 兼容契约：bash 3.2 子集——禁关联数组 / 禁 ${var,,} / 禁 &>>（lessons L-05）
# 消费方：package.sh（构建机）/ install.sh（Linux/mac 目标机）
# 约定：所有函数 phx_ 前缀；日志走 phx_log；失败出口唯一 phx_fail

# ---------- 日志（R-09：全程 tee 落盘，失败输出必含日志路径） ----------
PHX_LOG_FILE="${PHX_LOG_FILE:-$HOME/.phoenix/pipeline-$(date +%Y%m%d-%H%M%S).log}"

phx_log_init() {
  mkdir -p "$(dirname "$PHX_LOG_FILE")" || return 1
  : >> "$PHX_LOG_FILE" || return 1
}

phx_log() { # phx_log <LEVEL> <msg...>  —— stderr+文件双写；stdout 专属函数返回值通道
  # （selftest 抓获的真 bug#2：日志走 stdout 会污染 $(...) 命令替换的返回值）
  local lvl="$1"; shift
  local line
  line="[$(date '+%F %T')] [$lvl] $*"
  echo "$line" | tee -a "$PHX_LOG_FILE" >&2
}

# ---------- 九步状态机（R-07 幂等 + R-05 断点续传共用） ----------
PHX_STATE_FILE="${PHX_STATE_FILE:-.phoenix-install.state}"

phx_step_is_done() { # phx_step_is_done <n>
  [ -f "$PHX_STATE_FILE" ] && grep -qx "$1" "$PHX_STATE_FILE"
}

phx_step_mark() { # phx_step_mark <n>
  phx_step_is_done "$1" || echo "$1" >> "$PHX_STATE_FILE"
}

phx_step() { # phx_step <n> <total> <名称> → 0=该干活 1=已完成跳过
  local n="$1" total="$2" name="$3"
  if phx_step_is_done "$n"; then
    phx_log INFO "步骤 $n/$total 跳过（已完成）: $name"
    return 1
  fi
  phx_log INFO "====> 步骤 $n/$total: $name"
  return 0
}

phx_fail() { # phx_fail <n> <total> <原因...> —— 唯一失败出口（L-02/L-03：步骤号+日志路径）
  local n="$1" total="$2"; shift 2
  phx_log ERROR "步骤 $n/$total 失败: $*"
  phx_log ERROR "完整日志: $PHX_LOG_FILE"
  exit 1
}

# ---------- mirror 竞速探活（R-02；L-09：-m 8 且以 HTTP 响应码判活，401 也算活） ----------
# 抗抖动（BUG-147）：网络瞬时抖动时整轮候选会同时判死（实测三个源在同一秒内全死、
# 数分钟后复测全部 200/401）⇒ 整轮重试 2 遍（间隔 3s）再放弃。
phx_mirror_pick() { # phx_mirror_pick <候选前缀...> → stdout=首个活口；全死返回 1
  local m code attempt=1
  while [ "$attempt" -le 2 ]; do
    for m in "$@"; do
      [ -z "$m" ] && continue
      # curl 连接失败时 -w 自会输出 000——不可再 || echo 000（会拼接成 000000 骗过判活，selftest 抓获的真 bug）
      code=$(curl -s -m 8 -o /dev/null -w '%{http_code}' "$m/v2/" 2>>"$PHX_LOG_FILE" || true)
      if [ -n "$code" ] && [ "$code" != "000" ]; then
        phx_log INFO "mirror 选定: $m (HTTP $code)"
        echo "$m"
        return 0
      fi
      phx_log WARN "mirror 不可达: ${m}（第 $attempt/2 轮）"
    done
    [ "$attempt" -lt 2 ] && sleep 3
    attempt=$((attempt+1))
  done
  return 1
}

# ---------- sha256 双平台助手（Linux sha256sum / mac shasum） ----------
phx_sha256() { # phx_sha256 <file> → stdout=hex
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

phx_sha_check_dir() { # phx_sha_check_dir <目录> <SHA256SUMS文件> → stdout=坏文件清单；有坏返回 1（R-08）
  local dir="$1" sums="$2" bad=0 want f got
  while read -r want f; do
    [ -z "${f:-}" ] && continue
    case "$f" in \**) f="${f#\*}";; esac          # GNU coreutils 二进制标记
    if [ ! -f "$dir/$f" ]; then
      echo "MISSING: $f"; bad=1; continue
    fi
    got=$(phx_sha256 "$dir/$f")
    if [ "$got" != "$want" ]; then
      echo "BAD: $f"; bad=1
    fi
  done < "$sums"
  return $bad
}

# ---------- 收据卡（R-09：全绿才打；键值对入参，双写 stdout+RECEIPT 文件） ----------
phx_receipt() { # phx_receipt <RECEIPT文件路径> <标题> [键 值]...
  local out="$1" title="$2"; shift 2
  local body k v
  body="======================================================"$'\n'
  body="${body}  Phoenix 安装收据 —— ${title}"$'\n'
  body="${body}======================================================"$'\n'
  while [ $# -ge 2 ]; do
    k="$1"; v="$2"; shift 2
    body="${body}  ${k}: ${v}"$'\n'
  done
  body="${body}======================================================"
  printf '%s\n' "$body" | tee "$out"
}

# ---------- 环境与资源探测（L-12 三查的零件） ----------
phx_require_cmd() { # phx_require_cmd <cmd>
  command -v "$1" >/dev/null 2>&1
}

phx_disk_free_mb() { # phx_disk_free_mb <路径> → stdout=可用MB
  df -Pm "$1" 2>/dev/null | awk 'NR==2 {print $4}'
}

phx_disk_ok() { # phx_disk_ok <路径> <需要MB>
  local free
  free=$(phx_disk_free_mb "$1")
  [ -n "$free" ] && [ "$free" -ge "$2" ]
}

# ---------- 镜像预拉取 + 完整性校验（BUG-148 加固 / BUG-176） ----------
# 竞速只能探 /v2/ 响应码，没有便携的带宽测法；坏源会把"截断 blob"混过 pull 校验，
# 直到 BuildKit 计算缓存键才以 `short read: expected N bytes but got 0` 炸在离现场很远的步骤里
# （实测：nginx 627B 层长期 0B，步骤 4/8 报错，用户现场完全不可归因）。
# 故在动手构建前：逐个 docker pull，再用 docker save 把镜像**逐 blob 读一遍**（截断必现形）；
# 失败即 rmi 掉重来，重试 max 次仍失败则报明确错误并指向 --mirror。
# 可选环境变量 PHX_PREFETCH_PLATFORM（形如 linux/amd64）透传给 docker pull。
phx_prefetch_images() { # phx_prefetch_images <镜像>... → 全绿返回 0
  local img attempt max=2 bad=0 plat=""
  [ -n "${PHX_PREFETCH_PLATFORM:-}" ] && plat="--platform $PHX_PREFETCH_PLATFORM"
  for img in "$@"; do
    [ -z "$img" ] && continue
    attempt=1
    while [ "$attempt" -le "$max" ]; do
      phx_log INFO "预拉取+校验: ${img}（第 $attempt/$max 次）"
      # shellcheck disable=SC2086
      if docker pull $plat "$img" >>"$PHX_LOG_FILE" 2>&1 \
         && docker save "$img" >/dev/null 2>>"$PHX_LOG_FILE"; then
        phx_log INFO "  完整性校验通过: $img"
        break
      fi
      phx_log WARN "  预拉取/校验失败（blob 可能被源截断）——清掉重试: $img"
      docker rmi "$img" >>"$PHX_LOG_FILE" 2>&1 || true
      attempt=$((attempt+1))
    done
    [ "$attempt" -le "$max" ] || bad=1
  done
  if [ "$bad" -ne 0 ]; then
    phx_log ERROR "有镜像预拉取/完整性校验失败——镜像源传输质量差（竞速只测响应码、不测带宽）"
    phx_log ERROR "处置: 换源重跑，如 --mirror https://docker.m.daocloud.io 或 --mirror https://docker.1panel.live"
    return 1
  fi
  return 0
}

# ---------- 引擎安装：受限网络下的国内回退（BUG-145 / BUG-175） ----------
# get.docker.com 的 **安装脚本本体在境外**（`--mirror Aliyun` 只管 deb 包源），
# 国内受限网络下 curl 会被 reset ⇒ 脚本判定"引擎装不上"直接失败。
# 本函数改走 docker-ce 的国内 apt 仓库；**多源有序回退**——BUG-175：此前只硬编码阿里云单源，
# 阿里云 docker-ce 子仓索引 CDN 未同步时报 `File has unexpected size … Mirror sync in progress?`
# ⇒ `docker-ce has no installation candidate` ⇒ 整条安装链在 A/4 硬失败且无第二源可退。
# 成功返回 0 并在日志里留下实际生效的源；过程输出进 ${PHX_LOG_FILE}（调用方负责报错文案）。
phx_install_docker_aliyun() {
  local s="" arch="" codename="" m ok=0
  [ "$(id -u)" -ne 0 ] && s="sudo"
  case "$(uname -m)" in
    x86_64) arch=amd64;;
    aarch64|arm64) arch=arm64;;
  esac
  if [ -z "$arch" ]; then phx_log ERROR "回退安装仅覆盖 amd64/arm64（本机 $(uname -m)）"; return 1; fi
  if [ -r /etc/os-release ]; then
    # shellcheck disable=SC1091
    codename=$(. /etc/os-release 2>/dev/null; echo "${VERSION_CODENAME:-}")
  fi
  [ -n "$codename" ] || codename=jammy
  {
    export DEBIAN_FRONTEND=noninteractive
    $s apt-get update -qq
    $s apt-get install -y -qq ca-certificates curl gnupg
    $s install -m 0755 -d /etc/apt/keyrings
  } >>"$PHX_LOG_FILE" 2>&1
  for m in \
    https://mirrors.aliyun.com/docker-ce/linux/ubuntu \
    https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu \
    https://mirrors.ustc.edu.cn/docker-ce/linux/ubuntu ; do
    phx_log INFO "回退安装: docker-ce@${m#https://} ($codename/$arch)"
    {
      curl -fsSL -m 60 "$m/gpg" | $s gpg --dearmor -o /etc/apt/keyrings/docker.gpg
      $s chmod a+r /etc/apt/keyrings/docker.gpg
      echo "deb [arch=$arch signed-by=/etc/apt/keyrings/docker.gpg] $m $codename stable" | $s tee /etc/apt/sources.list.d/docker.list >/dev/null
      $s apt-get update -qq
      $s apt-get install -y -qq docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
    } >>"$PHX_LOG_FILE" 2>&1 && ok=1 && break
    phx_log WARN "  源不可用（索引未同步或被拦？）——换下一个国内源: $m"
  done
  [ "$ok" -eq 1 ] || { phx_log ERROR "三个国内源全部失败（明细见 ${PHX_LOG_FILE}）"; return 1; }
  return 0
}

# ---------- 引擎开机自启：设好 + 核实 + 讲清重启语义（BUG-178） ----------
# 背景：原先 bootstrap.sh A/4 与 install.sh 步骤2 各写一行
#   `systemctl enable --now docker || service docker start || true`
# ——失败被 `|| true` 吞掉、成功也不留痕，而 README 宣称"compose 已配 restart: unless-stopped，
# 机器重启后栈通常自动复活"。那个承诺的前提（docker 是否 enabled）其实从未被核实，用户也无从判断。
# 本函数：① 设开机自启并**核实**（systemctl is-enabled）② 把重启/休眠语义明确讲给用户
# ③ 抽成共享函数，免得两处同构脚本再各自漂移（BL-24）。
# 返回 0=已确认开机自启；1=未能确认（只告警、不中断安装——rootless/无 systemd 场景仍可手工起）。
phx_engine_autostart() { # phx_engine_autostart [SUDO]
  local s="${1:-}"
  if command -v systemctl >/dev/null 2>&1; then
    # shellcheck disable=SC2086
    $s systemctl enable --now docker >>"$PHX_LOG_FILE" 2>&1 || true
    if [ "$($s systemctl is-enabled docker 2>/dev/null)" = "enabled" ]; then
      phx_log INFO "docker 已确认开机自启（systemctl is-enabled=enabled）"
      phx_log INFO "  重启语义: 机器重启 → docker 自启 + compose restart:unless-stopped ⇒ 栈自动复活，无需人工干预"
      phx_log INFO "  休眠语义: 宿主休眠/挂起只是冻结进程（不是销毁），唤醒后继续跑，不会重建容器"
      return 0
    fi
    phx_log WARN "docker 未能确认开机自启（systemctl is-enabled 非 enabled）——机器重启后需手工起引擎"
    phx_log WARN "  重启语义: 栈不会自动复活；请先手工执行 systemctl enable --now docker"
    return 1
  fi
  # shellcheck disable=SC2086
  $s service docker start >>"$PHX_LOG_FILE" 2>&1 || true
  phx_log WARN "本机无 systemd（或无 systemctl）——引擎不会随开机自启"
  phx_log WARN "  重启语义: 机器重启后需手工起引擎；休眠/挂起仅冻结进程，唤醒后继续，不重建容器"
  phx_log WARN "  WSL 场景另有坑: WSL 会回收空闲发行版（dockerd 与容器随之停），需保活会话或手工唤醒"
  return 1
}

# =====================================================================
# 交付链加固通用函数（v2.1.0 release-delivery-hardening · T-01）
# 纯增量：不改动上方任何既有函数签名/输出格式（共享面身份矩阵：4 个调用方零回归）
# 兼容契约同文件头：bash 3.2 子集 + POSIX sh（build.sh/verify.sh 为 #!/bin/sh）
# =====================================================================

phx_runid() { # phx_runid [前缀] → stdout=<前缀>-<时间戳>-<pid>
  printf '%s-%s-%s' "${1:-run}" "$(date +%Y%m%d-%H%M%S)" "$$"
}

phx_bytes() { # phx_bytes <文件> → stdout=字节数（L-73 对账用）
  wc -c < "$1" | tr -d ' '
}

phx_sha256() { # phx_sha256 <文件> → stdout=sha256（三源回退，兼容 mac/Linux）
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  elif command -v openssl >/dev/null 2>&1; then
    openssl dgst -sha256 "$1" | awk '{print $NF}'
  else
    return 1
  fi
}

phx_assert_file_size() { # phx_assert_file_size <文件> <期望字节> → 0=一致 1=不一致（L-73）
  local f="$1" want="$2" got
  if [ ! -f "$f" ]; then
    phx_log ERROR "字节对账失败 文件=${f} 原因=文件不存在"
    return 1
  fi
  got="$(phx_bytes "$f")"
  if [ "$got" = "$want" ]; then
    phx_log INFO "字节对账 OK 文件=${f} 字节=${got}"
    return 0
  fi
  phx_log ERROR "字节对账失败 文件=${f} 期望字节=${want} 实际字节=${got} 提示=源可能被截断(L-73)"
  return 1
}

phx_image_info() { # phx_image_info <镜像> → stdout=Id/Size/Arch/Os（L-73 与 R-01 差异记录用）
  docker image inspect --format 'Id={{.Id}} Size={{.Size}} Arch={{.Architecture}} Os={{.Os}}' "$1" 2>/dev/null
}

phx_ostype_is_linux() { # 引擎类型三查之一：拒绝 Windows 容器引擎（L-12）
  local t
  t="$(docker info --format '{{.OSType}}' 2>/dev/null || echo unknown)"
  if [ "$t" = "linux" ]; then
    phx_log INFO "引擎类型 OK OSType=${t}"
    return 0
  fi
  phx_log ERROR "引擎类型不符 OSType=${t} 期望=linux；Windows 容器引擎无法运行 Linux 镜像 → 请在 Linux 引擎/上下文重跑"
  return 1
}

phx_compose_v2_ok() { # 只认 Compose v2；v1 明确拒绝并给指引（R-03 / L-100）
  if docker compose version >/dev/null 2>&1; then
    phx_log INFO "Compose 能力 OK 形态=docker compose(v2) 版本=$(docker compose version --short 2>/dev/null || echo '-')"
    return 0
  fi
  local p found=""
  for p in /usr/libexec/docker/cli-plugins/docker-compose \
           /usr/lib/docker/cli-plugins/docker-compose \
           /usr/local/lib/docker/cli-plugins/docker-compose \
           "$HOME/.docker/cli-plugins/docker-compose"; do
    [ -x "$p" ] && found="${found} ${p}"
  done
  phx_log ERROR "Compose 能力不足 需要=Compose v2(docker compose) 检出插件=${found:-无}"
  phx_log ERROR "  处置: 安装 docker-compose-plugin（系统级路径 /usr/libexec/docker/cli-plugins/）；并用普通用户+docker 组执行，勿 root/普通用户混跑(L-100)"
  return 1
}

phx_env_checkup() { # 门禁前环境体检三件套：磁盘/运行中容器/镜像占用（L-04，非致命）
  local dfree cps
  dfree="$(phx_disk_free_mb "$HOME" 2>/dev/null || echo '?')"
  cps="$(docker ps -q 2>/dev/null | wc -l | tr -d ' ')"
  phx_log INFO "环境体检 磁盘可用MB=${dfree} 运行中容器=${cps}"
  docker system df 2>/dev/null | while IFS= read -r l; do phx_log INFO "  占用 ${l}"; done
  return 0
}

phx_gate_begin() { # phx_gate_begin <证据目录> <版本> <架构> → 初始化 runId/证据文件（T-07 用）
  PHX_GATE_RUNID="$(phx_runid gate)"
  PHX_GATE_BEGIN_TS="$(date +%s)"
  PHX_GATE_PASS=0
  PHX_GATE_FAIL=0
  mkdir -p "$1" || return 1
  PHX_GATE_EVIDENCE="$1/gate-${2}-${3}-${PHX_GATE_RUNID}.md"
  {
    echo "# 出货门禁证据"
    echo
    echo "- runId: ${PHX_GATE_RUNID}"
    echo "- 版本: ${2}"
    echo "- 架构: ${3}"
    echo "- 开始: $(date '+%F %T')"
    echo
  } >> "$PHX_GATE_EVIDENCE" 2>/dev/null || return 1
  phx_log INFO "门禁开始 runId=${PHX_GATE_RUNID} 版本=${2} 架构=${3} 证据=${PHX_GATE_EVIDENCE}"
}

phx_gate_item() { # phx_gate_item <项名> <命令...> → 0=PASS 非0=FAIL；证据落 $PHX_GATE_EVIDENCE
  local name="$1"; shift
  local out rc
  out="$("$@" 2>&1)"; rc=$?
  if [ "$rc" -eq 0 ]; then
    PHX_GATE_PASS=$(( ${PHX_GATE_PASS:-0} + 1 ))
    phx_log INFO "门禁 PASS item=${name} runId=${PHX_GATE_RUNID:-'-'}"
  else
    PHX_GATE_FAIL=$(( ${PHX_GATE_FAIL:-0} + 1 ))
    phx_log ERROR "门禁 FAIL item=${name} rc=${rc} runId=${PHX_GATE_RUNID:-'-'} 复现=$*"
  fi
  if [ -n "${PHX_GATE_EVIDENCE:-}" ]; then
    {
      echo "## ${name} — $([ "$rc" -eq 0 ] && echo PASS || echo FAIL) (rc=${rc})"
      echo
      echo '```'
      echo "\$ $*"
      echo "${out}"
      echo '```'
      echo
    } >> "$PHX_GATE_EVIDENCE" 2>/dev/null || true
  fi
  return "$rc"
}

phx_gate_end() { # phx_gate_end → 0=全绿 1=有失败；日志含 runId/通过数/失败数/耗时/结果
  local dur
  dur=$(( $(date +%s) - ${PHX_GATE_BEGIN_TS:-$(date +%s)} ))
  if [ "${PHX_GATE_FAIL:-0}" -eq 0 ]; then
    phx_log INFO "门禁结束 runId=${PHX_GATE_RUNID:-'-'} 结果=PASS 通过=${PHX_GATE_PASS:-0} 失败=0 耗时=${dur}s"
    return 0
  fi
  phx_log ERROR "门禁结束 runId=${PHX_GATE_RUNID:-'-'} 结果=FAIL 通过=${PHX_GATE_PASS:-0} 失败=${PHX_GATE_FAIL} 耗时=${dur}s"
  return 1
}

phx_engine_tgz_stage() { # phx_engine_tgz_stage <本地路径|URL> <目标目录> <arch> [期望字节] → stdout=落盘路径
  # R-02（v2.1.0）：通用静态二进制入包；含 来源记录 / 字节对账(L-73) / tar 完整性校验 / sha256
  local src="$1" dest="$2" arch="$3" want="${4:-}" f base ver got sha out
  mkdir -p "$dest" || return 1
  if [ -f "$src" ]; then
    f="$src"
    phx_log INFO "离线引擎件 来源=local ref=${src}"
  else
    case "$src" in
      http://*|https://*) : ;;
      *) phx_log ERROR "离线引擎件来源非法 需=本地文件或http(s)URL src=${src}"; return 1;;
    esac
    f="$(mktemp "${TMPDIR:-/tmp}/phx-engine.XXXXXX")" || return 1
    phx_log INFO "离线引擎件 来源=url ref=${src}（L-71：外部来源逐条核实）"
    curl -fsSL --retry 3 --retry-delay 2 -m 3600 -o "$f" "$src" >>"$PHX_LOG_FILE" 2>&1 \
      || { phx_log ERROR "离线引擎件下载失败 url=${src}"; return 1; }
  fi
  got="$(phx_bytes "$f")"
  phx_log INFO "离线引擎件 字节数=${got} 临时文件=${f}"
  if [ -n "$want" ] && [ "$got" != "$want" ]; then
    phx_log ERROR "离线引擎件字节对账失败 期望=${want} 实际=${got}（源可能被截断，L-73）"; return 1
  fi
  tar -tzf "$f" >/dev/null 2>&1 \
    || { phx_log ERROR "离线引擎件完整性校验失败 原因=tar无法解析(疑似截断/损坏) 文件=${f}"; return 1; }
  base="$(basename "$src")"
  ver="$(printf '%s' "$base" | sed -n 's/^docker-\([0-9][0-9.]*\).*\.tgz$/\1/p')"
  [ -n "$ver" ] || ver=unknown
  out="$dest/docker-${ver}-${arch}.tgz"
  cp "$f" "$out" || return 1
  sha="$(phx_sha256 "$out")" || return 1
  {
    echo "# Phoenix 离线引擎件信息（package.sh 生成）"
    echo "source=$([ -f "$src" ] && echo local || echo url)"
    echo "source_ref=${src}"
    echo "arch=${arch}"
    echo "version=${ver}"
    echo "bytes=$(phx_bytes "$out")"
    echo "sha256=${sha}"
  } > "$dest/ENGINE-INFO.txt" || return 1
  phx_log INFO "离线引擎件已入包 path=${out} arch=${arch} version=${ver} sha256=${sha}"
  printf '%s' "$out"
}

phx_engine_static_install() { # phx_engine_static_install <tgz> [SUDO] → 0=二进制就位且守护进程可达（R-02）
  local tgz="$1" S="${2:-}" tmp b n i
  tar -tzf "$tgz" >/dev/null 2>&1 || { phx_log ERROR "离线引擎件完整性校验失败 文件=${tgz}"; return 1; }
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/phx-engine-x.XXXXXX")" || return 1
  tar -xzf "$tgz" -C "$tmp" || { phx_log ERROR "离线引擎件解包失败 文件=${tgz}"; return 1; }
  [ -d "$tmp/docker" ] || { phx_log ERROR "离线引擎件结构异常 缺少=docker/ 文件=${tgz}"; return 1; }
  n=0
  for b in docker dockerd docker-init docker-proxy containerd containerd-shim-runc-v2 ctr runc; do
    [ -f "$tmp/docker/$b" ] || continue
    # shellcheck disable=SC2086
    $S install -m 0755 "$tmp/docker/$b" "/usr/local/bin/$b" || { phx_log ERROR "二进制落地失败 bin=${b}"; return 1; }
    n=$((n+1))
  done
  phx_log INFO "离线引擎二进制已落地 数量=${n} 目录=/usr/local/bin 来源=${tgz}"
  if command -v systemctl >/dev/null 2>&1 && [ -d /run/systemd/system ]; then
    $S tee /etc/systemd/system/docker.service >/dev/null <<'UNIT'
[Unit]
Description=Docker Engine (static binary, installed by Phoenix)
After=network-online.target
[Service]
ExecStart=/usr/local/bin/dockerd
Restart=always
LimitNOFILE=1048576
[Install]
WantedBy=multi-user.target
UNIT
    $S systemctl daemon-reload >>"$PHX_LOG_FILE" 2>&1 || true
    # shellcheck disable=SC2086
    $S systemctl enable --now docker >>"$PHX_LOG_FILE" 2>&1 || phx_log WARN "systemd 启动 docker 失败（见日志）"
    phx_log INFO "已配置开机自启 方式=systemd unit=/etc/systemd/system/docker.service"
  else
    phx_log WARN "无 systemd（或无 /run/systemd/system）——降级为手工启动：nohup /usr/local/bin/dockerd &（机器重启后需手工起）"
    ( nohup /usr/local/bin/dockerd >>/var/log/dockerd.log 2>&1 & ) 2>/dev/null || phx_log WARN "dockerd 启动尝试失败（见 /var/log/dockerd.log）"
  fi
  i=0
  while [ "$i" -lt 30 ]; do
    if docker info >/dev/null 2>&1; then phx_log INFO "引擎可达 轮询=${i}次"; return 0; fi
    i=$((i+1)); sleep 2
  done
  phx_log ERROR "引擎装后仍不可达 说明=本机可能不支持容器内起 dockerd（见 /var/log/dockerd.log）"
  return 1
}
