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

    private Integer knowledgeTopK;

    private Double knowledgeSimilarityThreshold;

    private Boolean dbQueryEnabled;

    private Boolean dbDeepAnalysisEnabled;

    private Long datasourceId;

    /** 工具迭代上限（1~100；null=框架默认） */
    private Integer maxIterations;

    /** R-05：压缩触发令牌水位（null=全局默认） */
    private Integer compactionTriggerTokens;

    /** R-05：压缩保留原文条数（null=全局默认） */
    private Integer compactionKeepMessages;

    /** R-05：工具结果回收字符阈值（null=全局默认） */
    private Integer toolResultMaxChars;

    private String filesystemPolicy;
}
