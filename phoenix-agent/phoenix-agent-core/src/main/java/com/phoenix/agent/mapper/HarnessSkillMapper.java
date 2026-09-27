package com.phoenix.agent.mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.agent.model.HarnessSkill;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HarnessSkillMapper extends BaseMapper<HarnessSkill> {

    /**
     * 运行时技能池：某 harness 智能体可用的技能名集合 = 已绑定该智能体 ∧ 已发布（R-06）。
     * 关联表逻辑删除过滤 del_flag=0；名称与 AgentScope 仓库的 AgentSkill.name 同源。
     */
    @Select("""
            select s.name
              from tbl_harness_skills s
              join tbl_data_agent_skill_info b on b.skill_id = s.id and b.del_flag = 0
              join tbl_data_agent a on a.id = b.agent_id
             where a.sn = #{sn}
               and s.status = 'published'
            """)
    List<String> selectAllowedSkillNamesByAgentSn(@Param("sn") String sn);

    /**
     * 运行时技能池（R-03/R-10，库配置路径）：按 **agentId** 取「已绑定该智能体 ∧ 已发布」的技能名。
     * 与 sn 版本查的是同一批绑定行，只是解析方式不同——新建的库配置智能体没有 sn，必须走本方法。
     */
    @Select("""
            select s.name
              from tbl_harness_skills s
              join tbl_data_agent_skill_info b on b.skill_id = s.id and b.del_flag = 0
             where b.agent_id = #{agentId}
               and s.status = 'published'
            """)
    List<String> selectAllowedSkillNamesByAgentId(@Param("agentId") Long agentId);

    /**
     * 技能绑定版本（T-06 配置指纹组成项）：绑定条数 + 最近绑定变更时间。
     * 绑定变更即视为「运行实例已过期」，与 agent/运行配置的 update_time 一起决定是否重建。
     */
    @Select("""
            select count(*) || ':' || coalesce(to_char(max(b.update_time), 'YYYY-MM-DD HH24:MI:SS.MS'), '-')
              from tbl_data_agent_skill_info b
             where b.agent_id = #{agentId} and b.del_flag = 0
            """)
    String selectSkillBindingVersion(@Param("agentId") Long agentId);
}
