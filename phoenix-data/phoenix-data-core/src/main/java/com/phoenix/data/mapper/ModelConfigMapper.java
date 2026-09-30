package com.phoenix.data.mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.data.entity.ModelConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 模型配置 Mapper 接口
 */
@Mapper
public interface ModelConfigMapper extends BaseMapper<ModelConfig> {

	/**
	 * 查询所有未删除的模型配置
	 */
	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, is_default, max_tokens,
			       model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			       proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			FROM tbl_data_model_config WHERE is_deleted = 0 ORDER BY created_time DESC
			""")
	List<ModelConfig> findAll();

	/**
	 * 根据ID查询模型配置
	 */
	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, is_default, max_tokens,
			       model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			       proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			FROM tbl_data_model_config WHERE id = #{id} AND is_deleted = 0
			""")
	ModelConfig findById(Integer id);

	/**
	 * 查询指定类型的已启用模型配置
	 */
	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, is_default, max_tokens,
			       model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			       proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			FROM tbl_data_model_config WHERE model_type = #{modelType} AND is_active = true AND is_deleted = 0 LIMIT 1
			""")
	ModelConfig selectActiveByType(@Param("modelType") String modelType);

	/**
	 * 根据条件筛选模型配置
	 */
	@Select("""
			<script>
			   SELECT id, provider, base_url, api_key, model_name, temperature, is_active, is_default, max_tokens,
			          model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			          proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			   FROM tbl_data_model_config
			   <where>
			      is_deleted = 0
			      <if test='provider != null and provider != ""'>
			         AND provider = #{provider}
			      </if>
			      <if test='keyword != null and keyword != ""'>
			         AND (provider LIKE CONCAT('%', #{keyword}, '%')
			             OR base_url LIKE CONCAT('%', #{keyword}, '%')
			             OR model_name LIKE CONCAT('%', #{keyword}, '%'))
			      </if>
			      <if test='isActive != null'>
			         AND is_active = #{isActive}
			      </if>
			      <if test='maxTokens != null'>
			         AND max_tokens = #{maxTokens}
			      </if>
			      <if test='modelType != null'>
			         AND model_type = #{modelType}
			      </if>
			   </where>
			   ORDER BY created_time DESC
			</script>
			""")
	List<ModelConfig> findByConditions(@Param("provider") String provider, @Param("keyword") String keyword,
			@Param("isActive") Boolean isActive, @Param("maxTokens") Integer maxTokens,
			@Param("modelType") String modelType);

	/**
	 * 查询指定类型的**默认**模型配置（每类型至多一条，由部分唯一索引 uk_dmc_type_default 保证）
	 */
	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, is_default, max_tokens,
			       model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			       proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			FROM tbl_data_model_config WHERE model_type = #{modelType} AND is_default = true AND is_deleted = 0
			""")
	ModelConfig selectDefaultByType(@Param("modelType") String modelType);

	/**
	 * 查询指定类型的**启用集合**（启用=可被选择的模型，允许多条，不再互斥）
	 */
	@Select("""
			SELECT id, provider, base_url, api_key, model_name, temperature, is_active, is_default, max_tokens,
			       model_type, completions_path, embeddings_path, created_time, updated_time, is_deleted,
			       proxy_enabled, proxy_host, proxy_port, proxy_username, proxy_password
			FROM tbl_data_model_config WHERE model_type = #{modelType} AND is_active = true AND is_deleted = 0
			ORDER BY is_default DESC NULLS LAST, updated_time DESC NULLS LAST, id DESC
			""")
	List<ModelConfig> selectEnabledByType(@Param("modelType") String modelType);

	/**
	 * 取消指定类型当前的默认标记（设为新默认前调用；与 markDefaultById 同事务）
	 */
	@Update("UPDATE tbl_data_model_config SET is_default = false, updated_time = NOW() "
			+ "WHERE model_type = #{modelType} AND is_default = true AND is_deleted = 0")
	int clearDefaultByType(@Param("modelType") String modelType);

	/**
	 * 把指定配置设为其类型的默认；同时置为启用（R-12：设默认即启用）
	 */
	@Update("UPDATE tbl_data_model_config SET is_default = true, is_active = true, updated_time = NOW() "
			+ "WHERE id = #{id} AND is_deleted = 0")
	int markDefaultById(@Param("id") Integer id);

	/**
	 * 查询指定配置的模型类型（设默认/停用时需要按类型做唯一性处理，避免先查实体再判空两段式）
	 */
	@Select("SELECT model_type FROM tbl_data_model_config WHERE id = #{id} AND is_deleted = 0")
	String findModelTypeById(@Param("id") Integer id);

	/**
	 * 该配置是否为所在类型的当前默认
	 */
	@Select("SELECT count(*) FROM tbl_data_model_config WHERE id = #{id} AND is_default = true AND is_deleted = 0")
	int countDefaultById(@Param("id") Integer id);

	/**
	 * 根据ID更新模型配置（仅更新非空字段）
	 */
	@Update("""
			<script>
			          UPDATE tbl_data_model_config
			          <trim prefix="SET" suffixOverrides=",">
			            <if test='provider != null'>provider = #{provider},</if>
			            <if test='baseUrl != null'>base_url = #{baseUrl},</if>
			            <if test='apiKey != null'>api_key = #{apiKey},</if>
			            <if test='modelName != null'>model_name = #{modelName},</if>
			            <if test='temperature != null'>temperature = #{temperature},</if>
			            <if test='isActive != null'>is_active = #{isActive},</if>
			            <if test='isDefault != null'>is_default = #{isDefault},</if>
			            <if test='maxTokens != null'>max_tokens = #{maxTokens},</if>
			            <if test='modelType != null'>model_type = #{modelType},</if>
			            <if test='completionsPath != null'>completions_path = #{completionsPath},</if>
			            <if test='embeddingsPath != null'>embeddings_path = #{embeddingsPath},</if>
			            <if test='isDeleted != null'>is_deleted = #{isDeleted},</if>
			            <if test='proxyEnabled != null'>proxy_enabled = #{proxyEnabled},</if>
			            <if test='proxyHost != null'>proxy_host = #{proxyHost},</if>
			            <if test='proxyPort != null'>proxy_port = #{proxyPort},</if>
			            <if test='proxyUsername != null'>proxy_username = #{proxyUsername},</if>
			            <if test='proxyPassword != null'>proxy_password = #{proxyPassword},</if>
			            updated_time = NOW()
			          </trim>
			          WHERE id = #{id}
			</script>
			""")
	int updateById(ModelConfig modelConfig);

	/**
	 * 逻辑删除模型配置
	 */
	@Update("""
			UPDATE tbl_data_model_config SET is_deleted = 1 WHERE id = #{id}
			""")
	int logicalDeleteById(Integer id);

}
