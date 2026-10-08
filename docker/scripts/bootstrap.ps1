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
  # 走 cmd /c 取输出：PS 5.1 在 $ErrorActionPreference=Stop 下会把原生命令的 stderr 升级成
  # 终止错误，`2>$null` 拦不住（BUG-141）——引擎没起时那样会让脚本红堆栈死在这一行。
  $ost = & cmd /c 'docker info --format {{.OSType}} 2>nul'
  if ($ost -eq "windows") { Write-Fail "检测到 Windows 原生容器引擎(OSType=windows)——本脚本走 WSL2 路线，请先停用 Windows 容器模式或忽略本机既有引擎（不影响 WSL 内独立引擎）" }
}

# ── 3. WSL2 + 发行版（与 install.ps1 同构；-Distro 兼容 wsl --import 自定义名） ──
if (-not (Get-Command wsl -ErrorAction SilentlyContinue)) { Write-Fail "无 wsl 命令——系统版本过老或功能未启用（先跑 dism 两功能+重启）" }
# 发行版就绪判定 = **直接探测**，不解析 `wsl -l -q`：其输出在 PowerShell 5.1 里字间夹 NUL
# （实测 "U\0b\0u\0n\0t\0u\0"），-match 恒不命中 ⇒ 已装好被误判为未装 ⇒ 重装报
# ERROR_ALREADY_EXISTS ⇒ **第二次重跑必失败**（BUG-144）。探测全程走 cmd /c，避免
# native stderr 在 EAP=Stop 下抛错（BUG-141）。
function Test-Distro($name) {
  & cmd /c "wsl -d $name -u root -- true 1>nul 2>nul"
  return ($LASTEXITCODE -eq 0)
}
if (Test-Distro $Distro) {
  Write-Step "$Distro 已就绪，跳过发行版安装"
} else {
  if (-not (Test-Done "features")) {
    Write-Step "启用 WSL 与虚拟机平台功能（dism）..."
    & dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart | Out-Null
    & dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart | Out-Null
    Mark-Done "features"
    Write-Host "`n═══ 请现在【重启电脑】，然后以管理员身份重跑本脚本（自动续接） ═══" -ForegroundColor Yellow
    exit 2
  }
  Write-Step "安装 $Distro（若 Server 无商店报「无效的分发名称」，请按 README 的 rootfs 导入法建同名发行版后重跑）..."
  $installOut = & cmd /c "wsl --install -d $Distro --no-launch 2>&1"
  $installOut | ForEach-Object { Write-Host "  $_" }
  if (-not (Test-Distro $Distro)) {
    Write-Fail "$Distro 不可用——Server 无商店请改用导入法：aka.ms/wslubuntu2204 下载后 wsl --import $Distro C:\WSL\Ubuntu <rootfs.tar.gz> 后重跑；刚装完也可能需重启；仍失败查虚拟化/嵌套虚拟化"
  }
  Mark-Done "distro"
}

# ── 4. 源码拷入 WSL ext4（/mnt 下构建慢 10 倍） ──
$WinPath = ($SrcRoot -replace '\\','/')
$Drv = $WinPath.Substring(0,1).ToLower(); $Rest = $WinPath.Substring(2)
$WslSrc = "/mnt/$Drv$Rest"
Write-Step "拷贝源码进 WSL: $WslSrc → ~/phoenix-src"
& wsl -d $Distro -u root -- bash -c "mkdir -p ~/phoenix-src && cp -ru '$WslSrc/.' ~/phoenix-src/"
if ($LASTEXITCODE -ne 0) { Write-Fail "源码拷贝失败（路径换算: $WslSrc）" }

# ── 4.5 行尾规整 CRLF→LF（BUG-143）──
# Windows 工作树在 core.autocrlf=true（Git for Windows 默认）下检出为 CRLF，而仓库对象
# 里存的是 LF。直接把这棵树交给 bash，bash 会崩在 \r 上——bootstrap.sh 自己第 13 行
# `case "$1" in` 就报 syntax error，跑不到任何自愈代码，所以必须在**外部**先规整。
# 只规整被 bash/Docker/Compose 消费的文本资产；Java/TS/SQL 的 CRLF 无害，不动。
Write-Step "规整行尾 CRLF→LF（bash/Docker/Compose 资产）..."
& wsl -d $Distro -u root -- bash -c "cd ~/phoenix-src && find . -type f \( -name '*.sh' -o -name 'Dockerfile*' -o -name '.env*' -o -name '*.conf' -o -name '*.yaml' -o -name '*.yml' \) -exec sed -i 's/\r//g' {} +"
if ($LASTEXITCODE -ne 0) { Write-Fail "行尾规整失败（WSL 内 find/sed 不可用？）" }

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
