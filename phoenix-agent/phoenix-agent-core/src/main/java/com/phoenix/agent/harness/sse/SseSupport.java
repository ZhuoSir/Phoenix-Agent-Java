package com.phoenix.agent.harness.sse;

import reactor.core.publisher.Flux;
import org.springframework.http.codec.ServerSentEvent;

import java.time.Duration;
import java.util.Map;

/**
 * SSE 心跳封装（BUG-55）：数据流静默 ≥15s 注入 comment 帧（": ping"），
 * 防 nginx/网关 read-timeout 掐断长工具任务的 SSE；comment 行不是 data:，
 * 前端/旧客户端解析器天然忽略，零兼容成本。
 */
public final class SseSupport {

    private static final Duration HEARTBEAT = Duration.ofSeconds(15);

    private SseSupport() {
    }

    public static Flux<ServerSentEvent<Map<String, Object>>> withHeartbeat(Flux<Map<String, Object>> data) {
        Flux<ServerSentEvent<Map<String, Object>>> events = data
                .map(m -> (ServerSentEvent<Map<String, Object>>) ServerSentEvent.builder(m).build());
        return events.publish(shared -> Flux.merge(shared, Flux.interval(HEARTBEAT)
                .map(i -> (ServerSentEvent<Map<String, Object>>) ServerSentEvent.<Map<String, Object>>builder()
                        .comment("ping").build())
                .takeUntilOther(shared.ignoreElements())));
    }
}
