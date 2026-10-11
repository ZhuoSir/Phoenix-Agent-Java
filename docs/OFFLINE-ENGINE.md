# 离线安装 Docker 引擎（交付包内自带，R-02）

## 一、优先路径：通用静态二进制（推荐）

打包侧：
```bash
bash docker/scripts/package.sh --version <ver> --arch <arm64|amd64> \
  --engine-tgz https://mirrors.aliyun.com/docker-ce/linux/static/stable/<x86_64|aarch64>/docker-<ver>.tgz \
  [--engine-bytes <期望字节>]
```
- 入包路径：`engine/docker-<引擎版本>-<架构>.tgz` + `engine/ENGINE-INFO.txt`（来源/架构/版本/字节/sha256）
- 完整性：入包前做**字节对账**（`--engine-bytes`）与 **tar 完整性校验**；截断/损坏**拒绝入包**（L-73）
- 该文件**在 SHA256SUMS 之前**入包 ⇒ 安装侧会连带校验它

安装侧（目标机无 Docker、无外网）：
```bash
bash install.sh            # 步骤 2 自动发现 engine/docker-*-<本机架构>.tgz 并落地
```
- 落地内容：`docker/dockerd/containerd/...` → `/usr/local/bin`（0755）
- 自启：有 systemd（`/run/systemd/system`）⇒ 写 `/etc/systemd/system/docker.service` 并 `enable --now`；
  **无 systemd ⇒ 显式降级**为 `nohup dockerd`，并在日志中告知"重启后需手工起"
- 包内引擎件与包架构不匹配 ⇒ **明确报错停止**（不会尝试安装异构二进制）

## 二、备选路径：发行版 deb 组（`--with-engine-debs`）

```bash
bash docker/scripts/package.sh --version <ver> --arch <arch> --with-engine-debs
```
- 位置：`engine/*.deb`；安装侧在 `--offline` 且存在该组时用 `dpkg -i` 落地
- ⚠️ 已知缺陷 **BUG-196**：该 deb 组落在 `SHA256SUMS` 生成**之后**，未被完整性校验覆盖（静态二进制路径已避免该问题）

## 三、两条路径都不满足时

`install.sh` 会明确报错并提示手工安装（在线路径走 `get.docker.com`，受限网络下回退 `mirrors.aliyun.com` 的 docker-ce 仓库）。
装好后重跑 `install.sh` 即可（引擎已可用 ⇒ 步骤 2 跳过，幂等）。
