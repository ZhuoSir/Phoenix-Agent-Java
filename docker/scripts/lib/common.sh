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
phx_mirror_pick() { # phx_mirror_pick <候选前缀...> → stdout=首个活口；全死返回 1
  local m code
  for m in "$@"; do
    [ -z "$m" ] && continue
    # curl 连接失败时 -w 自会输出 000——不可再 || echo 000（会拼接成 000000 骗过判活，selftest 抓获的真 bug）
    code=$(curl -s -m 8 -o /dev/null -w '%{http_code}' "$m/v2/" 2>>"$PHX_LOG_FILE" || true)
    if [ -n "$code" ] && [ "$code" != "000" ]; then
      phx_log INFO "mirror 选定: $m (HTTP $code)"
      echo "$m"
      return 0
    fi
    phx_log WARN "mirror 不可达: $m"
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
