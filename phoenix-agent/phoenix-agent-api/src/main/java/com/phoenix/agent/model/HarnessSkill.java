package com.phoenix.agent.model;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 技能实体，映射 AgentScope PostgresSkillRepository 建的 tbl_harness_skills。
 * id 为库端 IDENTITY 生成（KeyType.Auto）；status 列由本 spec 增补（默认 draft），
 * 上游读写不含该列、互不影响。
 */
@Data
@Table("tbl_harness_skills")
public class HarnessSkill implements Serializable {

    @Id(keyType = KeyType.Auto)
    private Long id;

    private String name;

    private String description;

    /** SKILL.md 全文（frontmatter+正文） */
    private String skillContent;

    private String source;

    private String metadataJson;

    private String status;

    private Date createdAt;

    private Date updatedAt;
}
