package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.Date;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.mybatisflex.annotation.Table;

import lombok.Data;

/**
 * 智能体-MCP 绑定（多对多，与 tbl_data_agent_skill_info 同构；agent_id bigint 避历史坑）。
 */
@Data
@Table("tbl_data_agent_mcp_info")
public class AgentMcpInfo implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private String id;
    private Long agentId;
    private String mcpId;
    private String creator;
    private Date createTime = new Date();
    private String updator;
    private Date updateTime = new Date();
    private Integer delFlag = 0;
}
