package com.phoenix.data.mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.data.entity.KnowledgeBase;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 知识库 Mapper。显式列名，禁 SELECT *。
 * 组标签跨模块只读 JOIN（tbl_platform_group_kbase_info / tbl_platform_group_info 写侧归 platform 域，
 * 本处仅列表展示用——同库分层读取，禁止在此文件写平台域表）。
 */
@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBase> {

	/** 某库已授权的组名清单（展示用）。 */
	@Select("""
			SELECT g.name
			FROM tbl_platform_group_kbase_info k
			JOIN tbl_platform_group_info g ON g.id = k.group_id AND g.del_flag = 0
			WHERE k.kbase_id = #{kbId} AND k.del_flag = 0
			""")
	List<String> selectGroupNamesByKb(@Param("kbId") Long kbId);

	/** 某库的启用条目数（列表列）。 */
	@Select("SELECT count(*) FROM tbl_data_agent_knowledge WHERE knowledge_base_id = #{kbId} AND is_deleted = 0")
	long countItems(@Param("kbId") Long kbId);

	/** 绑定该库的智能体名清单（删除保护提示用，R-04）。 */
	@Select("""
			SELECT a.name
			FROM tbl_data_agent_kbase_bind b
			JOIN tbl_data_agent a ON a.id = b.agent_id
			WHERE b.knowledge_base_id = #{kbId}
			""")
	List<String> selectBoundAgentNames(@Param("kbId") Long kbId);

	/** 名称查重（未删集合内）。 */
	@Select("SELECT count(*) FROM tbl_data_knowledge_base WHERE name = #{name} AND del_flag = 0 AND id <> #{excludeId}")
	long countByName(@Param("name") String name, @Param("excludeId") long excludeId);
}
