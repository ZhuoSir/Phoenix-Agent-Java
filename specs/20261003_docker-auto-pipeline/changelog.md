# Changelog: docker-auto-pipeline

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
