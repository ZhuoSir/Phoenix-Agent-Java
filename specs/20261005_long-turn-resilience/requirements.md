# Requirements: 长轮次可靠性（long-turn-resilience）

> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-05
> 挂载: v1.6.0（用户明示「使用spec来修改这个bug，基于这个版本」；BUG-70/71 立项，不冻结续收第四 spec）

## 背景与目标

**现场取证（2026-10-05，用户 logo 设计实测，agent-33 会话 14264bfe/0690b2d0 活体）**：
- 问题①刷新后"think done 无正文"：join 追流渲染断链或死轮（部署重启清扫）单 end 帧+旧快照误导（BUG-71；bufferFrames=2000 已排除缓冲溢出）
- 问题②前端"卡死"：**服务端根本没卡**——turn/status=true、model-call 连发实证 logo 长轮在持续工作，UI 不渲染进度造成假死观感（BUG-70）
- 问题③"执行一半中断"主因：**turn-timeout-seconds=600 看门狗按总时长强杀**，logo 类多轮工具任务必超（调大 max_iterations 无效——卡的是轮时长不是轮数）
- 问题④用户问"上下文大小在哪设置"：**现无此配置**——runtime_config 18 列无上下文/压缩项，框架 CompactionConfig（triggerTokens/keepMessages 等全可调）被写死默认（triggerMessages=50/keepMessages=20/truncateArgs 2000）

**目标**：长任务轮（logo 设计/深度分析类）可靠跑完、过程可见、刷新可续、中断有据；上下文治理参数可配置。

**成功画面**：用户发起 logo 设计 → 前端持续显示思考/工具轨迹与"正在执行+已用时"→ 中途刷新无损续看 → 20 分钟长轮自然跑完出图 → 若真挂起/出错，正文里写明原因。

## 需求条款（EARS）

### R-01 活性感知看门狗（问题③主刀）
轮次看门狗 SHALL 区分「活跃」与「挂起」：轮内持续有事件活动（模型/工具/文本帧）时不因总时长强杀；仅当**连续无活动超过挂起阈值**（可配置，默认建议 180s，Q1）才判定挂起并定稿；总时长上限 SHALL 放宽且可配置（默认建议 3600s，Q2），env `PHOENIX_AGENT_TURN_TIMEOUT_SECONDS` 语义保留。

**验收场景**
- GIVEN logo 长轮持续产帧 25 分钟 WHEN 运行 THEN 不被腰斩，自然 done。
- GIVEN 轮次真挂起（无任何帧 > 挂起阈值）WHEN 到阈 THEN 定稿 timeout 且正文标注。

### R-02 长轮进度可见（问题②主刀，前端假死根治）
运行中的轮次 UI SHALL 持续可见进度：思考增量/工具调用轨迹正常渲染；WHEN 超过一定间隔无新内容帧, 前端 SHALL 显示"正在执行（已用时 mm:ss）"活性指示（断点定位 plan 期做：SSE 帧发出侧 vs UI 渲染侧，以金丝雀+浏览器实测钉死）。

**验收场景**
- GIVEN logo 长轮运行中 WHEN 观察前端 THEN 任意 30 秒窗口内都有可见变化（轨迹/文本/活性指示之一）。

### R-03 刷新追流正确渲染（问题①主刀，BUG-71）
WHEN 用户刷新/重进运行中会话, 系统 SHALL replay 缓冲帧并接续 live 渲染（思考与正文均可见、滚动至尾）；WHEN 轮次已定稿或已死（重启清扫）, SHALL 显示定稿全文+状态标注（done/timeout/重启中断），不得出现"think done 但无正文"的误导态。

**验收场景**
- GIVEN 长轮运行中 WHEN 刷新页面 THEN 数秒内续见流式输出直至完成。
- GIVEN 轮次已被重启清扫 WHEN 重进会话 THEN 见"⚠️ 服务重启…保留至最后增量"定稿文本而非空白/think done。

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

**现在纠正，否则按此执行。**

## 待确认问题（阻塞项，确认①前请裁决）
- **Q1 挂起阈值**：连续无活动多久判挂起强杀？建议 **180s**（模型单调用最长约 60-90s，180s 无帧=真死）
- **Q2 总时长上限**：建议 **3600s（1小时）**保留兜底防真跑飞；或完全去掉只靠活性检测？
