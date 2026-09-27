package com.phoenix.agent.service;

import com.phoenix.agent.vo.SkillListVO;
import com.phoenix.tools.vo.ReturnVo;

import java.util.List;

/**
 * 前台（普通用户）技能可见性与可用性判定（R-07 / R-09）。
 *
 * <p>三重交集：技能已发布 ∧ 已绑定所对话智能体 ∧ 已授权给账号所属任一用户组。
 */
public interface FrontSkillAccessService {

    /**
     * 当前前台账号在某智能体下可见可用的技能列表（R-09 技能区数据源）。
     */
    ReturnVo<List<SkillListVO>> mySkills(String accountId, Long agentId);

    /**
     * 智能体对当前账号是否可见（组-智能体授权链路），以及返回智能体 sn。
     */
    ReturnVo<String> resolveVisibleAgentSn(String accountId, Long agentId);

    /**
     * 校验显式勾选技能是否全部落在三重交集内；越权返回失败（R-09 场景2）。
     */
    ReturnVo<Boolean> validateExplicitSkills(String accountId, Long agentId, List<Long> skillIds);

    /**
     * 生成本轮可用技能范围提示文本（风险①缓解）。
     */
    String buildScopeHint(String accountId, Long agentId);
}
