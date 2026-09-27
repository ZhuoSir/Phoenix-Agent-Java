package com.phoenix.agent.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 技能列表行 VO（不含正文，详情单查）
 */
@Data
public class SkillListVO implements Serializable {

    private Long id;

    private String name;

    private String description;

    /** draft/published，见 SkillStatusEnm */
    private String status;

    private String source;

    private Date updatedAt;
}
