# Changelog: runtime-max-iterations

## Plan v0.1.0（2026-10-02）初稿
- V1.3.0_03 加列；工厂区间注入；警告文案生效值重读配置（P3 反射与事件夹带均否）；UI ElInputNumber 钳 1~100
- 风险1：maxIters 精确语义待 T 件探针定文案；风险3：实施分支待 thinking-display 合并后另开

## v1.0.0（2026-10-02）确认人: 陈卓
- **第①关通过**（用户「确认需求」，A-01~04 默认生效）；进入 Phase 2 Plan
- 注记：本笔此前一次落盘脚本笔误把本文件覆写成路径字符串，已重写恢复；requirements 未受影响（首行版本头已确认 v1.0.0）

## v0.1.0（2026-10-02）创建
- 立项来源：用户实测 ExceedMaxIters 中断（gantt svg→png），问"迭代次数能否可配置"；拍板走 A 标准三关
- 实证底料：Builder.maxIters(int) 存在、工厂零调用、runtime_config 表可直接扩列
- 5 R / 5 AC / Non-goals / 假设 A-01~04
