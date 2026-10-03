# Changelog: detached-stream

## Implement T-01（2026-10-02）A′ 一期上线
- stash 原型复活；修复原型遗留：send 签名被插坏（语法错，构建期才暴露）、readStreamSnapshot 漏 import（esbuild 不查→tsc 抓到）
- P7 守卫双端预埋（尾行 generating→快照让位），B 上线自动生效
- build=0，TS=215 基线，前端 rc10 部署 verify 全绿；浏览器刷新生效验证属用户实操（AC 剧本见 tasks）

## Implement T-02~T-05（2026-10-02）B 主体落地，六演练全绿
- HarnessTurnManager（自持订阅/replay sink 帧闸/watchdog/5s flusher/单行 upsert/HITL end 抑制并轮）+ 双域 join/cancel/status 端点 + 前端自动追流与真停止 + 前端保存退役（harness 路径）+ admin 5s 轮询
- compose 三配置键出厂 600s/5s/2000；verify 新增 [14]（12 断言）
- **六演练**：断后 continue 至 done·join 225帧+end·cancel 行 cancelled·P6 拒绝文案·断线轮文件照常登记(R-04 铁证)·20s 超时定稿标注；乱 token 401
- 过程自纠：docker PATH 断链×2、graph.ts 锚点 async 失配假成功、启动清扫 jsonb 语法重写、metadata 伪 generating 行辨析
- 待办：AC-04 HITL 断线确认回归 + T-01/T-05 浏览器实操（用户）；T-06 收口未勾

## 合并（2026-10-02）
- 用户「合并吧」→ feature/detached-stream 10 提交（T-01~T-06+三修复+补漏）并入 main；v1.4.0 首单入账；不发版

## 验收期修复补记（2026-10-03 早）
- admin 双泡根除：saveNodeMessage 按 agent.type 分流，harness 客户端保存退役（R-05 收口，graph 流保留）；存量 6 行重复 html 行清理（同会话有 turnId 行才删，报告类型无涉）
- admin 气泡合并卡：ThinkingBlock 从 v-for 根部移入 .message-content（think 上/回复下/同一气泡列对齐），content 纵排 gap 8px，thinking 灰卡+蓝竖线 vs 白底回复泡样式区分保留
- BUG-57 全链终验（用户实测「有样式了」）：裸 div 分支气泡化 + :deep 规则入 scoped 块（全局块中 :deep 为非法选择器——教训入册）

## BUG-61 追流重放风暴（2026-10-03）
- 用户浏览器实测「刷新中途卡死」：服务端 done 无恙，前端 join 重放千帧×全量渲染饱和主线程——150ms 节流合并修复（P2 追流性能条款落实）；死会话 pending_confirm 键×2 清理

## BUG-60 文件面板复用不可见（2026-10-03）
- 用户追证推翻我初判（"复用旧文件属正常"）：模型本轮确实覆写落盘，真身=全局 storeKey 去重吞掉跨会话重现 + create_time 95/95 全空致窗口失效灌洪
- 修复四件套：会话维度去重 / 窗口=会话创建时间(缺失跳过) / createSession 补时间戳 / 存量回填+灌洪 18 条清理（含物理副本）
- 终验：用户会话 scan 后恰好 3 文件、旧会话不受扰

## BUG-59 根治记（2026-10-03）
- 用户实测「计划确认后正式回复没了（3 次仅 1 次有内容）」→ 字节码取证：框架 maybePatchPendingToolCalls(offset46) 先于 applyConfirmResults(offset153)，plan_exit 无 ASKING 豁免被自动置错；且确认链路从未调 exitPlanMode → 模型困在 plan 阶段
- 修复：confirmStream 批准分支显式 harnessAgent.exitPlanMode(RuntimeContext)（公开 API）+ 续跑文案断言「已退出计划模式，立即执行」
- 验证：2/2 全绿（确认帧→放行→done 正文 171/336 字→产物 2 文件→exitPlanMode 日志×2）；此前成功率 1/3
- 演练脚本教训：confirm POST 的 curl -m 3 掐太早请求未达控制器（-m 15 正常），非链路问题

## Implement 收口后缺陷（2026-10-02）BUG-57
- 落库所有权移交的连锁反应：历史装载的旧 HTML 启发式对新 raw-markdown 行误伤（含标签即跳过转译）→ 双端样式偶发丢失；按「服务端行无条件转译、旧行保留启发式」修复
- 教训入册：持久化内容形态变更（HTML→markdown）必须同批清点所有读取端启发式

## Implement T-06（2026-10-02）AC 全绿收口
- 用户浏览器复验 AC-01（修复双气泡后）/AC-03 通过；服务端演练 AC-02/04/05/06 与 P6 全过（留证 artifacts）
- 收口补洞：admin confirm 流 thinking 通道对齐；演练脚本 confirm 缺 agentId 勘正记录
- BL-22 与 BUG-53 台账同步关闭（A′ 保留为秒级窗口兜底口径注记）

## v1.0.0 tasks（2026-10-02）确认人: 陈卓
- **第③关通过（三重确认门全绿）**→ Implement 启动：分支 feature/detached-stream

## Tasks v0.1.0（2026-10-02）草稿
- 两期六任务：T-01 A′ 先行（按 P7 改造）→ T-02 TurnManager → T-03 双域端点(+join/cancel/confirm 并轮) → T-04 落库所有权移交（单行状态流转）→ T-05 前端追流/真停止/P6 提示 → T-06 六 AC 回归 + BL-22/BUG-53 双单关账
- 超时/帧闸走配置键（出厂 600s/2000，演练短值）；admin 历史缺口顺带闭合如实记
- 注：本条目与下条曾于一次落章脚本崩溃中丢失（replace 静默未命中），此为真实补录

## v1.0.0 plan（2026-10-02）确认人: 陈卓
- **第②关通过**（用户「确认方案」，P1~P8/两专拍/四风险全接受）→ Phase 3

## Plan v0.2.0（2026-10-02）两处专拍入册
- A-01 修正经用户拍板采纳：轮次缓冲=进程内 Sinks（Redis 预铺否决，真多实例需求另立单）
- P6 经用户拍板：一轮一约束=拒绝+可停止提示，不建排队层

## Plan v0.1.0（2026-10-02）初稿
- 核心：TurnManager 自持订阅+replay sink；P1-P8 决策（P2 全量重放否 offset、P3 落库移交、P4 显式 cancel、P5 confirm 并轮、P6 一轮一约束、P7 A′ 服务端优先、P8 落库通道二选一）
- **A-01 修正式提案**：Redis 缓冲→进程内 Sinks（单实例+重启可丢已声明）；风险4条如实（内存闸/连发变拒绝/写放大/中段起）；零 DDL

## v1.0.0（2026-10-02）确认人: 陈卓
- **第①关通过**（用户「确认需求」）→ Phase 2 Plan

## v0.2.0（2026-10-02）四问决议入册
- Q1 自动追流 / Q2 兜底 10min / Q4 增量 5s 钉入条款；Q3 定**两期制**：A′（stash 原型）先行为一期，B 主体二期，R-09 衔接去重
- 待「确认需求」过第①关

## v0.1.0（2026-10-02）创建
- BL-22 立项（用户指令：新版本 v1.4.0，spec 方式做断线续传）；M0 已建 releases/v1.4.0 并 M1 挂接
- 需求 10 条 EARS / 6 AC / Non-goals / 假设 A-01~05；三起事故（png 掐流、刷新丢库、12:32 无响应）作背景证据
- 开放问题 Q1~Q4 待用户拍板
