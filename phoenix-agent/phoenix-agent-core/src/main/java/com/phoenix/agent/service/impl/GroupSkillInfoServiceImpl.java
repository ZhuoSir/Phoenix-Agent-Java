package com.phoenix.agent.service.impl;

import com.mybatisflex.core.query.QueryChain;
import com.phoenix.agent.mapper.GroupSkillInfoMapper;
import com.phoenix.agent.model.GroupSkillInfo;
import com.phoenix.agent.service.GroupSkillInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 组 × 技能授权实现（差集幂等：撤=逻辑删，增=新插入）。
 *
 * <p>{@link GroupSkillInfo#getDelFlag()} 已声明 {@code isLogicDelete}，因此本类所有
 * QueryChain 查询自动带 {@code del_flag = 0}（无需再显式过滤）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class GroupSkillInfoServiceImpl implements GroupSkillInfoService {

	private final GroupSkillInfoMapper groupSkillInfoMapper;

	@Override
	public void assignSkills(String groupId, List<Long> skillIds, String operator) {
		List<Long> want = skillIds == null ? List.of()
				: skillIds.stream().filter(Objects::nonNull).distinct().toList();
		List<Long> have = getSkillIdsByGroup(groupId);

		// 撤：在 have 不在 want —— 逻辑删（保留审计痕迹，读侧已自动过滤）
		for (Long id : have) {
			if (want.contains(id)) {
				continue;
			}
			List<GroupSkillInfo> rows = QueryChain.of(groupSkillInfoMapper)
				.eq(GroupSkillInfo::getGroupId, groupId)
				.eq(GroupSkillInfo::getSkillId, id)
				.list();
			for (GroupSkillInfo row : rows) {
				row.setDelFlag(1);
				row.setUpdator(operator);
				row.setUpdateTime(new Date());
				groupSkillInfoMapper.update(row);
			}
		}

		// 增：在 want 不在 have
		for (Long id : want) {
			if (have.contains(id)) {
				continue;
			}
			GroupSkillInfo row = new GroupSkillInfo();
			row.setGroupId(groupId);
			row.setSkillId(id);
			row.setCreator(operator);
			row.setDelFlag(0);
			row.setCreateTime(new Date());
			row.setUpdateTime(new Date());
			groupSkillInfoMapper.insert(row);
		}
		log.info("组技能授权完成: group={}, want={}, operator={}", groupId, want, operator);
	}

	@Override
	public List<Long> getSkillIdsByGroup(String groupId) {
		return QueryChain.of(groupSkillInfoMapper)
			.eq(GroupSkillInfo::getGroupId, groupId)
			.list()
			.stream()
			.map(GroupSkillInfo::getSkillId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
	}

	@Override
	public List<String> getGroupIdsBySkill(Long skillId) {
		return QueryChain.of(groupSkillInfoMapper)
			.eq(GroupSkillInfo::getSkillId, skillId)
			.list()
			.stream()
			.map(GroupSkillInfo::getGroupId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
	}

}
