# MILESTONE: v1.6.0

> 创建: 2026-10-04 | 状态: 进行中（M1 挂 3 需求均实现完成；前两需求已合并，workspace-isolation 在 feature 分支；**用户明示不冻结续收**） | 类型: MINOR | 验证日期: -
> **版本分支**: `v1.6.0`（2026-10-04 建，基点 = main tip 270fcfa，v1.5.0 已合 main 无悬空）

**版本语义**：MINOR——向下兼容新功能。首挂 BL-01 MCP 客户端工具接入。

## 一、需求挂接表（M1）

| spec | 版本 | 状态 | 新增 | 变更 | 摘要 |
|---|---|---|---|---|---|
| specs/20261004_mcp-client-tools | req v1.1.0 / plan v1.1.0 / tasks v1.0.0 | **实现完成（9/9）** | SQL 2件+rollback 2件；三表；后端 22 文件；前端 3 文件 | 0（R-07 平台 Server 端零变化 git diff 实证） | 插件管理·MCP 统一管理+组授权+智能体绑定+对话挂载（BL-01）；BUG-65/66 已验证在途；confirm 链实弹移交 M4 演练 |
| specs/20261004_agent-publish-group-grant | req/plan/tasks 均 v1.0.0 已确认 | **实现完成（7/7）** | 零 DDL；后端 6 文件+前端 5 文件 | 0（R-03 读点四零 diff 实证） | 智能体发布时关联组+空授权=全公开语义（BL-04；2026-10-04 用户裁决挂本版） |
| specs/20261005_workspace-isolation | req/plan/tasks 均 v1.0.0 | **实现完成（6/6）** | 后端 5 文件（2新3改）；零 DDL 零 DB 重写 | 0（对面五连全绿） | BUG-67+68 同刃双修：workspace 按智能体隔离+文件归属精确化+存量归档（2026-10-05 用户口令立项） |

## 二、汇总进度（M3/M4 勾选）

- [ ] M2 范围冻结
- [ ] M3 汇总（SQL/config/RELEASE-NOTES/UPGRADE/checklist）
- [ ] M4 发布（演练+tag）
