package com.phoenix.data.service.aimodelconfig;

import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.entity.ModelConfig;
import com.phoenix.data.enums.ModelProvider;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.event.AiModelConfigChangedEvent;
import com.phoenix.data.exception.InvalidInputException;
import com.phoenix.data.util.JsonUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 模型配置运维服务，提供配置的热切换、激活及连接测试功能。
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
@AllArgsConstructor
public class ModelConfigOpsService {

	@org.springframework.beans.factory.annotation.Autowired
	private OllamaApiClient ollamaApiClient;

	private final org.springframework.context.ApplicationEventPublisher eventPublisher;

	private final ModelConfigDataService modelConfigDataService;

	private final DynamicModelFactory modelFactory;

	private final AiModelRegistry aiModelRegistry;

	private final ObjectMapper objectMapper = JsonUtil.getObjectMapper();

	/**
	 * 更新配置并热刷新内存中的模型实例
	 */
	public void updateAndRefresh(ModelConfigDTO dto) {
		// 1. 更新数据库
		ModelConfig entity = modelConfigDataService.updateConfigInDb(dto);

		// 2. 检查是否是激活状态
		if (Boolean.TRUE.equals(entity.getIsActive())) {
			try {
				// 3. 刷新内存模型
				log.info("Detected update on active config [{}], refreshing memory...", entity.getModelType());
				refreshMemoryModel(entity.getModelType());
			}
			catch (Exception e) {
				// 抛出异常回滚数据库事务
				throw new RuntimeException("配置更新失败: " + e.getMessage(), e);
			}
		}
	}

	/**
	 * 激活指定 ID 的模型配置（先刷新内存，再更新数据库状态）
	 */
	public void activateConfig(Integer id) {
		// 1. 查数据
		ModelConfig entity = modelConfigDataService.findById(id);
		if (entity == null) {
			throw new RuntimeException("配置不存在");
		}

		// 2. 刷新内存模型
		log.info("Activating config ID={}, Type={}...", id, entity.getModelType());
		refreshMemoryModel(entity.getModelType());

		// 3. 更新数据库状态 (调用数据层)
		modelConfigDataService.switchActiveStatus(id, entity.getModelType());
		publishChanged(entity.getModelType(), id);

		log.info("Config ID={} activated successfully.", id);
	}

	/**
	 * 停用模型配置（R-13：默认项不允许直接停用，否则该类型会失去兜底模型）
	 */
	public void deactivateConfig(Integer id) {
		ModelConfig entity = modelConfigDataService.findById(id);
		if (entity == null) {
			throw new InvalidInputException("配置不存在");
		}
		if (modelConfigDataService.isDefaultConfig(id)) {
			throw new InvalidInputException("该模型是「" + entity.getModelType().getLabel()
					+ "」类型当前的默认模型，需先启用另一条同类型模型并设为默认，或直接删除本行，之后才能停用");
		}
		modelConfigDataService.deactivateConfig(id);
		// 先落库后刷新：否则重建时仍会读到旧状态
		refreshMemoryModel(entity.getModelType());
		publishChanged(entity.getModelType(), id);
	}

	/**
	 * 设为该类型的默认模型（R-11/R-12：同类型唯一，且设默认即启用）
	 */
	public void setDefaultConfig(Integer id) {
		ModelConfig entity = modelConfigDataService.findById(id);
		if (entity == null) {
			throw new InvalidInputException("配置不存在");
		}
		try {
			modelConfigDataService.setDefaultConfig(id);
		}
		catch (org.springframework.dao.DuplicateKeyException e) {
			// 并发设默认：部分唯一索引 uk_dmc_type_default 兜底，转成可展示提示而非 500
			throw new InvalidInputException("已有其他请求正在设置该类型默认模型，请稍后重试");
		}
		// 先落库后刷新内存，保证换默认后新请求立刻使用新默认（无需重启）
		refreshMemoryModel(entity.getModelType());
		publishChanged(entity.getModelType(), id);
		if (ModelType.EMBEDDING.equals(entity.getModelType())) {
			// 换嵌入模型后，历史向量与新模型不可比；提示需重建，但不自动重算（plan 风险④）
			log.warn("EMBEDDING 默认模型已切换为 configId={}, modelName={}；"
					+ "历史向量数据由旧模型生成，需重新初始化数据源 schema 才与新模型一致（本期不自动重算）",
					id, entity.getModelName());
		}
	}

	/** 通知各运行时"模型配置已变"，让其清理自己的缓存（data → agent 用事件解耦） */
	private void publishChanged(ModelType type, Integer id) {
		eventPublisher.publishEvent(new AiModelConfigChangedEvent(this,
				type == null ? null : type.getCode(), id));
	}

	/**
	 * 刷新内存中的模型实例（清空注册中心的缓存）
	 */
	private void refreshMemoryModel(ModelType type) {
		if (ModelType.CHAT.equals(type)) {
			aiModelRegistry.refreshChat();
		}
		else if (ModelType.EMBEDDING.equals(type)) {
			aiModelRegistry.refreshEmbedding();
		}
		else if (ModelType.AUDIO.equals(type)) {
			aiModelRegistry.refreshTranscription();
		}
		else if (ModelType.MULTIMODAL.equals(type)) {
			// T-03：data 域注册中心（AiModelRegistry）只构建 Chat/Embedding/Transcription，
			// **不构建多模态实例** ⇒ 此处无缓存可清；agent 域由 publishChanged 事件触发
			// AiModelConfigChangeListener → HarnessModelRegistry.refreshMultimodal()。
			log.debug("MULTIMODAL 配置变更：data 域无内存实例需刷新（agent 域由事件刷新）");
		}
		else {
			throw new RuntimeException("未知的模型类型: " + type);
		}
	}

	private static boolean looksMasked(String key) {
		return key != null && key.contains("****");
	}

	/** 脱敏值按 id 回源真实 key（前端编辑/测试场景回传脱敏串） */
	private void resolveMaskedApiKey(ModelConfigDTO config) {
		if (config.getId() == null || !looksMasked(config.getApiKey())) {
			return;
		}
		ModelConfig stored = modelConfigDataService.findById(config.getId());
		if (stored != null) {
			config.setApiKey(stored.getApiKey());
		}
	}

	/** 测试失败日志用：apiKey 一律脱敏后再序列化 */
	private String maskedConfigLog(ModelConfigDTO config) {
		String real = config.getApiKey();
		try {
			config.setApiKey(maskApiKey(real));
			return objectMapper.writeValueAsString(config);
		}
		catch (Exception ex) {
			return "<unserializable>";
		}
		finally {
			config.setApiKey(real);
		}
	}

	public static String maskApiKey(String key) {
		if (key == null || key.isBlank()) {
			return key;
		}
		String t = key.trim();
		return t.length() <= 7 ? "****" : t.substring(0, 3) + "****" + t.substring(t.length() - 4);
	}

	/**
	 * 测试模型连接（创建临时模型，不影响正在运行的模型）
	 */
	public void testConnection(ModelConfigDTO config) {
		// BUG-34：前端回传的是列表脱敏值（含****）→ 按 id 从库解析真实 key 再测
		resolveMaskedApiKey(config);
		String modelType = config.getModelType();

		try {
			// R-04：provider=ollama 走**原生** /api/tags 探针（既有分支走 OpenAI 兼容协议，路径不同）
			if (ModelProvider.OLLAMA.getCode().equalsIgnoreCase(config.getProvider())) {
				testOllamaConnection(config);
				return;
			}
			if (ModelType.CHAT.getCode().equalsIgnoreCase(modelType)) {
				testChatModel(config);
			}
			else if (ModelType.EMBEDDING.getCode().equalsIgnoreCase(modelType)) {
				testEmbeddingModel(config);
			}
			else if (ModelType.MULTIMODAL.getCode().equalsIgnoreCase(modelType)) {
				// BUG-159：多模态模型与 CHAT 同为 OpenAI 兼容 chat 端点（差异在能否吃图），
				// 连接测试复用最轻量 chat 探针验证 base_url / key / model 可达；视觉能力不在本探针范围
				log.info("Testing Multimodal Model connection (reuse chat probe), modelName: {}", config.getModelName());
				testChatModel(config);
			}
			else {
				throw new IllegalArgumentException("未知的模型类型: " + modelType);
			}
		}
		catch (Exception e) {
			try {
				log.error("Failed to test model connection. Config: {}", maskedConfigLog(config), e);
			}
			catch (JacksonException e1) {
				log.error("Failed to convert config to JSON. Config: {}", config, e1);
			}
			// 重新抛出异常，让 Controller 捕获并展示给前端
			// 如果是 OpenAiHttpException，通常包含具体的 API 错误信息
			throw new RuntimeException(parseErrorMessage(e));
		}
	}

	/**
	 * R-04：Ollama 连接测试 —— 走原生 {@code /api/tags} 探活，并**校验所配模型确实已安装**。
	 *
	 * <p>失败信息必须可据以定位（地址不通 / 服务异常 / 模型未安装并列出本机已装模型）。
	 */
	private void testOllamaConnection(ModelConfigDTO config) {
		java.util.List<String> models = ollamaApiClient.listModels(config.getBaseUrl());
		String name = config.getModelName() == null ? "" : config.getModelName().trim();
		boolean installed = models.stream().anyMatch(m -> m.equals(name) || m.startsWith(name + ":"));
		if (!installed) {
			throw new InvalidInputException("Ollama 服务可达，但未安装模型「" + name + "」（本机已装: "
					+ String.join(", ", models) + "）");
		}
		log.info("Ollama 连接测试通过: modelType={}, endpoint={}, model={}", config.getModelType(),
				config.getBaseUrl(), name);
	}

	/**
	 * R-05：列出 Ollama 本机模型（服务端代理，前端不直连内网/宿主地址）。
	 */
	public java.util.List<String> listOllamaModels(String baseUrl) {
		if (!org.springframework.util.StringUtils.hasText(baseUrl)) {
			throw new InvalidInputException("baseUrl 不能为空");
		}
		return ollamaApiClient.listModels(baseUrl);
	}

	/**
	 * 测试 Chat 模型连接
	 */
	private void testChatModel(ModelConfigDTO config) {
		log.info("Testing Chat Model connection, provider: {}, modelName: {}", config.getProvider(),
				config.getModelName());

		// 1. 创建临时模型
		ChatModel tempModel = modelFactory.createChatModel(config);

		// 2. 发起最轻量的请求
		String promptText = "Hello";

		// 3. 调用
		String response = tempModel.call(promptText);

		// 4. 校验结果
		if (!StringUtils.hasText(response)) {
			throw new RuntimeException("模型返回内容为空");
		}
		log.info("Chat Model test passed. Response: {}", response);
	}

	/**
	 * 测试 Embedding 模型连接
	 */
	private void testEmbeddingModel(ModelConfigDTO config) {
		log.info("Testing Embedding Model connection, provider: {} modelName: {}", config.getProvider(),
				config.getModelName());
		// 1. 创建临时模型
		EmbeddingModel tempModel = modelFactory.createEmbeddingModel(config);

		// 2. 发起请求
		float[] embedding = tempModel.embed("Test");

		// 3. 校验结果
		if (embedding == null || embedding.length == 0) {
			throw new RuntimeException("模型生成的向量为空");
		}
		log.info("Embedding Model test passed. Dimension: {}", embedding.length);
	}

	/**
	 * 提取更友好的错误信息（处理常见的 HTTP 状态码）
	 */
	private String parseErrorMessage(Exception e) {
		String rawMsg = e.getMessage();
		if (rawMsg == null) {
			return "未知错误";
		}
		String httpStatus = extractHttpStatus(rawMsg);
		String apiErrorMessage = extractApiErrorMessage(rawMsg);
		if (httpStatus != null && apiErrorMessage != null) {
			return formatErrorByStatus(httpStatus) + " " + apiErrorMessage;
		}
		if (apiErrorMessage != null) {
			return apiErrorMessage;
		}
		if (httpStatus != null) {
			return formatErrorByStatus(httpStatus);
		}
		return rawMsg;
	}

	private String extractHttpStatus(String rawMsg) {
		int spaceIdx = rawMsg.indexOf(" ");
		if (spaceIdx < 0) {
			return null;
		}
		String statusPart = rawMsg.substring(0, spaceIdx).trim();
		if (statusPart.matches("\\d{3}")) {
			return statusPart;
		}
		return null;
	}

	private String extractApiErrorMessage(String rawMsg) {
		int jsonStart = rawMsg.indexOf("{");
		if (jsonStart < 0) {
			return null;
		}
		String jsonPart = rawMsg.substring(jsonStart);
		try {
			JsonNode node = objectMapper.readTree(jsonPart);
			JsonNode error = node.get("error");
			if (error != null) {
				JsonNode message = error.get("message");
				if (message != null && message.isTextual()) {
					return message.asText();
				}
			}
		}
		catch (Exception ignored) {
		}
		return null;
	}

	private String formatErrorByStatus(String status) {
		return switch (status) {
			case "401" -> "鉴权失败 (401)，请检查 API Key 是否正确。";
			case "404" -> "接口未找到 (404)，请检查 Base URL 或者路径配置地址。";
			case "429" -> "请求过多或余额不足 (429)，请检查厂商额度。";
			default -> "请求失败 (" + status + ")";
		};
	}

}
