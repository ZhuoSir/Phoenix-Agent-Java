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

    /** BUG-77 根治：轮次开始前修复状态中缺失 reasoning 的 tool_use 消息 */
    private final com.phoenix.agent.harness.state.HarnessStateRepairService stateRepairService;


    public static final String STATUS_GENERATING = "generating";

    public static final String STATUS_DONE = "done";

    public static final String STATUS_TIMEOUT = "timeout";

    public static final String STATUS_CANCELLED = "cancelled";

    // ===== T-07（v1.7.0 R-01）：轮次阶段标记 =====
    /** 无在飞调用（帧间隔/待模型下一步） */
    public static final String PHASE_IDLE = "IDLE";
    /** 模型调用在飞（今天 503s 静默就发生在此阶段） */
    public static final String PHASE_MODEL = "MODEL";
    /** 工具/子代理执行中 */
    public static final String PHASE_TOOL = "TOOL";

    /** T-09：新增定稿状态——**模型调用首帧超时**（与既有 done/timeout/cancelled 并存） */
    public static final String STATUS_MODEL_TIMEOUT = "model_timeout";

    /** T-08：心跳最小间隔（节流 ≤1 帧/5s，防重演帧风暴） */
    private static final long HEARTBEAT_MIN_GAP_MS = 5000L;

    private static final int THINKING_CLIP = 65536;

    private final ChatMessageMapper chatMessageMapper;

    /** 总时长闸：默认 0=关闭（long-turn-resilience R-01/Q2 决议，DSH 轮次层无墙钟强杀）；>0 保留兜底语义 */
    @Value("${phoenix.agent.turn-timeout-seconds:0}")
    private long turnTimeoutSeconds;

    /** 空闲挂起闸：连续无帧超过此秒数判挂起（R-01/Q1=600s，DSH 工具等待上限对齐；idleWatchdog arm 语义） */
    @Value("${phoenix.agent.turn-idle-timeout-seconds:600}")
    private long turnIdleTimeoutSeconds;

    /** T-08：静默心跳阈值（秒）——静默达该值起按 ≤1 帧/5s 下发心跳帧 */
    @Value("${phoenix.agent.silence-heartbeat-seconds:15}")
    private long silenceHeartbeatSeconds;

    /** T-09：模型调用**首帧超时**（秒）——MODEL 阶段自调用发起起算；0=关闭 */
    @Value("${phoenix.agent.model-first-frame-timeout-seconds:180}")
    private long modelFirstFrameTimeoutSeconds;

    @Value("${phoenix.agent.turn-flush-seconds:5}")
    private long flushSeconds;

    @Value("${phoenix.agent.turn-buffer-frames:2000}")
    private int bufferFrames;

    private final Map<String, Turn> turns = new ConcurrentHashMap<>();

    /**
     * T-07：标记轮次阶段（由 {@code HarnessChatServiceImpl} 在模型/工具生命周期事件上回灌）。
     *
     * <p>用途：① 静默心跳按阶段给出"模型调用中/工具执行中"（T-08）；
     * ② 首帧超时**仅在 MODEL 阶段**生效，避免误杀长工具（T-09）。
     */
    /** 仅当当前阶段**不等于** avoidPhase（可空=不限制）时才切阶段（T-07 实测修正用）。 */
    public void onPhaseIfNot(String sessionId, String phase, String detail, String avoidPhase) {
        Turn turn = sessionId == null ? null : turns.get(sessionId);
        if (turn == null) {
            return;
        }
        if (avoidPhase != null && avoidPhase.equals(turn.phase)) {
            return;
        }
        onPhase(sessionId, phase, detail);
    }

    public void onPhase(String sessionId, String phase, String detail) {
        if (sessionId == null || phase == null) {
            return;
        }
        Turn turn = turns.get(sessionId);
        if (turn == null) {
            return;
        }
        boolean changed = !phase.equals(turn.phase);
        turn.phase = phase;
        turn.phaseDetail = detail;
        turn.phaseSince = System.currentTimeMillis();
        if (changed) {
            log.info("[turn-phase] session={} -> {}{}", sessionId, phase,
                    detail == null || detail.isBlank() ? "" : " (" + detail + ")");
        }
    }

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
        // BUG-77 根治：发请求前修复历史（仅 tool_use 无 thinking 的助手消息会被 provider 拒）
        stateRepairService.repairMissingReasoning(sessionId);
        turn.sourceSupplier = source; // BUG-77：留存供应商供自动重试
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
        stateRepairService.repairMissingReasoning(sessionId); // BUG-77 根治：续流同样先修复
        turn.sourceSupplier = source; // BUG-77：续流同样留存供应商
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

        /** R-01：每帧脉冲（=DSH idleWatchdog 的 arm 重置） */
        volatile long lastActivityAt = System.currentTimeMillis();

        /** T-07：当前阶段（IDLE/MODEL/TOOL）与其起始时刻；供静默心跳与首帧超时判定 */
        volatile String phase = PHASE_IDLE;

        volatile long phaseSince = System.currentTimeMillis();

        /** 阶段细节（工具名等，可空） */
        volatile String phaseDetail;

        /** T-08：心跳计数（进金丝雀体检行）与上次心跳时刻（节流用） */
        final AtomicLong heartbeats = new AtomicLong(0);

        volatile long lastHeartbeatAt = 0L;

        /** BUG-77：源流供应商（可再取一次 = 自动重试能力）与重试标记（每轮至多一次） */
        private Supplier<Flux<Map<String, Object>>> sourceSupplier;
        private volatile boolean retryUsed = false;

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
                    long now = System.currentTimeMillis();
                    Turn self = this;
                    // T-09 首帧超时：**仅 MODEL 阶段**（自调用发起起算）——避免误杀长工具
                    if (PHASE_MODEL.equals(phase) && modelFirstFrameTimeoutSeconds > 0
                            && now - phaseSince >= modelFirstFrameTimeoutSeconds * 1000L) {
                        HarnessTurnManager.this.turns.remove(self.sessionId, self);
                        self.finish(STATUS_MODEL_TIMEOUT, "\n\n⏱ 模型调用超时（" + modelFirstFrameTimeoutSeconds
                                + " 秒无任何响应），已中止本轮；已生成内容保留。可重试或检查模型服务连通性。");
                        return;
                    }
                    // T-08 静默心跳：静默达阈值 → 按 ≤1 帧/5s 下发进度帧（**不**脉冲 lastActivityAt，
                    // 保证空闲闸仍按"真实活动"计时；心跳自身独立计数进金丝雀）
                    long silentMs = now - lastActivityAt;
                    if (silentMs >= silenceHeartbeatSeconds * 1000L
                            && now - lastHeartbeatAt >= HEARTBEAT_MIN_GAP_MS && !sinkDone.get()) {
                        lastHeartbeatAt = now;
                        heartbeats.incrementAndGet();
                        Map<String, Object> beat = new java.util.HashMap<>();
                        beat.put("content", "");
                        beat.put("thinking", "");
                        beat.put("silenceMs", silentMs);
                        beat.put("phase", phase);
                        beat.put("phaseLabel", PHASE_TOOL.equals(phase) ? "工具执行中"
                                : (PHASE_MODEL.equals(phase) ? "模型调用中" : "等待响应中"));
                        if (phaseDetail != null && !phaseDetail.isBlank()) {
                            beat.put("phaseDetail", phaseDetail);
                        }
                        sink.emitNext(beat, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
                    }
                    // R-01 空闲闸（DSH idleWatchdog 语义）：连续无任何帧超阈值 → 判挂起定稿
                    if (now - lastActivityAt > turnIdleTimeoutSeconds * 1000L) {
                        HarnessTurnManager.this.turns.remove(self.sessionId, self);
                        self.finish(STATUS_TIMEOUT, "\n\n⚠️ 轮次挂起：连续 " + turnIdleTimeoutSeconds
                                + " 秒无任何模型/工具活动，已定稿至此内容（内容保留至最后增量）");
                        return;
                    }
                    // 总时长闸：默认 0=关闭（Q2 决议，DSH 轮次层无墙钟强杀）；>0 保留兜底
                    if (turnTimeoutSeconds > 0 && now - startedAt > turnTimeoutSeconds * 1000L) {
                        HarnessTurnManager.this.turns.remove(self.sessionId, self);
                        self.finish(STATUS_TIMEOUT, "\n\n⚠️ 生成超时（>" + turnTimeoutSeconds + "s），已定稿至此内容");
                        return;
                    }
                    flush();
                });
            // T-03b：100ms 文本合并闸——源帧可达 17 万/轮，合并后下发约 10 帧/秒
            this.mergeJanitor = Flux.interval(Duration.ofMillis(100), Schedulers.boundedElastic())
                .subscribe(t -> {
                    if (!sinkDone.get()) {
                        flushPendingText();
                    }
                });
        }

        /** BUG-69 金丝雀计数：帧总数/非空正文帧 */
        final AtomicLong framesSeen = new AtomicLong();
        final AtomicLong framesWithText = new AtomicLong();

        /** T-03b 帧风暴治理：文本/思考增量缓冲（~100ms 合并成一帧下发）与已下发帧计数 */
        final StringBuilder pendingText = new StringBuilder();
        final StringBuilder pendingThink = new StringBuilder();
        final AtomicLong framesEmitted = new AtomicLong();
        /** T-03b：丢弃的纯空生命周期帧计数（观测用） */
        final AtomicLong framesDropped = new AtomicLong();
        /** T-03b：随合并帧捎带的状态快照（技能/文件面板），避免因每帧携带而绕过合并 */
        private String pendingAgentFiles;
        private String pendingLoadedSkills;
        private Disposable mergeJanitor;

        /** T-03b：把缓冲的文本/思考增量合并成一帧下发（无缓冲则空操作） */
        private void flushPendingText() {
            if (pendingText.length() == 0 && pendingThink.length() == 0
                    && pendingAgentFiles == null && pendingLoadedSkills == null) {
                return;
            }
            Map<String, Object> merged = new LinkedHashMap<>(4);
            merged.put("content", pendingText.toString());
            merged.put("end", false);
            if (pendingThink.length() > 0) {
                merged.put("thinking", pendingThink.toString());
            }
            // T-03b：捎带状态快照（客户端幂等处理；不因每帧携带而绕过合并）
            if (pendingAgentFiles != null) {
                merged.put("agentFiles", pendingAgentFiles);
                pendingAgentFiles = null;
            }
            if (pendingLoadedSkills != null) {
                merged.put("loadedSkills", pendingLoadedSkills);
                pendingLoadedSkills = null;
            }
            pendingText.setLength(0);
            pendingThink.setLength(0);
            framesEmitted.incrementAndGet();
            sink.emitNext(merged, Sinks.EmitFailureHandler.busyLooping(Duration.ofMillis(100)));
        }

        /** T-03b：保序关键帧——这些帧的先后顺序对客户端语义重要，必须先冲批再透传 */
        private boolean needsOrderingFlush(Map<String, Object> frame) {
            return Boolean.TRUE.equals(frame.get("end")) || Boolean.TRUE.equals(frame.get("needConfirm"))
                    || frame.get("toolCalls") != null || frame.get("buttons") != null || frame.get("error") != null;
        }

        /** T-03b：纯文本/思考增量帧（无 end/确认/文件/技能/工具等语义键）→ 可合并推迟下发 */
        private boolean isPureTextDelta(Map<String, Object> frame) {
            if (Boolean.TRUE.equals(frame.get("end")) || Boolean.TRUE.equals(frame.get("needConfirm"))
                    || frame.get("toolCalls") != null || frame.get("buttons") != null
                    || frame.get("error") != null) {
                return false;
            }
            Object c = frame.get("content");
            Object t = frame.get("thinking");
            return (c instanceof String cs && !cs.isEmpty()) || (t instanceof String ts && !ts.isEmpty());
        }

        private void onFrame(Map<String, Object> frame) {
            try {
                lastActivityAt = System.currentTimeMillis();
                framesSeen.incrementAndGet();
                Object c = frame.get("content");
                if (c instanceof String s && !s.isEmpty()) {
                    framesWithText.incrementAndGet();
                    content.append(s);
                    pendingText.append(s);
                }
                Object th = frame.get("thinking");
                if (th instanceof String s && !s.isEmpty()) {
                    if (thinkStart.get() == 0) {
                        thinkStart.set(System.currentTimeMillis());
                    }
                    thinking.append(s);
                    pendingThink.append(s);
                }
                Object af = frame.get("agentFiles");
                if (af instanceof String afs && !afs.isEmpty()) {
                    pendingAgentFiles = afs;
                }
                Object ls = frame.get("loadedSkills");
                if (ls instanceof String lss && !lss.isEmpty()) {
                    pendingLoadedSkills = lss;
                }
                if (Boolean.TRUE.equals(frame.get("needConfirm"))) {
                    awaitingConfirm.set(true);
                }
                boolean isEnd = Boolean.TRUE.equals(frame.get("end"));
                if (isEnd && awaitingConfirm.get()) {
                    return; // P5：待确认期抑制 end，sink 保开
                }
                // T-03b：纯增量帧只入缓冲（由合并闸 ~100ms 下发）；语义帧先冲批再原样透传（保序保语义）
                if (isPureTextDelta(frame)) {
                    return;
                }
                // T-03b：纯空生命周期帧（无内容、无语义、无快照）对客户端零信息量 → 丢弃，不再下发
                if (!needsOrderingFlush(frame) && (c == null || !(c instanceof String cs2) || cs2.isEmpty())
                        && (th == null || !(th instanceof String ts2) || ts2.isEmpty())
                        && pendingAgentFiles == null && pendingLoadedSkills == null) {
                    framesDropped.incrementAndGet();
                    return;
                }
                // T-03b：只有保序关键帧才冲批；其余帧不打断合并节奏
                if (needsOrderingFlush(frame)) {
                    flushPendingText();
                }
                framesEmitted.incrementAndGet();
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

        /** BUG-77：思考模式 reasoning_content 未回传/400 类 provider 错误 → 可原样重试（实测重试即成功） */
        private boolean isRetryableProviderError(Throwable err) {
            String msg = err.toString() == null ? "" : err.toString();
            return msg.contains("reasoning_content") || msg.contains("BadRequestException")
                    || msg.contains("HTTP request failed with status 400");
        }

        private void onError(Throwable err) {
            log.warn("[turn] 源流异常 session={}: {}", sessionId, err.toString());
            // BUG-77：provider 4xx（典型 reasoning_content 未回传）且本轮尚无任何产出 → 自动重试一次，
            // 让用户感知不到这次中断；仍失败才按原逻辑定稿（内容保留）
            if (!retryUsed && isRetryableProviderError(err) && content.length() == 0 && thinking.length() == 0
                    && sourceSupplier != null && !sinkDone.get()) {
                retryUsed = true;
                log.warn("[turn] 识别为可重试的 provider 错误，自动重试一次 session={} turnId={}", sessionId, turnId);
                try {
                    subscribeCommon(sourceSupplier.get());
                    return;
                }
                catch (Exception e) {
                    log.warn("[turn] 自动重试订阅失败，转为定稿 session={}: {}", sessionId, e.toString());
                }
            }
            // R-04 文案分类：模型/流错误类；BUG-77 起带上 provider 原文（截断），免去翻日志
            String detail = err.getMessage() == null ? "" : err.getMessage().replaceAll("\\s+", " ").trim();
            if (detail.length() > 300) {
                detail = detail.substring(0, 300) + "…";
            }
            finish(STATUS_TIMEOUT, "\n\n⚠️ 模型或流错误中断：" + err.getClass().getSimpleName()
                    + (detail.isEmpty() ? "" : "：" + detail) + "（内容保留至最后增量）");
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
            // BUG-69 金丝雀：每轮一行体检；contentLen>0 而 textFrames=0 即用户所见与落库背离，告警级
            long cl = content.length(), tf = framesWithText.get(), fs = framesSeen.get(), fe = framesEmitted.get();
            long fd = framesDropped.get();
            if (cl > 0 && tf == 0) {
                log.warn("[b69-canary] 背离告警: session={} status={} contentLen={} frames={} emitted={} textFrames=0",
                        sessionId, status, cl, fs, fe);
            }
            else {
                log.info("[b69-canary] session={} status={} contentLen={} frames={} emitted={} dropped={} "
                        + "textFrames={} heartbeats={}",
                        sessionId, status, cl, fs, fe, fd, tf, heartbeats.get());
            }
            sinkDone.set(true);
            if (janitor != null) {
                janitor.dispose();
            }
            if (mergeJanitor != null) {
                mergeJanitor.dispose();
            }
            // T-03b：定稿前冲批，确保最后一段增量不丢
            try {
                flushPendingText();
            }
            catch (Exception ignored) {
                // sink 可能已关闭，忽略
            }
            if (sourceSub != null) {
                sourceSub.dispose();
            }
            turns.remove(sessionId, this);
            if (status.equals(STATUS_TIMEOUT) || status.equals(STATUS_CANCELLED)
                    || status.equals(STATUS_MODEL_TIMEOUT)) {
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
