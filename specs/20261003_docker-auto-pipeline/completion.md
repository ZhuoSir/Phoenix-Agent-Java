# Completion: docker-auto-pipeline（v1.5.0）

> 生成: 2026-10-03 | 分支: feature/docker-auto-pipeline | 任务: 10/10 勾

## 已完成项（证据索引）
| T | 交付 | 证据 | commit |
|---|---|---|---|
| T-01 | lib/common.sh + selftest 22断言 | bash3.2 22/22绿·bash5.1 21绿1环境跳过·shellcheck 0 | c59f343 |
| T-02 | package.sh 八步 + 五镜像 payload | t02a 1.08GB/t02b 1.4GB 出货·Hub直连0·sha全对·overseas接线干跑·八跑抓7真bug | e80d56e/eb16e2b 等 |
| T-03 | install.sh 九步 | 真装39s收据卡·幂等9/9跳·篡改点名拒装×2·IMAGE_TAG升级分支实证 | 2bce9e8/20828f6 |
| T-04 | 引擎模块（get.docker --mirror Aliyun/deb离线/降级指引） | 代码+分支桩测（Linux离线分支容器实证 exit1+指引）；真装延真机（v1.1.0） | 2bce9e8/fbb20f7 |
| T-05 | install.ps1（WSL2全链） | pwsh 7.4.2 Parser 实检 OK·三分支设计评审；真机延 T-10 移交 | 9af2d4d |
| T-06 | --with-engine-debs + dpkg双遍 | 代码完+离线降级桩测绿；断网真跑延真机（v1.1.0） | fbb20f7 |
| T-07 | 幂等升级 | t02b→t02c 真实升级：标记行存活/密码指纹一致/IMAGE_TAG更新/新镜像healthy/9180=200 | 演练留档 changelog |
| T-08 | payload-docs 三件套 + 旧脚本注记 | INSTALL/OFFLINE-ENGINE/RECEIPT-SAMPLE 随包吸入；build/save-offline/load-and-run 头注 | 50830ed/b6d99e8 |
| T-09 | 矩阵本机四项 | ①mac全链=演练1/4 ④开发栈verify 13/13 ⑤multistage结构核对(末阶段/ARG默认不变) ⑥docker load 吃包内 images.tar ✓ | 本轮 |
| T-10 | 台账收尾 | 本文件+artifacts+MILESTONE+移交物 | 本笔 |

## 延期项（v1.1.0 用户裁决「网络重验证项延真机」，去向明确）
| 项 | 去向 |
|---|---|
| T-02 amd64 真实出包 | 首个 amd64 目标机交付时执行（薄组装路径已实证） |
| T-04 引擎真装（Linux 从零） | 首台 Linux 真机 / Server 2022 WSL 安装时必然真跑，日志回传即验证 |
| T-06 断网全链 | 真机断网环境 |
| T-05 install.ps1 真机全链 | 移交用户 Server 2022（移交物见下） |

## Windows 真机移交物
1. amd64 正式包（待构建：`package.sh --arch amd64 --version 1.5.0`，基础三件需真实拉取——或目标机直接走仓库内手工链文档自建）
2. `install.ps1`（包内自带）
3. 三步指引：管理员 PowerShell → `.\install.ps1` → 若提示重启则重启后重跑（自动续接）→ 收据卡

## 演练替身与清理记账（用户裁决「假设源完成」）
- postgres:16-alpine=postgres:latest 替身 tag：**已删**（防未来打包误跳过真实拉取）
- 测试栈 phoenix-t03（4容器+卷）、/tmp/phx-t03*、测试镜像 t02a/b/c、dist 测试包：**已清**
