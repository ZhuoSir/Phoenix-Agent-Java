# Changelog: runtime-max-iterations

## v0.1.0（2026-10-02）创建
- 立项来源：用户实测 ExceedMaxIters 中断（gantt svg→png），问"迭代次数能否可配置"；方案评估后用户拍板走 A 标准三关
- 实证底料：Builder.maxIters(int) 存在、工厂零调用、runtime_config 表可直接扩列
- 5 R / 5 AC / Non-goals / 假设 A-01~04
