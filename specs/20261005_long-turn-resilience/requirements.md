# Requirements: 长轮次可靠性（long-turn-resilience）

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05
> v0.2.0→v1.0.0：用户指令「最好直接参考 DSH」→ 设计蓝本锚定 DSH 源码（A-6）；Q1/Q2 按 DSH 数值决议（600s / 不设总时长）；R-01/R-02/R-05 验收对齐 DSH 方法论；用户「确认」过门
> v0.1.0→v0.2.0：用户两点纠正落地——①"前端卡死"实为**浏览器整体无响应**（主线程阻塞，R-02 重写）②"服务重启提示"复发实为**重进旧会话展示旧轮定稿文案**（后端 RestartCount=0 无幻影重启，模型未感知重启——R-03 增补误导态条款）
> 挂载: v1.6.0（用户明示「使用spec来修改这个bug，基于这个版本」；BUG-70/71 立项，不冻结续收第四 spec）

## 背景与目标

**现场取证（2026-10-05，用户 logo 设计实测，agent-33 会话 14264bfe/0690b2d0 活体）**：
- 问题①刷新后"think done 无正文"：join 追流渲染断链或死轮单 end 帧+旧快照误导（BUG-71；bufferFrames=2000 已排除缓冲溢出）
- 问题⑤"服务重启提示复发"（用户纠正后查实）：**无幻影重启**（RestartCount=0/OOMKilled=false/清扫仅 @PostConstruct 一次）——12:47 用户重进 12:23 旧会话，页面回显该会话旧轮的"⚠️ 服务重启"定稿文案，误读为新尝试又失败；模型侧确实感知不到重启（用户判断正确）。归入 R-03 误导态治理
- 问题②浏览器无响应（用户纠正：非"没渲染"而是**整页卡死**）：服务端在正常工作（turn/status=true、model-call 连发），**前端渲染管线主线程阻塞**——实勘 api-transport.ts：每 150ms 对**全量累积文本**做 markdownToHtml+整树重渲，长轮数千增量帧下 O(n²) 压垮主线程（BUG-70）；admin run 页同类管线 plan 期一并钉死
- 问题③"执行一半中断"主因：**turn-timeout-seconds=600 看门狗按总时长强杀**，logo 类多轮工具任务必超（调大 max_iterations 无效——卡的是轮时长不是轮数）
- 问题④用户问"上下文大小在哪设置"：**现无此配置**——runtime_config 18 列无上下文/压缩项，框架 CompactionConfig（triggerTokens/keepMessages 等全可调）被写死默认（triggerMessages=50/keepMessages=20/truncateArgs 2000）

**目标**：长任务轮（logo 设计/深度分析类）可靠跑完、过程可见、刷新可续、中断有据；上下文治理参数可配置。

**成功画面**：用户发起 logo 设计 → 前端持续显示思考/工具轨迹与"正在执行+已用时"→ 中途刷新无损续看 → 20 分钟长轮自然跑完出图 → 若真挂起/出错，正文里写明原因。

## 需求条款（EARS）

### R-01 活性感知看门狗（问题③主刀）
轮次看门狗 SHALL 区分「活跃」与「挂起」：轮内持续有事件活动（模型/工具/文本帧）时不因总时长强杀；仅当**连续无活动超过挂起阈值**（可配置，**默认 600s**——对齐 DSH 工具等待上限，Q1 决议）才判定挂起并定稿；**总时长默认不设强杀**（Q2 决议，DSH 轮次层无墙钟强杀；env `PHOENIX_AGENT_TURN_TIMEOUT_SECONDS` 保留为可选兜底，0=关闭=新默认）。看门狗语义对标 DSH idleWatchdog：**每帧到达即重置计时**（arm），到期只定稿展示层、不强杀工作进程（promoteOnTimeout 哲学）。

**验收场景**
- GIVEN logo 长轮持续产帧 25 分钟 WHEN 运行 THEN 不被腰斩，自然 done。
- GIVEN 轮次真挂起（无任何帧 > 挂起阈值）WHEN 到阈 THEN 定稿 timeout 且正文标注。

### R-02 长轮浏览器不卡死（问题②主刀，v0.2.0 重写）
前端流渲染管线 SHALL 增量化：禁止对全量累积文本周期性整体重解析/重渲（现 markdownToHtml(textBuf) 全量重渲为实勘病灶）；长轮（数千增量帧/大工具输出）全程浏览器主线程 SHALL 保持可响应（可滚动、可切页、可点停止）；运行中 SHALL 有持续可见进度（增量正文/思考/工具轨迹，或"正在执行（已用时 mm:ss）"活性指示兜底）。渲染策略（增量 append/尾块重渲/节流升档/Worker 化）plan 期定案，admin run 页与前台 transport 两条管线都覆盖（L-06）。

**验收场景**
- GIVEN logo 长轮运行 20 分钟 WHEN 观察浏览器 THEN 页面全程可交互（实测：滚动/切会话/停止按钮响应 < 500ms），无"无响应"弹窗。
- GIVEN 长轮运行中 WHEN 任意 30 秒窗口 THEN 有可见进度变化。
- 验收方法论对标 DSH `reasoning-chunks.stress`：高频 chunk 风暴下测交互延迟（批量消费+思考折叠+增量渲染三件套齐上）。

### R-03 刷新追流与重进会话正确渲染（问题①⑤主刀，BUG-71，v0.2.0 增补）
WHEN 用户刷新/重进运行中会话, 系统 SHALL replay 缓冲帧并接续 live 渲染（思考与正文均可见、滚动至尾）；WHEN 轮次已定稿或已死（重启清扫/超时）, SHALL 显示定稿全文+状态标注，不得出现"think done 但无正文"的误导态；历史轮的中断标注 SHALL 明确归属（如"上一轮于 HH:MM 被中断"语义），不得让用户误读为新尝试失败。

**验收场景**
- GIVEN 长轮运行中 WHEN 刷新页面 THEN 数秒内续见流式输出直至完成。
- GIVEN 轮次已被重启清扫 WHEN 重进会话 THEN 见带时间归属的中断定稿文本而非空白/think done。
- GIVEN 用户重进含历史中断轮的旧会话并发起新轮 WHEN 新轮运行 THEN 旧轮中断文案明确是历史记录，不与新轮输出混淆。

### R-04 中断原因显性化
所有非自然完成的定稿（挂起超时/总时长超时/服务重启/模型流错误/手动取消）SHALL 在正文尾部标注可读原因（现有超时与重启文案保留，补模型错误类），前端如实渲染。

**验收场景**
- GIVEN 模型流中途报错 WHEN 定稿 THEN 正文含"⚠️ 模型/流错误"类标注而非无声截断。

### R-05 上下文治理可配置（问题④答复+落地）
运行配置 SHALL 新增上下文治理项并透出编辑 UI：**压缩触发阈值**（triggerTokens≈"上下文大小"水位，及/或 triggerMessages）、**保留窗口**（keepMessages/keepTokens 择要）、**工具结果截断长度**（truncateArgs maxArgLength）；缺省=现行为（50/20/2000），变更经指纹重建生效；Flyway 加列（本版唯一 DDL）。

**验收场景**
- GIVEN 某智能体调低 triggerMessages=10 WHEN 长对话 THEN 压缩提前触发（日志可观测），其余智能体不受影响。

### R-06 对面零变化
短轮/普通对话/confirm 续跑/MCP 挂载链/技能链行为 SHALL 零变化（回归矩阵+金丝雀对照）。

**验收场景**
- GIVEN 部署新版本 WHEN 常规短对话与 MCP 回环抽查 THEN 行为与旧版一致，金丝雀行 frames/textFrames 比例正常。

## Non-goals（范围外）
- 前端 UI 改版（只做进度可见与 join 渲染修复，不动视觉/交互框架）
- 轮次跨进程分布式执行（单实例语义不变）
- 模型供应商上下文窗口上限（provider 硬限非我方可配）
- BUG-69 复盘（已留存待复盘，金丝雀在位）

## 我正在做的假设
- **A-1** 看门狗改造在 TurnManager janitor：onFrame 记 lastActivityAt，判据从"总时长"改"无活动时长+总上限"双闸
- **A-2** CompactionConfig 全参数框架可调（javap 实证 triggerTokens/keepMessages/keepTokens/Ratio/reserved 俱在），R-05 纯接线+DDL+UI
- **A-3** R-02 断点大概率在 UI 渲染侧（服务端帧发出有金丝雀佐证），plan 期以浏览器实测钉死，若在服务端则扩围
- **A-4** runtime_config 加列走 V1.6.0_03 Flyway 件（M3 汇总入 releases/v1.6.0/sql）
- **A-5** 部署纪律：本 spec 各次部署避开用户实测时段（L-16），部署前查 turn/status 无活跃轮
- **A-6 设计蓝本=DSH 直接对标**（用户指令「最好直接参考他的」，源码出处入档）：
  - 看门狗：`packages/util/timeout` idleWatchdog（每事件 arm 重置/空闲才 abort）+ 流层 300s + 工具等待 120s/600s + `promoteOnTimeout=true`（到期转后台不杀）
  - 轮次层：**DSH 无总时长强杀**（约束=轮数/令牌预算，对应我们 max_iterations）
  - 渲染：`apps/web/stress-tests/reasoning-chunks.stress.ts` 方法论（10 万 chunk/128条16ms 批量/事件循环与交互延迟指标/Think 行折叠不逐帧全文重渲）
  - 上下文治理：`packages/compaction` 比例水位制——thresholdRatio=0.8 / retainRatio=0.16 / tool-result-pruner 8192·4096·1024；与我们框架 CompactionConfig(triggerTokens/keepTokensRatio/truncateArgs) 逐位对应，按 DSH 语义透出

**现在纠正，否则按此执行。**

## 决议记录（Q 区关闭，2026-10-05）
- **Q1 挂起阈值** → **600s**（用户裁决「180s 还是太低」+ 指令对标 DSH——DSH 工具等待上限 600s，工具静默期合法上限即此）
- **Q2 总时长上限** → **不设**（默认 0=关闭，env 兜底可配；DSH 轮次层无墙钟强杀，防跑飞=活性检测+轮数预算）
