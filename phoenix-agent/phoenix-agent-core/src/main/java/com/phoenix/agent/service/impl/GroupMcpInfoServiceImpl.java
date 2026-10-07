package com.phoenix.agent.service.impl;

import com.mybatisflex.core.query.QueryChain;
import com.phoenix.agent.mapper.GroupMcpInfoMapper;
import com.phoenix.agent.model.GroupMcpInfo;
import com.phoenix.agent.service.GroupMcpInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 组 × MCP 授权实现（差集幂等：撤=逻辑删，增=新插入）。
 *
 * <p>{@code GroupMcpInfo.delFlag} 于 v2.0.0 补上 {@code isLogicDelete}（BUG-121：
 * 此前读侧不过滤墓碑行），因此本类查询自动过滤 {@code del_flag = 0}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class GroupMcpInfoServiceImpl implements GroupMcpInfoService {

	private final GroupMcpInfoMapper groupMcpInfoMapper;

	@Override
	public void assignMcps(String groupId, List<String> mcpIds, String operator) {
		List<String> want = mcpIds == null ? List.of()
				: mcpIds.stream().filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().toList();
		List<String> have = getMcpIdsByGroup(groupId);

		// 撤：在 have 不在 want —— 逻辑删
		for (String id : have) {
			if (want.contains(id)) {
				continue;
			}
			List<GroupMcpInfo> rows = QueryChain.of(groupMcpInfoMapper)
				.eq(GroupMcpInfo::getGroupId, groupId)
				.eq(GroupMcpInfo::getMcpId, id)
				.list();
			for (GroupMcpInfo row : rows) {
				row.setDelFlag(1);
				row.setUpdator(operator);
				row.setUpdateTime(new Date());
				groupMcpInfoMapper.update(row);
			}
		}

		// 增：在 want 不在 have
		for (String id : want) {
			if (have.contains(id)) {
				continue;
			}
			GroupMcpInfo row = new GroupMcpInfo();
			row.setGroupId(groupId);
			row.setMcpId(id);
			row.setCreator(operator);
			row.setDelFlag(0);
			row.setCreateTime(new Date());
			row.setUpdateTime(new Date());
			groupMcpInfoMapper.insert(row);
		}
		log.info("组MCP授权完成: group={}, want={}, operator={}", groupId, want, operator);
	}

	@Override
	public List<String> getMcpIdsByGroup(String groupId) {
		return QueryChain.of(groupMcpInfoMapper)
			.eq(GroupMcpInfo::getGroupId, groupId)
			.list()
			.stream()
			.map(GroupMcpInfo::getMcpId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
	}

	@Override
	public List<String> getGroupIdsByMcp(String mcpId) {
		return QueryChain.of(groupMcpInfoMapper)
			.eq(GroupMcpInfo::getMcpId, mcpId)
			.list()
			.stream()
			.map(GroupMcpInfo::getGroupId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
	}

}
