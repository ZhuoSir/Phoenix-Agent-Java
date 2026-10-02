> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-02 | 确认人: 陈卓 | 确认日期: 2026-10-02

# 技术方案：断线续传 / 流恢复（detached-stream，对齐 requirements v1.0.0）

## 0. 对假设 A-01 的修正（已拍板 2026-10-02：Sinks 方案采纳）

A-01 原写「轮次缓冲用 Redis」。**plan 修正为：进程内 Sinks 缓冲为主，Redis 仅继续承担既有职责（pending_confirm 等）**。理由：①交付形态单实例，跨实例续传无现状需求 ②Non-goals 已接受「进程重启丢在跑轮次」③replay sink 全量帧内存可控（10min 时限+帧数硬闸双兜底）。**拍板记录（2026-10-02 用户选择）**：进程内 Sinks ✓（Redis 预铺方案否决——多实例真需求出现时按 P2 决策表另立单升级）。

## 1. 轮次生命周期（核心机制）

```
chat POST ─→ HarnessTurnManager.openTurn(sessionKey)
              ├─ 源流（现有 stream(...)链，含 filesTail/HITL/警告帧）由 manager 自持订阅 ← 关键：订阅权脱离 HTTP 连接
              ├─ replay sink（全量帧缓存，2000 帧硬闸→截尾续跑）
              ├─ 落库行：轮开始即插 assistant 占位行（status=generating，metadata 带 turnId）
              ├─ flusher：每 5s 把累积正文/思考 upsert 进该行（Q4）
              ├─ watchdog：10min 到点 cancel 源订阅、行定稿+「超时中断」标注（Q2/R-07）
              └─ 完成路径：end 帧后行定稿（status=done），TTL 清 sink，注册表除名
HTTP 消费者 ─→ attach(sink)：正常直播；断开只退订自己，源继续（R-01/R-06 断≠停）
追流者    ─→ join(sink)：replay 全量帧快进 + 续 live（R-03 自动追流）
```

## 2. 决策表

| # | 决策 | 被否替代与理由 |
|---|---|---|
| P1 | 订阅权归 TurnManager（manager 内 `.subscribe()` 源流），controller 一律 attach/join sink 视图 | `.cache()`+首订阅者驱动——否：取消语义仍传染；doOnCancel 转后台——否：Reactor 取消不可逆 |
| P2 | 追流协议 = **全量帧重放**（客户端当新流重演） | offset 断点续传——否：两轨文本拼接复杂度高一个量级，DB 增量已兜崩溃可见性 |
| P3 | 落库所有权移交：服务端**轮开插行、5s 增刷、终覆盖**（同一行，turnId 幂等）；前端 onComplete 保存助手消息路径**退役**（R-05）；用户消息保存维持现状（发送即存，无竞态） | 前端续存——否：双写与丢库两患的根 |
| P4 | 停止 = 新端点 `POST …/turn/cancel`（admin 域+platform 前台域各一，属主校验），前端「停止」按钮改调此接口+本地退订；关页仅本地退订（R-06） | 维持断连即停——否：与 R-01 直接矛盾 |
| P5 | HITL 续跑 confirm 流**并回同一轮**：confirmStream 也交 manager attach 到原 sink（按钮暂存重送靠全量重放天然覆盖，R-08） | 新开轮次——否：会话出现两条半截消息 |
| P6 | 轮次键 = sessionId（**一轮一约束**：activeTurn 存在时新发送直接 ReturnVo 拒绝「上一轮仍在生成（可停止后重试）」） | 并行多轮——否：UI/内存/文件扫描全要重设计，收益不明 |
| P7 | A′（一期已发/待发）去重细则落地 R-09：进会话**先查服务端**（activeTurn 或 status=generating 行存在 → 走 join/DB 增量，丢弃本地快照）；仅服务端无线索时才回显 A′ | 快照优先——否：双半截画面 |
| P8 | flusher/落库通道：优先注入 data 域 ChatMessageService（saveMessage 实证存在）；若模块依赖不顺则按 AgentKnowledge 先例走 MyBatis-Flex Db 直表——实施时二选一，**不新建跨域 HTTP** | — |

## 3. 改动面清单

- **agent-core**：新增 `HarnessTurnManager`（注册表 ConcurrentHashMap<sessionId, Turn>、watchdog、flusher）；HarnessChatServiceImpl 出口从「返回可消费流」改「开轮+返回视图」
- **agent-rest / platform**：4 类端点——chat(开轮+attach)、**join**（`GET …/turn/stream?sessionId=`，SSE，attach 追流）、cancel、confirm 并轮；admin 与 front 两域对称
- **数据**：**零 DDL**（复用 assistant 行 + metadata JSON 扩 status/turnId 字段；无新列——与「升级件零新增」的 v1.4.0 台账一致性核对过）
- **前端（双端）**：进会话先探测（会话详情带 activeTurn 标记 or 消息行 status=generating）→ 自动 join；stopSending 接真 cancel API；退役前端助手消息保存；join 帧复用现有 dispatch（协议零改，thinking/agentFiles 语义不变）
- **一期 A′ 收编**：stash 原型按 P7 去重规则改造后先行交付（半天），B 上线后 A′ 自动降级为「秒级窗口兜底」

## 4. 风险

1. **内存**：replay().all() 极端长文（100k+ token）帧堆积——2000 帧硬闸+截尾语义（DB 增量兜历史可见性）；上限可配键 `phoenix.agent.turn-buffer-frames`
2. ~~P6 行为变更待拍~~ **已拍板（2026-10-02）**：拒绝+提示「上一轮仍在生成（可停止后重试）」，不引入排队层
3. flusher 5s 粒度写放大：单轮最多 120 写（10min 顶格），远低于现网单查询量级；实施压测留证
4. 前端 join 的快进重演对超长轮（接近帧闸截尾）首屏可能见中段起——接受（DB 定稿行完整），completion 里标注

## 5. 与既有体系咬合

- 帧协议零改动（SseSupport 心跳、agentFiles、thinking、needConfirm、end 全量重放兼容）
- 轮末文件扫描在源流链内 → 断线照跑（R-04 白送）
- BUG-46 兜底合成 end 帧逻辑随源流保留；超时定稿路径复用同一收尾
