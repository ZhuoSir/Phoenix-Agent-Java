package com.phoenix.agent.enums;

/**
 * MCP 插件管理错误码（46xxx 段，模块集中维护，禁散落硬编码——对齐 SkillErrorCodeEnm 41xxx 惯例）
 */
public enum McpErrorCodeEnm {
    MCP_NOT_FOUND(46101, "MCP 配置不存在"),
    MCP_NAME_CONFLICT(46102, "同名 MCP 已存在"),
    MCP_INVALID_PARAM(46103, "MCP 配置参数无效"),
    MCP_IN_USE(46104, "MCP 已被智能体绑定，请先解绑"),
    MCP_GROUP_NOT_FOUND(46105, "授权目标组不存在"),
    MCP_TRANSPORT_UNSUPPORTED(46106, "不支持的传输类型");

    private final int code;
    private final String msg;

    McpErrorCodeEnm(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
