package com.phoenix.agent.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 智能体技能绑定入参（覆盖式，R-04）。
 */
@Data
public class SkillBindingDTO implements Serializable {

    private List<Long> skillIds;
}
