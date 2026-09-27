package com.phoenix.agent.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 技能详情 VO：SKILL.md 全文 + 资源清单
 */
@Data
public class SkillDetailVO implements Serializable {

    private Long id;

    private String name;

    private String description;

    private String status;

    private String source;

    private Date updatedAt;

    private String skillContent;

    private List<SkillResourceVO> resources = new ArrayList<>();
}
