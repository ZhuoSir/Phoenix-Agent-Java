# Changelog: long-turn-resilience

## v0.1.0（2026-10-05）
- 初始化创建（用户口令「使用spec来修改这个bug，基于这个版本」；BUG-70/71 立项 + 用户问题③④收编为 R-01/R-05）
- 挂载: v1.6.0（用户明示基于当前版本，不冻结续收第四 spec）
- 现场取证入档：卡死轮实为活体（turn/status=true+model-call 连发）；600s 看门狗=腰斩主因；上下文配置确认不存在（CompactionConfig 写死默认，框架 API 全可调 javap 实证）；bufferFrames=2000 排除缓冲溢出
- requirements v0.1.0：R-01 活性看门狗/R-02 进度可见/R-03 join 渲染/R-04 中断显性化/R-05 上下文可配置(本版唯一 DDL)/R-06 对面零变化；Q1 挂起阈值/Q2 总上限待裁决
- 同批台账：BUG-70(高)/BUG-71(中) 入册；L-16 新坑（用户实测期间禁止静默部署——12:42 部署打断用户 12:22 轮次实证）
