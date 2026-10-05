package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.Date;

import lombok.Data;

/** MCP 列表行（R-01：含授权组数/绑定智能体数）。 */
@Data
public class McpListVO implements Serializable {
    private String id;
    private String name;
    private String transport;
    private String status;
    private String description;
    private long groupCount;
    private long boundCount;
    private Date updateTime;
}
