# 里程碑 v1.2.1
> 状态: 进行中 | 目标日期: 尽快（小版本） | 负责人: 陈卓 | 立项: 2026-10-01

**版本语义**：PATCH——仅向下兼容缺陷修复（BUG-31/32/34），无新功能。前端 baseURL 根治（BL-18）与会话文件面板（BL-19）**不进**本版。

## 纳入需求
| spec | spec版本 | 状态 | SQL件 | 配置项 | 备注 |
|---|---|---|---|---|---|
| （无新 spec，纯 bugfix 批次） | - | - | 0 | 0 | 走 Bug 管理旁路：单点直接修+台账登记 |

## 纳入缺陷（M1 挂接）
| 编号 | 标题 | 严重度 | 修复落点 | 状态 |
|---|---|---|---|---|
| BUG-31 | 全新库首启自注册智能体 NPE（createHarnessAgent 先于 saveBySn 读库） | P1 | `HumanInTheLoop` 等判空 + 交付包种子保留双保险 | 已规划(v1.2.1) |
| BUG-32 | deepseek AI 生成双字段 42013（空 content/JSON 解析失败） | P2 | `AgentProfileGenerationService` 解析容错 | 已规划(v1.2.1) |
| BUG-34 | model-config 列表接口回显明文 apiKey | P2 | DTO 出口脱敏 + 更新保旧 + 前端编辑态适配 | 已规划(v1.2.1) |

## 汇总进度（M3/M4 勾选）
- [ ] 全部修复合并 main（M2 冻结前置）
- [ ] M3 汇总（预计无 SQL/配置件——纯代码，changes.md 记"无"）
- [ ] M4：checklist → tag v1.2.1 → CHANGELOG → bugs 转已发布

## 备注
- 三项均源于 v1.2.0 交付包验收期实测，明细见 `specs/_project/bugs.md`
