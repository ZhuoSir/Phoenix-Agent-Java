package com.phoenix.data.event;

import org.springframework.context.ApplicationEvent;

/**
 * 模型配置发生变更（启用/停用/设默认/更新/删除）事件。
 *
 * <p>发布方：phoenix-data（模型配置的主人）。订阅方：各运行时按需清缓存 ——
 * 数据域自己的 {@code AiModelRegistry} 由服务内直接刷新，而 AgentScope 侧的
 * {@code HarnessModelRegistry} 与「按智能体缓存的运行时实例」在 agent 模块，
 * 用事件解耦避免 data → agent 的反向依赖。
 */
public class AiModelConfigChangedEvent extends ApplicationEvent {

    /** 变更涉及的模型类型编码（CHAT/EMBEDDING/AUDIO），可为 null 表示不区分 */
    private final String modelType;

    /** 被变更的配置ID，可为 null */
    private final Integer configId;

    public AiModelConfigChangedEvent(Object source, String modelType, Integer configId) {
        super(source);
        this.modelType = modelType;
        this.configId = configId;
    }

    public String getModelType() {
        return modelType;
    }

    public Integer getConfigId() {
        return configId;
    }
}
