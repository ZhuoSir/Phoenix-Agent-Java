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
 * 智能体-技能绑定（多对多，agent 编辑页维护）。
 * 部分唯一索引 uk_dasi_agent_skill(agent_id,skill_id) WHERE del_flag=0 防业务重复。
 */
@Data
@Table("tbl_data_agent_skill_info")
public class AgentSkillInfo implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private String id;

    private Long agentId;

    private Long skillId;

    private String creator;

    private Date createTime = new Date();

    private String updator;

    private Date updateTime;

    @Column(value = "del_flag", isLogicDelete = true)
    private Integer delFlag = 0;
}
