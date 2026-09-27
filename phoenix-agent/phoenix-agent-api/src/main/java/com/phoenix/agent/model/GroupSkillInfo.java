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
 * 用户组-技能授权（多对多，技能发布界面维护；与 tbl_platform_group_agent_info 同构）。
 * 未授权任何组的已发布技能对前台账号不可见不可用（默认非公开）。
 */
@Data
@Table("tbl_platform_group_skill_info")
public class GroupSkillInfo implements Serializable {

    @Id(keyType = KeyType.Generator, value = KeyGenerators.snowFlakeId)
    private String id;

    private String groupId;

    private Long skillId;

    private String creator;

    private Date createTime = new Date();

    private String updator;

    private Date updateTime;

    @Column(value = "del_flag", isLogicDelete = true)
    private Integer delFlag = 0;
}
