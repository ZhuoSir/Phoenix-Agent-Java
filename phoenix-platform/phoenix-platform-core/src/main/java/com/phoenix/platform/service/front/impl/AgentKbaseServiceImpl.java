package com.phoenix.platform.service.front.impl;

import com.mybatisflex.core.query.QueryChain;
import com.phoenix.data.entity.AgentKbaseBind;
import com.phoenix.data.entity.KnowledgeBase;
import com.phoenix.data.mapper.AgentKbaseBindMapper;
import com.phoenix.data.mapper.KnowledgeBaseMapper;
import com.phoenix.platform.mapper.front.GroupAgentInfoMapper;
import com.phoenix.platform.model.front.GroupAgentInfo;
import com.phoenix.platform.service.front.AgentKbaseService;
import com.phoenix.platform.service.front.GroupKbaseInfoService;
import com.phoenix.platform.vo.BindableKbaseVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 绑定实现。校验时机=绑定期（A-05）；agent 无组授权时全量可选（plan 风险3 兜底，changelog 注记）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentKbaseServiceImpl implements AgentKbaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final AgentKbaseBindMapper agentKbaseBindMapper;
    private final GroupAgentInfoMapper groupAgentInfoMapper;
    private final GroupKbaseInfoService groupKbaseInfoService;

    @Override
    public List<BindableKbaseVO> bindable(Long agentId) {
        List<String> agentGroups = agentGroupIds(agentId);
        List<Long> bound = agentKbaseBindMapper.selectKbIdsByAgent(agentId);
        List<KnowledgeBase> kbs = QueryChain.of(knowledgeBaseMapper)
                .eq(KnowledgeBase::getDelFlag, 0)
                .orderBy(KnowledgeBase::getUpdateTime, false)
                .list();
        return kbs.stream().map(kb -> {
            boolean selectable = agentGroups.isEmpty() || hasCommonGroup(agentGroups, kb.getId());
            return BindableKbaseVO.builder()
                    .id(kb.getId()).name(kb.getName()).status(kb.getStatus())
                    .itemCount(knowledgeBaseMapper.countItems(kb.getId()))
                    .bound(bound.contains(kb.getId()))
                    .selectable(selectable)
                    .disabledReason(selectable ? null : "该知识库未授权给此智能体所在的任何组")
                    .build();
        }).toList();
    }

    @Override
    public void bind(Long agentId, List<Long> kbaseIds, String operator) {
        List<Long> want = kbaseIds == null ? List.of() : kbaseIds.stream().distinct().toList();
        List<String> agentGroups = agentGroupIds(agentId);
        for (Long kbId : want) {
            if (!agentGroups.isEmpty() && !hasCommonGroup(agentGroups, kbId)) {
                KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbId);
                throw new IllegalArgumentException("知识库「" + (kb != null ? kb.getName() : kbId)
                        + "」未授权给该智能体的任何组，不能绑定");
            }
            if (knowledgeBaseMapper.selectOneById(kbId) == null) {
                throw new IllegalArgumentException("知识库不存在：" + kbId);
            }
        }
        List<AgentKbaseBind> current = QueryChain.of(agentKbaseBindMapper)
                .eq(AgentKbaseBind::getAgentId, agentId)
                .list();
        for (AgentKbaseBind row : current) {
            if (!want.contains(row.getKnowledgeBaseId())) {
                agentKbaseBindMapper.deleteById(row.getId());
            }
        }
        List<Long> have = current.stream().map(AgentKbaseBind::getKnowledgeBaseId).toList();
        for (Long kbId : want) {
            if (have.contains(kbId)) {
                continue;
            }
            AgentKbaseBind row = new AgentKbaseBind();
            row.setAgentId(agentId);
            row.setKnowledgeBaseId(kbId);
            row.setCreator(operator);
            row.setCreateTime(LocalDateTime.now());
            agentKbaseBindMapper.insert(row);
        }
        log.info("智能体知识库绑定: agent={}, want={}, by={}", agentId, want, operator);
    }

    private List<String> agentGroupIds(Long agentId) {
        return QueryChain.of(groupAgentInfoMapper)
                .select(GroupAgentInfo::getGroupId)
                // 组表 agent_id 列是 varchar（历史双账号体系类型遗留，BL-07 同族问题）——
                // PG 无 varchar=bigint 隐式算子，必须传字符串
                .eq(GroupAgentInfo::getAgentId, String.valueOf(agentId))
                .eq(GroupAgentInfo::getDelFlag, 0)
                .list()
                .stream().map(GroupAgentInfo::getGroupId).toList();
    }

    private boolean hasCommonGroup(List<String> agentGroups, Long kbId) {
        return groupKbaseInfoService.getGroupIdsByKbase(kbId).stream().anyMatch(agentGroups::contains);
    }
}
