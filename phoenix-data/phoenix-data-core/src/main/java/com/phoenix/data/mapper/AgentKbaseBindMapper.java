package com.phoenix.data.mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.data.entity.AgentKbaseBind;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 智能体↔知识库绑定 Mapper。召回主路径依赖 selectKbIdsByAgent——须走索引 (agent_id)。
 */
@Mapper
public interface AgentKbaseBindMapper extends BaseMapper<AgentKbaseBind> {

	@Select("SELECT knowledge_base_id FROM tbl_data_agent_kbase_bind WHERE agent_id = #{agentId}")
	List<Long> selectKbIdsByAgent(@Param("agentId") Long agentId);

	@Select("SELECT agent_id FROM tbl_data_agent_kbase_bind WHERE knowledge_base_id = #{kbId}")
	List<Long> selectAgentIdsByKb(@Param("kbId") Long kbId);
}
