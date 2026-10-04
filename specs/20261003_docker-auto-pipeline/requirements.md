# Requirements: Docker 自动化打包与安装流水线（docker-auto-pipeline）

> 版本: v1.1.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-03 | 更新: 2026-10-03（v1.1.0 新增 R-10，用户 bundled 确认 2026-10-03）
> 挂载: v1.5.0（2026-10-03，用户四裁决：新建 spec / 挂 v1.5.0 / 自动化管到引擎 / 五平台）

## 背景与目标

v1.4.0 交付体系（allinone-docker-packaging 遗产）已有 build.sh / save-offline / load-and-run / verify 手工链，multistage 构建已国内源化（阿里云 Maven + npmmirror）。但实战暴露（Windows Server 2022 部署支援轮，L-12）：**手工步骤多、目标机引擎缺失无脚本、Windows/mac 无入口、受限网络要手工配源、.env 陈旧值埋雷**。

**目标**：一条命令从源码出自包含安装包；目标机一条命令从**裸机**（含无 Docker 引擎）到**服务可用 + verify 全绿**。全程国内源默认、离线可用、幂等可升级。

**成功画面**：运维拿到一个 tar.gz，在任何一台目标机（Linux x86_64 / Windows Server / Win10-11 / mac / 离线内网机）执行一条命令，喝杯茶回来看到收据卡上 verify 全绿和访问地址。

## 需求条款（EARS）

### R-01 一键打包（构建机侧）
WHEN 构建机（任一有 Docker+出网/镜像源可达的机器，含 mac）执行 `docker/scripts/package.sh --arch <amd64|arm64>`, 系统 SHALL 产出单一自包含安装包 `phoenix-<版本>-<arch>.tar.gz`，内含：双侧镜像、compose 资产（compose/init/sql/releases 升级件/nginx conf）、安装脚本（sh+ps1）、verify 脚本、manifest、 sha256 清单；全程无人工介入；IF 任一步失败 THEN 非零退出并输出失败步骤与日志路径。

**验收场景**
- GIVEN 干净构建机（仅 Docker，无 JDK/Node）WHEN 跑 package.sh --arch amd64 THEN 产出 tar.gz，解包后 manifest 列出的每个文件 sha256 全对。
- GIVEN 构建中某镜像 pull 失败 WHEN package.sh 执行 THEN 退出码非 0，stderr 指明「步骤 X：拉取 <镜像> 失败」与日志文件路径。

### R-02 国内源全链默认
WHILE 打包与安装执行, 系统 SHALL 默认全部外部依赖走国内源：基础镜像走可配置 mirror 列表（默认 docker.1ms.run，含备选轮换）、Maven 走阿里云、npm 走 npmmirror、Linux apt 走阿里云/USTC 可选；并 SHALL 支持 `--overseas` 开关整体切回官方源。

**验收场景**
- GIVEN 国内网络构建机 WHEN package.sh 默认执行 THEN 构建日志中出现 maven.aliyun.com / registry.npmmirror.com / mirror 前缀，零 registry-1.docker.io 直连尝试。
- GIVEN 海外构建机 WHEN package.sh --overseas THEN 全走官方源且成功。

### R-03 双侧镜像多阶段容器内构建
打包 SHALL 通过 multistage Dockerfile 在容器内完成前后端编译（宿主机零 JDK/Maven/Node/pnpm 依赖），产出 phoenix-backend 与 phoenix-frontend 两镜像并 save 进包。

**验收场景**
- GIVEN 构建机未装 JDK/Node WHEN package.sh 执行 THEN 两镜像构建成功（日志含 mvn package 与 pnpm build 的容器内输出）。

### R-04 Linux/mac 目标机一键安装
WHEN 目标机（Linux x86_64 或 mac）执行 `install.sh`, 系统 SHALL 依序完成：①环境探测（OS/架构/权限/已有引擎与版本）→ ②引擎缺失时自动安装 Docker Engine + compose 插件（Linux 走国内 apt/yum 源；**mac 探测到无引擎时提示安装 Docker Desktop/Colima 并停住**——不代装，见假设 A-3）→ ③完整性校验（manifest sha256）→ ④镜像 load → ⑤生成 `.env`（PG_PASSWORD 随机生成并回显一次，IMAGE_TAG/端口默认）→ ⑥compose up → ⑦等待 healthy（超时上限可配）→ ⑧verify 全断言 → ⑨打印收据卡（版本/地址/账号/数据目录/日志路径/下一步）；IF 任一步失败 THEN 停在该步、打印已完成步骤清单与回滚指引（不自动回滚已 load 的镜像等无害步骤）。

**验收场景**
- GIVEN 无 Docker 的 Ubuntu 22.04 x86_64 WHEN install.sh THEN 引擎自动装好、服务起、verify 全绿、收据卡含随机密码。
- GIVEN 安装到第⑥步 compose 失败 WHEN install.sh THEN 退出码非 0，输出「已完成①~⑤，失败于⑥」与日志路径。
- GIVEN mac（arm64 包）无 docker 命令 WHEN install.sh THEN 明确提示装 Docker Desktop 或 Colima 后重跑，退出码非 0。

### R-05 Windows 目标机一键安装（WSL2 路线）
WHEN Windows 10/11 或 Server 2022 以管理员 PowerShell 执行 `install.ps1`, 系统 SHALL：①探测 Windows 原生容器引擎（OSType:windows）——IF 命中 THEN 拒绝并解释原因（Linux 镜像不兼容，L-12 教训）；②探测 WSL2——缺失则自动启用所需 Windows 功能并提示重启后重跑（断点续传：重跑从上次进度继续）；③WSL 发行版内自动装 Docker Engine（国内源，幂等）；④将安装包传入 WSL 并执行与 R-04 ③~⑨ 相同的安装链；⑤收据卡注明 Windows 侧访问地址。

**验收场景**
- GIVEN Server 2022 跑着 Windows 容器引擎 WHEN install.ps1 THEN 检测到 OSType:windows，拒绝执行并给出 WSL2 路线说明，退出码非 0。
- GIVEN 无 WSL2 的 Win11 WHEN install.ps1 THEN 功能启用+重启提示；重启后重跑自动续接完成全链。

### R-06 离线内网机可用
安装包 SHALL 在完全断网环境完成 ③~⑨（镜像 load/compose/verify 零外网依赖）；引擎自动安装在断网时 SHALL 降级为「检测缺引擎 → 打印离线安装指引（随包文档）→ 停住」；package.sh SHALL 支持 `--with-engine-debs` 可选打入引擎离线 deb 包（Ubuntu 22.04 x86_64 首发，其余平台文档指引）。

**验收场景**
- GIVEN 断网但已装 Docker 的内网机 WHEN install.sh THEN 全链成功（日志零外网请求失败记录）。
- GIVEN 断网无引擎机器 WHEN install.sh THEN 停住并打印离线引擎安装指引，退出码非 0。

### R-07 幂等与升级
WHEN install 重复执行（同版本或新版本包）, 系统 SHALL 幂等：引擎在→跳过安装；镜像 digest 已存在→跳过 load；`.env` 已存在→保留原值不覆盖（新键提示合并）；数据卷 SHALL 保留不清库（升级=migrator 自动增量，沿用既有机制）；同版本重装=无损修复安装。

**验收场景**
- GIVEN 已装 v1.5.0 的机器 WHEN 用 v1.6.0 包重跑 install THEN 数据保留、migrator 增量执行、verify 绿、收据卡显示新版本。
- GIVEN 已装机且 .env 改过密码 WHEN 同版本重跑 THEN .env 原值保留。

### R-08 manifest 与完整性
安装包 SHALL 内嵌 manifest.json（产品版本/git tag/架构/构建时间/文件清单+sha256）；install SHALL 在解包后先全量校验再动工；IF 任一 sha256 不符 THEN 拒绝安装并列出坏文件。

**验收场景**
- GIVEN 传输中损坏的 tar.gz WHEN install.sh THEN 校验失败、拒绝安装、列出损坏文件。

### R-09 日志与收据
install/package 全程 SHALL 写带时间戳日志到文件（路径打入收据与失败输出）；关键步骤控制台回显进度（步骤号/总数）；完成时 SHALL 打印收据卡：版本、架构、访问地址、初始账号、随机密码（仅首次）、数据目录、日志路径、「下一步建议」三行。

**验收场景**
- GIVEN 成功安装 WHEN 查看终端 THEN 收据卡字段齐全；日志文件可回放每一步耗时。

### R-10 目标机源码一键 bootstrap（v1.1.0 新增，用户「我要的是一键式，Windows Server 自己打包」）
WHEN 用户在目标机（Windows 管理员执行 `bootstrap.ps1` 指向源码目录 / Linux·WSL 执行 `bootstrap.sh`）, 系统 SHALL 自动串联完成：WSL2 就绪（Windows）→ 引擎就位 → `package.sh` 本机打包（native 架构）→ 解包 → `install.sh` 九步 → 收据卡；IF 任一环节失败 THEN 停在原地输出环节号与日志路径，修复后重跑 SHALL 自动续接（复用 package/install 内部状态机）；本机产出的 tar.gz SHALL 保留（可拷贝至其它离线机器直接安装）。

**验收场景**
- GIVEN 已装 WSL2+引擎的 mac/Linux WHEN `bootstrap.sh --port 9280 --project phx-bs` THEN 全链到收据卡，且 dist/ 留下可复用安装包。
- GIVEN 裸 Windows Server（无 WSL）WHEN 管理员 `bootstrap.ps1` THEN 自动启用功能并提示重启；重启重跑自动续接到收据卡。
- GIVEN bootstrap 中途某环节失败 WHEN 修复后重跑 THEN 已完成环节自动跳过（不重复编译/不重复安装）。

## Non-goals（范围外）
- Kubernetes/Helm/Swarm 编排（本流水线只出 compose 栈）
- 私有 registry 推送/拉取模式（候补 backlog，与离线包互斥场景）
- Windows ARM64、国产 OS（麒麟/统信）的引擎自动安装（文档指引兜底，脚本不做）
- 完全断网机的引擎**自动**安装（R-06 已定义降级行为；--with-engine-debs 仅 Ubuntu 22.04 x86_64）
- 既有安装的数据迁移/备份工具（migrator 已覆盖 schema 演进；数据备份另立需求）
- 安装包签名/加密分发（sha256 完整性为准，签名体系另立）

## 我正在做的假设
- **A-1** 构建机与目标机可以不是同一台（包经 U盘/内网/网盘转运）；构建机唯一硬性要求 = Docker + 可达镜像源（国内 mirror 或官方）
- **A-2** 目标机架构以 amd64 为主，mac（arm64）与 Linux arm64 用 --arch arm64 包；不做单包双架构（体积翻倍）
- **A-3** mac 引擎不代装（Docker Desktop 有 GUI 许可与安装交互，Colima 需 brew——超出脚本可靠边界），探测+提示+停住
- **A-4** Windows 路线全部经 WSL2（原生 Windows 容器引擎明确拒绝）；install.ps1 需要管理员权限，非管理员时提示并停住
- **A-5** compose 项目名统一 `phoenix`（与开发机 phoenix-release 区分）；端口默认 9080 可 .env 改
- **A-6** 随机密码只回显一次并写入 .env（文件权限 600）；收据卡提示首登改密
- **A-7** 引擎自动安装仅覆盖 Docker 官方支持的 Linux 发行版主流版本（Ubuntu 20/22/24、Debian 11/12、CentOS/RHEL 8/9 系）；其余发行版探测后降级为文档指引

**现在纠正，否则按此执行。**

## 决议记录（2026-10-03 用户裁决，Q 区关闭）
- **Q1 安装包形态** → **tar.gz**（用户：「tar 没问题」）
- **Q2 verify 失败** → **install 判定失败退出**（用户：「失败退出」）——收据卡只在全绿时打印「安装成功」
- **Q3 旧交付脚本** → **保留兼容**（用户：「保留」），文档标注「手工链，日常用 package/install」
- **Q4 --with-engine-debs** → **仅 Ubuntu 22.04 x86_64 可选打入**（用户：「可以」），默认关闭、开关启用 +~100MB
