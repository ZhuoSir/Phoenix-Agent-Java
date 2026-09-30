package com.phoenix.agent.enums;

/**
 * 智能体配置 AI 生成的错误码（42xxx 段，与本模块 AgentRuntimeErrorCodeEnm 同段续号，集中维护）。
 *
 * <p>注：模型管理侧「停用被默认占用项」的拒绝（plan 中的 42014 语义）由 phoenix-data 的
 * 统一异常约定承载（HTTP 400 + 可展示 message），不在本枚举内重复定义，避免两套码表漂移。
 */
public enum ProfileGenerationErrorCodeEnm {

    NAME_INVALID(42010, "请先填写智能体名称（不超过 64 字）"),
    NO_DEFAULT_CHAT_MODEL(42011, "未设置默认对话模型，请到模型管理设置后重试"),
    MODEL_CALL_FAILED(42012, "模型调用失败或超时，请稍后重试"),
    RESULT_PARSE_FAILED(42013, "生成结果解析失败，请重试或手工填写"),
    TARGETS_INVALID(42015, "生成目标字段为空或取值非法");

    private final int code;

    private final String msg;

    ProfileGenerationErrorCodeEnm(int code, String msg) {
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
