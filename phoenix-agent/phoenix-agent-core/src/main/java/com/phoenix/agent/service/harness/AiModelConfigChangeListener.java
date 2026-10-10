package com.phoenix.agent.service.harness;

import com.phoenix.data.event.AiModelConfigChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 模型配置变更订阅者（T-06）：清 AgentScope 侧模型缓存 + 递增实例纪元。
 *
 * <p>数据域的 {@code AiModelRegistry} 由发布方自己刷新；此处只处理 agent 模块的缓存，
 * 用事件解耦避免 phoenix-data 反向依赖 phoenix-agent。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelConfigChangeListener {

    private final HarnessModelRegistry harnessModelRegistry;

    private final AiModelConfigEpoch aiModelConfigEpoch;

    @EventListener
    public void onModelConfigChanged(AiModelConfigChangedEvent event) {
        harnessModelRegistry.refreshChat();
        harnessModelRegistry.refreshEmbedding();
        // T-03：多模态模型同样需要热切换（否则管理页改了 MULTIMODAL 行不生效）
        harnessModelRegistry.refreshMultimodal();
        aiModelConfigEpoch.bump(event.getModelType(), event.getConfigId());
    }
}
