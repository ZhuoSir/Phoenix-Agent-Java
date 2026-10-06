# Changelog: visibility-filetree-hygiene

## 收口：12/12 完成 + BUG-85 翻已验证（2026-10-06）
- **T-11 E2E/回归矩阵**：`verify 13/13`、typecheck 213=基线、技能链 100/100、树（新/老会话）、越权 42031、平铺 code=100 n=112、下载 200/117B、MCP code=100、取消 `cancelled`（内容保留）、删除不复活、**真实多步长轮**产出 `icons/`(8)+`index.md` 并在树中按文件夹呈现
- **T-12 台账**：新增 `completion.md`（12/12 + 矩阵 + 需求兑现 + 偏差去向）与 `artifacts.md`；`releases/v1.7.0/MILESTONE.md` 记 M2 勾 + 审计记录；**BUG-85 → 已验证(v1.7.0)**
- 如实偏差：长轮实测约 4 分钟（非 ≥10 分钟，由用户真实长轮同类覆盖）；HITL confirm 未新验（无改动面）；超时/心跳粒度跟随 `TURN_FLUSH_SECONDS`（生产 5s）
- 试验残留：7 个 scratch 会话（含长轮/取消/超时/负对照）与旁路容器全清；**保留**长轮演示会话（`43312352-eb5e-4a94-8007-cc6972fb3188`，含 `icons/`+`index.md`）供用户走查，可随时删除
- 待发版翻账：`BL-11/12/15/28 → 已交付(v1.7.0)`

## 组 3 收口：T-07~T-10 全部实测通过（2026-10-05）
- **T-07 阶段标记**：实测 `MODEL → TOOL(execute) → 12s → IDLE → MODEL → IDLE`；两次实测修正（ToolCallEnd 不等于执行结束；`ToolResultStartEvent` 才是执行起点）
- **T-08 静默心跳**：SSE 实测 `silenceMs=18451/23452`（5s 节流）+ 标签按阶段正确 + 金丝雀 `heartbeats=N`
- **T-09 首帧超时**：阈值 1s → `status=model_timeout` 定稿；**负对照**（5s 阈值 + 工具 sleep 12s）**未误杀**；补上 `finish()` 文案条件（漏了新状态会留空气泡）
- **T-10 前端指示**：两面（前台 chat / run 页）在「正在执行（已用时 mm:ss）」后展示「· 阶段 · 已静默 Ns」；typecheck 213=基线；前端已部署（chunk 哈希一致）
- **旁路验证法**：用 `docker compose run --rm -d -p <host>:8066 -e <覆盖项> backend` 起**独立实例**验证（如首帧超时 1s），**不触碰生产配置**
- ⚠️ **部署事故（如实记录）**：我并发执行"旁路容器构建"与"守候部署构建"，两者同时写 `.stage/phoenix-admin.jar` → 镜像装进半截文件 → 生产 backend **crash-loop（Invalid or corrupt jarfile）约 1 分钟**（health 502）；已串行重建恢复（health 200），根因与纪律入 `lessons.md` **L-22**
- 粒度说明：超时/心跳的检查粒度跟随 `PHOENIX_AGENT_TURN_FLUSH_SECONDS`（生产默认 **5s**）→ 180s 实为 180~185s；已在 `config/changes.md` 口径内
- 试验残留已清：旁路容器全删、7 个 scratch 会话及其消息/文件行删除（残留 0）

## T-06 走查反馈修复：文件删除"复活"（BUG-87）+ 物化文件回落（2026-10-05）
- 用户反馈：「文件树没问题，但是删除文件又失败了」→ 现场取证（日志）：**删除本身成功**，但扫描器随后把磁盘上仍在的文件**重新登记**（同 store_key）→ 刷新后文件复活
- **真根因（两段）**：① 扫描器按 store_key 去重时只数 `del_flag=0`；② 把条件删掉也无效——`del_flag` 被框架按**逻辑删列**处理，QueryChain 生成 SQL 时**自动追加 `del_flag = 0`**（日志实证：`WHERE ("store_key" = ?) AND "del_flag" = ?`）
- **修复**：`existsByStoreKeyAnySession` 改**原生 SQL** `select count(*) from tbl_data_agent_file where store_key = ?`，绕开自动注入 → 墓碑优先生效
- **同时修我引入的回归**：`materialize`（报告「另存为文件」）行 `store_key` 为空 → 树里被跳过（平铺列表有）→ 补回落：以文件名落**会话根**，并保留内部件过滤
- **验证（硬证据）**：用户被删的 `harness-讲义.md`（该键 4 行全删）→ 连续 3 次带 `scan=true` 扫描**无新行**；去重 SQL 已是原生 1 参数形态；全局"复活键"清零；新文件仍正常登记（OCR 层 16 文件）；物化文件可见且删除后不复活；编译绿
- **复盘如实记**：我第一次的"修复生效"验证是**假阳性**（用物化文件做删除→扫描试验，而物化文件盘上落在 uploads tee、不在会话工作区，扫描器根本不会登记它）→ 教训入 `lessons.md` **L-21**（含"验证必须打在真实受控路径上"）
- 数据回填（用户删除意图优先）：4 组复活活行 `del_flag=1`（id 清单 `/tmp/bug87_ids.txt`，同谓词可回滚）

## T-06 收口：前端树 UI 实现并部署（2026-10-05，UI 走查移交用户）
- `agentFiles.ts`：新增 `getAgentFileTreeApi(sessionId, path, scan)` + `AgentFileTreeNode` / `AgentFileTreeLevel` 类型 + `HISTORY_PATH` 常量（与后端 `AgentFileService.HISTORY_PATH` 对齐）
- `ChatFilesPanel.vue`：改树形——面包屑（会话目录 / a / b，可点跳转）、返回上级、文件夹优先、**单层懒加载**（改 `path` 拉一层）、`FILES_CHANGED_EVENT` 刷新当前层、目录显示「N 个文件夹 · M 个文件」、历史节点显示「跨会话遗留 · N 项」；文件行预览/下载/删除与 temp 会话空态**保持不变**
- 质量门：`vue-tsc` **213 = 基线**（本 spec 文件 0 错误；过程中 1 条未用函数已删并复验）
- 部署：build → `.stage/dist` → `compose build nginx` → `up -d nginx`（health 200）；线上 chunk `js/ChatFilesPanel-C3cJj9Ah.js` 与本地 **sha256 一致**（b72fb31229d3eadd），关键字「会话目录/跨会话遗留/此文件夹为空」均在线上包内
- **UI 走查清单（移交用户，两个入口）**
  1. 前台 chat（`chenzhuo/12345678`）打开「梅西蓝白 logo」会话（`6e9c09e0`）→ 抽屉根层应直接见 `messi-personal-logo` 文件夹 + `harness-讲义.md`（**不应**再有纯数字文件夹）
  2. 点进 `messi-personal-logo` → 面包屑出现，子目录计数（deliverables 84 / concepts 10 / presentations 8 / tools 8 / renders 3）；点面包屑或返回箭头可回上级
  3. 文件行「预览 / 下载 / 删除」仍正常（删除后当前层自动刷新）
  4. 老会话（文件数多者，如 `3ccf0341`/`0690b2d0` 对应会话）→ 应见真实文件夹层级，而非单个「历史文件」
  5. 若有跨会话遗留行的会话 → 根层出现带「跨会话遗留」标注的「历史文件」节点，进入可见明细
  6. 发一条会产出文件的消息 → 回复结束后当前层自动出现新文件
  7. 后台 run 页（`admin/123456`）`/agent/33/run` → 同一抽屉组件，同样验证 1~5（两面一致）

## T-05 收口：v1.1.0 归属/折叠口径落地（2026-10-05）
- 用户重确认「确认」→ requirements/plan **v1.1.0 已确认（陈卓）**
- 实现落地：`SessionFileTree` 归属改**三态**（SESSION / OTHER_SESSION / NO_SESSION）；新增 `foldUserNamespace`（**循环折叠双层 uid**，展示层单一实现）；`AgentFileServiceImpl` 按「他会话→历史文件；无会话段→归本会话」归位 + 折叠 uid
- 复测（新部署 health 200）：新会话根层 = `messi-personal-logo`(113 文件) + `harness-讲义.md`（**uid 噪音消失**）；老会话出真目录（`3ccf0341`→chen-logo/logo-chen、`0690b2d0`→4 目录+1 文件、`c11a9ae7`→logo-chen）；逐层计数正确（deliverables 84/concepts 10/presentations 8/tools 8/renders 3）；越权 42031；594 行会话 **51ms/1017B**；隐藏件 20 层遍历 0 泄漏；夹具探针 **14/14 PASS**
- 遗留观察（不阻塞）：同一会话内 shell 产物（会话根）与 file 工具产物（原 `{uid}/` 下）折叠后可能同名并列（各带各的 row id，下载/删除按 id 不受影响）

## T-05 实现中实测发现 → 回改文档 v1.1.0（待重确认），暂停 T-05 收口（2026-10-05）
- 已实现并部署：`GET /api/agent/files/tree`（单层懒加载 + 目录计数 + 隐藏内部件 + 复用属主/管理员校验 + `scan` 补扫同源抽取为 `maybeScan`）；部署 19:41 health=200、0 轮误伤
- **实测暴露两处显示口径问题（非代码缺陷，是 v1.0.0 文档口径）**：
  1. 老会话塌成单节点：`3ccf0341`（712 行二代）根层 `dirs=0 files=0 history=712`；库里旧格式行 **2124/2317** → 绝大多数会话都会这样
  2. 新会话多一层 uid：`6e9c09e0`（114 行三代）根层 `dirs=1`，唯一节点名 **`461681072489615360`**，文件全在其内
- **按铁律 5 停编码回改**：requirements v1.0.0 → **v1.1.0 待重确认**（R-02.4 归位口径改为「无会话段→按行 `session_id` 归本会话；他会话段→「历史文件」」+ 新增 R-02.7 uid 层展示折叠）；plan v1.0.0 → **v1.1.0 待重确认**（归属表 + uid 口径 + 实测证据）
- 隔离不受影响：他会话行仍分离；不触存储/`store_key`/row id/下载删除口径
- 待用户重确认后：改实现（归属 + 折叠 uid）→ 复测三场景/越权/大目录 → 才勾 T-05

## T-04 完成：路径解析器 + 隐藏规则同源化（2026-10-05）
- 新增 `SessionWorkspaceFilters`（内部件判定**单一实现**：目录名单∪点开头 / 文件名单∪`call_*`∪点开头无扩展名∪内部后缀 / 会话 UUID 判据）
- 新增 `SessionFileTree`（三代 `store_key` → 会话内相对路径；**非本会话→历史**；解析失败不抛异常、保留文件名）
- `WorkspaceArtifactScanner` 改为**同源引用**：删除 5 个局部名单常量，`isInternal`/`otherSessionArtifact` 全部委托共享工具（`grep` 复核局部名单已零残留）
- **实现细化（探针发现，不改需求/验收）**：归属判定改为**以会话段为锚**（只看第 0/1 段是否等于本会话 ID），前缀（agentKey/显示名）不参与判定——BUG-78 类"入库前缀 ≠ 库中 sn"现场下不会把本会话文件误归历史；同时他会话 UUID 落在第 0/1 段一律归历史（隔离不放宽）。仅记入 changelog，不回改已确认 plan（非需求/设计缺陷）
- **夹具证据（可复现）**：`fixture/TreeProbe.java` 13 用例 → **PASS=13 FAIL=0**（三代各一 / uid 嵌套 / `.pylibs` 噪音 / `call_*` 占位 / 二代历史 / 一代历史 / 他会话隔离×2 / 无 size / 前缀不匹配 / 内部状态文件 / 内部后缀 / 空键不抛）
  复现命令：`java -cp phoenix-agent/phoenix-agent-core/target/classes specs/20261005_visibility-filetree-hygiene/fixture/TreeProbe.java`
- 编译：`mvn -pl phoenix-admin/phoenix-admin-manager -am package -DskipTests` 绿

## tasks v1.0.0 确认③通过——三重门全绿，进 Phase 4（2026-10-05）
- 用户口令「确认」→ tasks v0.1.0 → **v1.0.0 已确认（陈卓）**；三重门（requirements①/plan②/tasks③）全绿
- Phase 4 开工，首任务 **T-01**（清账组，零风险先行）；交付顺序：T-01~03 清账 → T-04~06 文件树 → T-07~10 静默可见性 → T-11~12 收口

## plan v1.0.0 确认②通过 + tasks v0.1.0 草稿（2026-10-05）
- 用户口令「确认」→ plan v0.1.0 → **v1.0.0 已确认（陈卓）**；三重门第②关过，进 Phase 3
- Q1~Q6 暂定口径随 plan 一并生效（记入 plan §九）
- Phase 3 拆解 **12 任务四组**：组1 清账 T-01~03（.gitignore/残留/死配置/台账销账）｜组2 文件树 T-04~06（解析器+隐藏规则同源 → 单层树接口 → 前端树 UI 两面共用）｜组3 静默可见性 T-07~10（阶段标记 → 心跳节流 → 首帧超时 → 两面指示）｜组4 收口 T-11~12（E2E 矩阵/台账）
- 覆盖核对 R-01(T-07~10)/R-02(T-04~06)/R-03(T-01~03)/R-04(T-11)，无孤儿无环；**零 DDL 零布局改动**；交付顺序=清账→树→静默→收口
- 状态：**待确认③**（未过门，仍未写任何生产代码）

## v1.0.0 确认①通过 + v1.7.0 双建（2026-10-05）
- 用户口令「确认」→ requirements v0.1.0 → **v1.0.0 已确认（陈卓）**；三重门第①关过，进 Phase 2
- Q1~Q6 未逐条答复 → 按 agent 建议口径暂定执行并在 requirements §七 记明，② 关口可纠
- **v1.7.0 双建**：version.md 新增 v1.7.0「在途」行（基点 main tip `456f8ab`，v1.6.0 已合 main 无悬空）+ `releases/v1.7.0/MILESTONE.md` + 分支 `v1.7.0` + 特性分支 `feature/visibility-filetree-hygiene`
- 待办挂载：BL-28 / BL-11 / BL-12 / BL-15 → 已立项(v1.7.0)；BUG-85 → 已规划(v1.7.0)；§四 agent-config 销账随本 spec R-03

## v0.1.0（2026-10-05）立项草稿
- 用户口令：「BUG-85 和 BL-28，还有 BL-12/15/11、§四 两条过期注记 + agent-config 历史快照销账，**列为一个 spec，新建**」
- 挂载：**v1.7.0**（v1.6.0 已于同日发布 tag `v1.6.0` 并 `--no-ff` 合入 main；在途唯一 ⇒ 待立项双建 v1.7.0）
- 范围三组：R-01 长轮静默可见性 + 模型调用超时（BUG-85）｜R-02 会话文件文件夹树（BL-28）｜R-03 工程清账 + 台账销账（BL-11/12/15 + §四）
- 现状取证（全部实测，非推测）：
  - **BUG-85**：会话 `6e9c09e0` 实测单次模型调用静默 **503 秒**（17:39:34.717 发起 → 17:47:57.666 才有下一事件），其间无 End/无错误/无重试；看门狗 600s 未触发（503<600）＋总时长闸=0 ⇒ 设计上不杀；该轮自行恢复并 done。金丝雀同轮 `frames=179917/emitted=5481/dropped=47279`（合并闸 33:1，前端未被压垮；`dropped` 为设计内空帧丢弃）。
  - **BL-28**：`listBySession` 平铺查库（VO 无目录信息）；目录层次可从 `store_key` 的会话内相对路径还原；`rel_path` 为 tee 布局不可展示。
  - **清账**：未跟踪噪声 `.mvn-home/`、`.pnpm-store/`、`AGENTS.md.bak.20260927101837`、`diagrams/`、`scripts/`、`WSL`(0B)、`或在`(0B)；`PhoenixAgentProperties.skillPath` **零引用确证**（`getSkillPath` 全仓 0 命中）；`RulesHarnessAgent.java` 已无未提交改动（旧注记过期可销）；backlog §四 的 agent-config 条目「8/13 已勾」**实测为 13/13 全勾**（注记写错）。
- 开放问题 6 条（Q1~Q6）随 requirements 提出，**plan 前需用户裁决**（其中 Q3 空文件夹、Q5 残留去向影响范围）
- 状态：**待确认①**（未过门，未写任何生产代码、未建版本双建）
