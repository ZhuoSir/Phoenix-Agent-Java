# Changelog: long-turn-resilience

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
