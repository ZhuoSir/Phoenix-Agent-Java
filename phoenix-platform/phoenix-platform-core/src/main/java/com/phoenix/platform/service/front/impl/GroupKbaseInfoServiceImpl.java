package com.phoenix.platform.service.front.impl;

import com.mybatisflex.core.query.QueryChain;
import com.phoenix.data.mapper.AgentKbaseBindMapper;
import com.phoenix.platform.mapper.front.GroupKbaseInfoMapper;
import com.phoenix.platform.model.front.GroupKbaseInfo;
import com.phoenix.platform.service.front.GroupKbaseInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 组×知识库授权实现（逻辑删+重插差集，幂等）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupKbaseInfoServiceImpl implements GroupKbaseInfoService {

    private final GroupKbaseInfoMapper groupKbaseInfoMapper;
    private final AgentKbaseBindMapper agentKbaseBindMapper;

    @Override
    public void assignKbases(String groupId, List<Long> kbaseIds, String operator) {
        List<Long> want = kbaseIds == null ? List.of() : kbaseIds.stream().distinct().toList();
        List<Long> have = getKbaseIdsByGroup(groupId);
        // 撤：在 have 不在 want
        for (Long id : have) {
            if (!want.contains(id)) {
                List<GroupKbaseInfo> rows = QueryChain.of(groupKbaseInfoMapper)
                        .eq(GroupKbaseInfo::getGroupId, groupId)
                        .eq(GroupKbaseInfo::getKbaseId, id)
                        .eq(GroupKbaseInfo::getDelFlag, 0)
                        .list();
                for (GroupKbaseInfo row : rows) {
                    row.setDelFlag(1);
                    row.setUpdator(operator);
                    row.setUpdateTime(new java.util.Date());
                    groupKbaseInfoMapper.update(row);
                }
            }
        }
        // 增：在 want 不在 have
        for (Long id : want) {
            if (have.contains(id)) {
                continue;
            }
            GroupKbaseInfo row = new GroupKbaseInfo();
            row.setGroupId(groupId);
            row.setKbaseId(id);
            row.setCreator(operator);
            row.setDelFlag(0);
            // BaseModel 无填充监听：insert 显式补 update_time（列 NOT NULL，此前实测撞约束 500）
            row.setCreateTime(new java.util.Date());
            row.setUpdateTime(new java.util.Date());
            groupKbaseInfoMapper.insert(row);
        }
        log.info("组知识库授权: group={}, want={}, by={}", groupId, want, operator);
    }

    @Override
    public List<Long> getKbaseIdsByGroup(String groupId) {
        return groupKbaseInfoMapper.selectKbaseIdsByGroup(groupId);
    }

    @Override
    public List<String> getGroupIdsByKbase(Long kbaseId) {
        return groupKbaseInfoMapper.selectGroupIdsByKbase(kbaseId);
    }

    @Override
    public long countBoundAgentsOfKbase(Long kbaseId) {
        return agentKbaseBindMapper.selectAgentIdsByKb(kbaseId).size();
    }
}
