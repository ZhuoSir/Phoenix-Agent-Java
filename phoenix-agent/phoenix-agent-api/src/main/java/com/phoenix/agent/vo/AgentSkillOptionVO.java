package com.phoenix.agent.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 智能体编辑页技能选项 VO：已发布池 + 已绑定但已下线的（bound=true 且 status=draft → 前端灰显）。
 */
@Data
public class AgentSkillOptionVO implements Serializable {

    private Long skillId;

    private String name;

    private String description;

    /** draft/published */
    private String status;

    private boolean bound;
}
