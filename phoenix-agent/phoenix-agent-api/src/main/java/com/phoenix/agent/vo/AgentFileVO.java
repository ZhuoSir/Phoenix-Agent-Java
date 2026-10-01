package com.phoenix.agent.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话文件面板展示 VO（不含 relPath 等内部定位信息，防探测）。
 */
@Data
@Builder
public class AgentFileVO {
    private String id;
    private String fileName;
    private Long sizeBytes;
    private String mime;
    private String source;
    private LocalDateTime createTime;
}
