# Changelog: long-turn-resilience

## T-04 五刀（2026-10-05）BUG-75 双输出窗口（用户实测，BUG-73 副作用）
- 用户原话：「刷新后，任务正常执行，但是下方会多一个输出窗口，上面有个，下面还有个」
- 根因（自省）：BUG-73 用 `isStreaming=true` 表达"轮次在跑"，而直播区渲染条件同为 `v-if="isStreaming || nodeBlocks.length>0"` → 刷新后除已加载的服务端行外，**凭空多渲染一个空直播窗口**
- 修复：拆双状态——`remoteRunning`（轮询态：只控输入禁用 + 终止按钮）与 `isStreaming`（本页真直播：驱动直播区渲染）；顺带补齐轮询态终止语义（`harnessTurnCancelApi` 服务端取消，原先只认本地 closeStream → 点了无反应）
- 教训入账：**状态语义单一职责**——新增布尔态前先核清它的全部消费点，避免"一个标志两种含义"
- 证据：typecheck 213=基线；新 run 分片已上线；容器内 `turn/cancel` 实证；首页 200

## T-04 四刀（2026-10-05）BUG-74 跨会话串消息（用户实测，高危）
- 用户原话：「当前任务执行中，我新建个会话，就会把执行中的这个会话的当前的内容显示在新建的会话里，这个不对，不同会话肯定是不能消息串通的」
- 根因：detached-stream T-05 轮询闭包**无会话守卫**——`tick` 无条件执行 `currentMessages.value = applyServerRowRender(旧会话消息)`；切会话/新建会话后旧 tick 仍在跑 → 直接把旧会话内容写进新会话视图；BUG-73 新增的 `isStreaming=true` 同为全局无条件（会顺带把新会话标成进行中）
- 修复：闭包入口 + 每 tick 拉取前后 + 结束收敛处共**三处会话守卫**（`currentSession.id === session.id`）；一旦切走立即终止轮询、不再触碰任何视图态 → 会话内容严格隔离
- 证据：typecheck 213=基线；新 run 分片 `run-BopHG054.js` 上线容器；首页 200；行为面待用户走查（L-07）
- 若仍串：下一嫌疑=直播流回调写视图（现有 16 处守卫覆盖 currentMessages/nodeBlocks，需逐点核）+ 将加临时会话标签埋点定位

## T-04 三刀（2026-10-05）BUG-73 刷新后进行中态丢失（用户实测）
- 用户原话：「刷新后，任务还在进行中，输入窗口的可编辑且可发送了…应该是没执行完的时候不可编辑，但是可以终止，现在终止按钮变成了发送按钮」
- 根因：detached-stream T-05 轮询只刷新消息（currentMessages），**从不置 isStreaming** → 刷新后按空闲态渲染：textarea `:disabled="isStreaming"` 为假、发送按钮 `v-if="!isStreaming"` 显示、终止按钮隐藏
- 修复：turnStatus 为真即 `isStreaming=true`（输入禁用+终止按钮恢复）；tick 检测到轮次结束置 false 并做末次拉取收敛终稿；异常路径同样收敛防卡死
- 证据：typecheck 213=基线；新 run 分片 `run-BmwtWO69.js` 已上线容器；首页 200；**行为面待用户走查（L-07）**
- 运行页刷新态三连修齐：BUG-71家族(Think Done 误判)+BUG-72(思考块消失)+BUG-73(进行中态丢失)

## T-04 二刀（2026-10-05）BUG-72 思考块自行消失（用户实测）
- 用户原话：「think窗口有时候自己会消失，刷新后就有，过几秒钟又消失」
- 根因（实证定位）：运行页刷新首载路径会解析 `metadata.thinking` 写入消息（故可见），但 **5s 轮询 tick 只调用 `applyServerRowRender`**——该函数只处理 content/streaming，**不解析 thinking** → tick 覆盖消息即丢 thinking 字段 → 模板 `v-if="message.thinking"` 为假 → 思考块卸载消失
- 修复：metadata 解析（thinking/thinkingMs/streaming）**统一收进 `applyServerRowRender`**，首载与轮询同源，杜绝两路不一致；BUG-72 已入册并翻「已验证」
- 证据：typecheck 213=基线；新 run 分片 `run-B02KaC87.js`；容器内 `thinking===\`string\`&&(n.thinking=...` 实证；首页 200
- 遗留：**T-03b**（性能三刀需在运行页重做——此前打偏到前台聊天页；运行页才是用户实际界面，见同日 L-17 修正）

## T-04 首刀（2026-10-05）刷新后"Think Done"误判修复（用户实测）
- 用户原话：「还有个问题，明明是thinking ，刷新后就think done了」
- 根因（实证）：刷新走 `loadMessages` → `transport.listMessages` → `toStoreMessage` **从不设置 streaming 标记**；行 metadata 虽有 `status=generating`，但 UI 只看 `msg.streaming` → ThinkingBlock 标签 `streaming ? 'Thinking…' : 'Think Done'` 判成完成态
- 修复：`toStoreMessage` 解析 metadata，`status==='generating'` → `streaming:true`（类型已有 `streaming?: boolean`，共享包无需改）；与 join 追流回调的 `streaming:true` 语义一致，轮次结束后重载自然归位
- 证据：typecheck 213=基线；容器内分片含状态判定（上下文实证）；首页 200
- 归属：T-04（join/重进正确渲染，R-03）首刀；T-04 剩余=「刷新无正文」断点复现 + 历史中断文案归属

## T-03 附加（2026-10-05）流式贴底跟随（用户实测反馈）
- 用户原话：「thinking那个滚动条能不能到底自动滚动，现在需要手动下拉」
- 实现两处**贴底跟随 + 尊重手动上翻**：①思考区 ThinkingBlock（`max-height:180px` 内滚动，原无任何跟随）②消息区 ChatMessages（原只在消息数组变化时滚动，正文/思考增长不跟随）
- 规则：距底 ≤24px/≤40px 视为贴底持续跟随；用户上翻即暂停跟随，滚回底部自动恢复；新一轮/换会话强制跳底
- 证据：typecheck 213=基线；容器内实证 `clientHeight<=40`（ChatMessages 分片）/`clientHeight<=24`（思考区）+ 首页 200
- 归属：R-02「持续可见进度」子项（流式可读性），非新增需求

## T-03 进行中（2026-10-05）活页面三刀已落 + 一条自纠
- **自纠（L-17 新坑）**：首刀误改 `components/run/index.vue`（全仓零引用死组件）——改前未做 import 链核验；已 `git checkout` 还原，零残留
- 活页面真身：`views/front/chat.vue` → `views/front/api-transport.ts`（帧消费/节流）+ `views/front/components/ChatMessages.vue`（渲染），核心状态在共享包 `packages/chat-shared`（四面共用根因）
- 三刀落活页面：①**增量 markdown**（完成块冻结/仅重解析尾块/围栏奇偶守护，替代 BUG-61 时代"每推全量解析"）②**大缓冲自适应节流**（>20k 字 400ms，抑制 DOM 整体替换频率）③**长轮活性指示**（流式中每秒刷新"正在执行（已用时 mm:ss）"，R-02 兜底）
- 证据：typecheck 213=基线零回归；产物与**容器内**（js+css）均含标记实证；nginx 已 `compose build`+recreate（L-03）
- 待办：浏览器实测（需用户走查长轮不卡死）；若仍卡 → **T-03b 追加块级 DOM 增量（v-for 分块，只补尾块节点）**

## T-02 完成（2026-10-05）上下文治理三列贯通三证
- 交付：V1.6.0_03 三列 DDL+rollback；实体/DTO/VO/Service 四层贯通；**UpdateChain 强写三列**（复用既有 maxIterations「留空=清除」先例机制）；factory compactionFor/toolResultEvictionFor 两级回退（DSH 换算默认 102400/20/8192）+ 指纹显式纳入
- 三证：①全新库重放（台账 12/三列在/rollback 零残留）②配置往返（写 5000/5/1000 → DB/GET 一致；留空 → 三列全 null 回默认，**首测踩坑即修**：flex update 忽略 null，须走 UpdateChain 定向强写——既有先例模式复用）③**指纹重建实证**（改配置→「运行配置已变更，重建实例」日志+新旧指纹可见 null 位）
- 纪律：部署前查活跃轮=0（L-16）；两批夹具（99913/99914/99915）全清；verify=0

## T-01 完成（2026-10-05）看门狗活性化三证+意外活体战果
- 前置：分支叠基 workspace-isolation（TurnManager 同文件共享面仲裁，M4 先 isolation 后本支平滑）；compose env 两件（TURN_TIMEOUT 默认 0=关+TURN_IDLE 默认 600）
- 交付：Turn.lastActivityAt 每帧脉冲（DSH arm 语义）；janitor 双闸换轨（空闲>600s 判挂起；总时长默认关）；文案五分类就位（挂起/总时长/重启既有/模型流错误/取消既有）
- 三证（idle=30s 临时加速实测后恢复默认）：①**黑洞模型挂起轮**（host 黑洞 stub+夹具 agent99912/model999）30s 定稿+「⚠️ 轮次挂起：连续 30 秒无任何模型/工具活动」✓ ②**意外活体**：短轮测试撞上 DeepSeek 真实流停滞（7帧后无终号）——空闲闸当场优雅定稿内容保留（用户历史"执行一半中断"的另一真凶现形：旧闸下要挂满600s）✓ ③正常短轮 done 不误杀 ✓
- 部署纪律执行（L-16）：部署前查 generating=0；夹具全清（agent/model/runtime/会话/黑洞stub/workspace目录）；verify=0

## tasks v1.0.0（2026-10-05）—— 确认③通过，三重门全绿
- 用户「确认」；tasks v0.1.0→v1.0.0 已确认（陈卓）；三重门自检后切 feature/long-turn-resilience（基点 v1.6.0），进入 Phase 4，首任务=T-01

## tasks v0.1.0（2026-10-05）
- Phase 3 拆解：T-01~T-08 三组（后端/前端渲染/收口）；看门狗假死模拟验证、Flyway 重放、DSH stress 风暴灌帧、join 三场景、四面枚举收口、logo 真跑 20 分钟一锤定音；R 全覆盖自检无孤儿

## plan v1.0.0（2026-10-05）—— 确认②通过
- 用户「确认」；plan v0.1.0→v1.0.0 已确认（陈卓）；进入 Phase 3 Tasks

## plan v0.1.0（2026-10-05）
- Phase 2 落盘（DSH 对标设计）：看门狗=TurnManager 单点活性化（onFrame 脉冲 arm+600s 空闲闸+总时长默认关+文案分原因）；渲染=DSH 三件套（批量消费/快照 1s 降频+beforeunload 兜底/尾块增量+完成块冻结）覆盖四面（admin live 零节流实勘/admin join 5s 轮询/front transport 150ms 全量 markdown/pc+mobile 枚举后同治）；join"think done 无正文"列复现定位任务；上下文=V1.6.0_03 三列 nullable+全局 env 默认按 DSH 换算（102400≈0.8×128k、pruner 8192）+factory 两级回退+指纹白拿；被否案 4（含 Worker 化与比例制列 BL 候补）；坑核对 16 条（L-06 四面枚举/L-07 用户可见面验收/L-16 部署避让）

## v1.0.0（2026-10-05）—— 确认①通过（DSH 对标版）
- 用户「确认」；前置修订入版：Q1=600s（DSH 工具等待上限）/Q2=不设总时长（DSH 轮次层无墙钟强杀）；**A-6 设计蓝本=DSH 直接对标**（idleWatchdog arm 语义/promoteOnTimeout/reasoning-chunks.stress 渲染方法论/compaction 比例水位 0.8·0.16·8192-4096-1024，源码出处全入档）；R-01 看门狗语义/R-02 验收/R-05 参数语义对齐 DSH
- 过程注记：首次落盘锚点默写偏差（漏"logo"二字）被 assert 拦截零污染，读实文重做（L-01 v2 纪律生效）
- 进 Phase 2 Plan

## v0.2.0（2026-10-05）—— 用户两点纠正 + 取证修正
- 用户纠正①：前端非"没渲染"而是**浏览器整体无响应** → 实勘 api-transport.ts 病灶：每 150ms 全量 markdownToHtml(textBuf)+整树重渲，长轮 O(n²) 压垮主线程；R-02 重写为"长轮浏览器不卡死"（增量化渲染+可交互性验收，admin/前台双管线 L-06 覆盖）
- 用户纠正②：停止金丝雀部署后重试仍见"服务重启" → 查证：后端 RestartCount=0/OOMKilled=false/清扫仅启动一次，**无幻影重启**；12:47 匹配"服务重启"的行实为只读查询 Row dump（我误读为 UPDATE Parameters）；真相=用户重进 12:23 旧会话回显旧轮定稿文案；R-03 增补"历史中断标注归属，不得误读为新失败"
- 排障自纠两则如实记：①"saveMessageApi 双写复活"猜错（harness 拦截在位，BL-22 退役有效）②"神秘 UPDATE"猜错（Row dump 误读）——均经日志/代码复核证伪后才落笔
- BUG-70/71 备注栏同步更新（前端病灶/误会查实）

## v0.1.0（2026-10-05）
- 初始化创建（用户口令「使用spec来修改这个bug，基于这个版本」；BUG-70/71 立项 + 用户问题③④收编为 R-01/R-05）
- 挂载: v1.6.0（用户明示基于当前版本，不冻结续收第四 spec）
- 现场取证入档：卡死轮实为活体（turn/status=true+model-call 连发）；600s 看门狗=腰斩主因；上下文配置确认不存在（CompactionConfig 写死默认，框架 API 全可调 javap 实证）；bufferFrames=2000 排除缓冲溢出
- requirements v0.1.0：R-01 活性看门狗/R-02 进度可见/R-03 join 渲染/R-04 中断显性化/R-05 上下文可配置(本版唯一 DDL)/R-06 对面零变化；Q1 挂起阈值/Q2 总上限待裁决
- 同批台账：BUG-70(高)/BUG-71(中) 入册；L-16 新坑（用户实测期间禁止静默部署——12:42 部署打断用户 12:22 轮次实证）
