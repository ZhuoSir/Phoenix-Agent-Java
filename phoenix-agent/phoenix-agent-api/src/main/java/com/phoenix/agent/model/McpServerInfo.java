package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.Date;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.core.keygen.KeyGenerators;
import com.mybatisflex.annotation.Table;

import lombok.Data;

/**
 * MCP Server 注册表实体（插件市场·插件管理·MCP，与技能三件套同构）。
 * config 为 jsonb 列映射 String（先例：ChatMessage.metadata）；敏感值以 enc:v1: 密文存储。
 */
@Data
@Table("tbl_mcp_server")
public class McpServerInfo implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private String id;
    private String name;
    /** stdio | sse | streamable_http | http */
    private String transport;
    private String config;
    private String description;
    /** enabled | disabled */
    private String status;
    private String creator;
    private Date createTime = new Date();
    private String updator;
    private Date updateTime = new Date();
    private Integer delFlag = 0;
}
