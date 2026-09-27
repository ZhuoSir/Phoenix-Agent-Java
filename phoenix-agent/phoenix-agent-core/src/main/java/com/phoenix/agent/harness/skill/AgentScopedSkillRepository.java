package com.phoenix.agent.harness.skill;

import com.phoenix.agent.mapper.HarnessSkillMapper;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import io.agentscope.core.skill.repository.AgentSkillRepositoryInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 按智能体隔离的技能仓库装饰器（R-06）。
 *
 * <p>把上游仓库的"全表共享"收缩为「已发布 ∧ 已绑定本智能体」：
 * 上游 HarnessAgent 的提示注入与 load_skill 都经 AgentSkillRepository，
 * 因此只需包一层即可完成隔离，无需改动 AgentScope 任何代码。
 *
 * <p>读路径每次实时查库（不做本地缓存），保证授权/绑定变更下一轮会话即生效。
 * 写路径（save/delete）直通上游：agent 自管理技能不经发布治理，属已知范围（plan 风险④）。
 */
@Slf4j
@RequiredArgsConstructor
public class AgentScopedSkillRepository implements AgentSkillRepository {

    private final AgentSkillRepository delegate;

    private final String agentSn;

    private final HarnessSkillMapper harnessSkillMapper;

    @Override
    public AgentSkill getSkill(String name) {
        return allowedNames().contains(name) ? delegate.getSkill(name) : null;
    }

    @Override
    public List<String> getAllSkillNames() {
        Set<String> allowed = allowedNames();
        return delegate.getAllSkillNames().stream().filter(allowed::contains).toList();
    }

    @Override
    public List<AgentSkill> getAllSkills() {
        Set<String> allowed = allowedNames();
        return delegate.getAllSkills().stream().filter(s -> allowed.contains(s.getName())).toList();
    }

    @Override
    public boolean skillExists(String name) {
        return allowedNames().contains(name) && delegate.skillExists(name);
    }

    @Override
    public boolean save(List<AgentSkill> skills, boolean overwrite) {
        return delegate.save(skills, overwrite);
    }

    @Override
    public boolean delete(String name) {
        return delegate.delete(name);
    }

    @Override
    public AgentSkillRepositoryInfo getRepositoryInfo() {
        return delegate.getRepositoryInfo();
    }

    @Override
    public String getSource() {
        return delegate.getSource();
    }

    @Override
    public void setWriteable(boolean writeable) {
        delegate.setWriteable(writeable);
    }

    @Override
    public boolean isWriteable() {
        return delegate.isWriteable();
    }

    /**
     * 不关闭被共享的上游单例仓库（其它智能体仍在用）。
     */
    @Override
    public void close() {
        log.debug("AgentScopedSkillRepository.close() 忽略：上游仓库为共享单例, sn={}", agentSn);
    }

    private Set<String> allowedNames() {
        List<String> names = harnessSkillMapper.selectAllowedSkillNamesByAgentSn(agentSn);
        return names == null ? Set.of() : new HashSet<>(names);
    }
}
