package com.phoenix.agent.service.harness;

import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.entity.ModelConfig;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import io.agentscope.core.embedding.EmbeddingModel;
import io.agentscope.core.embedding.dashscope.DashScopeTextEmbedding;
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
    private volatile OpenAIChatModel currentChatModel;
    /** 指定模型配置ID 的对话模型缓存（R-03：智能体可自选模型） */
    private final Map<Long, OpenAIChatModel> configuredChatModels = new ConcurrentHashMap<>();

    /**
     * 获取全局 ChatClient（懒加载 + 双重检查锁缓存）
     */
    public OpenAIChatModel getOpenAIChatModel() {
        if (currentChatModel == null) {
            synchronized (this) {
                if (currentChatModel == null) {
                    log.info("Initializing global ChatClient...");
                    try {
                        ModelConfigDTO config = modelConfigDataService.getActiveConfigByType(ModelType.CHAT);
                        if (config != null) {
                            currentChatModel = OpenAIChatModel.builder()
                                    .apiKey(config.getApiKey())
                                    .modelName(config.getModelName())
                                    .baseUrl(config.getBaseUrl())
                                    .stream(true)
                                    .formatter(new DeepSeekFormatter())
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
    public OpenAIChatModel getOpenAIChatModel(Long modelConfigId) {
        if (modelConfigId == null) {
            return getOpenAIChatModel();
        }
        return configuredChatModels.computeIfAbsent(modelConfigId, id -> {
            ModelConfig config = modelConfigDataService.findById(id.intValue());
            if (config == null || config.getModelType() != ModelType.CHAT) {
                log.warn("模型配置不可用于对话，回退全局默认模型: modelConfigId={}", id);
                return getOpenAIChatModel();
            }
            log.info("初始化智能体指定对话模型: modelConfigId={}, modelName={}", id, config.getModelName());
            return OpenAIChatModel.builder()
                    .apiKey(config.getApiKey())
                    .modelName(config.getModelName())
                    .baseUrl(config.getBaseUrl())
                    .stream(true)
                    .formatter(new DeepSeekFormatter())
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

}
