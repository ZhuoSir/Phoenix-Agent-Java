# v1.5.0 Release Notes（2026-10-04）

**主题：交付工具链——从源码到目标机服务的全自动流水线**（零 DDL、应用代码零变化）

## 新增
1. **一键 bootstrap（R-10）**：目标机源码一把梭——Windows `bootstrap.ps1`（管理员）自动完成 WSL2 就绪→引擎→打包→安装→收据卡；Linux/mac `bootstrap.sh` 同款；中途重启/失败重跑自动续接
2. **一键打包 package.sh（R-01/02/03/08）**：构建机一条命令出自包含 tar.gz（Phoenix 双侧+基础运行时五镜像、compose 资产、双平台安装脚本、manifest+sha256、文档）；国内源全链默认（mirrors.list 竞速/阿里云 Maven 含 30s 超时/npmmirror），`--overseas` 一键切官方；容器内编译=构建机零 JDK/Node 依赖；跨架构=编译一次+薄组装
3. **九步安装 install.sh / install.ps1（R-04/05/06/07/09）**：环境三查（架构比对/OSType=windows 拒绝/磁盘）→引擎自动装（get.docker 阿里源/离线 deb 降级/mac 提示）→sha 拒坏包→load→.env 生成（随机密码 600 权限）→compose→healthy 确定性轮询→**verify 失败=安装失败**→收据卡；幂等重跑/断点续传/`--env-from` 升级承接/IMAGE_TAG 自动跟版
4. **运维控制 phoenix-ctl.sh/.ps1**：start/stop/restart/status/logs/verify/purge 一条入口，项目名四级自动解析，Windows 侧自动转发 WSL
5. **compose 自愈**：长驻服务 restart: unless-stopped（宿主重启栈自动复活）
6. **脚本工具箱文档**：docker/scripts/README.md（脚本地图/四场景/故障速查）

## 修复（2）
- BUG-62 multistage settings XML 注释双横线致 Maven Non-parseable（v1.4.0 交付暗雷，打包流水线首炸排雷）
- BUG-63 compose 长驻服务无 restart 策略（宿主重启栈不自愈）

## 已知边界
- Windows 真机全链（T-12）待用户 Server 2022 实测销账（ps1 已过 Parser 实检+分支桩测；WSL 内九步与 Linux 同码已全链实证）
- 完全断网机引擎安装：`--with-engine-debs` 首发仅 Ubuntu 22.04（amd64/arm64）；其余发行版走 OFFLINE-ENGINE.md 静态二进制路线
- mirror 竞速探活测响应不测带宽（慢源可 `--mirror` 手工指定；吞吐型探活在 backlog）

## 升级说明
既有 v1.4.0 部署：**无需任何操作**（应用零变化）；想要重启自愈则替换 compose 文件后 `up -d --force-recreate --pull never`。新装/铺新机：直接用 bootstrap（见 docker/scripts/README.md）。
