> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-06

# 技术方案：unified-account-center（统一账号中心 / 消除双账号体系）

> Phase 2 产物。**前置条件**：requirements.md 达到 `状态: 已确认` 后才开始填写本节内容。
> 现在仅登记待办骨架，避免在需求未定时先定实现。

## 坑核对（必填，确认②审这一节）

（Phase 2 填写：逐条核对 `specs/_project/lessons.md` 全部 active 坑，相交的写「本方案如何避开」；
无相交也要写「已核对 N 条 active 坑，无相交」）

## 方案概述

（Phase 2：采用路线 A/B/C 中的哪一条 + 一段话说明；对应满足哪些 R-xx）

## 涉及模块与数据流

（Phase 2：phoenix-privilege / phoenix-admin / phoenix-platform / phoenix-agent / web-frontend 的具体改动面）

## 接口设计

（Phase 2：认证端点收敛方案；新增/修改/废弃的端点与错误码；遵守 standards/api-design.md）

## 数据模型变更

（Phase 2：DDL 草案 + 索引 + 迁移与回滚；预期含账号表/关联表迁移；遵守 standards/database-design.md）

## 共享面身份矩阵（触碰共享面时必填）

（Phase 2：认证端点、用户表列、前端登录路由都是共享面——必须逐身份列出"变更后预期行为"）

## 关键决策

（Phase 2：每决策含「采用/理由/被拒绝的替代方案/重新评估条件」）

## 风险与规避

（Phase 2）

## 依赖与前置

（Phase 2）
