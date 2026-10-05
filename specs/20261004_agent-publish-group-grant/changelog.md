# Changelog: agent-publish-group-grant

## T-04~T-07 收口（2026-10-04）实现完成 7/7，合并进 v1.6.0（不冻结）
- 用户走查通过（原话「我已经测过了没问题」）→ T-04/T-05 勾选；completion/artifacts 落盘；MILESTONE 翻实现完成
- 用户指令：「先把BL04收尾…合并到版本；然后BL-01也合并到版本；但是1.6.0不冻结」→ 双 feature 分支合并 v1.6.0（先 MCP 后本支），版本保持在途续收

## BUG-68 勘察记（2026-10-04）跨智能体记忆污染——根因锁定共享 workspace
- 用户报告：不同智能体不同会话串记忆。勘察链：①持久层排除（store_state 键=userId:sessionId 会话隔离✓、user_memory 表空✓）②UI 排除（admin/前台会话列表均按 agentId 过滤✓）③Redis 分布式存储键解剖（NUL 分隔：agents␀HumanInTheLoop␀users␀uid␀sessions␀path——含 agentName 维，为存量 Java 智能体遗留键）④**真凶=HarnessAgentFactory:110 workspace 固定共享目录**——所有库驱动智能体记忆文件/产物同目录互读互写
- 与 BUG-67 同根认定：文件面板跨会话污染=共享 workspace 的另一症状；修复应同刃（workspace 按 agent 分目录后，BUG-67 的时间窗补扫再收窄为轮次归属即双收口）
- 结构性附带发现：PostgresAgentStateStore 键无 agentId 维（userId:sessionId）——sessionId 唯一性目前兜底，属加固候选

## T-04/T-05 代码完成部署 + T-06 API 级全绿（2026-10-04）——UI 走查待用户
- T-04/T-05 交付：publishAgentApi 可选 body；列表页发布弹窗（组多选+重发布回显+空选全公开警示）；AgentGroupGrant.vue（全公开语义提示/非 published 禁用引导）；**双入口挂载**（抽屉「授权组」菜单+独立编辑页）；typecheck 213=基线（过程抓己错：抽屉数据变量是 form 非 agent，即修）；容器内标记串证+verify=0（L-03 回炉条款执行）
- T-06 API 级：E2E 收敛链三断言（公开→无组用户可见 / 勾 G1 收敛→无组用户失见名单空 / G1 用户 chenzhuo 仍可见）+ 对面断言双证（**MCP 判定/挂载四文件对本 spec 零 diff**；MY_SKILLS_SQL 零改动=技能域不串）+ admin 列表不变（B3）+ verify=0 + 夹具全清
- T-04/T-05 界面走查项与 T-06 的 UI 面移交用户走查（发布弹窗三态/抽屉授权组块/独立页块）

## T-02+T-03 完成（2026-10-04）两个判定读点全绿
- T-02 validateVisible 双分支：无授权行→**仅 published 享全公开**（草稿拒绝，防反向漏洞——设计时补的安全闸）；有授权行→交集 SQL 原样。四态实测：A1 公开可对话(899字节真回复,且运行配置缺失也走通=工厂默认兜底)✓ A2 授权假组→chenzhuo 拒绝"未授权"文案✓ A3 撤权回公开✓ A4 草稿拒绝✓
- T-03 getMyAgents 公开合并：交集∪公开集(NOT EXISTS+id::text+sn空过滤)，无组账号死角修复(现返回空列表非 null)。B1 chenzhuo=组授权4+公开夹具✓ B2 无组夹具=纯公开集✓ B3 admin 列表 5 条不变✓
- 附带发现：**「制度专家」未出现在公开列表**——其 sn 非空被"平台内创建"过滤挡下（存量自注册不进前台列表既有规则），A-5 存量翻转实际影响面比预估更小（chat 直达仍公开，列表不可见）
- 夹具全清（99905/99906/99907/t02 会话），合并部署一轮完成（合体镜像，MCP 共存无损——B3 与 MCP 端点此前 sanity 已过）

## T-01 完成（2026-10-04）发布扩展+授权端点 七断言全绿
- 前置：合并 feature/mcp-client-tools 入本分支（dev 栈单实例需合体构建；M4 合并序=先 MCP 后本支则近平滑）；台账冲突 5 文件按块定向消解（bugs 64-67 归位/version/lessons ×5 超集/MILESTONE 双行各取新）；.mvn-home 重建后构建须带 -s settings.aliyun.xml（默认库失败标记缓存）
- 交付：AgentPublishDTO；publish 可选 body（无 body=仅置状态，A-1 兼容实证）；GET/PUT /api/agent/{id}/groups（PUT 假组回可读 400——BUG-64 绕行 catch）；覆盖式物理删重建；del_flag 默认 1 陷阱显式置 0
- 七断言（夹具 agent99903/组99904，不碰用户数据）：①初始[] ②无body发布=仅置状态授权不动 ③带组发布=授权1行 ④假组=可读400"授权目标组不存在" ⑤覆盖式换组=仅新组+旧行物理删 ⑥空数组=清空(R-05全公开数据态) ⑦清场0/0/0
- 合体 sanity：MCP 列表 100、麦当劳MCP 在列、agent36 绑定完好、verify=0——merge 未伤 MCP
- 过程小账：重复 @Override 编译错（锚点吃了 deleteById 的注解）即修；一次构建忘带 -s 卡失败标记即纠

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
