# docker/scripts 使用指南

> 一句话选型：**目标机从源码到跑起来 = `bootstrap`（一条命令）；构建机只出包 = `package.sh`；拿到包只安装 = `install.sh` / `install.ps1`。**

## 脚本地图
| 脚本 | 干什么 | 在哪跑 |
|---|---|---|
| **bootstrap.sh** | 一键全链：引擎→打包→安装→收据卡 | Linux / WSL / mac（有 Docker 或可自动装引擎） |
| **bootstrap.ps1** | Windows 一键：WSL2 就绪→进 WSL 调 bootstrap.sh | Windows 10/11 / Server 2022（管理员 PowerShell） |
| package.sh | 只打包：源码 → 自包含 tar.gz（五镜像+资产+脚本+sha256） | 构建机（任一有 Docker 的机器） |
| install.sh | 只安装：解包后九步到收据卡 | Linux / mac / WSL 内 |
| install.ps1 | 只安装（Windows）：WSL2 就绪 + 进 WSL 跑 install.sh | Windows（管理员） |
| **phoenix-ctl.sh / .ps1** | 日常运维一条入口：start/stop/restart/status/logs/verify/purge | 装机（ps1 为 Windows 转发） |
| verify.sh | 13 项健康断言（装完自检/日常体检） | 有栈的机器 |
| build.sh / save-offline.sh / load-and-run.sh | 旧手工链（保留兼容，日常不推荐） | 构建机/目标机 |
| backup.sh / upgrade.sh | 数据备份 / 旧版升级辅助（手工链时代产物） | 运维机 |
| mirrors.list | 镜像加速候选源清单（package/install 竞速选优，可直接编辑） | - |
| lib/common.sh + selftest.sh | 共用函数基座 + 22 断言自测（`bash lib/selftest.sh` 可跑） | - |

## 场景一：目标机一键（最常用）
### Windows Server / Win10/11
```powershell
# 源码 zip 解压后，管理员 PowerShell 进源码根目录：
.\docker\scripts\bootstrap.ps1
# 中途提示重启 → 重启 → 重跑同一条命令（自动续接）
# 约 40-70 分钟后出收据卡；浏览器开 http://localhost:9080
```
常用参数：`-Port 9080`（改端口）`-Project phoenix`（同机多套时区分）`-Distro Ubuntu-22.04`（WSL 发行版名，导入法建的可自定义）`-Offline`
> Server 无商店报「无效的分发名称」：按提示用导入法（aka.ms/wslubuntu2204 → `wsl --import Ubuntu-22.04 C:\WSL\Ubuntu <rootfs.tar.gz>`）再重跑。

### Linux / WSL 内 / mac
```bash
cd <源码根>
bash docker/scripts/bootstrap.sh                 # 默认全走
bash docker/scripts/bootstrap.sh --port 9180 --project phx2   # 同机第二套
bash docker/scripts/bootstrap.sh --mirror https://docker.m.daocloud.io  # 手工指定镜像源
```
mac 注意：不代装引擎（先装 Docker Desktop/Colima 再跑）。

## 场景二：构建机只出包（给离线机器铺货）
```bash
bash docker/scripts/package.sh --arch amd64 --version 1.5.0
# 产物: docker/dist/phoenix-1.5.0-amd64.tar.gz + .sha256 + .RECEIPT
# 可选: --overseas(全官方源) --mirror <前缀> --with-engine-debs(打入Ubuntu22.04引擎离线deb)
```
包拷到目标机（U盘/内网）→ 解包 → `bash install.sh`（Windows：`.\install.ps1`）。

## 场景三：拿包安装 / 升级
```bash
tar -xzf phoenix-<ver>-<arch>.tar.gz && cd phoenix-<ver>-<arch>
bash install.sh                                  # 全新安装
bash install.sh --env-from /旧安装/docker/.env    # 升级：承接旧配置（密码端口不漂移），数据卷自动保留
bash install.sh --offline                        # 断网机（引擎已装 或 包内有 engine/*.deb）
```
幂等：所有脚本重跑自动跳过已完成步骤（断点续传），失败输出必带「步骤 N/M + 日志路径」。

## 场景四：日常运维（装好之后）
```bash
# Linux/WSL/mac —— 自动定位安装目录与项目名，直接：
bash docker/scripts/phoenix-ctl.sh status     # 状态+健康+端口+首页探活
bash docker/scripts/phoenix-ctl.sh stop       # 暂停（容器保留，秒级恢复）
bash docker/scripts/phoenix-ctl.sh start      # 启动（等 backend healthy 才返回）
bash docker/scripts/phoenix-ctl.sh restart backend   # 重启单个服务
bash docker/scripts/phoenix-ctl.sh logs backend      # 跟日志
bash docker/scripts/phoenix-ctl.sh verify            # 13 项体检
bash docker/scripts/phoenix-ctl.sh purge             # 毁灭性清场（删数据卷，需输入 yes）
```
```powershell
# Windows 侧同款（自动转发进 WSL）：
.\docker\scripts\phoenix-ctl.ps1 status|start|stop|restart|logs|verify
```
服务器重启后：WSL 场景先 `sudo service docker start`（未开 systemd 时），然后 `phoenix-ctl.sh start`；
compose 已配 `restart: unless-stopped`，docker 引擎起来后栈通常**自动复活**，ctl start 只是兜底。

## 国内源说明（默认全国内，无需配置）
- 基础镜像：`mirrors.list` 三候选竞速（1ms.run/daocloud/dockerproxy），失稳时编辑该文件或 `--mirror` 指定
- Maven=阿里云（`docker/maven/settings.aliyun.xml`，含 30s 超时加固）；npm=npmmirror；引擎安装=get.docker `--mirror Aliyun`
- 海外环境：`package.sh --overseas` 一键切官方源

## 装完自检 / 日常体检
```bash
sh docker/scripts/verify.sh                                   # 开发栈（compose name: 自动解析）
PHOENIX_COMPOSE_PROJECT=phx2 PHOENIX_HTTP_PORT=9180 sh verify.sh   # 指定项目/端口
# 全新安装刚跑完 [7] 会 WARN（首启两拍，首轮对话后自动齐）——install.sh 已自动带 PHOENIX_FRESH=1 处理
```

## 故障速查
| 症状 | 处置 |
|---|---|
| 拉镜像超时 | `mirrors.list` 换序或 `--mirror`；`docker info \| grep -A3 Mirrors` 核对 daemon 配置 |
| mvn/pnpm 阶段失败 | 看日志是否走 aliyun/npmmirror；内网仓传 `--build-arg MAVEN_SETTINGS=<路径>` |
| `OSType: windows` | Windows 原生引擎跑不了 Linux 镜像——走 bootstrap.ps1 的 WSL2 路线 |
| WSL 嵌套虚拟化不可用 | Server 是虚拟机的话，宿主机开嵌套虚拟化（此项无法脚本代劳） |
| 端口被占 | `--port` 换一个；或改 .env 的 PHOENIX_HTTP_PORT 后重跑 |
| 失败后重跑 | 直接重跑同一命令即可（断点续接，无副作用）；日志在 ~/.phoenix/ |
| 日志/收据在哪 | 全程日志 `~/.phoenix/*.log`；收据卡=包根 `RECEIPT` 文件 |
