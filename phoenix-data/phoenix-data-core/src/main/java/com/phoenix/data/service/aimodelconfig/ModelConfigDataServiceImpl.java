package com.phoenix.data.service.aimodelconfig;

import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.phoenix.data.converter.ModelConfigConverter;
import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.entity.ModelConfig;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.mapper.ModelConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static com.phoenix.data.converter.ModelConfigConverter.toDTO;
import static com.phoenix.data.converter.ModelConfigConverter.toEntity;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class ModelConfigDataServiceImpl extends ServiceImpl<ModelConfigMapper, ModelConfig> implements ModelConfigDataService {

	@Override
	@Transactional(readOnly = true)
	public ModelConfig findById(Integer id) {
		return getById(id);
	}

	@Override
	public void switchActiveStatus(Integer id, ModelType type) {
		// 启用改为「可多选集合」，不再取消同类型其他配置的启用态
		// （原 deactivateOthers 的 SQL 与注释相反、会把同类型其他条一并置为 true —— bugs.md B-20，随本改造删除）
		ModelConfig entity = getById(id);
		if (entity != null) {
			entity.setIsActive(true);
			entity.setUpdatedTime(LocalDateTime.now());
			getMapper().update(entity);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<ModelConfigDTO> listConfigs() {
		return QueryChain.of(getMapper())
			.eq(ModelConfig::getIsDeleted, 0)
			.orderBy(ModelConfig::getCreatedTime, false)
			.list()
			.stream()
			.map(ModelConfigConverter::toDTO)
			.collect(Collectors.toList());
	}

	@Override
	public void addConfig(ModelConfigDTO dto) {
		// BUG-106（2026-10-06）：新增必须由服务端生成主键——客户端可能带上旧主键（前端表单残留 id），
		// 而实体是 @Id(keyType = KeyType.Auto)，id 非空时 MyBatis-Flex 会**显式插入 id** ⇒ 主键冲突、
		// 新增静默失败。此处强制清空，任何调用方都不会再踩到。
		dto.setId(null);
		clean(dto);
		save(toEntity(dto));
	}

	private void clean(ModelConfigDTO dto) {
		dto.setModelName(dto.getModelName().trim());
		dto.setBaseUrl(dto.getBaseUrl().trim());
		dto.setApiKey(dto.getApiKey().trim());
		if (dto.getCompletionsPath() != null) {
			dto.setCompletionsPath(dto.getCompletionsPath().trim());
		}
		if (dto.getEmbeddingsPath() != null) {
			dto.setEmbeddingsPath(dto.getEmbeddingsPath().trim());
		}
	}

	@Override
	public ModelConfig updateConfigInDb(ModelConfigDTO dto) {
		clean(dto);
		ModelConfig entity = getById(dto.getId());
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		if (!entity.getModelType().getCode().equals(dto.getModelType()))
			throw new RuntimeException("模型类型不允许修改");

		mergeDtoToEntity(dto, entity);
		entity.setUpdatedTime(LocalDateTime.now());

		getMapper().update(entity);

		return entity;
	}

	private static void mergeDtoToEntity(ModelConfigDTO dto, ModelConfig oldEntity) {
		oldEntity.setProvider(dto.getProvider());
		oldEntity.setBaseUrl(dto.getBaseUrl());
		oldEntity.setModelName(dto.getModelName());
		oldEntity.setTemperature(dto.getTemperature());
		oldEntity.setMaxTokens(dto.getMaxTokens());
		oldEntity.setCompletionsPath(dto.getCompletionsPath());
		oldEntity.setEmbeddingsPath(dto.getEmbeddingsPath());
		oldEntity.setUpdatedTime(LocalDateTime.now());
		oldEntity.setProxyEnabled(dto.getProxyEnabled());
		oldEntity.setProxyHost(dto.getProxyHost());
		oldEntity.setProxyPort(dto.getProxyPort());
		oldEntity.setProxyUsername(dto.getProxyUsername());
		oldEntity.setProxyPassword(dto.getProxyPassword());

		if (dto.getApiKey() != null && !dto.getApiKey().contains("****")) {
			oldEntity.setApiKey(dto.getApiKey());
		}
	}

	@Override
	public void deleteConfig(Integer id) {
		ModelConfig entity = getById(id);
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		if (Boolean.TRUE.equals(entity.getIsActive())) {
			throw new RuntimeException("该配置当前正在使用中，无法删除！请先激活其他配置，再进行删除操作。");
		}

		entity.setIsDeleted(1);
		entity.setUpdatedTime(LocalDateTime.now());
		int updated = getMapper().update(entity);
		if (updated == 0) {
			throw new RuntimeException("删除失败");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void setDefaultConfig(Integer id) {
		ModelConfig entity = getById(id);
		if (entity == null) {
			throw new IllegalArgumentException("模型配置不存在: " + id);
		}
		// 同类型内先清后设（唯一索引兜底并发）；markDefaultById 同时置为启用（R-12：设默认即启用）
		getMapper().clearDefaultByType(entity.getModelType().getCode());
		int updated = getMapper().markDefaultById(id);
		if (updated == 0) {
			throw new IllegalArgumentException("模型配置不存在或已删除: " + id);
		}
		log.info("模型默认配置已切换: type={}, configId={}, modelName={}", entity.getModelType().getCode(), id,
				entity.getModelName());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deactivateConfig(Integer id) {
		ModelConfig entity = getById(id);
		if (entity == null) {
			throw new IllegalArgumentException("模型配置不存在: " + id);
		}
		entity.setIsActive(false);
		entity.setUpdatedTime(LocalDateTime.now());
		updateById(entity);
		log.info("模型配置已停用: configId={}, type={}, modelName={}", id, entity.getModelType().getCode(),
				entity.getModelName());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean isDefaultConfig(Integer id) {
		return getMapper().countDefaultById(id) > 0;
	}

	@Override
	@Transactional(readOnly = true)
	public ModelConfigDTO getDefaultConfigByType(ModelType modelType) {
		ModelConfig entity = getMapper().selectDefaultByType(modelType.getCode());
		if (entity != null) {
			return toDTO(entity);
		}
		// 无默认：回落该类型启用集合的首条（selectEnabledByType 已按"默认优先→最新"排序）
		List<ModelConfig> enabled = getMapper().selectEnabledByType(modelType.getCode());
		if (enabled != null && !enabled.isEmpty()) {
			ModelConfig fallback = enabled.get(0);
			log.warn("类型 [{}] 未设置默认模型，回落启用项: configId={}, modelName={} —— 请到模型管理设置默认",
				modelType.getCode(), fallback.getId(), fallback.getModelName());
			return toDTO(fallback);
		}
		log.warn("类型 [{}] 既无默认模型也无启用配置", modelType.getCode());
		return null;
	}

	@Override
	@Transactional(readOnly = true)
	public ModelConfigDTO findDefaultConfigByType(ModelType modelType) {
		return toDTO(getMapper().selectDefaultByType(modelType.getCode()));
	}

	@Override
	@Transactional(readOnly = true)
	public List<ModelConfigDTO> listEnabledConfigsByType(ModelType modelType) {
		List<ModelConfig> enabled = getMapper().selectEnabledByType(modelType.getCode());
		if (enabled == null) {
			return List.of();
		}
		return enabled.stream().map(ModelConfigConverter::toDTO).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public ModelConfigDTO getActiveConfigByType(ModelType modelType) {
		ModelConfig entity = getMapper().selectActiveByType(modelType.getCode());
		if (entity == null) {
			log.warn("Activation model configuration of type [{}] not found, attempting to downgrade...", modelType);
			return null;
		}
		return toDTO(entity);
	}

}
