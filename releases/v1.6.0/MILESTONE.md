# MILESTONE: v1.6.0

> 创建: 2026-10-04 | 状态: 进行中（M1 挂 4 需求：mcp-client-tools 9/9、workspace-isolation 6/6+v1.1.0 增量、long-turn-resilience 7/8 均实现完成，四个 spec 的改动均在 feature 分支待合并；**用户明示不冻结续收**） | 类型: MINOR | 验证日期: -
> **版本分支**: `v1.6.0`（2026-10-04 建，基点 = main tip 270fcfa，v1.5.0 已合 main 无悬空）

**版本语义**：MINOR——向下兼容新功能。首挂 BL-01 MCP 客户端工具接入。

## 一、需求挂接表（M1）

| spec | 版本 | 状态 | 新增 | 变更 | 摘要 |
|---|---|---|---|---|---|
| specs/20261004_mcp-client-tools | req v1.1.0 / plan v1.1.0 / tasks v1.0.0 | **实现完成（9/9）** | SQL 2件+rollback 2件；三表；后端 22 文件；前端 3 文件 | 0（R-07 平台 Server 端零变化 git diff 实证） | 插件管理·MCP 统一管理+组授权+智能体绑定+对话挂载（BL-01）；BUG-65/66 已验证在途；confirm 链实弹移交 M4 演练 |
| specs/20261004_agent-publish-group-grant | req/plan/tasks 均 v1.0.0 已确认 | **实现完成（7/7）** | 零 DDL；后端 6 文件+前端 5 文件 | 0（R-03 读点四零 diff 实证） | 智能体发布时关联组+空授权=全公开语义（BL-04；2026-10-04 用户裁决挂本版） |
| specs/20261005_workspace-isolation | v1.0.0（req/plan/tasks）+ **v1.1.0 增量（R-06）** | **实现完成（v1.0.0 6/6 + v1.1.0 6/6）** | 后端 10 文件（2 新：WorkspacePaths/WorkspaceMigrationRunner）；零 DDL 零 DB 重写 | 0（**verify 13/13 全 PASS**；对面五连全绿） | v1.0.0：workspace 按智能体隔离+文件归属精确化+存量归档；**v1.1.0(R-06)：会话级工作区 `{root}/{agentKey}/{sessionId}`+shell cwd 统一(BUG-79)+读路径统一(BUG-78/82)+`/app` 清理**；BUG-67/68/78/79/80 已验证；**记忆重置提示（会话级后按会话落）移交 M3/BL-17 与 LTR 合并成一条**；**布局唯一 owner**（LTR 未改布局，见 artifacts 归属对账） |
| specs/20261005_long-turn-resilience | req v1.0.0 / plan v1.0.0 / tasks v1.0.0 | **实现完成（7/8 收口；T-06 转 `BL-27`）** | SQL 1 件 `V1.6.0_03`（运行配置加 3 列）+ rollback 1 件；后端 10 文件（1 新）+ 前端 6 文件；config 新键 ×5 | 0（verify 13/13 全 PASS；typecheck 基线 213 条预存、本次 0 新增） | 长轮可靠性：看门狗活性化（空闲 600s 判据、总时长闸默认关）+ 服务端 100ms 帧合并 + join 追流续渲 + 中断五分类文案（**用户实测"基本通过"**）；BUG-70/71/72/73/74/75/76/77/81/82 已验证；T-03 实现位置偏差（服务端合并替代客户端批量）已入档；T-06 参数形态被否→`BL-27`（改为模型上下文 token/输出最大 token/压缩比例）；记忆重置与 config 变更移交 M3 RELEASE-NOTES |

## 二、汇总进度（M3/M4 勾选）

- [ ] M2 范围冻结
- [ ] M3 汇总（SQL/config/RELEASE-NOTES/UPGRADE/checklist）
- [ ] M4 发布（演练+tag）
