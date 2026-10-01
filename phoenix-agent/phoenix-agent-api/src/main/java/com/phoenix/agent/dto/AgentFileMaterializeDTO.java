package com.phoenix.agent.dto;

import lombok.Data;

/**
 * 「另存为文件」物化请求（R-12/P4）：把消息内容（如报告 HTML）显式物化为会话产物。
 */
@Data
public class AgentFileMaterializeDTO {
    private String sessionId;
    private String fileName;
    /** 文本内容（UTF-8）；大小受 50MB 闸口约束 */
    private String content;
}
