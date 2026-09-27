package com.phoenix.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 技能发布/组授权入参（R-03/R-07）。groupIds 允许为空=仅后台可见。
 */
@Data
public class SkillPublishDTO implements Serializable {

    private List<String> groupIds;
}
