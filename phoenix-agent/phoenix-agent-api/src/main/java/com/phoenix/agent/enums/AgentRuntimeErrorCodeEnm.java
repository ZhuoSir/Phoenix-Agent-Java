package com.phoenix.agent.enums;

/**
 * 对话智能体运行配置错误码（42xxx 段，模块集中维护）
 */
public enum AgentRuntimeErrorCodeEnm {
    CONFIG_DATASOURCE_REQUIRED(42001, "开启数据库查询能力前需先选择目标数据源"),
    CONFIG_TOOL_LIMIT(42002, "同时开启的工具数量超出上限"),
    CONFIG_POLICY_INVALID(42003, "文件系统策略取值非法"),
    AGENT_NOT_FOUND(42004, "智能体不存在");

    private final int code;
    private final String msg;

    AgentRuntimeErrorCodeEnm(int code, String msg) {
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
