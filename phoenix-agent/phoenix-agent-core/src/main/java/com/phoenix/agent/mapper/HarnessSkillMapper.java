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
}
