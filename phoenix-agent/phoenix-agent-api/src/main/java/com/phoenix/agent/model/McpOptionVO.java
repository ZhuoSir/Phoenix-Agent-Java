package com.phoenix.agent.model;

import java.io.Serializable;

import lombok.Data;

/** MCP 绑定选项（智能体编辑页；镜像 AgentSkillOptionVO 语义：停用且未绑定不可新选，已绑定停用保留）。 */
@Data
public class McpOptionVO implements Serializable {
    private String id;
    private String name;
    private String transport;
    private String status;
    private boolean bound;
}
