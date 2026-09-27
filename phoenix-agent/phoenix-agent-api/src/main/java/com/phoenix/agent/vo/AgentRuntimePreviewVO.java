package com.phoenix.agent.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 对话智能体「构建预演」结果（T-05 验证入口，管理端可见的生效能力摘要）。
 *
 * <p>真实执行一次 Factory 构建（成功后立即关闭实例），用于回答
 * 「按当前配置，这个智能体实际会被装配成什么样」——工具清单、模型、技能池大小。
 */
@Data
public class AgentRuntimePreviewVO implements Serializable {

    private Long agentId;

    private String sn;

    /** 运行时身份：有 sn 用 sn，无 sn 用 agent-{id}（T-05） */
    private String runtimeKey;

    /** 构建是否成功（失败时 errorMessage 给出原因，不抛 500） */
    private Boolean buildOk;

    private String errorMessage;

    /** 实例来源：legacy（存量自注册）/ cached（注册表命中）/ built（本次构建） */
    private String instanceSource;

    /** 人类可读构建摘要（与运行日志同一行文本） */
    private String summary;

    /** 注册表计数快照（cached/hits/builds/legacy/evictions） */
    private String registryStats;

    /** 实际装配的工具名清单（与运行配置开关一致） */
    private List<String> toolNames;

    private Long modelConfigId;

    private Boolean planMode;

    private Boolean memoryEnabled;

    private Boolean knowledgeEnabled;

    private Boolean dbQueryEnabled;

    private Boolean dbDeepAnalysisEnabled;

    private Long datasourceId;

    private String filesystemPolicy;

    /** 该智能体运行时可见技能数（= 已发布 ∧ 已绑定本智能体） */
    private Integer skillPoolSize;

    /** 该智能体运行时可见技能名清单（按 agentId 解析，T-10） */
    private List<String> skillNames;
}
