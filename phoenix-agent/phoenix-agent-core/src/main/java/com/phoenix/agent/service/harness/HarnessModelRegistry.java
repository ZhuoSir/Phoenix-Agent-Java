package com.phoenix.agent.service.harness;

import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.entity.ModelConfig;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import io.agentscope.extensions.model.openai.formatter.DeepSeekFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型注册中心，管理 ChatClient 和 EmbeddingModel 的懒加载、缓存及热切换。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessModelRegistry {
    private final ModelConfigDataService modelConfigDataService;
    // 缓存对象 (volatile 保证可见性)
    private volatile EmbeddingModel currentEmbeddingModel;
    private volatile ChatModelBase currentChatModel;

    /** 多模态模型缓存（chat-attachment-understanding T-03） */
    private volatile ChatModelBase currentMultimodalModel;
    /** 指定模型配置ID 的对话模型缓存（R-03：智能体可自选模型） */
    private final Map<Long, ChatModelBase> configuredChatModels = new ConcurrentHashMap<>();

    /**
     * 获取全局 ChatClient（懒加载 + 双重检查锁缓存）
     */
    public ChatModelBase getOpenAIChatModel() {
        if (currentChatModel == null) {
            synchronized (this) {
                if (currentChatModel == null) {
                    log.info("Initializing global ChatClient...");
                    try {
                        ModelConfigDTO config = modelConfigDataService.getDefaultConfigByType(ModelType.CHAT);
                        if (config != null) {
                            // 可诊断：换默认后新构建即用新模型，需能看出用的是哪一条
                            log.info("默认对话模型: configId={}, provider={}, modelName={}", config.getId(),
                                    config.getProvider(), config.getModelName());
                            currentChatModel = OpenAIChatModel.builder()
                                    .apiKey(config.getApiKey())
                                    .modelName(config.getModelName())
                                    .baseUrl(config.getBaseUrl())
                                    .stream(true)
                                    .formatter(new DeepSeekFormatter())
                                    // BUG-35：模型管理的 maxTokens/temperature 此前从未传给对话请求，
                                    // 长输出被服务端默认上限截断
                                    .generateOptions(GenerateOptions.builder()
                                            .maxTokens(config.getMaxTokens())
                                            .temperature(config.getTemperature())
                                            .build())
                                    .build();
                        }
                    } catch (Exception e) {
                        log.error("Failed to initialize ChatClient: {}", e.getMessage(), e);
                    }
                    // 兜底：如果还没初始化成功，抛出运行时异常，提示用户配置
                    if (currentChatModel == null) {
                        throw new RuntimeException(
                                "No active CHAT model configured. Please configure it in the dashboard.");
                    }
                }
            }
        }
        return currentChatModel;
    }

    /**
     * 按模型配置ID 获取对话模型（对话智能体运行配置 modelConfigId）。
     *
     * <p>空 → 全局默认；配置不存在或非 CHAT 类型 → 降级全局默认并告警（不阻断对话）。
     */
    public ChatModelBase getOpenAIChatModel(Long modelConfigId) {
        if (modelConfigId == null) {
            return getOpenAIChatModel();
        }
        return configuredChatModels.computeIfAbsent(modelConfigId, id -> {
            ModelConfig config = modelConfigDataService.findById(id.intValue());
            // R-16：所选模型必须仍在启用集合内；被停用则回退默认模型并留下可诊断日志
            if (config == null || config.getModelType() != ModelType.CHAT || !Boolean.TRUE.equals(config.getIsActive())) {
                log.warn("所选对话模型不可用（不存在/非CHAT/已停用），回退默认模型: modelConfigId={}, 库中状态={}", id,
                    config == null ? "无记录" : ("active=" + config.getIsActive() + ",type=" + config.getModelType()));
                return getOpenAIChatModel();
            }
            log.info("初始化智能体指定对话模型: modelConfigId={}, modelName={}", id, config.getModelName());
            return OpenAIChatModel.builder()
                    .apiKey(config.getApiKey())
                    .modelName(config.getModelName())
                    .baseUrl(config.getBaseUrl())
                    .stream(true)
                    .formatter(new DeepSeekFormatter())
                    .generateOptions(GenerateOptions.builder()
                            .maxTokens(config.getMaxTokens())
                            .temperature(config.getTemperature())
                            .build())
                    .build();
        });
    }

    /**
     * 获取全局 EmbeddingModel（懒加载 + 双重检查锁缓存，无可用模型时使用 Dummy 兜底）
     */
    public EmbeddingModel getEmbeddingModel() {
        if (currentEmbeddingModel == null) {
            synchronized (this) {
                if (currentEmbeddingModel == null) {
                    log.info("Initializing global EmbeddingModel...");
                    try {
                        ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
                        if (config != null) {
                            currentEmbeddingModel = DashScopeTextEmbedding.builder()
                                    .apiKey(config.getApiKey())
                                    .modelName(config.getModelName())
                                    .dimensions(512)
                                    .build();
                        }
                    } catch (Exception e) {
                        log.error("Failed to initialize EmbeddingModel: {}", e.getMessage());
                    }
                }
            }
        }
        return currentEmbeddingModel;
    }

    /**
     * 获取多模态模型（图片理解；T-03 / R-06）。
     *
     * <p>**未配置或不可用时返回 null**，由调用方按 R-07 降级并显式告知；
     * 刻意**不回退 CHAT 模型**——那会造成"假装看过图"的回答（requirements R-07 明令禁止）。
     */
    public ChatModelBase getOpenAIMultimodalModel() {
        if (currentMultimodalModel == null) {
            synchronized (this) {
                if (currentMultimodalModel == null) {
                    try {
                        ModelConfigDTO config = modelConfigDataService.getDefaultConfigByType(ModelType.MULTIMODAL);
                        if (config == null) {
                            log.warn("未配置 MULTIMODAL 模型：带图请求将按 R-07 降级并显式告知");
                            return null;
                        }
                        log.info("多模态模型: configId={}, provider={}, modelName={}", config.getId(),
                                config.getProvider(), config.getModelName());
                        currentMultimodalModel = OpenAIChatModel.builder()
                                .apiKey(config.getApiKey())
                                .modelName(config.getModelName())
                                .baseUrl(config.getBaseUrl())
                                .stream(true)
                                .formatter(new DeepSeekFormatter())
                                .generateOptions(GenerateOptions.builder()
                                        .maxTokens(config.getMaxTokens())
                                        .temperature(config.getTemperature())
                                        .build())
                                .build();
                    } catch (Exception e) {
                        log.error("多模态模型初始化失败（将按 R-07 降级）: {}", e.getMessage());
                        return null;
                    }
                }
            }
        }
        return currentMultimodalModel;
    }

    /**
     * 刷新 Chat 缓存（用于热切换）
     */
    public void refreshChat() {
        this.currentChatModel = null;
        this.configuredChatModels.clear();
        log.info("Chat cache cleared.");
    }

    /**
     * 刷新 Embedding 缓存（用于热切换）
     */
    public void refreshEmbedding() {
        this.currentEmbeddingModel = null;
        log.info("Embedding cache cleared.");
    }

    /**
     * 刷新多模态模型缓存（用于热切换；T-03）
     */
    public void refreshMultimodal() {
        this.currentMultimodalModel = null;
        log.info("Multimodal cache cleared.");
    }

}
