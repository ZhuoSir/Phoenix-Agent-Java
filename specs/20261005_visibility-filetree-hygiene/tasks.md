# Tasks: visibility-filetree-hygiene

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05
> 上游: requirements v1.0.0（已确认①）+ plan v1.0.0（已确认②）｜挂载: v1.7.0｜分支: `feature/visibility-filetree-hygiene`

## 组 1：工程清账（R-03，先清工作树）

- [x] T-01 `.gitignore` + 工作区残留清理：`.gitignore` 增 `.mvn-home/`、`.pnpm-store/`；删 `WSL`、`或在`（0 字节误建）、`AGENTS.md.bak.20260927101837`；删 `scripts/fib.py`（测试夹具）；`diagrams/phoenix-architecture.html` 入库
  关联: R-03.1, R-03.2 | 依赖: 无
  验证方式: `git status --porcelain` 前后对照；`git check-ignore -v .mvn-home .pnpm-store` 命中；删除项 `ls` 复核；建筑图 `git status` 显示为新增
  验收标准: status 仅剩有意保留项；无交付物误删（diagrams 入库可 review）
  **收口(2026-10-05, commit 6f16a8c)**：`.gitignore` 增 `.mvn-home/`/`.pnpm-store/`（`git check-ignore -v` 双双命中）；删 `WSL`/`或在`(各 0B)/`AGENTS.md.bak.20260927101837`/`scripts/fib.py`(目录随之清空)；`diagrams/phoenix-architecture.html`(15K) 入库。**`git status` 由 7 条未跟踪噪声 → 0**

- [x] T-02 删死配置 `PhoenixAgentProperties.skillPath`：删字段（含注解/Lombok 访问器），全链编译
  关联: R-03.3 | 依赖: 无
  验证方式: `mvn -pl phoenix-admin/phoenix-admin-manager -am package -DskipTests` 绿；`grep -rn getSkillPath --include=*.java` **0 命中**；技能链回归（技能列表 options / 前台 mySkills 各 100）
  验收标准: 编译绿 + 零引用复核 + 技能链不回退
  **收口(2026-10-05, commit a84538b)**：字段已删（Lombok @Data 访问器自动收敛）；`mvn -pl phoenix-admin/phoenix-admin-manager -am package -DskipTests` **绿**；`getSkillPath/setSkillPath` **0 命中**；技能链回归 `/api/skill/options?agentId=33` **code=100**、`/platform/account-info/getMySkills?agentId=33` **code=100**（注：该套 API 成功码即 `100`）

- [x] T-03 台账销账：backlog §四 两条注记修正（agent-config：**实测 13/13 全勾**、历史快照、分支不存在；里程碑：v1.6.0 已发布并合 main、在途=v1.7.0）；`RulesHarnessAgent.java` 旧注记销账（bugs.md 工作区遗留段，实测已无未提交改动）
  关联: R-03.4 | 依赖: 无
  验证方式: `mdtable_check` 全绿；每条注记与 `git`/`tasks.md` 事实逐条对照留档
  验收标准: 台账无过期注记、无自相矛盾
  **收口(2026-10-05, commit 6fb8cda)**：§四 agent-config 条目改为 **13/13 全勾**并封存为历史快照（旧注"已勾 8 个"系误记）；里程碑条目更新为 **v1.6.0 已发布并合 main（merge d6dbb4d）、在途唯一=v1.7.0**；bugs.md 工作区遗留段两条作废（RulesHarnessAgent 实测无未提交改动 / 未跟踪噪声已清零）；**清掉 2 条被取代的中间注记**（防台账自相矛盾）；两文件 `mdtable_check` 全绿

## 组 2：会话文件文件夹树（R-02）

- [x] T-04 路径解析器 + 隐藏规则同源化：新增 `SessionFileTree`（判三代 `store_key` → 会话内相对路径，非本会话段→历史）；把「框架内部目录/占位」判定从 `WorkspaceArtifactScanner` **抽为公共常量/工具**，扫描器改为引用同一处（防两处漂移，落实 L-19）
  关联: R-02.3, R-02.4 | 依赖: 无
  验证方式: 夹具断言三代各一（三代→相对路径；二代/一代→历史）＋噪音样例（`.pylibs`/`.skills-cache`/`large_tool_results`/`call_*`）全判隐；`grep` 证明扫描器已改用同一常量（同源引用证据）
  验收标准: 三代解析全绿 + 同源引用可证
  **收口(2026-10-05)**：新增 `SessionWorkspaceFilters`（判定单一实现）+ `SessionFileTree`（三代解析）；扫描器删 5 个局部名单常量并全部委托共享工具（`grep` 零残留=同源可证）；`fixture/TreeProbe.java` **13/13 PASS**（含他会话隔离 2 例、前缀不匹配 1 例、空键不抛 1 例）；编译绿。实现细化：归属改为**会话段锚定**（前缀不参与判定），仅记 changelog 未回改已确认 plan

- [x] T-05 树接口：`GET /api/agent/files/tree?sessionId=&path=&scan=` **单层**返回（含目录 `dirCount/fileCount` 一次性分组计数；隐藏项不出现；历史行归「历史文件」节点）；属主/管理员校验复用 `canAccessSession`；`scan=true` 保留抽屉补扫语义
  关联: R-02.1, R-02.2, R-02.4, R-02.6 | 依赖: T-04
  验证方式: 夹具会话实测（层级进入/返回、隐藏项、历史节点、计数正确）；越权负对照（前台 token 访问他会话 → 42031）；大目录用会话 `0690b2d0`（594 件真产物）测首层时延与 payload 大小并留档
  验收标准: 三场景（正常/隐藏/历史）+ 性能口径（首层 ≤1s、展开 ≤500ms，dev 栈）
  **收口(2026-10-05, v1.1.0 口径落地)**：`GET /api/agent/files/tree` 已部署（health 200 / 0 轮误伤）
  - 新会话 `6e9c09e0` 根层 = 1 目录(`messi-personal-logo` 113 文件) + 1 文件，**uid 层已折叠**（原为 dirs=1 且唯一节点名=uid）
  - 老会话不再塌成单节点：`3ccf0341`→[chen-logo, logo-chen]；`0690b2d0`→4 目录+1 文件；`c11a9ae7`→[logo-chen]（原均只有「历史文件」）
  - 逐层进入 + 目录计数：`messi-personal-logo` → deliverables 84 / concepts 10 / presentations 8 / tools 8 / renders 3
  - 越权负对照 **42031**；大目录 `0690b2d0`（594 行）**51ms / 1017B**（口径 ≤1s）
  - 隐藏内部件：遍历 20 层全树 **0 泄漏**
  - 夹具探针 14/14 PASS（含双层 uid 折叠、他会话隔离、无会话段旧行三态）
  - 双次部署留痕：19:41（v1.0.0 口径，暴露问题）→ 20:0x（v1.1.0 口径，达标）

- [x] T-06 前端树 UI（`ChatFilesPanel.vue`，run 页与前台**共用同一组件**）：面包屑 + 文件夹优先 + 进入/返回 + 懒加载 + `FILES_CHANGED_EVENT` 刷新当前层；下载/删除/空面板提示保持不变
  关联: R-02.2, R-02.5, R-02.6 | 依赖: T-05
  验证方式: 两面走查（run 页 + 前台 chat 同一组件）；下载/删除/SSE 产物刷新回归；`vue-tsc` 基线（213 预存 / **0 新增**）
  验收标准: 两面体验一致 + 既有能力不回归
  **收口(2026-10-05, 实现+API级/构建级)**：`ChatFilesPanel.vue` 改树形（面包屑 + 返回 + 文件夹优先 + 单层懒加载 + `FILES_CHANGED_EVENT` 刷新当前层 + `path` 导航），文件行预览/下载/删除与空态提示保持不变（`HISTORY_PATH` 常量与后端对齐）。新增 `getAgentFileTreeApi` + `AgentFileTreeNode/TreeLevel` 类型。
  - `vue-tsc` **213 错误 = 基线（本 spec 文件 0 错误）**（过程中曾因未用函数 `toItem` 多 1 条，已删并复验回基线）
  - 构建产物已部署：`pnpm -F @vben/web-ele build` → stage → `compose build nginx` → `up -d`（health 200）
  - **线上校验**：线上 `js/ChatFilesPanel-C3cJj9Ah.js` 与本地构建 **sha256 一致**（b72fb31229d3eadd），且含「会话目录 / 跨会话遗留 / 此文件夹为空」关键字
  - **UI 走查移交用户**（项目规矩：agent 不自测 UI）——走查清单见本 spec changelog 末尾
  - **走查中发现的缺陷已修（BUG-87 + 物化回落）**：
    ① `existsByStoreKeyAnySession` 改**原生 SQL**（`del_flag` 被框架按逻辑删列处理，QueryChain 会被自动追加 `del_flag=0` → 查不到墓碑行 → 删除后文件复活）；实测用户被删的 `harness-讲义.md` 连续 3 次扫描**不复活**，全局复活键清零（仅剩空 store_key 物化行分组，与扫描无关）
    ② 树补 `store_key` 为空的**物化文件**回落（以文件名落会话根）——原先树里丢失（平铺列表有）属我引入的回归；实测物化文件可见且删除后不复活
    ③ `mvn ... package -DskipTests` 绿、`verify` 交付面不回退

## 组 3：长轮静默可见性（R-01）

- [x] T-07 轮次阶段标记：`HarnessChatServiceImpl` 把 `ModelCallStartEvent/ModelCallEndEvent` 回灌 `HarnessTurnManager.onPhase(sessionId, MODEL|TOOL, detail)`；Turn 保存 `phase/phaseSince`
  关联: R-01.1, R-01.2 | 依赖: 无
  验证方式: 一轮含工具调用的实测 → 阶段切换可观测（日志/新指标）；既有帧路径零变化（内容/帧速回归对照金丝雀 `frames/emitted/textFrames`）
  验收标准: 阶段可观测且除新增外零行为变化
  **收口(2026-10-05 实测)**：阶段序列实测（scratch 会话）`MODEL → TOOL(execute) → (12s 工具期) → IDLE → MODEL → IDLE`，`[turn-phase]` 日志可查、工具名随行。**两次实测修正**：① `ToolCallEnd` 只是"工具调用块"结束，真正执行在其后 → 改为保持 TOOL；② 真正标志执行开始的是 **`ToolResultStartEvent`**（实测 :51 到达而 sleep 到 :03 才结束）→ 修正为 `ToolResultStart→TOOL / ToolResultEnd→IDLE`

- [x] T-08 静默心跳：janitor 分支按 `phoenix.agent.silence-heartbeat-seconds`（默认 15s）下发心跳帧 `{silenceMs, phase, phaseLabel}`，**≤1 帧/5s** 节流；新增 `heartbeats` 计数并打印进 `[b69-canary]`
  关联: R-01.2, R-01.3 | 依赖: T-07
  验证方式: **stub 模型服务**（OpenAI 兼容，可配延迟）延迟 30s → 心跳按 15s 起出现、秒数递增、前端可见；金丝雀 `heartbeats=` 与 `frames/emitted` 对比无恶化
  验收标准: 心跳可见 + 节流生效 + 帧指标不恶化
  **收口(2026-10-05 实测)**：SSE 实测心跳帧 `silenceMs=18451/23452`（间隔 5s=**节流生效**）；标签按阶段正确取「模型调用中 / 工具执行中 / 等待响应中」（12s 工具期实测为**工具执行中**）；金丝雀行新增 `heartbeats=N`（实测 2/3/1）

- [x] T-09 首帧超时定稿：`MODEL` 阶段自调用发起 ≥ `phoenix.agent.model-first-frame-timeout-seconds`（默认 180s，**0=关**）→ `finish(STATUS_MODEL_TIMEOUT, 文案)` + `sourceSub.dispose()`；**不走** BUG-77 重试分支
  关联: R-01.1, R-01.4, R-01.5 | 依赖: T-07
  验证方式: stub 延迟 200s → 180s 触发定稿（文案「⏱ 模型调用超时」）、已生成内容保留、`turn/status` 收敛 false；**负对照**：`TOOL` 期长任务（`execute sleep 200`）**不得**被杀，且心跳显示「工具执行中」
  验收标准: 超时可控可关 + 工具期零误杀
  **收口(2026-10-05 实测，旁路容器）**：阈值 1s + tick 1s → 触发 `status=model_timeout` 定稿；**负对照**：阈值 5s + 工具 `sleep 12` → TOOL 期静默 12s **未被误杀**（`status=done`）。修正项：`finish()` 的文案追加条件原先只含 `timeout|cancelled`，**漏了新状态** → 已纳入（否则用户面对空气泡）

- [x] T-10 前端静默指示（两面）：`components/run/index.vue` 与 `views/front/components/ChatMessages.vue` 读新帧键，显示「模型调用中/工具执行中 · 已静默 Ns」；帧键进 mapper 白名单，**join 追流同样透传**
  关联: R-01.2 | 依赖: T-08
  验证方式: 首发流与 join 追流**两路**都能看到心跳（身份矩阵「心跳两面」）；运行中刷新后仍可见；`vue-tsc` 0 新增
  验收标准: 两面两路一致
  **收口(2026-10-05)**：`chat-shared` 增会话级 `silenceByS`（心跳帧不入消息列表，真实增量/结束即清除）；前台 chat 在「正在执行（已用时 mm:ss）」后追加「· 工具执行中 · 已静默 23s」；run 页按会话 `silenceText`（L-20 口径）同位置展示。typecheck **213=基线（0 新增）**；构建产物已部署且线上 chunk 哈希与本地一致（`run-D0GF370F.js` / `src-0GuhAFpw.js`）

## 组 4：收口

- [x] T-11 E2E + 回归矩阵：三组联合验收（树夹具 + stub 静默 + 清账对照）＋ 长轮真实跑（≥10 分钟含工具阶段，观察静默指示）＋ `verify 13/13` ＋ typecheck 基线 ＋ 既有能力回归（下载/删除/取消/confirm/MCP 链/技能链）
  关联: R-04 全部 | 依赖: T-01~T-10
  验证方式: 逐项留档（**用户可见面为准**，L-07）；夹具全清
  验收标准: 全矩阵绿、现场零残留
  **收口(2026-10-05/06)**：verify **13/13**；typecheck **213=基线**；技能链 100/100；树（新/老会话）正常 + 越权 **42031**；平铺 code=100 n=112；下载 200/117B；MCP code=100；取消 `cancelled`（内容保留）；删除不复活；**真实多步长轮**产出 `icons/`(8)+`index.md` 且在树中按文件夹呈现（canary frames=4715/emitted=84/textFrames=521/heartbeats=0）。如实偏差：长轮实测约 4 分钟（非 ≥10 分钟，由用户真实长轮覆盖）；HITL confirm 未新验（无改动面）。夹具/试验残留已清

- [x] T-12 台账收尾：completion/artifacts/MILESTONE（M2/M3 勾）+ BUG-85 翻已验证 + BL-11/12/15/28 状态随发版翻「已交付(v1.7.0)」+ changelog 收口
  关联: 流程 | 依赖: T-11
  验证方式: completion 与勾选核对；`mdtable_check` 全绿
  验收标准: 台账齐、偏差与去向写明
  **收口(2026-10-06)**：`completion.md`（12/12 + 回归矩阵 + 需求兑现核对 + 偏差去向）+ `artifacts.md`（代码/配置/夹具面）+ MILESTONE 挂载注记 + **BUG-85 翻已验证(v1.7.0)**；`BL-11/12/15/28` 待 v1.7.0 发版时翻「已交付」

## 自检
- R 覆盖：R-01(T-07~10) R-02(T-04~06) R-03(T-01~03) R-04(T-11)——全覆盖无孤儿
- 依赖无循环；交付顺序=T-01~03（清账）→ T-04~06（树）→ T-07~10（静默）→ T-11~12（收口）
- 改动面：后端 3~4 文件（TurnManager/ChatServiceImpl/mapper + 新解析工具）+ 前端 2~3 文件 + 扫描器隐藏规则抽公共（1 文件改引用）+ 工程/台账 6 项；**零 DDL、零布局改动**
- 粒度：12 任务各可独立验证；每组可单独交付（R-03 零风险先行）
