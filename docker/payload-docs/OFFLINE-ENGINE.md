# 断网环境安装 Docker 引擎指引

安装包的 `install.sh --offline` 在目标机缺引擎时：有 `engine/*.deb` 则本地安装；没有则停在本页指引。

## 路线 A：包内自带 deb（构建时加了 --with-engine-debs，仅 Ubuntu 22.04 x86_64）
```bash
bash install.sh --offline     # 自动 dpkg -i engine/*.deb，无需手工
```

## 路线 B：静态二进制（任意 x86_64/arm64 Linux，最通用）
在**有网的同架构机器**上：
```bash
# 官方静态包（国内可达性自行验证；版本 ≥20.10 即可）
curl -fsSLO https://download.docker.com/linux/static/stable/x86_64/docker-27.3.1.tgz
```
拷到目标机后：
```bash
tar xzf docker-27.3.1.tgz && sudo cp docker/* /usr/local/bin/
sudo tee /etc/systemd/system/docker.service <<'UNIT'
[Unit]
Description=Docker Engine
After=network-online.target
[Service]
ExecStart=/usr/local/bin/dockerd
Restart=always
[Install]
WantedBy=multi-user.target
UNIT
sudo systemctl daemon-reload && sudo systemctl enable --now docker
docker info >/dev/null && echo OK
```
然后回安装包重跑 `bash install.sh --offline`。

## 路线 C：发行版仓库离线镜像（有内网 apt/yum 仓的单位）
把 `mirrors.aliyun.com/docker-ce` 的对应发行版目录整体镜像进内网仓，
目标机指内网仓后按在线方式安装（get.docker.com 脚本支持 `--mirror` 参数换源）。

## 常见坑
| 症状 | 处置 |
|---|---|
| dpkg 依赖不齐 | deb 组需在同版本 Ubuntu 容器内 `apt-get download` 全套依赖（构建侧 T-06 负责）；临时可用路线 B |
| dockerd 起不来 | `journalctl -u docker -n 50`；常见为 iptables 缺失（静态二进制路线需系统有 iptables/nftables） |
| 装完 install.sh 仍说无引擎 | 重开终端（PATH/docker 组生效），或 `sudo systemctl status docker` 看状态 |
