package com.phoenix.agent.harness.skill;

import com.mybatisflex.core.query.QueryWrapper;
import com.phoenix.agent.enums.SkillErrorCodeEnm;
import com.phoenix.agent.enums.SkillStatusEnm;
import com.phoenix.agent.mapper.HarnessSkillMapper;
import com.phoenix.agent.model.HarnessSkill;
import com.phoenix.agent.properties.PhoenixAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 显式技能注入准备（R-05 / R-09）。
 *
 * <p>服务端三重校验（已发布 ∧ 已绑定本智能体 ∧ 数量上限）后，把所选技能全文拼成
 * {@code <active_skills>} 块，由调用方前置注入本轮 user message——不依赖模型自主匹配，
 * 满足「强制生效」语义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillExplicitInjectionService {

    private final HarnessSkillMapper harnessSkillMapper;

    private final PhoenixAgentProperties phoenixAgentProperties;

    /**
     * 注入准备结果：ok=true 时 block 为待注入文本（可能为空串=未指定技能）；ok=false 时 errorMsg 为拒绝原因。
     */
    /** 技能池解析键：agentId 优先，否则 sn */
    private record SkillPoolKey(Long agentId, String agentSn) {

        static SkillPoolKey of(Long agentId, String agentSn) {
            return new SkillPoolKey(agentId, agentSn);
        }

        List<String> names(HarnessSkillMapper mapper) {
            List<String> names = agentId != null ? mapper.selectAllowedSkillNamesByAgentId(agentId)
                    : mapper.selectAllowedSkillNamesByAgentSn(agentSn);
            return names == null ? List.of() : names;
        }

        @Override
        public String toString() {
            return agentId != null ? "agentId=" + agentId : "sn=" + agentSn;
        }
    }

    public record InjectionResult(boolean ok, String errorMsg, String block, List<String> skillNames) {

        static InjectionResult none() {
            return new InjectionResult(true, null, "", List.of());
        }

        static InjectionResult error(SkillErrorCodeEnm code, String detail) {
            return new InjectionResult(false, code.getMsg() + (detail == null ? "" : ": " + detail), "", List.of());
        }
    }

    private static SkillPoolKey prepareKey(String agentSn, Long agentId) {
        return SkillPoolKey.of(agentId, agentSn);
    }

    /**
     * 校验并构建注入块。ids 为空视为未启用显式执行（返回 none）。
     */
    public InjectionResult prepare(String agentSn, List<Long> ids) {
        return prepare(prepareKey(agentSn, null), ids);
    }

    /**
     * 按 **agentId** 校验并构建注入块（R-03/R-10：库配置智能体没有 sn，技能池按 agentId 解析）。
     */
    public InjectionResult prepareByAgentId(Long agentId, List<Long> ids) {
        return prepare(prepareKey(null, agentId), ids);
    }

    /** 技能池解析主体：agentId 优先（库配置路径），否则 sn（存量自注册路径） */
    private InjectionResult prepare(SkillPoolKey key, List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return InjectionResult.none();
        }
        List<Long> distinct = ids.stream().filter(java.util.Objects::nonNull).distinct().toList();
        int max = phoenixAgentProperties.getSkill() == null || phoenixAgentProperties.getSkill().getMaxExplicit() == null
            ? 3 : phoenixAgentProperties.getSkill().getMaxExplicit();
        if (distinct.size() > max) {
            return InjectionResult.error(SkillErrorCodeEnm.SKILL_EXCEED_LIMIT, "上限 " + max + " 个");
        }
        List<HarnessSkill> skills = harnessSkillMapper
            .selectListByQuery(QueryWrapper.create().in("id", distinct));
        if (skills.size() != distinct.size()) {
            return InjectionResult.error(SkillErrorCodeEnm.SKILL_ACCESS_DENIED, "存在不存在的技能");
        }
        // 该智能体可用技能名集合（已发布∧已绑定），用于越权拦截
        Set<String> allowed = new HashSet<>(key.names(harnessSkillMapper));
        List<String> names = new ArrayList<>();
        StringBuilder block = new StringBuilder();
        block.append("<active_skills>\n")
            .append("【平台技能指令】管理端已为本轮显式启用下列技能，其触发条件视为**已满足**：\n")
            .append("- 直接执行下列技能的指令，无需再用技能自身的触发词/适用场景做判断；\n")
            .append("- 技能要求输出固定模板时，直接输出该模板，不要附加无关内容。\n\n");
        for (HarnessSkill skill : skills) {
            if (!SkillStatusEnm.PUBLISHED.getCode().equals(skill.getStatus()) || !allowed.contains(skill.getName())) {
                return InjectionResult.error(SkillErrorCodeEnm.SKILL_ACCESS_DENIED, skill.getName());
            }
            names.add(skill.getName());
            block.append("### 技能: ").append(skill.getName()).append('\n')
                .append(skill.getSkillContent() == null ? "" : skill.getSkillContent())
                .append("\n\n");
        }
        block.append("</active_skills>\n\n");
        log.info("显式技能注入准备完成, {}, skills={}", key, names);
        return new InjectionResult(true, null, block.toString(), names);
    }
}
