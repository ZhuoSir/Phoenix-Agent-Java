# Plan: docker-auto-pipeline

> 版本: v1.0.1 | 状态: 待重确认 | 更新: 2026-10-03（原确认: 陈卓 2026-10-03 v1.0.0；本次 PATCH：payload 镜像清单补全，见变更记录）
> 规范路由（.specrc.yml）：code-backend=global（Java 规范不适用 shell——跟随 docker/scripts 周边风格）；code-frontend=none；api-design/database 不涉（无新接口无库表）；git-workflow=global（版本分支 v1.5.0 制式）

## 〇、坑核对（lessons.md 全部 12 条 active 逐条过）

| L | 相交? | 本方案如何避开 |
|---|---|---|
| L-01 文本补丁脱靶 | ✅ | 实现期一切文档/脚本编辑走带 assert 的 edit 助手；锚点先读实文 |
| L-02 日志截断误证 | ✅ | install 日志全量落盘不截断；失败输出含「日志路径+失败步骤号」，排障看全文件 |
| L-03 部署三段证明 | ✅✅ | **install.sh 的 ⑦⑧⑨ 步就是三段证明的制度化**：healthy 轮询（inspect）→ verify 断言（功能探针）→ 收据卡（产物标记）；package.sh 产包前自校验（解包→sha 全对→manifest 复算） |
| L-04 VM盘满/prune误删 | ✅ | install 前置检查磁盘余量（镜像体积×2.5 告警线）；脚本永不执行裸 prune |
| L-05 平台方言雷区 | ✅✅ | 全部 shell 限 **bash 3.2 兼容子集**（mac 祖传 bash——禁关联数组/`${var,,}`）；`#!/usr/bin/env bash` + `set -euo pipefail`；busybox 不进目标面（引擎装的是 Ubuntu/主流发行版）；开发期 shellcheck 过一遍 |
| L-06 多入口枚举 | ✅ | 共享面身份矩阵（§三）枚举 compose/verify/Dockerfile 的全部既有消费方，逐一给「变更后预期」 |
| L-07 假阳性验证 | ✅✅ | 验收标准全部双侧取证（命令输出+落盘文件回读）；「演练必须真做」——mac 本机全链真跑、Linux 路径容器伪靶机真跑 |
| L-08 时序演练靠运气 | ✅ | healthy 等待=确定性轮询（inspect .State.Health 每 5s，上限可配默认 300s），不用裸 sleep |
| L-09 curl -m 太小 | ✅ | 脚本内一切探活/下载 curl 带 `-m ≥10`（下载类 --retry 3）；判定到达只认 HTTP 码+响应体 |
| L-10 记忆污染 | ❌ | 无相交（不涉智能体记忆） |
| L-11 用户环境≠我以为 | ✅✅ | R-05 的 OSType:windows 拒绝分支就是 L-12 现场教训的制度化；install 第一步环境探测把「我以为」变成「脚本读数」（OS/arch/权限/引擎/虚拟化） |
| L-12 交付三查 | ✅✅ | install.sh 探测段=三查制度化（docker info OSType+架构匹配 manifest+源连通性预检）；架构不符拒装（arm64 包进 amd64 机=明确报错，不靠 docker 报错天书） |

**结论**：12 条中 11 条相交（L-10 无相交），均已给规避动作。

## 一、采用方案

**纯脚本流水线（bash + PowerShell），零新工具链依赖**，落在 `docker/` 既有交付体系之上：

```
构建机（任一有 Docker 的机器）                     目标机（五平台）
docker/scripts/package.sh ──▶ phoenix-<ver>-<arch>.tar.gz ──▶ install.sh / install.ps1
   │ multistage 双侧构建（国内源默认）                  │ ①探测(三查) ②引擎(装/提示/拒绝)
   │ save 镜像 + 组装 payload                          │ ③sha校验 ④load ⑤.env生成
   │ manifest.json + SHA256SUMS                        │ ⑥compose up ⑦healthy轮询
   └ 产包自校验（解包复算）                              │ ⑧verify ⑨收据卡+日志
```

### 1.1 安装包 payload 布局（tar.gz 解开即此树）
```
phoenix-<ver>-<arch>/
├── manifest.json          # 版本/git描述/arch/构建时间/文件清单+sha256/引擎最低版本
├── SHA256SUMS
├── images/phoenix-images.tar      # docker save 五镜像单 tar：phoenix 双侧 + 基础运行时三件（redis:7-alpine / pgvector/pgvector:pg16 / postgres:16-alpine，compose 默认引用；目标机零拉取=R-06 离线成立的前提）
├── deploy/                        # compose 运行资产（从仓库拷贝，非软链）
│   ├── docker-compose.yaml  ├── init/  ├── sql/  ├── releases/  ├── nginx/
│   └── verify.sh
├── bin/docker-compose-<os>-<arch>  # 独立 compose 二进制（引擎装不上 plugin 时兜底）
├── engine/                # 可选（--with-engine-debs）：Ubuntu 22.04 amd64 引擎离线 deb 组
├── install.sh             # Linux/mac
├── install.ps1            # Windows（WSL2 路线）
├── docs/
│   ├── INSTALL.md（五平台速查） ├── OFFLINE-ENGINE.md（断网装引擎指引）
│   └── RECEIPT-SAMPLE.md
```

### 1.2 package.sh（构建机）
- 参数：`--arch amd64|arm64`（默认=本机）、`--version <v>`（默认 `git describe --tags` 去 v 前缀）、`--overseas`、`--with-engine-debs`、`--mirror <前缀>`（默认读 `docker/scripts/mirrors.list` 竞速选优）
- 步骤：环境自检（docker/buildx/磁盘余量）→ mirror 可达性竞速选优 → 双侧 multistage 编译（native，工件提取）→ 目标架构薄组装 → **基础运行时三件按目标架构备齐（mirror pull+retag 裸名；架构不符本地缓存强制重拉）** → save 五镜像一 tar → 组装 payload → 生成 manifest+SHA256SUMS → tar.gz → **自校验** → 输出包路径+体积+sha
- （v1.0.1 补）基础三件清单以 compose 默认引用为准：`redis:7-alpine`、`pgvector/pgvector:pg16`、`postgres:16-alpine`；非 library 镜像 mirror 前缀不带 `/library/`（ref 分型）
- 失败语义：任一步非零即退，stderr 打「[package] 步骤 N/M 失败：<原因>，日志 <路径>」（L-02/L-03）

### 1.3 install.sh（Linux/mac，bash 3.2 兼容）
九步状态机（`.phoenix-install.state` 记完成位，重跑自动跳过已完成步——幂等 R-07 与断点续传共用一套机制）：
1. **探测**：OS/arch（对照 manifest，不符拒装）/sudo 或 root/磁盘余量/已有引擎（`docker info` 读 OSType+ServerVersion；**OSType=windows → 拒绝**，L-12）/端口占用（9080 冲突提前报）
2. **引擎**：有→跳过；无→ Linux：发行版识别（os-release）→ apt/yum 走国内源装 docker-ce+compose plugin（断网：engine/ 有 deb 则 dpkg 本地装，否则打印 OFFLINE-ENGINE.md 指引退出 1）；mac：打印 Docker Desktop/Colima 指引退出 1（A-3）
3. **校验**：SHA256SUMS 全量核对，坏文件列名拒装（R-08）
4. **load**：digest 已存在跳过（`docker image inspect` 比对，R-07）
5. **.env**：不存在→生成（PG_PASSWORD=`openssl rand -hex 16`，权限 600，回显一次）；存在→保留原值，新增键追加并提示（R-07）
6. **up**：`docker compose -p phoenix up -d`（无 compose plugin 用 bin/ 独立二进制兜底）
7. **healthy**：轮询 `docker inspect .State.Health` 5s×上限（默认 300s，`--timeout` 可调）
8. **verify**：跑 deploy/verify.sh，**任一断言失败→install 退出 1**（Q2 决议），输出失败断言号
9. **收据卡**：全绿才打「安装成功」+ 版本/arch/地址/账号/密码（仅首次）/数据目录/日志路径/下一步三行；写入 `RECEIPT` 文件

日志：全程 `tee` 到 `~/.phoenix/install-<ts>.log`（路径打入收据与一切失败输出）。

### 1.4 install.ps1（Windows，管理员 PowerShell）
1. 管理员检查（非管理员→提示+退出）；引擎若在且 `docker info` OSType=windows → **拒绝+解释**（R-05①）
2. WSL2 探测：`wsl --status`/功能位检查 → 缺→ `dism /online /enable-feature`（WSL+VirtualMachinePlatform）→ `wsl --install -d Ubuntu-22.04 --no-launch` → 提示重启后**重跑本脚本**（状态文件记「功能启用已完成」，重跑续接）；嵌套虚拟化不可用→如实报错停住（文档给宿主侧开法）
3. 包目录拷入 WSL → WSL 内 `bash install.sh --from-wsl`（Ubuntu 内装引擎——与 Linux 路径同一段代码，不分叉）
4. Windows 侧收尾：收据卡注明 `http://localhost:9080`（WSL2 端口自动转发；旧版 Win10 需 netsh 转发时脚本代执行）

### 1.5 国内源体系（R-02）
- `docker/scripts/mirrors.list`：`docker.1ms.run`、`docker.m.daocloud.io`、`dockerproxy.net` 逐行候选；package/install 共用「竞速探活选优」函数（curl -m 8 探 /v2/，取首个活口）
- Maven：`docker/maven/settings.aliyun.xml`（v1.4.0 已入库，直接引用）；npm：`NPM_REGISTRY=https://registry.npmmirror.com`（同）
- apt（引擎安装时）：`mirrors.aliyun.com` ubuntu/debian 源替换（备份原 sources.list）
- `--overseas`：以上全部切官方默认值

## 二、涉及模块与数据流
- 新增：`docker/scripts/package.sh`、`install.sh`、`install.ps1`、`mirrors.list`、`docker/scripts/lib/common.sh`（日志/步骤状态机/探活/收据卡函数）、`docker/payload-docs/`（INSTALL/OFFLINE-ENGINE/RECEIPT-SAMPLE）
- 修改：`docker/scripts/build.sh`（文档注一行「手工链」）、README/UPGRADE 指路；`verify.sh` 仅在硬编码端口时参数化（默认值不变）
- 数据流：仓库源码 → package.sh（容器内编译）→ tar.gz → 目标机 install →（引擎）→ load → compose up → verify → 收据
- 无接口/库表/前端应用代码变更

## 三、共享面身份矩阵（触碰 docker/ 交付资产）

自检问句「还有谁依赖这个路径/文件？」——compose.yaml、verify.sh、Dockerfile.multistage、.env.example、init/sql/releases 均为共享面：

| 共享对象 | 既有身份（全部枚举） | 变更后预期行为 |
|---|---|---|
| docker-compose.yaml | ①开发栈 `docker compose`（项目名 phoenix-release，本机 daily）②旧 load-and-run 手工链 ③**新增：payload 内被 install 以 `-p phoenix` 消费** | ①②行为不变（本 spec 原则上不改 compose 文件本体；若必须改，①②③三面同测）；③同一文件不同项目名，卷/网络天然隔离 |
| scripts/verify.sh | ①开发栈回归（13 断言）②**新增：payload 内 install 第⑧步消费** | 断言逻辑不改；若需端口参数化则默认值=现行为（①逐字节不变） |
| Dockerfile.*.multistage | ①build.sh --multistage 手工链 ②**新增：package.sh buildx 消费** | v1.4.0 已有 MAVEN_SETTINGS/NPM_REGISTRY ARG，预期零改动；若加 ARG 默认值不变（①行为一致） |
| .env.example | ①手工链 cp 使用 ②**新增：install 第⑤步以其为模板生成 .env** | 不改键集；install 生成时注入随机密码与 tag |
| init/ sql/ releases/ | ①migrator 消费 ②**新增：payload 拷贝件** | 内容零改动，package 只做拷贝+入清单 |
| build.sh、save-offline.sh、load-and-run.sh | ①既有手工链用户 | **保留不动**（Q3 决议），文档标注手工链定位 |

**验证规则**（进 tasks 断言）：每个「行为不变」身份至少一条对面断言——开发栈 verify 全绿复跑、build.sh --multistage 构建参数干跑核对、旧 load-and-run 对同 tar 可用性抽测。

## 四、被拒绝的替代方案
| 方案 | 拒绝理由 |
|---|---|
| 自解压单文件 sh | Q1 用户裁决 tar.gz；自解压在 Windows 侧仍需 tar 二次解，收益虚 |
| 私有 registry 推拉 | Non-goal；引入仓库服务依赖，与离线内网主场景冲突 |
| 单包双架构（manifest list） | 体积×2（~7GB），转运痛苦；--arch 双包更实际（A-2） |
| mac 代装引擎（brew colima） | brew 有无不确定+GUI 许可交互，脚本可靠边界外（A-3，用户已认） |
| Ansible/自动化工具 | 目标机零预装前提被破坏；纯 shell+ps1 到处能跑 |
| rootless 引擎安装路径 | 复杂度与发行版碎片化高，root/sudo 路径覆盖主场景（A-4/A-7） |
| Windows 原生容器适配 | OSType:windows 跑不了 Linux 镜像（物理事实，L-12），拒绝+WSL2 是唯一正路 |

## 五、风险与规避
| # | 风险 | 规避 |
|---|---|---|
| 1 | mac 系统 bash 3.2 古老语法雷（关联数组/正则操作符） | 编码规约限 bash 3.2 子集+shellcheck；本机（就是 mac）全链真跑即天然验证 |
| 2 | 第三方镜像源失稳（1ms.run 等说挂就挂） | mirrors.list 多备选竞速+`--mirror` 手工指定+`--overseas` 兜底；源全死时错误信息给出手工拉取指令 |
| 3 | WSL2 嵌套虚拟化不可用（VM 上的 Server） | 探测（systeminfo/wsl --status）如实停住，文档给宿主开启指引——不假装能装 |
| 4 | 包体积 ~3.5GB 转运损坏 | SHA256SUMS 全量校验前置（R-08），坏包拒装列名 |
| 5 | 目标机端口/数据目录冲突 | 探测步预检 9080 占用与 /var/lib/docker 余量，冲突提前报并可 .env 改 |
| 6 | install 半途失败留脏状态 | 状态机断点续传+每步幂等（R-07）；失败输出「已完成/失败于」清单（L-03 制度化） |

## 六、测试策略（真做，非口头）
| 面 | 手段 | 通过线 |
|---|---|---|
| package.sh | 本 mac（arm64）出包+自校验；buildx 跨出 amd64 包 | 双架构包产出、解包 sha 全对、开发栈 verify 复跑仍绿（身份矩阵对面断言） |
| install.sh mac 路径 | 本机第二 compose 项目名真装（隔离端口） | 九步全绿+收据卡+重复执行幂等 |
| install.sh Linux 路径 | docker 起 ubuntu:22.04 伪靶机（--privileged 供 dinD）真装引擎+全链 | 九步全绿；断网变体走通离线降级 |
| install.ps1 | 语法/逻辑评审+状态机路径桩测；**真机=用户办公室 Server 2022（今日支援轮那台）** | 真机全链由用户执行、agent 收日志判定（L-11：真实环境实测） |
| 升级/幂等 | 同包重跑+改密 .env 保留验证 | R-07 两场景 |
| 回归 | 开发栈（phoenix-release）全程不受扰 | verify 13/13 |

## 变更记录
- **v1.0.1（2026-10-03，Implement 期发现，铁律6 停编回改）**：T-03 mac 真装演练第 6 步 compose up 失败暴露 payload 设计缺口——包内只有 phoenix 双侧镜像，基础运行时三件（redis/pgvector/postgres-client）未打包，目标机 compose 必拉 Hub（离线 R-06 不成立；开发机验证全绿纯因本地恰有缓存——演练价值自证）。补全 §1.1 payload 清单与 §1.2 save 步骤为五镜像；PG_PASSWORD 弱默认值问题随演练记录（.env.example 注释已警示，install 生成路径用随机密码，预置路径尊重用户文件——不改）。同轮演练正面战果：步骤1-5 全绿、sha 拒装 hotfix 篡改文件（完整性体系首战即立功）。

## 七、任务预告（Phase 3 细化）
T-01 lib/common.sh 基座（日志/状态机/探活/收据函数）→ T-02 package.sh → T-03 install.sh 九步主链 → T-04 引擎自动安装模块（apt/yum/deb离线）→ T-05 install.ps1+WSL2 → T-06 离线降级与 --with-engine-debs → T-07 幂等升级专项 → T-08 文档套件+旧脚本定位注记 → T-09 实测矩阵（mac/Linux伪靶机/开发栈回归）→ T-10 台账收尾（Windows 真机移交用户）。
