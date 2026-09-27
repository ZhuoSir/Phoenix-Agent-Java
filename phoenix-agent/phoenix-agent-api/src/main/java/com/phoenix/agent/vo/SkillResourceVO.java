package com.phoenix.agent.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 技能资源行 VO（详情抽屉展示清单与内容）
 */
@Data
public class SkillResourceVO implements Serializable {

    private String path;

    private String content;
}
