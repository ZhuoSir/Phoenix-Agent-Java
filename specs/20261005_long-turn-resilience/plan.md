# Plan: long-turn-resilience（长轮次可靠性 · DSH 对标）

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05
> 设计蓝本：DSH 源码对标（requirements A-6 清单）；规范路由：code-frontend=none（跟随周边）/code-backend=global/database=global

## 〇、坑核对（lessons 全 16 条 active 逐条过）

| L | 相交 | 规避 |
|---|---|---|
| L-06 多入口枚举 | ✅✅✅ | 渲染管线**四面**：admin run 页 live 路径（零节流实勘）/admin run 页 join 5s 轮询/前台 api-transport（150ms 节流但全量 markdown）/pc-ui+mobile-ui transport（T 任务先枚举再改）；看门狗单点（TurnManager 三链共用） |
| L-07 断言绑用户可见面 | ✅✅ | BUG-69/70 两连教训：验收=浏览器实测可交互性+肉眼进度，DB/日志绿不算数；DSH stress 方法论（交互延迟指标）入验收 |
| L-16 部署避让用户实测 | ✅ | 每次部署前查 turn/status 活跃轮+群里说一声 |
| L-01/L-03/L-14 | ✅ | 锚点实读/容器内证/表格闸 照常 |
| 其余 | 弱 | 照常 |

## 一、采用方案（逐条对标 DSH）

### 1.1 R-01 活性看门狗（对标 idleWatchdog+promoteOnTimeout）
TurnManager 改造（单点覆盖 admin/front/confirm 三链）：
- `Turn` 增 `volatile long lastActivityAt`；`onFrame` 每帧脉冲（=DSH idleWatchdog 的 arm 重置）
- janitor 判据换轨：`now - lastActivityAt > idleTimeout`（新 env `phoenix.agent.turn-idle-timeout-seconds` 默认 **600**）→ 定稿 timeout；**总时长闸默认关**（`turn-timeout-seconds` 默认 600→**0=禁用**，>0 时保留原语义作兜底）
- 定稿语义不变（promoteOnTimeout 哲学：只定稿展示层、内容保留至最后增量、不强杀框架内工作线程——与 DSH"到期转后台"精神对齐的可达等价物）
- 中断文案分原因（R-04）：挂起超时/总时长超时/服务重启（现有）/模型流错误（onError 路径补"⚠️ 模型或流错误中断"）/手动取消（现有"停止"）

### 1.2 R-02 渲染管线增量化（对标 reasoning-chunks.stress 三件套）
**病灶（实勘）**：admin live 路径每 delta 帧 = saveStreamSnapshot 同步写 localStorage + snapText 累加 + handleNodeResponse→nodeBlocks→**全量 markdownToHtml 重解析** + 列表整树重渲；front transport 有 150ms 节流但同样全量 markdown。
**改造（DSH 三件套对齐）**：
1. **批量消费**：帧入缓冲队列，rAF/150ms 批量 flush 到响应式状态（admin live 补节流，与 front transport 拉平）
2. **快照降频**：saveStreamSnapshot 从每帧改 1s 节流 + beforeunload/visibilitychange 兜底写（A′ 防双半截语义保留）
3. **增量渲染**：流式期间只对**尾部未完成块**做 markdown 重渲（按 \n\n 分块，已完成块 v-memo 冻结）；thinking 独立折叠区已有（thinking-display 遗产）保持折叠不逐帧全文重渲（DSH Think 行策略）
4. 活性指示：>10s 无新内容帧显示"正在执行（已用时 mm:ss）"（R-02 兜底条款）
**覆盖面（L-06）**：admin run 页（live+join 轮询渲染同享增量函数）/前台 api-transport/pc-ui、mobile-ui transport 枚举后同方治理（若结构同源则共享工具函数）

### 1.3 R-03 join/重进正确渲染
- 现机制：刷新→DB 读+5s 轮询（detached-stream T-05 遗产）——保留骨架，修渲染断点：
- **复现定位任务**（T 任务）：admin run 页刷新运行中轮，钉死"think done 无正文"具体断点（疑点：generating 行的 content 渲染被 metadata.thinking 展示抢占/applyServerRowRender 对 generating 态的处理/轮询 tick 整表替换打断增量）
- 历史中断轮文案带时间归属（R-03 v0.2.0 增补）：定稿后缀含定稿时刻，前端渲染为弱化历史样式，不与新轮混淆

### 1.4 R-05 上下文治理配置（对标 compaction 比例水位）
- Flyway **V1.6.0_03**（本 spec 唯一 DDL）：`tbl_data_agent_runtime_config` 加三列（全 nullable=用全局默认）：`compaction_trigger_tokens` / `compaction_keep_messages` / `tool_result_max_chars` + rollback
- 全局默认 env：`PHOENIX_AGENT_COMPACTION_TRIGGER_TOKENS`（默认按 DSH 0.8×DeepSeek 128k≈**102400**）/`PHOENIX_AGENT_COMPACTION_KEEP_MESSAGES`（默认 20 现值）/`PHOENIX_AGENT_TOOL_RESULT_MAX_CHARS`（默认 **8192**，DSH pruner 值；头尾保留比例 plan 实现时按 4096/1024 精神截断）
- factory `defaultCompaction()` 改为读 agent 配置→全局默认 两级回退；**新列进 fingerprint**（改配置自动重建实例，既有机制白拿）
- UI：AgentRuntimeConfig 块加三个可选输入（留空=全局默认，附 DSH 语义说明文案）

### 1.5 R-06 对面
短轮/confirm/MCP 链/技能链/BL-22 断线续传语义（快照/轮询/停止按钮）零变化断言。

## 二、涉及模块
后端：HarnessTurnManager（看门狗+文案）、HarnessAgentFactory（compaction 两级回退+fingerprint）、Flyway 两件；前端：run/index.vue（批量+快照降频+增量渲染+join 修复）、api-transport.ts（增量渲染）、AgentRuntimeConfig.vue（三输入）、pc-ui/mobile-ui transport（枚举后同治）。

## 三、共享面身份矩阵
| 共享对象 | 既有身份 | 变更后预期 |
|---|---|---|
| TurnManager janitor | ①admin②front③confirm 轮次守护 | 判据换活性；三链同享；短轮零感知 |
| run/index.vue 渲染 | ①live 流②join 轮询③历史回显 | 三态共用增量渲染函数；nodeBlocks 数据结构不变 |
| saveStreamSnapshot | ①A′ 防双半截 | 降频不改语义（beforeunload 兜底） |
| runtime_config 表 | ①既有 18 列消费方 | 纯加列 nullable，旧行为=默认值等价 |
| factory 指纹 | ①配置变更重建 | +新列纳入，既有触发面不变 |
| front/pc/mobile transport | ①各自流消费 | 同方治理，UI 结构不动 |

## 四、被拒绝的替代方案
| 方案 | 拒绝理由 |
|---|---|
| 看门狗调大总时长了事（如 3600s） | 治标：logo 轮仍会被腰斩只是晚点；DSH 哲学=活性检测，用户已裁决 |
| Web Worker 渲染 markdown | 改动面大（DOM 序列化回传），v1 用批量+尾块增量已达标；Worker 化列 BL 候补 |
| 前端全量换虚拟列表 | 消息数不大（几十条），瓶颈在单条流式重渲不在列表长度 |
| 压缩配置照抄 DSH 比例制 | 我方框架 CompactionConfig 是绝对值 API，比例换算需模型窗口元数据（model_config 无此列）——v1 绝对值+DSH 换算默认，比例制列 BL 候补 |

## 五、风险与规避
| # | 风险 | 规避 |
|---|---|---|
| 1 | 增量渲染引入显示回归（历史消息渲染差异） | 增量只作用于流式进行中消息；完成态/历史态走原全量路径；四面走查 |
| 2 | 快照降频丢 A′ 语义 | beforeunload+visibilitychange 兜底写；1s 节流窗口可接受（原设计即"尽力快照"） |
| 3 | 600s 空闲阈值误杀超长静默工具 | 工具启停本身产帧（PRE/POST_ACTING 空 NodeOutput 也是帧）；真 10 分钟零帧=死 |
| 4 | fingerprint 加列致升级日全量重建 | 一次性成本，重建路径已有（BL 时代实证） |
| 5 | pc/mobile transport 结构未知 | T 任务先枚举后改，结构不合则单列方案回 requirements（铁律6） |

## 六、测试策略（真做，DSH stress 方法论）
- 看门狗：长活轮（>600s 有帧）不杀实测（logo 场景真跑）；假死轮（模拟无帧）600s 定稿+文案；总时长闸 0=关断言
- 渲染：logo 长轮全程浏览器可交互（滚动/切会话/停止 <500ms 肉眼+performance.now 抽查）；chunk 风暴模拟（脚本灌帧）无冻结
- join：运行中刷新→续渲实证（"think done 无正文"复现→修复→复测）；死轮重进→带时间归属中断文案
- 上下文：配置 trigger 小值→长对话压缩提前触发日志；留空=默认等价
- 对面：短轮/confirm/MCP 回环/技能/BL-22 停止按钮全回归；verify 13/13；typecheck 基线
- 部署纪律：每批部署前查活跃轮（L-16）

## 七、任务预告（Phase 3 细化）
T-01 看门狗活性化+中断文案分原因 → T-02 Flyway 三列+compaction 两级回退+指纹 → T-03 admin 渲染管线三件套（批量/快照降频/尾块增量+活性指示） → T-04 join 断点复现与修复+历史中断归属样式 → T-05 front/pc/mobile transport 枚举与同治 → T-06 运行时配置 UI 三输入 → T-07 E2E（logo 真跑 20 分钟+全矩阵） → T-08 台账收尾。
