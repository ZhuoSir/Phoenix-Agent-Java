package com.phoenix.agent.dto;

import lombok.Data;

import java.util.List;

/** 技能 id 集合载荷（组侧「分配技能」用，全量替换语义；镜像 KbaseIdsDTO）。 */
@Data
public class SkillIdsDTO {

	private List<Long> skillIds;

}
