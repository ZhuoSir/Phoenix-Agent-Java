package com.phoenix.agent.enums;

/**
 * 产物原后端（tbl_data_agent_file.backend），仅溯源用；下载恒走 tee 副本。
 */
public enum AgentFileBackendEnm {

    LOCAL("local"),
    REMOTE("remote");

    private final String code;

    AgentFileBackendEnm(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
