package com.phoenix.agent.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 对话智能体运行配置入参（R-03/R-05/R-09）。
 * 开关用 Boolean 语义表达，落库转 smallint 0/1。
 */
@Data
public class AgentRuntimeConfigDTO implements Serializable {

    /** 对话模型配置ID；不传=默认模型 */
    private Long modelConfigId;

    /** 计划模式 */
    private Boolean planMode;

    /** 记忆开关 */
    private Boolean memoryEnabled;

    /** 知识库检索工具 */
    private Boolean knowledgeEnabled;

    /** 知识库检索召回条数（1~50；不传=10） */
    private Integer knowledgeTopK;

    /** 知识库检索相似度阈值（0~1；不传=0.65） */
    private Double knowledgeSimilarityThreshold;

    /** 数据库取数工具（NL→SQL→只读结果） */
    private Boolean dbQueryEnabled;

    /** 数据库深度分析工具（内部走 NL2SQL 状态图） */
    private Boolean dbDeepAnalysisEnabled;

    /** 数据库类工具目标数据源 */
    private Long datasourceId;

    /** 文件系统策略，见 FilesystemPolicyEnm（local/remote） */
    private String filesystemPolicy;
}
