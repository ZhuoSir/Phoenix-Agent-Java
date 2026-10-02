# 升级件登记: detached-stream

## 零 DDL 声明
本需求无 SQL 升级件：复用 tbl_data_chat_message 既有行 + metadata(jsonb) 扩展
{status: generating|done|timeout|cancelled, turnId}——历史接口/前端解析天然兼容（未知键忽略）。

## 配置变更（记入 v1.4.0 M3 config 汇总）
| 键 | 默认 | 说明 |
|---|---|---|
| phoenix.agent.turn-timeout-seconds | 600 | 轮次兜底时限（compose 可覆盖 PHOENIX_AGENT_TURN_TIMEOUT_SECONDS） |
| phoenix.agent.turn-flush-seconds | 5 | 增量落库周期 |
| phoenix.agent.turn-buffer-frames | 2000 | replay 帧闸（超限截尾，DB 增量兜历史） |

## 端点新增（v1.4.0 API 清单）
admin：GET /api/admin/harness/turn/stream、GET …/turn/status、POST …/turn/cancel
前台：GET /platform/harness/turn/stream(属主)、GET …/status、POST …/cancel
chat/confirm 改走 TurnManager（开轮/并轮），SSE 帧协议零改动。

## 验证记录（2026-10-02 rc11，全部真机）
- 断线续跑：curl 12s 硬断 → turn/status=true → join 225帧+end → 单行 done ✓（AC-01，用户浏览器复验通过）
- 断线产物：dsm-proof.txt 无客户端在线照常登记 + 行 done ✓（AC-02/R-04）
- 显式停止：cancel → data:true、行 cancelled；关页续跑对照 ✓（AC-03，用户浏览器复验通过）
- HITL：planMode 触发 needConfirm（buttons ✓）→ 客户端断，行持 generating → join 重放 1306 帧含 needConfirm 原样重送 → confirm(带agentId) 并轮回原轮 → 1712 帧续流 → done len=361 + plan 文件产出 + 全程单行 ✓（AC-04 服务端链路；按钮渲染复用既有通道，浏览器可复验：智能体配置开计划模式后提问需确认的任务并刷新）
- 属主：乱 token 401 ✓（AC-05）；超时：20s 演练 status=timeout+标注，复位 600s ✓（AC-06）
- P6：进行中二次发送 → 「上一轮仍在生成中（可点击「停止」后重试）」✓
- 过程勘正：演练 confirm 缺 agentId 属脚本参数错非链路缺陷；演练期发现并补 admin confirm 流 thinking 通道缺失（与 chat 流对齐）
- verify 新增 [14]（12 断言全绿）；测试数据全清；planMode 复位
