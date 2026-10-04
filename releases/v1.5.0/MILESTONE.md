# MILESTONE: v1.5.0

> 创建: 2026-10-03 | 状态: **已冻结（M2，2026-10-04 用户口令）** | 类型: MINOR | 验证日期: 2026-10-04
> **版本分支**: `v1.5.0`（2026-10-03 建，基点 = main tip 3083273，v1.4.0 已合 main 无悬空）

**版本语义**：MINOR——新增交付工具链能力（Docker 自动化打包+安装流水线），向下兼容。

## 一、需求挂接表（M1）
| spec | 版本 | 状态 | 新增 | 变更 | 摘要 |
|---|---|---|---|---|---|
| specs/20261003_docker-auto-pipeline | req v1.0.0 / plan v1.0.1 / tasks v1.1.0 | **已合并 v1.5.0 分支（2026-10-03）**；10/10 勾；4 项网络重验证延真机=v1.1.0 用户裁决，去向见 completion | 0 | 0 | Docker 自动化打包+目标机自动安装：国内源全链、双侧镜像、裸机到可用（Linux/Win/mac/离线内网） |

## 一之二、纳入缺陷表
| BUG | 级别 | 状态 | 摘要 |
|---|---|---|---|
| BUG-62 | P3 | 已验证(v1.5.0) | settings XML 注释双横线致 Maven Non-parseable（v1.4.0 交付暗雷，package.sh 首炸） |
| BUG-63 | P2 | 已验证(v1.5.0) | compose 长驻服务无 restart 策略，宿主重启栈不自愈；连带交付 phoenix-ctl 运维脚本 |

> 合并后直入件（用户直接指令）：scripts/README.md 使用指南 · phoenix-ctl.sh/.ps1（BUG-63 随附）——87b2ae7

## 二、汇总进度（M3/M4 勾选）
- [x] M2 范围冻结（2026-10-04：孤儿spec=0/账证初对全对/本版BUG-62,63已验证/tasks唯一开口T-12去向在案且**用户裁决不等真机**；49提交；此后新需求默认进 v1.6.0）
- [x] M3 汇总（2026-10-04：审计对账绿——A=C 单spec无漏挂/SQL 0=0 零DDL相符/M2勾实相符/账证初对全对；config/changes（compose重启策略+脚本参数面+构建面+无变更面声明）/RELEASE-NOTES（6新增+2修复+3边界）/UPGRADE（既有部署零操作可选自愈+新机bootstrap+包对包升级）/checklist/sql README/artifacts补记）
- [ ] M4 发布（演练+tag）

## 三、审计记录
- **2026-10-04 M3 审计（第一动作）**：挂接对账 A(提交 Spec: footer)={docker-auto-pipeline}=C(表 1 行) ✓；B 挂载行指向 v1.5.0 ✓；进度重算：M2 勾有实(1fdf1a0)、M3/M4 未勾属实 ✓；SQL 判据 releases/sql V件 0 == artifacts 声明零 DDL ✓；账证初对（冻结时已跑）Bug footer 62/63 全在册已验证 ✓；无假勾无漏挂，账平进 M3 步骤 1-6
