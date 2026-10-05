# Completion: long-turn-resilience

> 版本: v1.0.0 | 状态: 实现完成（7/8 收口，T-06 转待办 BL-27） | 更新: 2026-10-05

## 一、需求兑现（R-01~R-06）

| 需求 | 兑现情况 | 证据 |
|---|---|---|
| R-01 活性看门狗（不再"假死腰斩"） | ✅ | `HarnessTurnManager` `lastActivityAt` 帧脉冲 + janitor 空闲判据（`phoenix.agent.turn-idle-timeout-seconds:600`）；总时长闸默认关（`turn-timeout-seconds:0`）；changelog「T-01 完成：三证+意外活体战果」 |
| R-02 渲染管线不冻结浏览器 | ✅（等价达成，见下偏差） | 服务端 100ms 帧合并（实测 600→6.6 帧/s）+ 前台增量渲染（`incrementalMarkdown`+400ms 节流）+ >10s 活性指示；BUG-70/72/75 已验证 |
| R-03 断线/刷新续跑可续看 | ✅ | `streamHarnessTurnJoin`（`/api/admin/harness/turn/stream` replay+live，原位续渲）+ 中断轮文案带时间归属；**用户界面实测通过（验证人=陈卓，2026-10-05「没问题」）** |
| R-04 中断原因对用户显性 | ✅ | 五分类文案（挂起超时/总时长/服务重启/模型流错误/手动取消）+ `sweepStaleGenerations` 重启注记 |
| R-05 上下文可配置 | ⚠️ 部分：后端贯通、**UI 设计被否** | Flyway `V1.6.0_03` 三列 + 两级回退（agent 配置→全局 env 默认 102400/20/8192）+ 三列进指纹（实测 cached/built 切换正确）；**用户实测判定运行配置面板「设计不对」→ T-06 转 `BL-27`（改为：模型上下文 token / 输出最大 token / 压缩比例，其余收默认）** |
| R-06 E2E 全矩阵 | ✅（用户判定"基本通过"） | `verify 13/13` 全 PASS；typecheck 基线 213 条预存、本次 0 新增；T-04 刷新续流用户实测通过；MCP 链（agent36 变体键含 sessionId、35 工具）与技能链回归 ✅ |

## 二、任务完成度：7/8

- ✅ T-01 看门狗活性化+五分类文案
- ✅ T-02 Flyway V1.6.0_03 三列+两级回退+指纹
- ✅ T-03 渲染管线（**等价达成**，偏差入档）
- ✅ T-04 join/重进修复（**用户界面实测通过**）
- ✅ T-05 多端 transport 四面结论
- ⚠️ **T-06 运行时配置 UI 三输入 → 设计被否，转 `BL-27`**（功能已上线可用，需求形态待重做）
- ✅ T-07 E2E 全矩阵（`verify 13/13` ✅ + typecheck 基线 ✅ + 用户实测「基本通过」）
- ✅ T-08 台账收口（本文件 + `artifacts.md` + MILESTONE 第四行 + BUG-70/71/82 状态 + config 移交 M3）

## 三、偏差与去向（不藏）

1. **T-03 实现位置偏差**：任务书要求"浏览器端 150ms 批量 flush + 完成块 v-memo 冻结"；实交为**服务端 100ms 帧合并**（`HarnessTurnManager.mergeJanitor`）+ 前台增量渲染 + join 原位更新 + ≥150ms 重渲节流。目的（不冻结）达成且有实测帧速；客户端 150ms flush/v-memo 未单独实现。
2. **T-05 第四面（mobile-ui）单列**：mobile-ui 自带 `services/stream.ts`（独立 token key `mobile-ui:auth:token` 与端点），与 admin 侧不同源 → 按规约"不合→单列方案"，本轮**不改**，留待独立立项；pc-ui 无对话流（N/A）。
3. **T-06 需求形态被否**：现面板暴露"压缩触发 token / 保留条数 / 工具结果截断"三个**底层技术参数**，用户判定不对；正确形态=**模型上下文 token、输出最大 token、压缩比例**三项，其余参数收为默认。已登记 `BL-27`，重做时走 requirements/plan 增量 + （如新增列）Flyway 件。
4. **上下文治理的副作用**：压缩参数按会话/智能体生效后，历史记忆与技能缓存的落点随 R-06 会话级工作区变化 → 记忆重置提示**移交 M3 RELEASE-NOTES**（与 workspace-isolation 同一提示合并）。

## 四、config changes（移交 M3 / BL-17 汇总升级件）

| 键 | 类型 | 默认 | 语义 |
|---|---|---|---|
| `phoenix.agent.turn-timeout-seconds` | 新 | `0` | 轮次总时长闸；**0=关**（长活轮不被总时长杀） |
| `phoenix.agent.turn-idle-timeout-seconds` | 新 | `600` | 空闲看门狗（600s 无帧才判挂起） |
| `phoenix.agent.compaction-trigger-tokens` | 新 | `102400` | 压缩触发 token 数（≈0.8×128k，DSH 换算） |
| `phoenix.agent.compaction-keep-messages` | 新 | `20` | 压缩保留最近消息条数 |
| `phoenix.agent.tool-result-max-chars` | 新 | `8192` | 工具结果回收阈值（超出转预览，原文落盘可回读） |

- 交付包 compose 已透传 `PHOENIX_AGENT_TURN_TIMEOUT_SECONDS`（默认 0）与 `PHOENIX_AGENT_TURN_IDLE_TIMEOUT_SECONDS`（默认 600）；compaction 三键走 Spring `@Value` 默认，交付环境如需覆盖再补 env。

## 五、部署纪律记录（L-16 执行痕迹）

- 本 spec 相关部署均**先查活跃轮次**（`tbl_data_chat_message.metadata->>'status'='generating'` 计数=0）再动 backend；两次重启前门禁均为 0，重启清扫 SQL 命中 **0 行**（无轮次被打断）。
- 前端（nginx）单独重建若干次，**不触碰 backend**，对在跑轮次零影响。
- 唯一一次例外为历史事件（L-16 立条原因，见 `lessons.md`），本 spec 期间未再复发。
