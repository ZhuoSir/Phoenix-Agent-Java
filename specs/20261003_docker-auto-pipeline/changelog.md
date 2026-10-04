# Changelog: docker-auto-pipeline

## T-11 完成（2026-10-04）—— 演练五全链终证
- 一条命令 `bootstrap.sh --port 9280 --project phx-bs --version bs-test --mirror daocloud`：引擎跳过→打包(1.36GB,缓存热9分钟)→解包→九步安装→**verify 12 PASS+1 预期 WARN(fresh) exit=0**→收据卡 16:10:41 完赛；独立复验+9280 首页 200+4 容器 healthy
- 演练四~五共抓 3 真问题并全修：verify 裸 exec 假绿通道(项目参数化)/[7]首启断言误杀新装(PHOENIX_FRESH 软化)/步骤7 migrator 等待(防御性加固)
- 环境事件如实记：VM 出网坏死(macOS26×Desktop4.13 vpnkit  compat)由用户 VPN 救活；我方"容器网络DEAD"诊断为失效探针假证据(L-02×2)；根治建议=升级 Docker Desktop

## 演练四复盘与两次归因更正（2026-10-04）
- **打包段首次全链通过**（VPN 修好 VM 出网后：编译/五镜像/tar/自校验/解包全绿）
- 安装段步骤 8 红 → 深挖出**比竞态更大的鱼**：verify.sh 裸 `docker compose exec` 被 compose.yaml 顶层 `name: phoenix-release` 解析到**开发栈**——昨日 drill-1 的 [6][10][12][7] PASS 全是假绿（查的 dev 数据）；今日 dev 栈午后又宕（Docker Desktop 4.13×macOS 26.6.2 不稳，已拉起）→ 假通道断裂 → 诚实报 0
- 修复：verify 项目参数化（PHOENIX_COMPOSE_PROJECT）+ PHOENIX_FRESH 软化 [7]（首启两拍=全新安装预期态）；phx-bs 真验证 exit=0（12 PASS+1 WARN）、dev 严格回归 exit=0
- **3251d50 归因更正**：步骤 7「等 migrator」保留为防御性加固，但演练四红的真因是本项目参数化缺失，非竞态
- L-07 假阳性验证**复发×2 回炉**：断言必须显式绑定目标，禁止依赖默认解析的全局查询

## R-10 bootstrap 增量（2026-10-03）—— 三文档 bundled 确认
- 用户 Server 2022 实战反馈「我要的是一键式，Windows Server 自己打包」→ requirements +R-10(v1.1.0)/plan +§1.6(v1.1.0)/tasks +T-11,T-12(v1.2.0)，用户单条「确认」bundled 过三门（增量条款已在前一条消息全文展示）
- spec 已合并 v1.5.0 → 增量走新分支 feature/bootstrap-oneclick（基点 v1.5.0）
- 过程注记：首次批量编辑 plan 头锚点默写偏差被 assert 拦截（L-01 v2 第二次实战生效），读实文后补完

## 合并（2026-10-03）
- 用户「合并进1.5.0分支吧」→ feature/docker-auto-pipeline（~22 commits）--no-ff 并入 v1.5.0，分支已删；amd64 正式包不在本机打（用户裁决：直接在 Server 2022 真机试装，走 WSL2 操作单/自建路线，真机日志回传即 T-04/T-05 延期项验证）

## T-02+T-03 完成（2026-10-03）八跑收敛+三连测全绿
- T-02：t02a(1.08GB)+t02b 五镜像版(1.4GB) 双包出货；证据=Hub直连0/国内源13处/解包sha全对/manifest JSON自检/--overseas接线干跑(官方名+default settings✓,断网exit1预期内)/失败输出格式五跑实证；amd64 按 tasks v1.1.0 降级口径(薄组装已实证)
- T-03：**九步真装全绿 exit=0 仅39秒**(镜像本地时)——IMAGE_TAG v1.3.0→t02b 升级分支实测过；幂等重跑 9/9 步全跳+栈不抖；篡改拒装点名 BAD: docs/INSTALL.md exit=1；栈取证:4容器healthy+9180登录出token+迁移台账9行(五镜像payload+替身migrator全链成立)
- 替身记账：postgres:16-alpine=postgres:latest tag(用户裁决"假设源完成")，演练毕删除；t02c(升级演练"新版")打包中

## tasks v1.1.0（2026-10-03）—— 验证口径调整，用户裁决+确认
- 用户裁决「源可以假设完成，验证整体流程，没必要真拉，最后还要删除」→ 网络重验证项统一延真机：T-02 amd64 第二包降级（薄组装已实证）/T-04 引擎真装延真机/T-06 断网真跑延真机/T-09 六项改「本机四项真跑+两项延真机」；T-07 伪靶机改本机 t02a→t02b 真实升级（方法替换非降级）
- 原则：代码一行不少写；替身镜像（postgres:latest→16-alpine tag）仅准演练环境、用后删除、如实记账
- 过程注记：首次编辑因默写锚点（v0.1.0 vs 实文 v1.0.0）被 assert 拦截零污染——L-01 v2 规则首次实战生效

## plan v1.0.1 已确认（2026-10-03）—— 重确认②通过，恢复 Implement
- 用户「确认」；五镜像 payload 方案生效；package.sh 改造 → t02b 重打 → 九步全链真装三连测 → amd64 交叉包

## plan v1.0.1 待重确认（2026-10-03，铁律6 触发）
- T-03 mac 真装演练：步骤1-5 全绿（含 sha 拒装被 hotfix 的 install.sh——完整性体系立功），步骤6 compose up 失败暴露 **payload 缺基础运行时三件镜像**（redis/pgvector/postgres-client）——R-06 离线不成立的设计缺口，开发机全绿假象纯因本地有缓存
- 停编回改：plan §1.1/§1.2 补五镜像打包+ref 分型（非 library 前缀），v1.0.0→v1.0.1 待重确认；amd64 缺陷包后台跑已中止

## T-01 完成（2026-10-03）lib/common.sh 基座库
- 交付：common.sh 119 行（日志 stderr+落盘/九步状态机/mirror 竞速/sha256 双平台/收据卡/资源探测，全 phx_ 前缀 bash3.2 契约）+ selftest.sh 22 断言
- 验证：mac bash 3.2.57 → 22/22 exit=0；ubuntu 容器 bash 5.1.16 → 21过/0败/1环境跳过(无python3,活口子测已在mac覆盖)；shellcheck 0.11.0（用户本机自装,选项c）common.sh 零告警、selftest 清理后零告警
- **selftest 抓到 2 个真 bug**：①mirror 判活 curl 失败输出 000 与 ||echo 000 拼接成 000000 死口误判活口 ②phx_log 走 stdout 污染命令替换返回值通道——均修复复测绿

## tasks v1.0.0（2026-10-03）—— 确认③通过，三重门全绿
- 用户「确认」；tasks v0.1.0→v1.0.0 已确认（陈卓）
- 三重确认自检：requirements v1.0.0 已确认(陈卓) / plan v1.0.0 已确认(陈卓) / tasks v1.0.0 已确认(陈卓)——进入 Phase 4 Implement

## tasks v0.1.0（2026-10-03）
- Phase 3 拆解落盘：T-01~T-10 三组（基座与打包/安装主链/文档与收口）；每任务五件套齐；R-01~R-09 全覆盖自检无孤儿；共享面 6 对象对面断言全落 T-09；验证方式全真做（mac 真装/ubuntu 特权伪靶机/断网变体/Windows 真机移交用户）；bash 3.2 双版本硬证入 T-01
- 状态：草稿（第三重确认门待过）

## plan v1.0.0（2026-10-03）—— 确认②通过
- 用户「确认」；plan v0.1.0→v1.0.0 已确认（陈卓）；进入 Phase 3 Tasks

## plan v0.1.0（2026-10-03）
- Phase 2 方案落盘：纯脚本流水线（package.sh/install.sh/install.ps1+lib/common.sh，零新工具链）；payload 布局定稿；九步安装状态机（幂等+断点续传一套机制）；国内源三件套（mirrors.list 竞速/阿里云 Maven/npmmirror+--overseas 开关）；坑核对 12 条过 11 相交（L-10 无）；共享面身份矩阵 6 对象全枚举；被否案 7 条；风险 6 条；测试策略含 mac 真跑/ubuntu 伪靶机/Windows 真机移交用户
- 状态：草稿（第二重确认门待过）

## v1.0.0（2026-10-03）—— 确认①通过
- 用户「确认」（确认人沿用陈卓默认，未异议）；requirements v0.2.0→v1.0.0 已确认；进入 Phase 2 Plan

## v0.2.0（2026-10-03）
- Q1~Q4 用户裁决全落定（tar.gz / verify 失败退出 / 旧脚本保留兼容 / engine-debs 仅 Ubuntu22.04-x86_64 可选）；Q 区转决议记录；状态 草稿→待确认（第一重确认门待「确认」口令）

## v0.1.0（2026-10-03）
- 初始化创建：背景=Windows Server 2022 部署支援轮实战暴露（L-12/L-03 教训直接喂入条款）；前置=20260930_allinone-docker-packaging（v1.2 时代手工链遗产）+ v1.4.0 multistage 国内源化
- requirements.md v0.1.0 草稿：R-01~R-09（一键打包/国内源全链/双侧镜像/Linux+mac 安装/Windows WSL2 安装/离线可用/幂等升级/manifest 完整性/日志收据）+ Non-goals 6 条 + 假设 A-1~A-7 + 阻塞问题 Q1~Q4
- 挂载: v1.5.0（2026-10-03，用户确认「v1.5.0（推荐）」——ask_user_question 四裁决记录在 MILESTONE 与 version.md）
