# Windows Server / 无 Docker Desktop 环境安装指南（WSL2 路线）

> 适用：Windows Server 2022 等装不了 Docker Desktop 的机器，或不想装桌面软件。
> 原理：WSL2 里跑 Ubuntu + Docker Engine（Linux 容器），Phoenix 全部镜像均为 Linux 镜像，
> **Windows 原生容器引擎（OSType: windows）无法运行本产品**，务必走本路线。

## 0. 前置检查（PowerShell 管理员）
```powershell
systeminfo | findstr /i "Hyper-V"     # 虚拟机上跑需宿主开嵌套虚拟化
wsl --status                          # 看 WSL 是否可用
```

## 1. 启用 WSL2 + Ubuntu
```powershell
dism.exe /online /enable-feature /featurename:Microsoft-Windows-Subsystem-Linux /all /norestart
dism.exe /online /enable-feature /featurename:VirtualMachinePlatform /all /norestart
# 重启服务器
wsl --install -d Ubuntu-22.04
# 按提示设置 Linux 用户名密码；若 wsl --install 不可用（老补丁级），改用:
#   dism 两行 + 重启 + 安装 WSL2 内核更新包 + wsl --set-default-version 2 + 商店装 Ubuntu 22.04
```

## 2. Ubuntu 内装 Docker + 国内镜像源
```bash
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://docker.1ms.run", "https://docker.m.daocloud.io", "https://dockerproxy.net"]
}
