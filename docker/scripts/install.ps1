<#
Phoenix 一键安装（Windows / WSL2 路线，docker-auto-pipeline T-05）
用法: 管理员 PowerShell 中执行  .\install.ps1 [-Timeout 300] [-Project phoenix] [-Offline]
流程: 管理员检查 → Windows 原生引擎拒绝(L-12) → WSL2 探测/功能启用(重启断点续跑)
      → 包拷入 WSL → Ubuntu 内 install.sh 九步(引擎装 WSL 内) → Windows 侧收据
退出码: 0=成功  1=失败  2=需重启后重跑(功能刚启用)
#>
param(
  [int]$Timeout = 300,
  [string]$Project = "phoenix",
  [switch]$Offline
)
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$StateFile = Join-Path $ScriptDir ".phoenix-install-ps.state"

function Write-Step($msg) { Write-Host "[install.ps1] $msg" -ForegroundColor Cyan }
function Write-Fail($msg) {
  Write-Host "[install.ps1] 失败: $msg" -ForegroundColor Red
  Write-Host "状态文件: $StateFile（修复后重跑自动续接）"
  exit 1
}
function Mark-Done($k) { Add-Content -Path $StateFile -Value $k }
function Test-Done($k) { return (Test-Path $StateFile) -and (@(Get-Content $StateFile) -contains $k) }

# ── 1. 管理员 ──
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { Write-Fail "请以管理员身份运行（右键 PowerShell → 以管理员身份运行）" }

# ── 2. Windows 原生引擎 = 直接拒绝（跑不了 Linux 镜像，L-12 现场教训） ──
if (Get-Command docker -ErrorAction SilentlyContinue) {
  $ost = & docker info --format '{{.OSType}}' 2>$null
  if ($ost -eq "windows") {
    Write-Fail "检测到 Windows 原生容器引擎(OSType=windows)——Phoenix 是 Linux 镜像栈，跑不了。请停用 Windows 容器模式（Docker Desktop: Switch to Linux containers），本脚本走 WSL2 路线"
  }
  if ($ost -eq "linux") {
    Write-Step "检测到 Docker Desktop(linux 引擎)——无需本脚本，直接在 WSL/终端执行: bash <包目录>/install.sh"
    exit 0
  }
}

# ── 3. WSL2 + Ubuntu-22.04（功能启用后需重启，状态文件断点续跑） ──
if (-not (Get-Command wsl -ErrorAction SilentlyContinue)) {
  Write-Fail "无 wsl 命令——先执行: dism 启用 WSL 功能（本脚本会自动做，若连 dism 都失败请检查系统版本 ≥ Win10 2004 / Server 2022）"
}
$distros = (& wsl -l -q 2>$null) -join "`n"
if ($distros -notmatch "Ubuntu-22\.04") {
  if (-not (Test-Done "features")) {
    Write-Step "启用 WSL 与虚拟机平台功能（dism）..."
    & dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart | Out-Null
    & dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart | Out-Null
    Mark-Done "features"
    Write-Host ""
    Write-Host "═══ 请现在【重启电脑】，然后以管理员身份重跑本脚本（自动续接） ═══" -ForegroundColor Yellow
    exit 2
  }
  Write-Step "安装 Ubuntu-22.04（下载数百 MB，耐心等）..."
  & wsl --install -d Ubuntu-22.04 --no-launch
  if ($LASTEXITCODE -ne 0) {
    Write-Fail "wsl --install 失败(码 $LASTEXITCODE)。若报虚拟化不可用：本机是虚拟机的话，需宿主机开启【嵌套虚拟化】后重试"
  }
  Mark-Done "distro"
}

# ── 4. WSL 可用性（root 拉起；嵌套虚拟化不可用会在这里现形） ──
& wsl -d Ubuntu-22.04 -u root -- true 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Fail "Ubuntu-22.04 无法以 root 启动——刚装完可能需重启；仍失败则检查虚拟化/嵌套虚拟化"
}

# ── 5. 包拷入 WSL ext4（/mnt 下跑安装慢 10 倍——性能坑位制度化） ──
$WinPath = ($ScriptDir -replace '\\','/')
$Drv = $WinPath.Substring(0,1).ToLower()
$Rest = $WinPath.Substring(2)
$WslSrc = "/mnt/$Drv$Rest"
$PkgName = Split-Path -Leaf $ScriptDir
Write-Step "拷贝安装包进 WSL: $WslSrc → /root/$PkgName（数 GB，首次较慢）"
& wsl -d Ubuntu-22.04 -u root -- bash -c "mkdir -p /root/$PkgName && cp -ru '$WslSrc/.' /root/$PkgName/"
if ($LASTEXITCODE -ne 0) { Write-Fail "拷包失败（路径换算: $WslSrc 是否存在？）" }

# ── 6. WSL 内走 install.sh 九步（引擎装 Ubuntu 内） ──
$offlineFlag = ""
if ($Offline) { $offlineFlag = "--offline" }
Write-Step "WSL 内启动九步安装（Timeout=$Timeout Project=$Project Offline=$Offline）..."
& wsl -d Ubuntu-22.04 -u root -- bash "/root/$PkgName/install.sh" --timeout $Timeout --project $Project $offlineFlag --from-wsl
if ($LASTEXITCODE -ne 0) { Write-Fail "WSL 内 install.sh 退出码 $LASTEXITCODE——见上方输出与 WSL 内 ~/.phoenix/ 日志" }

# ── 7. Windows 侧收据 ──
$port = & wsl -d Ubuntu-22.04 -u root -- bash -c "grep '^PHOENIX_HTTP_PORT=' /root/$PkgName/docker/.env 2>/dev/null | head -1 | cut -d= -f2 | awk '{print `$1}'"
if (-not $port) { $port = 9080 }
Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host "  Phoenix 安装成功（Windows / WSL2 路线）" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Green
Write-Host "  访问地址 : http://localhost:$port   （Windows 浏览器直接打开）"
Write-Host "  管理账号 : admin / 123456（首登立即改密）"
Write-Host "  服务管理 : wsl -d Ubuntu-22.04 -u root -- docker compose -p $Project ps"
Write-Host "  若 localhost 不通(老版 Win10): 管理员执行 netsh interface portproxy add v4tov4"
Write-Host "    listenport=$port listenaddress=0.0.0.0 connectport=$port connectaddress=<WSL内 hostname -I>"
Write-Host "======================================================" -ForegroundColor Green
exit 0
