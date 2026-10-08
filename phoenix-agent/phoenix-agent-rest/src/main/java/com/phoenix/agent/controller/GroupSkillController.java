package com.phoenix.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.agent.dto.SkillIdsDTO;
import com.phoenix.agent.service.GroupSkillInfoService;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组 × 技能授权端点（R-04；入口=组管理页「分配技能」dialog，同构 group-kbase/group-agent）。
 *
 * <p>补齐前只有资源侧单向入口（{@code PUT /api/skill/{id}/groups}），组管理页无法管理技能授权。
 */
@RestController
@RequestMapping("/platform/group-skill")
@RequiredArgsConstructor
public class GroupSkillController {

	private final GroupSkillInfoService groupSkillInfoService;

	/** 全量替换某组的技能授权（差集幂等）。 */
	@PutMapping("/{groupId}/assign")
	public ReturnVo<Boolean> assign(@PathVariable String groupId, @RequestBody(required = false) SkillIdsDTO dto) {
		groupSkillInfoService.assignSkills(groupId, dto == null ? null : dto.getSkillIds(),
				StpUtil.getLoginIdAsString());
		return ReturnVo.ok(Boolean.TRUE);
	}

	/** 某组已授权的技能 id 清单（dialog 回显）。 */
	@GetMapping("/{groupId}/skills")
	public ReturnVo<List<Long>> skillsOfGroup(@PathVariable String groupId) {
		return ReturnVo.ok(groupSkillInfoService.getSkillIdsByGroup(groupId));
	}

	/** 反向入口：某技能已授权给哪些组（技能发布页预选用）。 */
	@GetMapping("/skill/{skillId}/groups")
	public ReturnVo<List<String>> groupsOfSkill(@PathVariable Long skillId) {
		return ReturnVo.ok(groupSkillInfoService.getGroupIdsBySkill(skillId));
	}

}
