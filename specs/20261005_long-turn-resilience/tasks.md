# Tasks: long-turn-resilience

> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-05

## 组 1：后端

- [ ] T-01 看门狗活性化+中断文案分原因：Turn 增 lastActivityAt（onFrame 脉冲=DSH arm）；janitor 判据=空闲>600s（env turn-idle-timeout-seconds）；总时长闸默认关（turn-timeout-seconds 默认 0，>0 保留兜底）；定稿文案五分类（挂起超时/总时长/服务重启/模型流错误/手动取消）
  关联: R-01, R-04
  依赖: 无
  验证方式: 假死轮模拟（构造无帧轮：断点或桩）600s 定稿+文案；短轮/活跃轮零误杀（金丝雀行对照）；总时长闸 0=关断言（长活轮>600s 总时长不被杀——与 T-07 logo 真跑联动）
  验收标准: 假死定稿+活跃不杀+文案五类实证

- [ ] T-02 Flyway V1.6.0_03 三列+compaction 两级回退+指纹：runtime_config 加 compaction_trigger_tokens/compaction_keep_messages/tool_result_max_chars（nullable）+rollback；factory defaultCompaction 改 agent 配置→全局 env 默认（102400/20/8192，DSH 换算）两级回退；三列进 fingerprint
  关联: R-05
  依赖: 无
  验证方式: 全新库重放（台账+1/列存在/rollback 零残留）；配置小值→压缩提前触发日志；留空=默认等价（指纹不变断言）；改值→指纹变更重建日志
  验收标准: 重放绿+触发实证+默认等价

## 组 2：前端渲染（DSH 三件套）

- [ ] T-03 admin run 页渲染管线：①帧缓冲批量 flush（150ms）②saveStreamSnapshot 1s 节流+beforeunload/visibilitychange 兜底 ③尾块增量渲染（流式中只重渲最后未完成块，完成块 v-memo 冻结；历史/完成态走原全量路径）④>10s 无帧活性指示"正在执行（mm:ss）"
  关联: R-02
  依赖: 无
  验证方式: chunk 风暴脚本灌帧（对标 DSH stress 128条/16ms）浏览器可交互（滚动/停止按钮 <500ms，performance.now 抽查）；A′ 快照语义回归（刷新防双半截）；短轮渲染与旧版一致（历史消息走查）
  验收标准: 风暴不冻结+快照语义保留+渲染回归

- [ ] T-04 join/重进修复：复现"think done 无正文"（admin run 页刷新运行中轮）钉死断点→修复续渲；历史中断轮文案带时间归属+弱化样式（不与新轮混淆）
  关联: R-03
  依赖: T-03
  验证方式: 运行中刷新→数秒内续见流式输出直至完成（实测）；死轮重进→归属时间文案；新轮运行时旧中断文案明确历史态
  验收标准: 三场景实测全过

- [ ] T-05 多端 transport 同治（L-06 四面收口）：front api-transport 增量渲染改造；pc-ui/mobile-ui transport 枚举定性（同源→共享工具函数；不合→单列方案回 requirements=铁律6 出口）
  关联: R-02
  依赖: T-03
  验证方式: 前台长轮浏览器可交互实测；pc/mobile 枚举清单入 changelog（改或不改及理由逐一标注）
  验收标准: 四面全有结论+改造面实测绿

- [ ] T-06 运行时配置 UI 三输入：AgentRuntimeConfig 块加压缩触发/保留条数/工具结果截断三可选输入（留空=全局默认+DSH 语义说明文案）；抽屉+独立页双入口核对（L-06）
  关联: R-05
  依赖: T-02
  验证方式: 走查（填值保存→DB 列→指纹重建日志；清空→回默认）；typecheck 基线；双入口一致
  验收标准: 走查全通+基线守住

## 组 3：收口

- [ ] T-07 E2E 全矩阵：logo 真跑（"设计个新的logo 单字陈 黑红"≥20 分钟长轮不被腰斩、全程浏览器可交互、产物落文件面板）+中断五文案抽查+对面回归（短轮/confirm/MCP 回环/技能/BL-22 停止按钮）+verify 13/13+typecheck 基线
  关联: 全部 R
  依赖: T-01~T-06
  验证方式: 如上逐项留档（logo 真跑为一锤定音证，用户可见面为准——L-07）
  验收标准: logo 跑完出图+全矩阵绿

- [ ] T-08 台账收尾：completion/artifacts/MILESTONE 第四需求行/BUG-70、71 翻已验证/config changes（env 三新键+两默认变更）移交 M3/部署纪律记录（L-16 执行痕迹）
  关联: 流程
  依赖: T-07
  验证方式: completion 与勾选核对；artifacts 与实改动对账
  验收标准: 台账齐、偏差与去向写明

## 自检
- R 覆盖：R-01(T-01/07) R-02(T-03/05/07) R-03(T-04/07) R-04(T-01/07) R-05(T-02/06/07) R-06(T-07 对面)——全覆盖无孤儿
- 依赖无循环；L-06 四面在 T-03/T-04/T-05 逐一收口；L-16 部署纪律贯穿各部署批次
- 粒度：8 任务各一次会话可完成可独立验证
