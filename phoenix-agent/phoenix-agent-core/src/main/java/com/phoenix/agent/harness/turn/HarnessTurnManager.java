package com.phoenix.agent.harness.turn;

import com.phoenix.data.entity.ChatMessage;
import com.phoenix.data.mapper.ChatMessageMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * 断线续传轮次管理器（detached-stream T-02）。
 *
 * 核心：轮次源流的订阅权归本管理器（自持），HTTP/SSE 消费者只是 sink 的附加观察者——
 * 客户端断开仅退订自己，执行继续（R-01/R-06 断≠停）。assistant 消息行由本管理器
 * 开轮即插（status=generating）、每 5s 增量刷、终态覆盖定稿（R-02/R-05，同一行）。
 * HITL：end 帧在待确认期被抑制、sink 保持开，confirm 续流并回同一轮（R-08/P5）。
 * 缓冲为进程内 replay sink（plan §0 拍板）；重启丢在跑轮次由启动清扫兜底标 timeout。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessTurnManager {

    public static final String STATUS_GENERATING = "generating";

    public static final String STATUS_DONE = "done";

    public static final String STATUS_TIMEOUT = "timeout";

    public static final String STATUS_CANCELLED = "cancelled";

    private static final int THINKING_CLIP = 65536;

    private final ChatMessageMapper chatMessageMapper;

    @Value("${phoenix.agent.turn-timeout-seconds:600}")
    private long turnTimeoutSeconds;

    @Value("${phoenix.agent.turn-flush-seconds:5}")
    private long flushSeconds;

    @Value("${phoenix.agent.turn-buffer-frames:2000}")
    private int bufferFrames;

    private final Map<String, Turn> turns = new ConcurrentHashMap<>();

    /** 重启残留清扫（Non-goals：在跑轮次接受丢失，标 timeout 由 A′/DB 可见） */
    @PostConstruct
    public void sweepStaleGenerations() {
        try {
            // metadata 列在 PG 实为 jsonb（实体按 String 映射）——纯 jsonb 语法，NULL 安全
            int n = com.mybatisflex.core.row.Db.updateBySql(
                "UPDATE tbl_data_chat_message SET "
                    + "metadata = COALESCE(jsonb_set(metadata, '{status}', '\"timeout\"'), '{\"status\":\"timeout\"}'::jsonb), "
                    + "content = COALESCE(content,'') || E'\\n\\n⚠️ 服务重启，本轮生成被中断（内容保留至最后增量）' "
                    + "WHERE role='assistant' AND metadata->>'status'='generating'");
            if (n > 0) {
                log.warn("[turn] 启动清扫遗留 generating 行: {}", n);
            }
        }
        catch (Exception e) {
            log.warn("[turn] 启动清扫失败（忽略）: {}", e.toString());
        }
    }

    // ============ 对外 API ============

    /**
     * 开轮（chat 入口）。已有进行中轮次 → 返回 P6 拒绝单帧流（plan P6 拍板：拒绝+提示，不排队）。
     */
    public Flux<Map<String, Object>> openOrReject(String sessionId, Supplier<Flux<Map<String, Object>>> source) {
        Turn probe = turns.get(sessionId);
        if (probe != null) {
            log.info("[turn] 拒绝新开（P6 一轮一约束）session={}", sessionId);
            Map<String, Object> rej = new LinkedHashMap<>();
            rej.put("content", "\n⚠️ 上一轮仍在生成中（可点击「停止」后重试）。");
            rej.put("end", true);
            return Flux.just(rej);
        }
        Turn turn = new Turn(sessionId);
        turns.put(sessionId, turn);
        try {
            turn.messageId = insertRow(turn);
        }
        catch (Exception e) {
            turns.remove(sessionId);
            throw e;
        }
        turn.wire(source.get());
        return turn.sink.asFlux();
    }

    /**
     * 确认续流（confirm 入口）：同轮并流（P5）；轮已不在（过期/重启）则按新轮开。
     */
    public Flux<Map<String, Object>> confirmOrResume(String sessionId, Supplier<Flux<Map<String, Object>>> source) {
        Turn turn = turns.get(sessionId);
        if (turn == null) {
            log.warn("[turn] confirm 时轮次已不在（超时/取消/重启），按新轮处理 session={}", sessionId);
            return openOrReject(sessionId, source);
        }
        log.info("[turn] confirm 并轮回原轮 session={} turnId={}", sessionId, turn.turnId);
        turn.awaitingConfirm.set(false);
        turn.wireConfirm(source.get());
        return turn.sink.asFlux();
    }

    /** 追流（join 入口）：replay 全量 + live；无进行中轮 → 单 end 帧（前端转重读消息）。 */
    public Flux<Map<String, Object>> join(String sessionId) {
        Turn turn = turns.get(sessionId);
        if (turn == null) {
            Map<String, Object> end = new LinkedHashMap<>();
            end.put("content", "");
            end.put("end", true);
            return Flux.just(end);
        }
        return turn.sink.asFlux();
    }

    public boolean hasActive(String sessionId) {
        return turns.containsKey(sessionId);
    }

    /** 显式停止（R-06）：取消源订阅、行定稿标「已取消」。 */
    public boolean cancel(String sessionId) {
        Turn turn = turns.remove(sessionId);
        if (turn == null) {
            return false;
        }
        turn.finish(STATUS_CANCELLED, "\n\n⏹ 已按用户指令停止生成");
        return true;
    }

    // ============ 内部 ============

    private Long insertRow(Turn turn) {
        ChatMessage row = new ChatMessage();
        row.setSessionId(turn.sessionId);
        row.setRole("assistant");
        row.setContent("");
        row.setMessageType("text");
        row.setMetadata(metaJson(turn, STATUS_GENERATING));
        row.setCreateTime(java.time.LocalDateTime.now());
        chatMessageMapper.insert(row);
        return row.getId();
    }

    private String metaJson(Turn turn, String status) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"status\":\"").append(status).append("\",\"turnId\":\"").append(turn.turnId).append('"');
        String th = turn.thinking.toString();
        if (!th.isEmpty()) {
            sb.append(",\"thinking\":\"").append(escape(jsonClip(th))).append('"');
            sb.append(",\"thinkingMs\":").append(turn.thinkingMs.get());
        }
        sb.append('}');
        return sb.toString();
    }

    private static String jsonClip(String s) {
        return s.length() > THINKING_CLIP ? s.substring(0, THINKING_CLIP) : s;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
    }

    private final class Turn {

        final String sessionId;

        final String turnId = UUID.randomUUID().toString();

        final Sinks.Many<Map<String, Object>> sink = Sinks.many().replay().limit(bufferFrames);

        final StringBuilder content = new StringBuilder();

        final StringBuilder thinking = new StringBuilder();

        final AtomicLong thinkingMs = new AtomicLong(0);

        final AtomicLong thinkStart = new AtomicLong(0);

        final AtomicBoolean awaitingConfirm = new AtomicBoolean(false);

        final AtomicBoolean sinkDone = new AtomicBoolean(false);

        volatile boolean endForwarded = false;

        final long startedAt = System.currentTimeMillis();

        volatile Long messageId;

        volatile Disposable sourceSub;

        volatile Disposable janitor;

        Turn(String sessionId) {
            this.sessionId = sessionId;
        }

        void wire(Flux<Map<String, Object>> source) {
            subscribeCommon(source);
            startFlusherAndWatchdog();
        }

        void wireConfirm(Flux<Map<String, Object>> source) {
            subscribeCommon(source);
        }

        private void subscribeCommon(Flux<Map<String, Object>> source) {
            this.sourceSub = source.subscribe(this::onFrame, this::onError, this::onSourceEnd);
        }

        private void startFlusherAndWatchdog() {
            this.janitor = Flux.interval(Duration.ofSeconds(flushSeconds), Schedulers.boundedElastic())
                .subscribe(t -> {
                    if (System.currentTimeMillis() - startedAt > turnTimeoutSeconds * 1000L) {
                        Turn self = this;
                        HarnessTurnManager.this.turns.remove(self.sessionId, self);
                        self.finish(STATUS_TIMEOUT, "\n\n⚠️ 生成超时（>" + turnTimeoutSeconds + "s），已定稿至此内容");
                        return;
                    }
                    flush();
                });
        }

        private void onFrame(Map<String, Object> frame) {
            try {
                Object c = frame.get("content");
                if (c instanceof String s && !s.isEmpty()) {
                    content.append(s);
                }
                Object th = frame.get("thinking");
                if (th instanceof String s && !s.isEmpty()) {
                    if (thinkStart.get() == 0) {
                        thinkStart.set(System.currentTimeMillis());
                    }
                    thinking.append(s);
                }
                if (Boolean.TRUE.equals(frame.get("needConfirm"))) {
                    awaitingConfirm.set(true);
                }
                boolean isEnd = Boolean.TRUE.equals(frame.get("end"));
                if (isEnd && awaitingConfirm.get()) {
                    return; // P5：待确认期抑制 end，sink 保开
                }
                sink.emitNext(frame, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
                if (isEnd) {
                    endForwarded = true; // 双 end 修复：透传过真 end 不再补发合成帧
                    finish(STATUS_DONE, null);
                }
            }
            catch (Exception e) {
                log.warn("[turn] 帧处理异常(忽略) session={}: {}", sessionId, e.toString());
            }
        }

        private void onSourceEnd() {
            if (awaitingConfirm.get()) {
                return; // 等 confirm 续流，行保持 generating、sink 不关
            }
            finish(STATUS_DONE, null);
        }

        private void onError(Throwable err) {
            log.warn("[turn] 源流异常 session={}: {}", sessionId, err.toString());
            finish(STATUS_TIMEOUT, "\n\n⚠️ 生成异常中断：" + err.getClass().getSimpleName());
        }

        private synchronized void flush() {
            if (messageId == null) {
                return;
            }
            try {
                ChatMessage row = new ChatMessage();
                row.setId(messageId);
                row.setContent(content.toString());
                if (thinkStart.get() > 0 && thinkingMs.get() == 0 && content.length() > 0) {
                    thinkingMs.set(System.currentTimeMillis() - thinkStart.get());
                }
                row.setMetadata(metaJson(this, STATUS_GENERATING));
                chatMessageMapper.update(row);
            }
            catch (Exception e) {
                log.warn("[turn] 增量刷失败(容忍) session={}: {}", sessionId, e.toString());
            }
        }

        synchronized void finish(String status, String suffix) {
            if (sinkDone.get()) {
                return;
            }
            sinkDone.set(true);
            if (janitor != null) {
                janitor.dispose();
            }
            if (sourceSub != null) {
                sourceSub.dispose();
            }
            turns.remove(sessionId, this);
            if (status.equals(STATUS_TIMEOUT) || status.equals(STATUS_CANCELLED)) {
                content.append(suffix == null ? "" : suffix);
            }
            // 模型偶发「工具全执行完但跳过收尾正文」（BUG-59 三报）：诚实注记兜底，不让用户面对空气泡
            if (status.equals(STATUS_DONE) && content.length() == 0 && thinking.length() > 0) {
                content.append("ℹ️ 本轮已完成工具执行但未输出正文总结（产物见文件面板）。如需说明请追问。");
            }
            if (messageId != null) {
                try {
                    ChatMessage row = new ChatMessage();
                    row.setId(messageId);
                    row.setContent(content.toString());
                    if (thinkStart.get() > 0 && thinkingMs.get() == 0) {
                        thinkingMs.set(System.currentTimeMillis() - thinkStart.get());
                    }
                    row.setMetadata(metaJson(this, status));
                    chatMessageMapper.update(row);
                }
                catch (Exception e) {
                    log.error("[turn] 定稿落库失败 session={} id={}", sessionId, messageId, e);
                }
            }
            if (!status.equals(STATUS_CANCELLED) && !endForwarded) {
                try {
                    Map<String, Object> end = new LinkedHashMap<>();
                    end.put("content", "");
                    end.put("end", true);
                    sink.emitNext(end, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
                }
                catch (Exception ignored) {
                }
            }
            sink.tryEmitComplete();
        }

    }

}
