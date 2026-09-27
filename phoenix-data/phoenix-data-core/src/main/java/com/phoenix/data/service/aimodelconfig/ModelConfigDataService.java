package com.phoenix.data.service.aimodelconfig;

import com.mybatisflex.core.service.IService;
import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.entity.ModelConfig;
import com.phoenix.data.enums.ModelType;

import java.util.List;

/**
 * 模型配置数据服务接口，提供模型配置的增删改查及状态切换功能。
 */
public interface ModelConfigDataService extends IService<ModelConfig> {

	/**
	 * 根据 ID 查找模型配置
	 */
	ModelConfig findById(Integer id);

	/**
	 * 切换指定配置的激活状态（启用为可多选集合，不影响同类型其他配置）
	 */
	void switchActiveStatus(Integer id, ModelType type);

	/**
	 * 把指定配置设为其类型的默认：事务内「先取消同类型旧默认，再置新默认（并置为启用）」。
	 * 每类型至多一条默认另由部分唯一索引 uk_dmc_type_default 兜底。
	 */
	void setDefaultConfig(Integer id);

	/**
	 * 取消指定配置的启用态（默认项不允许直接停用，由调用方先行校验）
	 */
	void deactivateConfig(Integer id);

	/**
	 * 该配置是否为其类型的当前默认
	 */
	boolean isDefaultConfig(Integer id);

	/**
	 * 获取所有模型配置 DTO 列表
	 */
	List<ModelConfigDTO> listConfigs();

	/**
	 * 新增模型配置
	 */
	void addConfig(ModelConfigDTO dto);

	/**
	 * 更新数据库中的模型配置（不处理热切换）
	 */
	ModelConfig updateConfigInDb(ModelConfigDTO dto);

	/**
	 * 删除模型配置（逻辑删除）
	 */
	void deleteConfig(Integer id);

	/**
	 * 获取指定类型的活跃模型配置
	 */
	ModelConfigDTO getActiveConfigByType(ModelType modelType);

	/**
	 * 获取指定类型的**默认**模型配置（R-15/R-17）：
	 * 默认优先；该类型无默认时**回落任一启用配置并 WARN**（不静默），
	 * 以免存量库未回填默认时把既有对话/向量化整体打死。
	 *
	 * @return 命中的配置；该类型连启用项都没有时返回 null（调用方按"无可用模型"处理）
	 */
	ModelConfigDTO getDefaultConfigByType(ModelType modelType);

	/**
	 * 获取指定类型的**启用集合**（R-10/R-16：可被选择的模型来源，默认那条排在首位）
	 */
	List<ModelConfigDTO> listEnabledConfigsByType(ModelType modelType);

	/**
	 * 严格取指定类型的默认模型：不做"回落启用项"。
	 *
	 * <p>运行时取模型可容忍回落（不让既有对话被打断），但「AI 生成」这类管理动作必须
	 * 在没设默认时明确拒绝（R-18），故分开两个语义。
	 */
	ModelConfigDTO findDefaultConfigByType(ModelType modelType);

}
