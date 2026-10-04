# MILESTONE: v1.6.0

> 创建: 2026-10-04 | 状态: 进行中（M1 挂 2 需求：mcp-client-tools 实现完成 + agent-publish-group-grant 立项中） | 类型: MINOR | 验证日期: -
> **版本分支**: `v1.6.0`（2026-10-04 建，基点 = main tip 270fcfa，v1.5.0 已合 main 无悬空）

**版本语义**：MINOR——向下兼容新功能。首挂 BL-01 MCP 客户端工具接入。

## 一、需求挂接表（M1）

| spec | 版本 | 状态 | 新增 | 变更 | 摘要 |
|---|---|---|---|---|---|
| specs/20261004_mcp-client-tools | req v1.1.0 / plan v1.1.0 / tasks v1.0.0 | **实现完成（9/9）** | SQL 2件+rollback 2件；三表；后端 22 文件；前端 3 文件 | 0（R-07 平台 Server 端零变化 git diff 实证） | 插件管理·MCP 统一管理+组授权+智能体绑定+对话挂载（BL-01）；BUG-65/66 已验证在途；confirm 链实弹移交 M4 演练 |
| specs/20261004_agent-publish-group-grant | v0.1.0 | 立项中（Phase 1） | 待定 | 待定 | 智能体发布时关联组（与技能发布授权同构，BL-04；2026-10-04 用户裁决挂本版） |

## 二、汇总进度（M3/M4 勾选）

- [ ] M2 范围冻结
- [ ] M3 汇总（SQL/config/RELEASE-NOTES/UPGRADE/checklist）
- [ ] M4 发布（演练+tag）
