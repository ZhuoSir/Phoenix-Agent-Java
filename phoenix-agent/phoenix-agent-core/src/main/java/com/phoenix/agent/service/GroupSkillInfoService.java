package com.phoenix.agent.service;

import java.util.List;

/**
 * 组 × 技能授权（组侧入口，R-04）。
 *
 * <p>与资源侧 {@code SkillAdminServiceImpl.replaceGroupGrants} 操作同一张
 * {@code tbl_platform_group_skill_info}：资源侧语义=「某技能授权给哪些组」，
 * 本服务语义=「某组可看到哪些技能」，两者互为反向视图，均按差集幂等写入。
 */
public interface GroupSkillInfoService {

	/**
	 * 全量替换某组的技能授权（差集：撤掉 have-want，新增 want-have）。
	 * @param groupId 组 id
	 * @param skillIds 期望授权的技能 id 全量集合（null 视为空）
	 * @param operator 操作人（写审计列）
	 */
	void assignSkills(String groupId, List<Long> skillIds, String operator);

	/** 某组当前已授权的技能 id 清单。 */
	List<Long> getSkillIdsByGroup(String groupId);

	/** 某技能已授权给的组 id 清单（反向回显用）。 */
	List<String> getGroupIdsBySkill(Long skillId);

}
