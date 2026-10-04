<#
Phoenix 服务控制 · Windows 侧（转发进 WSL）
用法: .\phoenix-ctl.ps1 start|stop|restart|status|logs|verify [服务名] [-Distro Ubuntu-22.04]
#>
param(
  [Parameter(ValueFromRemainingArguments = $true)][string[]]$Args,
  [string]$Distro = "Ubuntu-22.04"
)
$cmd = ($Args -join ' ')
if (-not $cmd) { $cmd = "status" }
# 定位 WSL 内安装目录（install/bootstrap 默认 ~/phoenix-install/phoenix-*/docker）
$probe = & wsl -d $Distro -u root -- bash -c "ls -dt ~/phoenix-install/phoenix-*/docker 2>/dev/null | head -1"
if (-not $probe) { Write-Host "WSL 内未找到安装目录（~/phoenix-install/phoenix-*/docker）；源码自建请用: wsl -d $Distro -u root -- bash <源码>/docker/scripts/phoenix-ctl.sh $cmd" -ForegroundColor Yellow; exit 1 }
$ctl = ($probe.Trim() -replace '/docker$', '/docker/scripts/phoenix-ctl.sh')
& wsl -d $Distro -u root -- bash $ctl $cmd.Split(' ')
exit $LASTEXITCODE
