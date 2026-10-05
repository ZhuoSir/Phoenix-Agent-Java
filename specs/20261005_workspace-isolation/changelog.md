# Changelog: workspace-isolation

## BUG-83 裁决（2026-10-05）按 A 保留，C 列待办
- 用户裁决「先做 A（保持现状），把 C 列入待办」→ 本轮**零代码改动**（现状即 A）：shell cwd=会话根、文件工具落 `{sessionRoot}/{uid}/`，两者都在会话目录内，面板递归扫全树故都可见
- C（去 USER namespace 层做单根，文件工具根=shell cwd=技能包根=memory 根）已登记 `BL-26`（建议 M3 批次；立项第一步=AGENT 档位落点/记忆/技能三件 spike；触及已确认 plan「框架内再拼 uid」→ 需增量重确认）
- 定案依据（实测）：技能包物化在**会话根** `.skills-cache/...`，而文件工具相对根在 `{uid}/` 差一层——老会话 a467aecf 模型自述「read_file 打不开 .skills-cache…；改用 execute+cat」即此代价；B（shell 挪进 {uid}/）会让两个工具都够不到技能包相对路径且 shell cwd 随"谁在跑这轮"漂移，故弃

## T-11~T-14 部署 + 实测验收通过（2026-10-05，commit f7a0964 + 1b0a3a0 噪声过滤 + BUG-81 前端 7bacf16）
- 部署：`docker compose build backend nginx`（ES9 基座）+ `up -d`；健康 200、首页 200；L-16 门禁两轮均先查 `metadata->>'status'='generating'`=0
- **T-11 实证**：构建日志 `对话智能体构建完成: agentId=33, runtimeKey=agent-33, ..., workspace=/app/uploads/agent-workspace/agent-33/{sessionId}`；磁盘树 `agent-33/{sessionId}/{uid}/file_probe.txt`（文件工具）+ `agent-33/{sessionId}/sh_probe.txt`（shell）
- **T-12 实证（BUG-79 毙）**：会话内 shell `pwd` = `/app/uploads/agent-workspace/agent-33/{sessionId}`（修前 `/app`）；shell 产物落会话目录且面板可见；三入口共用 `ShellExecuteTool→ProcessBuilder.directory(shellCwd)`
- **T-13 实证**：同会话三工具产物同处可见（面板列出 `sh_probe.txt`＋`file_probe.txt`）；**新建会话 B 文件数=0**（跨会话零交叉）；旧会话属主可见（3a3f8fe1=3 条、31f42c23=10 条）；`call_*` 条目=0
- **BUG-82 实测发现并修复**：扫描器把框架技能缓存当产物登记——`.skills-cache/` 只被"点开头且无扩展名"规则漏过，单会话噪音 28 条（catalog.json/search_library.py/svg_audit.py…）；修法=目录段凡 `.` 开头即内部件；**C 会话修后 1 条（c_probe.txt）**
- **BUG-80 实测通过**：admin token 列前台用户会话 → code=100/30 条；admin 删 `catalog.json` → code=100 且库内 `del_flag=1`；属主本人删自己文件 → 100（正对照）；前台 token 访问他人会话 → 42031（负对照，越权仍拦）
- 遗留（如实记）：①文件工具根=会话目录/uid 子层、shell cwd=会话目录，**二者差一层**（模型当场指出并要求确认）→ 记 BUG-83 待裁决 ②`listBySession` 仍按会话查 DB，历史广扫遗留的跨会话行（10-02 老数据）不动 ③agent 根下 `probe_test.txt`/`sample-note.txt` 等旧探针文件仍在（tee 副本已登记，删盘会伤下载，留待迁移）
- 夹具清理：r06-verify A/B/C 三会话（文件行 del_flag=1 + 会话行删除 + 磁盘目录 rm）全清

## T-11~T-14 代码落地（2026-10-05）**未部署**，commit f7a0964
- 交付（后端 8 文件；前端/DB/配置零改动）：
  - T-11 `HarnessAgentFactory.buildWithSummary(agent, sessionId)` → `.workspace({root}/{agentKey}/{sessionId})`（空=存量智能体级，零行为变化）；`HarnessAgentRegistry` 缓存键 `agentId@sessionId`（LRU 键改 String）、`acquire/get/buildUncached` 增会话重载、`invalidate` 连带清全部会话实例；`McpMountService` 变体键含 sessionId；`HarnessChatServiceImpl` 对话+确认续跑均按（智能体,会话）取实例
  - T-12 **框架原生支持 cwd**（无需包装工具）：`.project(Path)` 即 `LocalFilesystemWithShell.shellCwd`，缺省回落 `project==null → user.dir`（容器内 `/app`，BUG-79 根因一锤）；显式置为会话目录 → shell/文件工具同根。`ShellExecuteTool` 是唯一 shell 入口（本地/交互/后台同走 `ProcessBuilder.directory(shellCwd)`），三入口一次覆盖。**前置补丁**：框架只在 namespace 分支 mkdir、shellCwd 命中时直接当 directory 用，故 factory 建实例时先 `createDirectories(会话目录)`（实测 ProcessBuilder 对不存在 cwd 抛 `IOException error=2`，不补这一步会让新会话的**每条 shell 命令**全废）
  - T-13 扫描根=会话目录（会话目录存在→**只扫它**，会话间零交叉）；未建立时回落智能体级历史多根只读兼容，受 turnStart 时间窗 + 他会话 UUID 目录排除双约束；`runtimeKey` 改**以库中智能体为准**（请求侧 sn 缺省=BUG-78 真根因）；storeKey 改 `agentKey+智能体根相对路径`（跨会话稳定）；过滤 `call_*` 与 `large_tool_results`
  - T-14 `/app` 实测清单：仅 `.agentscope/workspace/agents/RulesHarnessAgent/tasks`（存量 Java 智能体遗留）+ jar/logs/uploads，**零业务产物**（BUG-79 的 `probe_sh.txt` 已随容器重建蒸发——正好反证 shell 逃逸产物不持久）
- BUG-80 同批修复（详见 bugs.md）：`canAccessSession`=属主 或 后台用户表命中的管理员（`tbl_privilege_user`，实测 admin✓/前台✗ 判定正确）+ 孤儿行回落产物创建者；抽屉补扫同步放行
- 编译：`mvn -pl phoenix-admin/phoenix-admin-manager -am package -DskipTests`（skip format）通过；**运行时验收（T-15 四项）待部署后执行**——本轮按「先改不发布」纪律不部署

## T-11 起步（2026-10-05）会话级根地基 + 交接点
- 已落：`WorkspacePaths.sessionRoot(workspaceRoot, runtimeKey, sessionId)` = `{root}/{agentKey}/{sessionId}`（含 {uid} 层注记）；编译通过
- **下一会话从这三处接续（T-11 主体）**：
  1. `HarnessAgentFactory`（约 105/121 行）：`buildWithSummary(Agent)` 增会话工作区重载（null=保持现行为），`.workspace(...)` 改用 `sessionRoot(..., sessionId)`
  2. `HarnessAgentRegistry.acquire(Long agentId)`（72 行）：签名加 sessionId、缓存键加 sessionId（`fingerprint` 不变，键=(agentId,sessionId,fingerprint)）
  3. 调用方：`HarnessChatServiceImpl`/`HarnessController` 链路把 `request.getSessionId()` 透传到 acquire（预览服务保持无会话=legacy）
- 之后 T-12（shell cwd 统一：先查框架 shell/exec 规格 cwd 支持，否则包装注入 `cd {sessionDir} && `，覆盖 shell-local/pwsh/后台 job）→ T-13（扫描/面板以会话目录为唯一根+legacy 只读兼容）→ T-14（/app 清理）→ T-15（四项验收）→ T-16（台账）
- 上下文耗尽说明：本次会话上下文已满，以上交接点已写细，新会话按此续做即可

## v1.1.0 确认① + plan/tasks 增量（2026-10-05）
- 用户「确认」→ requirements v1.1.0 已确认（陈卓）；plan v1.1.0 + tasks v0.2.0 增量落盘（T-11~T-16）
- 方案要点：会话级工作区根 `{root}/{agentKey}/{sessionId}`（框架再拼 `{uid}` 层→实际 `.../{sessionId}/{uid}/`，面板递归扫）；shell cwd 统一（先查框架 cwd 支持，否则包装注入 `cd`）；读路径以会话目录为唯一根+legacy 只读兼容；实例缓存改按 (agent,session)
- 待过 ②③ 门（本次一并呈请确认）

## v1.1.0（2026-10-05）用户新增要求：每智能体每会话独立目录（读写统一）
- 用户原话：「我要求的是每个智能体每个会话都有单独的文件夹，无论是写入还是读取，现在读都读不到」
- 触发实测：①file 工具 → `{agent}/{uid}/` ②**shell → `/app`（逃出工作区）** ③绝对路径 → `{agent}/` 根级；面板因扫描面不匹配而读不到（BUG-78/79 入册）
- 新增 R-06：`{root}/{agentKey}/{sessionId}/` 唯一目录，写（file/shell/脚本/下载）与读（file/面板/扫描）全部统一；会话间互不可见；存量 legacy 只读兼容
- 待裁决：实现取舍——按 (agent, session) 构建实例（+0.2~2s/会话）vs 仅 shell cwd 按轮注入（不满足统一，不推荐）
- 状态回退：requirements v1.1.0 **待重确认**（铁律3），确认后方可动代码

## BUG-69 排障记（2026-10-05，宿主分支顺带）——不可复现，金丝雀+TTL 布防
- 用户报告 admin 暗号会话三连问 UI 无输出即"会话完成"；取证：DB 三轮全满（done/143/79/195字）、SSE 实测 28 帧全空；范围隔离：无 MCP 的 agent-33 流正常（37 非空帧）→ 病灶锁定 MCP 变体路径（T-05 接线回归嫌疑）
- 六场景复现全绿（含 stdio stub 死连接对照、工具调用轮、缓存复用轮）——**不可复现**；故障仅现于 9h 长运行实例，重启消失；核心矛盾（同一 replay sink：TurnManager 见文/SSE 订阅见空）未解，如实保留在 BUG 行
- 布防三件：变体 30min 闲置 TTL 重建（缓解）+ [b69-canary] 每轮体检日志（contentLen>0∧textFrames=0 即告警；局限注记：sink→订阅段测不到）+ 撤逐事件仪表；金丝雀轮实测 33 非空帧+日志在位
- L-07 再证入档：昨日 1426 字节空帧被我误判"脚本截断"，DB 面绿掩盖用户可见面黑——**流类断言必须绑用户可见出口**

## T-05+T-06 完成（2026-10-05）实现完成 6/6
- T-05 E2E：**暗号双向断言一锤定音**（A agent36 记住 alpha-w05 → B agent33 答「不知道」→ A 复认答出——BUG-68 主症状毙且记忆功能未损）；对面五连（技能 options 100/MCP 列表+回环测试 success/前台 mySkills 100/前台对话 done/legacy 默认根未动）+verify=0；夹具全清（含暗号记忆文件 MEMORY.md+日记，归档区外 md 残留=0）
- 排障注记：对面④首测 500=我猜错端点路径（真路径 /platform/account-info/getMySkills 复测 100）——BUG-64 包装再误导，佐证其修复价值
- T-06：completion（R-01~05 兑现+四注记）/artifacts（后端 5 文件零 DDL）/MILESTONE 第三行翻实现完成/BUG-67+68 翻已验证(v1.6.0在途)/记忆重置提示移交 M3

## T-04 完成（2026-10-05）迁移演练三段全绿
- 交付：WorkspaceMigrationRunner（ApplicationRunner；允许集=_legacy_shared ∪ DB 实时 runtimeKey；只 move 不删；名字冲突加时间戳绝不覆盖；允许集查询失败=宁可不迁；整体失败 WARN 不阻塞启动）
- 演练：①布假存量（888-fake-user/MEMORY.md+memory/note.md+legacy-probe.txt）→ 部署 → 根级只剩 _legacy_shared+agent-36，**16 项归档**（含真存量 88MB 用户树/散文件/框架缓存），假存量三件在档 ②**二次重启幂等**：日志"无存量条目（幂等零动作）"、_legacy_shared 零嵌套、agent-36 树完好 ③**下载对面证**：迁移前登记的老文件走 tee 副本下载 HTTP 200/3859 字节（workspace 搬家零影响实证）④verify=0

## T-02+T-03 完成（2026-10-05）全局首占+窄窗四证全绿
- 交付：existsByStoreKeyAnySession 全局首占方法；scanner 扫描根同源下沉（WorkspacePaths）+ storeKey 加 runtimeKey 前缀（防跨智能体相对路径误撞）；AgentFileController 补扫窗=最近 assistant 消息起（无消息不补扫）
- 夹具实测（免模型）：①S1 补扫登记 w02-new ✓ ②S2 补扫**空**（不收编 S1 已占——BUG-67 主症状毙）✓ ③窄窗证：mtime 窗外文件零登记 ✓ ④归属 DB 双侧=w02-s1 ✓；调用方枚举复核：仅轮末主通道+本抽屉入口两处，前后台共用单端点无漏
- 工装自记：清场核验 SQL 三犯 bigint||bigint 拼接错（删除本身已执行，::text 修正复核全零）

## T-01 完成（2026-10-05）factory 换根生效实证
- 交付：WorkspacePaths（runtimeKey/agentRoot 单一实现）+ factory .workspace 下沉 {root}/{runtimeKey} + runtimeKey 方法同源化委托；build 绿
- 目录实证：部署后 agent-36 对话轮 → 框架自建 `agent-36/agents/36/tasks/_sweep.marker` 落**专属子树**，根级零新增散文件 ✓；存量旧树（461671…/agents/fonts/散文件）原样未动（待 T-04 迁移归档）
- 环境注记：探针轮模型调用报 OpenAIException(HTTP transport error)=容器→模型 API 网络故障（既有 VPN 拐杖问题，非本改动）——R-02 暗号等模型依赖验证待外联恢复（T-05 批次）

## tasks v1.0.0（2026-10-05）—— 确认③通过，三重门全绿
- 用户「确认」；tasks v0.1.0→v1.0.0 已确认（陈卓）；三重门自检后切 feature/workspace-isolation（基点 v1.6.0），进入 Phase 4，首任务=T-01

## tasks v0.1.0（2026-10-05）
- Phase 3 拆解：T-01~T-06 三组（隔离主刀/迁移/收口）；BUG-67 双主刀分置 T-02(全局首占)+T-03(窄窗)；迁移演练含幂等二次重启+tee 副本对面证；暗号双向断言=R-02 一锤定音；改动面后端 5 文件、前端/DB 零改动

## plan v1.0.0（2026-10-05）—— 确认②通过
- 用户「确认」；plan v0.1.0→v1.0.0 已确认（陈卓）；进入 Phase 3 Tasks

## plan v0.1.0（2026-10-05）
- Phase 2 落盘：L-06 消费方勘察闭环（真实消费仅 3 处，8/10 文件零引用；**下载走 tee 副本 filesRoot 另一根→迁移零 DB 重写**——枚举省掉一台多余手术）；方案=factory 换根一行级+scanner 全局首占+补扫窄窗（最近 assistant 消息起）+迁移 runner（允许集=DB runtimeKey 实时查询，只 move 不删，幂等）；身份矩阵 6/被否案 4/风险 5；坑核对 15 条全过

## v1.0.0（2026-10-05）—— 确认①通过
- 用户「确认，按推荐」（Q1 归档重置/Q2 接受记忆重置）；存储布局图已向用户展示（卷不动、根下加 agent 层、_legacy_shared 归档）；进 Phase 2

## v0.1.0（2026-10-05）
- 初始化创建（用户口令「新建一个spec，解决bug 67-68」；BUG-67+BUG-68 同刃双修立项）
- 挂载: v1.6.0（用户现行指令「1.6.0 不冻结续收」，第三 spec；如需另起版本随时改判）
- 勘察终版入档：真通道={userId}/MEMORY.md 用户级记忆无 agent 维+根级散文件；已排除=会话转录/任务区/Postgres状态键/Redis(仅legacy)；BUG-68 根因栏精化回写 bugs.md
- requirements v0.1.0 草稿：R-01~R-05 + Q1/Q2（存量归档策略+记忆重置副作用确认）
