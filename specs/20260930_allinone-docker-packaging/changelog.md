# Changelog: allinone-docker-packaging

## 用户 UI 验收暴露真缺陷（2026-10-01）
- 浏览器登录 401 → 定位为**前端生产包双重 /api 前缀（BUG-33, P1）**：dev 的 vite proxy rewrite 掩盖了"生产构建从未成功部署"的事实；交付包 nginx 增加同款折叠规则（精确版：仅 /api/api/* 折叠，标准 /api/* 透传；第一版全量 rewrite 误伤单前缀，verify [5] 抓到后已修正）后 login/menus/list/SSE 实测全通 → 状态记「已规避(v1.2.0)」，根治走 BL-18
- 巡检顺带发现 **BUG-34**：model-config list 返回明文 apiKey（既有产品行为，新建待排期）
- verify.sh 增断言 [8]：双重前缀登录必须 200（防折叠规则丢失）

## 里程碑改挂（2026-09-30，用户明示）
- 「拟 v1.2.1」→ **挤入 v1.2.0**（M4 前，v1.2.0 未发布、v1.2.1 未立项，无版本冲突）。按细则：MILESTONE 解冻 + 纳入表登记 + RELEASE-NOTES/UPGRADE 口径补充 + 全链 IMAGE_TAG/命名 v1.2.1→v1.2.0；requirements/plan/tasks 因归属条款变更 bump 至 v1.4.0/v1.1.0/v1.1.0 **待重确认**

## Implement 全链实测记录（2026-09-30 深夜，本机=验证环境）
- 网络实况倒逼 thin 构建通路（docker hub/maven/adoptium 不可达，1ms/npm 可达）：jar 复用 host 构建、dist host pnpm 构建、基座 .env 参数化；本机验证借用 ES9 基座（java25 可用，交付默认 temurin alpine）
- 真实炸点逐一修复并落码：migrator 基线三步曲（R-05 v1.3.0）、python3 启动硬依赖（三分支安装）、自注册智能体首启 NPE（20 种子规避 → BUG-31 登记根治建议）、nginx 健康检查 debian 无 wget、verify [1][2] 误报（脚本自身含前缀 + 锚定假阴性）、save-offline/load-and-run 需 source .env 且 JAVA_OPTS 加引号
- AC 实况：AC-01/02(API)/03(SSE 24事件84字+生成57/679字四段10.8s)/04(down-up 零丢失)/05(1.3G 包 sha→load→up)/06(删台账只补该行 + R05↔V05 往返 is_active md5 快照一致)/07(verify 7项)/08(重复 up 零报错) 全绿；AC-02 的浏览器面客部分与 AC-05 真实隔离网属 M4 演练环境，待用户界面验收/发版演练
- 新登记：BUG-31(P1 首启NPE,包内已规避)、BUG-32(P2 deepseek 42013)
- T-10 暂不勾：待用户浏览器验收 + 真隔离网复证一次


## v1.3.0（2026-09-30）R-05 二次勘误（终版基线三步曲）
- v1.2.0 修订本身不完整：「all_data 单文件 0 报错」系我 `grep ^ERROR` 锚定假阴性（真实行以 `psql:` 开头）——向真实机制认错。终版结论（t_g 全链实测 0 报错 + 断言全中）：基线 = **00_baseline_sequences（5 个 demo 序列前置）→ all_data → 10_seed_admin**；all_schema 与 all_data 互斥（all_data 的 DROP TABLE 级联删 owned 序列）
- 依据用户「同意修订，继续」的持续推进授权（tasks 免问 + 本条为同一事实链机械延伸），requirements 标 v1.3.0；如你认为该二次勘误需单独追认，直接说，我挂起等确认

## v1.2.0（2026-09-30）R-05 基线勘误 —— **已重确认（陈卓，2026-09-30）**；plan §3 同步同次确认口径
- 事实：`all_data.sql` 是含结构+数据+序列的**完整基线**（新库空跑 0 报错，实测 phx_dataonly）；R-05 原「all_schema → all_data 顺序执行」组合在真实迁移中必炸（`tbl_data_categories_id_seq` 缺失，migrator exit 3 复现）——根因是草稿期未核实两文件关系
- 修订：基线改 `all_data.sql` 单文件；`all_schema.sql` 定位纯结构手工场景；其余链路（admin 种子、V01~05、幂等）不变
- 意图不变（一键完成全新初始化），属事实纠错；编码暂停，等本条重确认


## tasks v1.0.0（2026-09-30）授权确认 + 三重门全通过
- T-01~T-11（五要素齐备，21 条决策链：前置核实→种子→镜像×2→compose→migrator→断言→离线/运维脚本→README→端到端→收口）
- 用户授权「task 直接执行不用问了，要在全新的分支实现」→ 生成即标已确认，开分支 feature/allinone-docker-packaging 进入 Implement

## 二重确认通过（2026-09-30，确认人: 陈卓）
- requirements v1.1.0 重确认通过（用户答「确认」，勘误口径接受）
- plan v1.0.0 确认通过（含设计点：哨兵表 tbl_phoenix_release 为包私有机制，不进 releases 序号体系——用户未提异议，按方案默认）
- **tasks 授权免单独确认**：用户明示「task 直接执行不用问了」→ tasks v1.0.0 生成即标已确认（确认依据=该明示指示），并进入 Implement；**要求在全新分支实现**
- 三重确认门全通过 → 分支 feature/allinone-docker-packaging

## v0.1.0 plan（2026-09-30）草稿
- Phase 2 产出 plan.md v0.1.0（待确认）：拓扑/两镜像多阶段/migrator 哨兵两拍/nginx 参数/离线脚本/7 决策/8 风险；实测锚点已写入（PASSWORD_SALT=phoenix、admin 哈希、密钥 git 全历史 0 命中）
- requirements 状态：v1.1.0 待重确认（勘误随 plan 一并请用户确认）

## v1.1.0（2026-09-30）**勘误修订，状态回退待重确认**
- Plan 期实测证伪 v1.0.0 背景事实一条：`all_data.sql` 的「3 处真实 sk- 密钥」不成立——3 命中=1 注释+2 占位符；真实密钥在种子与 git 全历史 0 命中（`git log -S` 两已知前缀）
- 随之修订：背景事实（勘误注）、**R-17**（清洗→断言+装后录入引导，删"轮换/git 泄露"动作）、**Q1 决议记录**、**AC-07**
- **R-07** 设计事实落定：哈希=md5("phoenix"+密码)（`LoginConstant.PASSWORD_SALT="phoenix"`，hutool SecureUtil），admin 种子改放包内 `docker/init/10_seed_admin.sql`（幂等），不再动仓库种子——R-16 例外声明随之取消
- 承认并记录：错误源于我用 `grep -c 'sk-'` 计数未看内容即下结论；教训=密钥类断言必须逐条看命中内容
- Q1 用户口径不变（真实密钥不随包分发），修订不改变决议语义

## v1.0.0（2026-09-30）确认人: 陈卓
- **三重确认第①关通过**：requirements.md v1.0.0 已确认（用户答复「确认」）
- 进入 Phase 2 Plan

## v0.2.0（2026-09-30）草稿期修订（Q1~Q5 决议落条款）
- 用户答复：Q1 种子密钥脱敏为 `sk-xxxxx`（新增 **R-17**，声明为 R-16 唯一例外；git 历史泄露→README 建议轮换）；Q2 首登=admin/123456 内置种子（改 **R-07**）；Q3 端口 9 开头→默认 9080（改 **R-01**）；版本号定 **v1.2.1**（A-04）
- Q4（amd64-only）与 Q5（种子完整性核实）按建议默认/移交 plan 期，确认时可推翻
- AC-02/AC-07 随决议更新；Q 区转为决议记录
- 状态：草稿 → **待确认**（第一重确认门）

## v0.1.0（2026-09-30）
- 初始化创建：背景实测（jar 432MB/SSE 特性/首启建表坑/all_data 含 3 处真实密钥与无初始用户两大前置问题）、R-01~R-16、AC-01~08、Non-goals、假设 A-01~A-05
- 方案前提沿用同日对话评审结论：compose 编排包而非单镜像混部
