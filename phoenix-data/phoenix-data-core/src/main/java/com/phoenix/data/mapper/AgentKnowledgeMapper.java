package com.phoenix.data.mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.data.dto.knowledge.agentknowledge.AgentKnowledgeQueryDTO;
import com.phoenix.data.entity.AgentKnowledge;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 智能体知识库 Mapper 接口
 */
@Mapper
public interface AgentKnowledgeMapper extends BaseMapper<AgentKnowledge> {

	/**
	 * 根据ID查询知识（未删除的）
	 */
	@Select("""
			SELECT * FROM tbl_data_agent_knowledge WHERE id = #{id} AND is_deleted = 0
			""")
	AgentKnowledge selectById(@Param("id") Integer id);

	/**
	 * R-01 / T-05（T-03 取证口径）：判断某用户能否读取某知识库的原件。
	 *
	 * <p>① 系统管理员（role_id=428007432736870400 且 del_flag=0）→ 允许；
	 * ② 该用户在"绑定了此知识库的智能体"下存在会话 → 允许（一期代理判据，二期由 R-05/BL-31 的正式关系替换）；
	 * ③ 否则拒绝。
	 */
	@Select("""
			SELECT CASE
			  WHEN EXISTS (SELECT 1 FROM tbl_privilege_user_role ur
			                WHERE ur.user_id = #{userId} AND ur.role_id = '428007432736870400'
			                  AND COALESCE(ur.del_flag, 0) = 0) THEN 1
			  WHEN EXISTS (SELECT 1 FROM tbl_data_chat_session s
			                JOIN tbl_data_agent_kbase_bind b ON b.agent_id = s.agent_id
			               WHERE s.user_id = #{userId} AND b.knowledge_base_id = #{kbId}) THEN 1
			  ELSE 0 END
			""")
	int canReadKnowledgeSource(@Param("userId") String userId, @Param("kbId") Long kbId);

	/**
	 * 根据ID查询知识（包含已删除的）
	 */
	@Select("""
			    SELECT * FROM tbl_data_agent_knowledge WHERE id = #{id}
			""")
	AgentKnowledge selectByIdIncludeDeleted(@Param("id") Integer id);

	/**
	 * 更新知识记录
	 */
	@Update("""
			<script>
			UPDATE tbl_data_agent_knowledge
			<set>
				<if test="title != null">title = #{title},</if>
				<if test="content != null">content = #{content},</if>
				<if test="type != null">type = #{type},</if>
				<if test="question != null">question = #{question},</if>
				<if test="isRecall != null">is_recall = #{isRecall},</if>
				<if test="embeddingStatus != null">embedding_status = #{embeddingStatus},</if>
				<if test="errorMsg != null">error_msg = #{errorMsg},</if>
				<if test="sourceFilename != null">source_filename = #{sourceFilename},</if>
				<if test="filePath != null">file_path = #{filePath},</if>
				<if test="fileSize != null">file_size = #{fileSize},</if>
				<if test="fileType != null">file_type = #{fileType},</if>
				<if test="splitterType != null">splitter_type = #{splitterType},</if>
				<if test="isDeleted != null">is_deleted = #{isDeleted},</if>
				<if test="isResourceCleaned != null">is_resource_cleaned = #{isResourceCleaned},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateWithNow(AgentKnowledge knowledge);

	/**
	 * 根据条件分页查询知识列表
	 */
	@Select("""
			<script>
			SELECT id, agent_id AS agentId, title, type, question, content, is_recall AS isRecall,
			embedding_status AS embeddingStatus, error_msg AS errorMsg, source_filename AS sourceFilename,
			file_path AS filePath, file_size AS fileSize, file_type AS fileType, splitter_type AS splitterType,
			created_time AS createdTime, updated_time AS updatedTime, is_deleted AS isDeleted,
			is_resource_cleaned AS isResourceCleaned, knowledge_base_id AS knowledgeBaseId
			FROM tbl_data_agent_knowledge
			WHERE 1=1
			<if test="queryDTO.agentId != null">
				AND agent_id = #{queryDTO.agentId}
			</if>
			<if test="queryDTO.kbId != null">
				AND knowledge_base_id = #{queryDTO.kbId}
			</if>
			<if test="queryDTO.title != null and queryDTO.title != ''">
				AND title LIKE '%' || #{queryDTO.title} || '%'
			</if>
			<if test="queryDTO.type != null and queryDTO.type != ''">
				AND type = #{queryDTO.type}
			</if>
			<if test="queryDTO.embeddingStatus != null and queryDTO.embeddingStatus != ''">
				AND embedding_status = #{queryDTO.embeddingStatus}
			</if>
			AND is_deleted = 0
			LIMIT #{queryDTO.pageSize} OFFSET #{offset}
			</script>
			""")
	List<AgentKnowledge> selectByConditionsWithPage(@Param("queryDTO") AgentKnowledgeQueryDTO queryDTO,
			@Param("offset") Integer offset);

	/**
	 * 根据条件统计知识数量
	 */
	@Select("""
			<script>
			SELECT COUNT(*) FROM tbl_data_agent_knowledge
			WHERE 1=1
			<if test="queryDTO.agentId != null">
				AND agent_id = #{queryDTO.agentId}
			</if>
			<if test="queryDTO.kbId != null">
				AND knowledge_base_id = #{queryDTO.kbId}
			</if>
			<if test="queryDTO.title != null and queryDTO.title != ''">
				AND title LIKE '%' || #{queryDTO.title} || '%'
			</if>
			<if test="queryDTO.type != null and queryDTO.type != ''">
				AND type = #{queryDTO.type}
			</if>
			<if test="queryDTO.embeddingStatus != null and queryDTO.embeddingStatus != ''">
				AND embedding_status = #{queryDTO.embeddingStatus}
			</if>
			AND is_deleted = 0
			</script>
			""")
	Long countByConditions(@Param("queryDTO") AgentKnowledgeQueryDTO queryDTO);

	/**
	 * 查询智能体所有已启用的召回知识ID列表
	 */
	@Select("""
			SELECT id FROM tbl_data_agent_knowledge WHERE agent_id = #{agentId} AND is_recall = 1 AND is_deleted = 0
			""")
	List<Integer> selectRecalledKnowledgeIds(@Param("agentId") Integer agentId);

	/**
	 * 知识-base T-06 召回主路径：智能体 → 绑定知识库(启用未删) → 召回中条目。
	 * 依赖 idx_dakb_agent 与 idx_dak_kb；存量兼容由迁移回填保障（bind+kb_id 已就位）。
	 */
	@Select("""
			SELECT k.id FROM tbl_data_agent_knowledge k
			JOIN tbl_data_agent_kbase_bind bd ON bd.knowledge_base_id = k.knowledge_base_id
			JOIN tbl_data_knowledge_base kb ON kb.id = k.knowledge_base_id AND kb.status = 1 AND kb.del_flag = 0
			WHERE bd.agent_id = #{agentId} AND k.is_recall = 1 AND k.is_deleted = 0
			""")
	List<Integer> selectRecalledKnowledgeIdsByBindings(@Param("agentId") Long agentId);

	/**
	 * 查询待清理的“僵尸”记录 条件：is_deleted = 1 AND is_resource_cleaned = 0 AND updated_time <(当前时间
	 * - N分钟)
	 */
	@Select("""
			    SELECT * FROM tbl_data_agent_knowledge
			    WHERE is_deleted = 1
			      AND is_resource_cleaned = 0
			      AND updated_time < #{beforeTime}
			    LIMIT #{limit}
			""")
	List<AgentKnowledge> selectDirtyRecords(@Param("beforeTime") LocalDateTime beforeTime, @Param("limit") int limit);

}
