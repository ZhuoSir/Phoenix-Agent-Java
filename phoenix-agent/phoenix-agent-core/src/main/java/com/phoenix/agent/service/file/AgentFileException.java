package com.phoenix.agent.service.file;

import com.phoenix.agent.enums.AgentFileErrorCodeEnm;
import lombok.Getter;

/**
 * 会话文件业务异常（携带 4203x 错误码，由 REST 层转 ReturnVo）。
 */
@Getter
public class AgentFileException extends RuntimeException {

    private final int code;

    public AgentFileException(AgentFileErrorCodeEnm error) {
        super(error.getMsg());
        this.code = error.getCode();
    }
}
