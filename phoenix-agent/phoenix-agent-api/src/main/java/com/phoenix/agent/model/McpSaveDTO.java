package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * MCP 配置保存请求（新建 id 空；编辑带 id）。
 * headers/env 值回传掩码形态（含 ****）时表示"未修改"，服务端保留原密文。
 */
@Data
public class McpSaveDTO implements Serializable {
    private String id;
    private String name;
    /** stdio | sse | streamable_http | http */
    private String transport;
    private String description;
    /** enabled | disabled（缺省 enabled） */
    private String status;
    // 远程族（sse/streamable_http/http）
    private String url;
    private Map<String, String> headers;
    // stdio 族
    private String command;
    private List<String> args;
    private Map<String, String> env;
    // 通用
    private List<String> enableTools;
    private Long timeoutMs;
    private Long initTimeoutMs;
}
