package com.phoenix.agent.enums;

/**
 * 会话文件面板错误码（4203x 段，与 ProfileGenerationErrorCodeEnm 4201x、
 * AgentRuntimeErrorCodeEnm 同仓集中维护，禁止散落字符串码）。
 */
public enum AgentFileErrorCodeEnm {

    FILE_NOT_FOUND(42030, "文件不存在或已删除"),
    FILE_FORBIDDEN(42031, "无权访问该文件"),
    FILE_TOO_LARGE(42032, "文件超过单文件大小上限（50MB）"),
    FILE_NAME_INVALID(42033, "文件名非法，已按安全规则改写"),
    INLINE_FORBIDDEN(42034, "该类型文件不支持在线预览，请使用下载");

    private final int code;

    private final String msg;

    AgentFileErrorCodeEnm(int code, String msg) {
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
