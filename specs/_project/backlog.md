# 待办清单（Backlog）
> 非需求、非缺陷的功能待办池 | 立项时把条目移入 `specs/{日期}_{功能名}/` 并从此表移除或标注已立项

| # | 待办 | 来源 | 备注 |
|---|---|---|---|
| BL-01 | **MCP 支持**：对话智能体（type=harness）接入 MCP 工具（作为 MCP Client 消费外部 MCP Server 的工具，与现有「平台作为 MCP Server 对外暴露 nl2sql/agent 列表」形成双端能力） | 2026-09-27 用户列入待办 | 现状：平台侧已有 MCP Server 能力（`McpServerService` 暴露 `nl2SqlToolCallback` / `listAgentsToolCallback`）与 `McpServerConfig`；AgentScope Harness 侧具备 `McpServerRegistrar`/`McpServerConfig`（jar 内已见）但**未接线**。建议独立 spec：`mcp-client-tools` |
| BL-02 | 工作流智能体的**图定义与可视化编排** | 2026-09-27 用户明确"本期不做，先放起来" | 现状：图由 Java 硬编码（`ParolCompiledGraph`），DB 无图定义存储、前端无画布。建议独立 spec：`workflow-orchestration` |
| BL-03 | 存量 5 个 Java 自注册智能体的**迁移与删除** | 2026-09-27 用户决定"代码保留，暂作实现参考" | 待 `dynamic-agent-types` 一期把数据驱动跑通、行为等价验证完成后再评估 |
