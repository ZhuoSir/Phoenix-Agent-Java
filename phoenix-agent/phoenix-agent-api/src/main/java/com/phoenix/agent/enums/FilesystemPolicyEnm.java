package com.phoenix.agent.enums;

/**
 * 对话智能体文件系统策略（tbl_data_agent_runtime_config.filesystem_policy）。
 *
 * <p>与 AgentScope Harness 的文件系统实现对应：Local 走本地沙箱（shell 可用），
 * Remote 走共享存储（多实例共享，shell 不可用，见 bugs.md B-07）。
 */
public enum FilesystemPolicyEnm {
    /** 本地沙箱：支持 shell 与脚本类技能（单机） */
    LOCAL("local", "本地沙箱"),
    /** 远程存储：技能资源可共享，不支持 shell */
    REMOTE("remote", "远程存储");

    private final String code;
    private final String desc;

    FilesystemPolicyEnm(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
