package com.phoenix.agent.model;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/** 测试连接结果（成功=工具清单；失败=可读分类原因，R-06）。 */
@Data
public class McpTestResultVO implements Serializable {
    private boolean success;
    private int toolCount;
    private List<String> toolNames;
    /** 失败原因（不可达/鉴权失败/超时/协议错误+原始信息） */
    private String error;
    private long elapsedMs;
}
