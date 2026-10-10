<#
Phoenix 服务控制 · Windows 侧（转发进 WSL）
用法: .\phoenix-ctl.ps1 start|stop|restart|status|logs|verify [服务名] [-Distro Ubuntu-22.04]
#>
param(
  # ⚠️ Position = 0 不可省（BUG-187）：带 ValueFromRemainingArguments 的参数在隐式位置排序里被排到
  # **最后一位** ⇒ 第一个位置参数会绑到 $Distro 上。实测 `phoenix-ctl.ps1 stop` 会执行 `wsl -d stop`，
  # 报 WSL_E_DISTRO_NOT_FOUND；而该报错走 stdout，"空输出"守卫也会被绕过（报错文本被当成安装目录）。
  [Parameter(Position = 0, ValueFromRemainingArguments = $true)][string[]]$Args,
  [string]$Distro = "Ubuntu-22.04"
)
$cmd = ($Args -join ' ')
if (-not $cmd) { $cmd = "status" }
$env:WSL_UTF8 = 1
# 预检 1：发行版是否存在。WSL 的报错文本含中文/空格，用"名字白名单"过滤，避免把报错当成发行版列表
$distros = @(& wsl -l -q 2>$null | ForEach-Object { "$_".Trim() } | Where-Object { $_ -match '^[A-Za-z0-9._\-]+$' })
if ($distros.Count -gt 0 -and $distros -notcontains $Distro) {
  Write-Host "WSL 内不存在发行版 '$Distro'；当前可用: $($distros -join ' / ')" -ForegroundColor Yellow
  Write-Host "指定发行版示例: .\phoenix-ctl.ps1 $cmd -Distro $($distros[0])" -ForegroundColor Yellow
  exit 2
}
# 预检 2：定位 WSL 内安装目录（install/bootstrap 默认 ~/phoenix-install/phoenix-*/docker），
# 并校验返回值**长得像路径**（否则说明 wsl 调用失败，报错文本会伪装成"探测结果"）
$probe = (& wsl -d $Distro -u root -- bash -c "ls -dt ~/phoenix-install/phoenix-*/docker 2>/dev/null | head -1" | Out-String).Trim()
if ($probe -notmatch '/docker$') {
  if ($probe) { Write-Host "WSL 调用未返回安装目录，原始输出: $probe" -ForegroundColor Yellow }
  Write-Host "WSL($Distro) 内未找到安装目录（~/phoenix-install/phoenix-*/docker）；源码自建请用: wsl -d $Distro -u root -- bash <源码>/docker/scripts/phoenix-ctl.sh $cmd" -ForegroundColor Yellow
  exit 1
}
$ctl = ($probe -replace '/docker$', '/docker/scripts/phoenix-ctl.sh')
& wsl -d $Distro -u root -- bash $ctl $cmd.Split(' ')
exit $LASTEXITCODE
