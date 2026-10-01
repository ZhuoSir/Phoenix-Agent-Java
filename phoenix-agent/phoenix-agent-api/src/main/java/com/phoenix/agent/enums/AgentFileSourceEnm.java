package com.phoenix.agent.enums;

/**
 * 会话产物文件的产出来源（tbl_data_agent_file.source）。
 */
public enum AgentFileSourceEnm {

    /** 经 AbstractFilesystem 的 write/edit/uploadFiles 工具路径 tee */
    TOOL("tool"),
    /** 会话轮末扫描 workspace 增量兜底（shell 直写等绕过工具路径） */
    SCAN("scan"),
    /** 消息内容手动物化（如报告 HTML「另存为文件」） */
    MATERIALIZE("materialize");

    private final String code;

    AgentFileSourceEnm(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
