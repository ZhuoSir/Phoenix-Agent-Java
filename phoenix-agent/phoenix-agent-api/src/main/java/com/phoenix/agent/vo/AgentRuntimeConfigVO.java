package com.phoenix.agent.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 对话智能体运行配置出参（不暴露审计字段）。
 */
@Data
public class AgentRuntimeConfigVO implements Serializable {

    private Long agentId;

    private Long modelConfigId;

    private Boolean planMode;

    private Boolean memoryEnabled;

    private Boolean knowledgeEnabled;

    private Boolean dbQueryEnabled;

    private Boolean dbDeepAnalysisEnabled;

    private Long datasourceId;

    private String filesystemPolicy;
}
