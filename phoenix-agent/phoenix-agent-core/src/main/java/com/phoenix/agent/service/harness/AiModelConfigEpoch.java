package com.phoenix.agent.service.harness;

import java.util.concurrent.atomic.AtomicLong;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模型配置变更纪元（T-06）。
 *
 * <p>「换默认模型」不落在任何智能体的运行配置里，因此不进实例指纹；若不做处理，
 * 已缓存的运行时实例会一直用旧模型，违反「换默认后新请求即生效」。
 * 故把纪元纳入指纹：纪元 +1 → 全部实例下次访问时重建。
 */
@Slf4j
@Component
public class AiModelConfigEpoch {

    private final AtomicLong epoch = new AtomicLong();

    public long current() {
        return epoch.get();
    }

    public void bump(String modelType, Integer configId) {
        long now = epoch.incrementAndGet();
        log.info("模型配置变更，运行时实例纪元递增: modelType={}, configId={}, epoch={}", modelType, configId, now);
    }
}
