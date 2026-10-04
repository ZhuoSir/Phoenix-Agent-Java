package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/** 智能体 MCP 绑定保存请求（覆盖式）。 */
@Data
public class McpBindDTO implements Serializable {
    private Long agentId;
    private List<String> mcpIds;
}
