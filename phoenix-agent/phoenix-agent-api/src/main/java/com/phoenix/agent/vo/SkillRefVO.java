package com.phoenix.agent.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能引用计数 VO（删除确认框知情展示，R-08）。
 */
@Data
public class SkillRefVO implements Serializable {

    /** 绑定该技能的智能体数 */
    private long boundAgentCount;

    /** 授权到该技能的用户组数 */
    private long authorizedGroupCount;
}
