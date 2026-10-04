package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * 测试连接请求（按当前表单值即时试连，不落库）。
 * 编辑场景带 id：headers/env 中掩码值自动取库中原密文合并。
 */
@Data
public class McpTestDTO implements Serializable {
    private String id;
    private String name;
    private String transport;
    private String url;
    private Map<String, String> headers;
    private String command;
    private List<String> args;
    private Map<String, String> env;
    private Long timeoutMs;
    private Long initTimeoutMs;
}
