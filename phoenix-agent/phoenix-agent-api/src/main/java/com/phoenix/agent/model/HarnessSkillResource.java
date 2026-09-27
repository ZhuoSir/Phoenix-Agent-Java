package com.phoenix.agent.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 技能资源文件（表由 AgentScope 创建：联合主键 id+resource_path，FK 级联删除随技能）。
 */
@Data
@Table("tbl_harness_skill_resources")
public class HarnessSkillResource implements Serializable {

    /** 所属技能ID（非自增，联合主键成员） */
    @Id(keyType = KeyType.None)
    private Long id;

    @Id(keyType = KeyType.None)
    private String resourcePath;

    private String resourceContent;

    private Date createdAt;

    private Date updatedAt;
}
