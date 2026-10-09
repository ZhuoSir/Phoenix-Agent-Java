package com.phoenix.agent.dto;

import lombok.Data;

@Data
public class ChatModelRequest {
    /**
     * 当前会话的sessionId
     */
    private String sessionId;
    /**
     * 用户输入的信息
     */
    private String content;
    /**
     * 智能体标示
     */
    private String agentSn;

    /**
     * 对话附件 id 列表（chat-attachment-understanding T-06；**可选**，不传即短路，行为不变 —— 共享面 S1'）。
     */
    private java.util.List<Long> attachmentIds;
}
