package com.phoenix.agent.model;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import com.mybatisflex.core.keygen.KeyGenerators;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 对话智能体运行配置（1:1，库配置驱动 HarnessAgent 构建）。
 * 开关字段用 smallint 0/1 表达（对齐 tbl_data_agent.api_key_enabled 等既有风格）。
 */
@Data
@Table("tbl_data_agent_runtime_config")
public class AgentRuntimeConfig implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private String id;

    /** 关联 tbl_data_agent.id（uk 唯一） */
    private Long agentId;

    /** 对话模型配置ID；空=默认模型 */
    private Long modelConfigId;

    /** 计划模式 0关 1开 */
    private Integer planMode = 0;

    /** 记忆开关 0关 1开 */
    private Integer memoryEnabled = 1;

    /** 知识库检索工具 0关 1开 */
    private Integer knowledgeEnabled = 0;

    /** 数据库取数工具（NL→SQL→只读结果）0关 1开 */
    private Integer dbQueryEnabled = 0;

    /** 数据库深度分析工具（内部走 NL2SQL 状态图）0关 1开 */
    private Integer dbDeepAnalysisEnabled = 0;

    /** 数据库类工具的目标数据源ID */
    private Long datasourceId;

    /** 文件系统策略，见 FilesystemPolicyEnm */
    private String filesystemPolicy = "local";

    private String creator;

    private Date createTime = new Date();

    private String updator;

    private Date updateTime;

    @Column(value = "del_flag", isLogicDelete = true)
    private Integer delFlag = 0;
}
