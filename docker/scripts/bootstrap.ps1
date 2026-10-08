<#
Phoenix 一键 bootstrap · Windows 版（R-10/T-11）
用法: 管理员 PowerShell，在源码目录执行  .\docker\scripts\bootstrap.ps1 [-Version v] [-Project phoenix] [-Port 0] [-Distro Ubuntu-22.04] [-Offline]
流程: 管理员检查 → Windows 原生引擎拒绝(L-12) → WSL2 就绪(功能启用/重启续接/-Distro 兼容 --import 自定义名)
      → 源码拷入 WSL → bootstrap.sh(引擎/打包/安装/收据) → Windows 侧收据
退出码: 0=成功  1=失败  2=需重启后重跑
（WSL 就绪段与 install.ps1 同构——共享 ps1 库列 backlog，两处改动需同步，见 plan §1.6）
#>
param(
  [string]$Version = "",
  [string]$Project = "phoenix",
  [int]$Port = 0,
  [int]$Timeout = 300,
  [string]$Distro = "Ubuntu-22.04",
  [switch]$Offline
)
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$SrcRoot   = (Resolve-Path (Join-Path $ScriptDir "..\..")).Path
$StateFile = Join-Path $SrcRoot ".phoenix-bootstrap-ps.state"

function Write-Step($msg) { Write-Host "[bootstrap.ps1] $msg" -ForegroundColor Cyan }
function Write-Fail($msg) {
  Write-Host "[bootstrap.ps1] 失败: $msg" -ForegroundColor Red
  Write-Host "状态文件: $StateFile（修复后重跑自动续接）"
  exit 1
}
function Mark-Done($k) { Add-Content -Path $StateFile -Value $k }
function Test-Done($k) { return (Test-Path $StateFile) -and (@(Get-Content $StateFile) -contains $k) }

# ── 1. 管理员 ──
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $isAdmin) { Write-Fail "请以管理员身份运行 PowerShell" }

# ── 2. Windows 原生引擎 = 拒绝（跑不了 Linux 镜像） ──
if (Get-Command docker -ErrorAction SilentlyContinue) {
  $ost = & docker info --format '{{.OSType}}' 2>$null
  if ($ost -eq "windows") { Write-Fail "检测到 Windows 原生容器引擎(OSType=windows)——本脚本走 WSL2 路线，请先停用 Windows 容器模式或忽略本机既有引擎（不影响 WSL 内独立引擎）" }
}

# ── 3. WSL2 + 发行版（与 install.ps1 同构；-Distro 兼容 wsl --import 自定义名） ──
if (-not (Get-Command wsl -ErrorAction SilentlyContinue)) { Write-Fail "无 wsl 命令——系统版本过老或功能未启用（先跑 dism 两功能+重启）" }
$distros = (& wsl -l -q 2>$null) -join "`n"
if ($distros -notmatch [regex]::Escape($Distro)) {
  if (-not (Test-Done "features")) {
    Write-Step "启用 WSL 与虚拟机平台功能（dism）..."
    & dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart | Out-Null
    & dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart | Out-Null
    Mark-Done "features"
    Write-Host "`n═══ 请现在【重启电脑】，然后以管理员身份重跑本脚本（自动续接） ═══" -ForegroundColor Yellow
    exit 2
  }
  Write-Step "安装 $Distro（若 Server 无商店报「无效的分发名称」，请按 README 的 rootfs 导入法建同名发行版后重跑）..."
  & wsl --install -d $Distro --no-launch
  if ($LASTEXITCODE -ne 0) { Write-Fail "wsl --install 失败(码 $LASTEXITCODE)——Server 环境请改用导入法：aka.ms/wslubuntu2204 下载后 wsl --import $Distro C:\WSL\Ubuntu <rootfs.tar.gz>" }
  Mark-Done "distro"
}
& wsl -d $Distro -u root -- true 2>$null
if ($LASTEXITCODE -ne 0) { Write-Fail "$Distro 无法以 root 启动——刚装完可能需重启；仍失败查虚拟化/嵌套虚拟化" }

# ── 4. 源码拷入 WSL ext4（/mnt 下构建慢 10 倍） ──
$WinPath = ($SrcRoot -replace '\\','/')
$Drv = $WinPath.Substring(0,1).ToLower(); $Rest = $WinPath.Substring(2)
$WslSrc = "/mnt/$Drv$Rest"
Write-Step "拷贝源码进 WSL: $WslSrc → ~/phoenix-src"
& wsl -d $Distro -u root -- bash -c "mkdir -p ~/phoenix-src && cp -ru '$WslSrc/.' ~/phoenix-src/"
if ($LASTEXITCODE -ne 0) { Write-Fail "源码拷贝失败（路径换算: $WslSrc）" }

# ── 5. WSL 内 bootstrap.sh 全链 ──
$bsArgs = "--project $Project --timeout $Timeout"
if ($Version) { $bsArgs += " --version $Version" }
if ($Port -gt 0) { $bsArgs += " --port $Port" }
if ($Offline) { $bsArgs += " --offline" }
Write-Step "WSL 内启动 bootstrap 全链（引擎→打包→安装，首次约 40-70 分钟）..."
& wsl -d $Distro -u root -- bash -c "cd ~/phoenix-src && bash docker/scripts/bootstrap.sh $bsArgs"
if ($LASTEXITCODE -ne 0) { Write-Fail "bootstrap.sh 退出码 $LASTEXITCODE——见上方输出与 WSL 内 ~/.phoenix/ 日志" }

# ── 6. Windows 侧收据 ──
$showPort = 9080; if ($Port -gt 0) { $showPort = $Port }
Write-Host ""
Write-Host "======================================================" -ForegroundColor Green
Write-Host "  Phoenix bootstrap 完成（Windows/WSL2 全链）" -ForegroundColor Green
Write-Host "======================================================" -ForegroundColor Green
Write-Host "  访问地址 : http://localhost:$showPort （Windows 浏览器直接开）"
Write-Host "  管理账号 : admin / 123456（首登立即改密）"
Write-Host "  离线包   : WSL 内 ~/phoenix-src/docker/dist/*.tar.gz（可拷去其它机器）"
Write-Host "  服务管理 : wsl -d $Distro -u root -- docker compose -p $Project ps"
Write-Host "  localhost 不通时: netsh interface portproxy add v4tov4 listenport=$showPort listenaddress=0.0.0.0 connectport=$showPort connectaddress=<WSL hostname -I>"
Write-Host "======================================================" -ForegroundColor Green
exit 0
