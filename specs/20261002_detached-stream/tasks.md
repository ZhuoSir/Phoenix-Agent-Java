> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-02 | 确认人: 陈卓 | 确认日期: 2026-10-02

# 任务清单：断线续传 / 流恢复

> 分支：自 main 开 `feature/detached-stream`。口径：A 栈真机 + SSE/psql 留证；超时/帧闸走配置键（演练用短值，出厂默认 600s/2000 帧）。
> 两期制：T-01 一期 A′ 先行可独立上线；T-02 起 B 主体。

## 一期（A′ 止血）

- [x] T-01 复活 stash 原型并按 P7 改造：进会话**先探服务端**（无 generating 行/activeTurn 才回显本地快照）；补 admin 页同款；上线后 BUG-53 场景内容不蒸发
  关联: R-09 | 依赖: 无
  验证方式: 浏览器流式中刷新→重进见半截+「已中断」条（curl 无法代验，用户实操）；vue-tsc 基线不涨
  验收标准: 双端回显；服务端已有增量行时快照被丢弃不双显

## 二期（B 主体）

- [ ] T-02 HarnessTurnManager：注册表(sessionId→Turn)/自持源订阅/replay sink(帧闸 `phoenix.agent.turn-buffer-frames`=2000)/watchdog(`…turn-timeout-seconds`=600，演练设短)/flusher(5s upsert 同行)；Turn 状态机 generating→done/timeout/cancelled
  关联: R-01, R-02, R-07 | 依赖: T-01（不互斥，先行独立）
  验证方式: 单测无基建→以 T-03 端点合验；本任务交编译绿+代码走查记录
  验收标准: 源订阅不随 HTTP 取消（日志证明断开后帧继续消费至 end）

- [ ] T-03 服务端端点改造（admin+platform 双域对称）：chat 走开轮+attach；**新增 join** `GET …/turn/stream?sessionId=`(SSE 全量重放+live)；**新增 cancel**；confirm 流并回同轮；会话详情/消息列表带 activeTurn 与 status
  关联: R-01, R-03, R-06, R-08, R-10 | 依赖: T-02
  验证方式: curl 四连：chat 起流 kill -9 客户端→join 重放见全帧+end；cancel 后源停+行定稿；confirm 并轮单行；他人 token join=401
  验收标准: 五路径实测输出留证

- [ ] T-04 落库所有权移交：服务端轮开插 assistant 行（metadata.status/turnId）→5s 刷→终覆盖（含 thinking 通道与 64KB 截尾沿用）；前端 onComplete 保存助手路径退役（双端）；「超时中断/已取消」标注入文案
  关联: R-02, R-04, R-05 | 依赖: T-02
  验证方式: psql 看单行状态流转 generating→done（无重复行）；断线完成任务后文件面板照常登记（R-04 实证）；admin 历史从此带助手正文（旧缺口顺带闭合，如实记）
  验收标准: 同一 turnId 恒一行；断线轮定稿完整

- [ ] T-05 前端追流与真停止：进会话见 activeTurn→自动 join（复用现有 dispatch，零新帧型）；「停止」按钮调 cancel API+本地退订；P6 拒绝提示 UI（「上一轮仍在生成（可停止后重试）」）；A′ 按 P7 退位为秒级窗口兜底
  关联: R-03, R-06, R-09 | 依赖: T-03, T-04
  验证方式: 浏览器实操由用户验收（AC-01/03 剧本）；curl 面先行验证 join 帧序与直播帧序一致
  验收标准: 刷新重进自动续看；点停止真停；关页真续跑

- [ ] T-06 回归与收口：AC-01~06 全项留证（AC-06 用短超时配置键演练）；10min 默认值回归出厂；verify 全绿+（宜补 join/cancel 断言各一）；completion/artifacts（零 DDL 声明）/changelog；MILESTONE v1.4.0 状态推进；backlog BL-22 关单；BUG-53 关单（根治达成，A′ 保留兜底口径记注）
  关联: 全部 | 依赖: T-01~T-05
  验收标准: 六 AC 红绿如实；两单（BL-22/BUG-53）台账闭环有据
