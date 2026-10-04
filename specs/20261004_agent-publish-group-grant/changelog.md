# Changelog: agent-publish-group-grant

## 分支纠偏（2026-10-04）spec 文档迁入正确分支
- 用户发现工作区看不到本 spec 文件夹——根因：立项~tasks 确认的 7 笔提交顺手落在了 feature/mcp-client-tools（MCP 收尾后未切分支），本 feature 分支基点 v1.6.0 不含它们
- 修复：`git checkout feature/mcp-client-tools -- specs/20261004_agent-publish-group-grant/` 整目录迁移 + 共享台账（backlog/version/MILESTONE）BL-04 增量在本分支重放（b94a851）
- 险情如实记（**二次修正：并非无损**）：迁移时误用 `git stash -u`，海量未跟踪目录被卷入，命令卡死被击杀；源码与 T-01 未提交改动完好、stash 快照冗余已 drop——**但 drop 时未核查快照删除面：`.mvn-home`（本地 Maven 仓库）被 stash 清掉大半（剩 141MB，spring-ai 等依赖缺失，23 个下载失败标记），后续编译卡远程下载 10 分钟才暴露**。修复：aliyun 镜像配置重拉依赖（后台 job）。教训升级：drop stash 前必须 `git stash show --include-untracked --stat` 核查删除面，"冗余"判断只对已核实的文件成立
- 账实不符第二例如实记：b94a851 提交语声称"L-06 计次×5 入 lessons"，实际 python 在 lessons 锚点 assert 死（本分支 L-06 还是 ×3，四犯记录在 MCP 分支未含）——lessons/changelog 未写。本笔补正：L-06 直接写全局终态 ×5（四犯抽屉+五犯 stash 注记合并，M4 合并以此行为准）

## tasks v1.0.0（2026-10-04）—— 确认③通过，三重门全绿
- 用户「确认」；tasks v0.1.0→v1.0.0 已确认（陈卓）；三重门自检后切 feature/agent-publish-group-grant（基点 v1.6.0），进入 Phase 4，首任务=T-01

## tasks v0.1.0（2026-10-04）
- Phase 3 拆解：T-01~T-07 三组（后端判定与端点/前端/收口）；读点清单六项落位（#1→T-02、#2→T-03、#3~6→T-06 对面断言）；T-05 显式双入口挂载（抽屉+独立页，L-06 今日教训）；E2E 主链含无组夹具用户；对面断言四连（技能/MCP/组管理侧/admin 列表）

## plan v1.0.0（2026-10-04）—— 确认②通过
- 用户「确认」；plan v0.1.0→v1.0.0 已确认（陈卓）；进入 Phase 3 Tasks

## plan v0.1.0（2026-10-04）
- Phase 2 落盘：发布扩展(镜像技能 publish+DTO)+groups 读写端点；validateVisible 双分支；getMyAgents 交集∪公开合并(无组账号分支修复)；**L-06 读点清单六项实勘定案**(2改4零变化,前端三门面零改动全吃服务端)；前端发布弹窗+抽屉授权区块；身份矩阵6/被否案4/风险5(读点漏改头号)；坑核对15条全过

## v1.0.0（2026-10-04）—— 确认①通过
- 用户「确认」；requirements v0.2.0→v1.0.0 已确认（陈卓）；进入 Phase 2 Plan

## v0.2.0（2026-10-04）—— Q1/Q2/Q3 裁决落定 + 空授权语义翻转
- 用户三条裁决：①组管理侧双入口保留 ②允许空选 ③**空选=全公开（语义级修订），chat 段查看逻辑随改**
- R-03 重写（消费端零变化→判定语义双分支：有授权行=交集/无行=全公开；技能/MCP 非公开语义不动，域分裂=有意）；R-05 重写（空选发布=全公开+弹窗明示+公开→交集收敛场景）
- A-5 存量实测：5 个已发布中 1 个无授权（id=24 制度专家）将翻全公开——M3 入 RELEASE-NOTES 提醒；A-1 补注（无 body 发布=不动授权行→随新语义成公开）
- Q 区关闭转决议记录

## v0.1.0（2026-10-04）
- 初始化创建（BL-04 立项，用户裁决「新需求就在 v1.6.0 版本里做」——不冻结续收，v1.6.0 第二 spec）
- requirements.md v0.1.0 草稿落盘：R-01~R-05（发布内联授权/编辑面调整入口/消费端零变化/下线重发布/空授权发布）+Non-goals 5+假设 A-1~A-4（零 DDL、body 向后兼容）+阻塞 Q1~Q2；勘察事实入档（现发布端点薄体仅置状态、技能 publish+groupIds 模板、GroupAgentInfoController 组侧现状）
- 挂载: v1.6.0（2026-10-04，用户明示；与 mcp-client-tools 同版，M2 冻结时两 spec 一起冻）
