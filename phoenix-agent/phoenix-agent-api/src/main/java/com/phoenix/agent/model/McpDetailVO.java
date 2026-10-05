package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.Data;

/** MCP 详情（配置敏感值脱敏回显 sk-xxxxx 式；含授权组与绑定智能体 id 集）。 */
@Data
public class McpDetailVO implements Serializable {
    private String id;
    private String name;
    private String transport;
    private String description;
    private String status;
    private String url;
    private Map<String, String> headers;
    private String command;
    private List<String> args;
    private Map<String, String> env;
    private List<String> enableTools;
    private Long timeoutMs;
    private Long initTimeoutMs;
    private List<String> groupIds;
    private List<Long> boundAgentIds;
    private Date createTime;
    private Date updateTime;
}
