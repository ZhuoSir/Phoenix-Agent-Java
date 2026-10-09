package com.phoenix.platform.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.phoenix.agent.harness.request.ConfirmRequest;
import com.phoenix.agent.harness.request.HarnessRequest;
import com.phoenix.agent.harness.send.HarnessChatService;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author burce.liu
 */
@Slf4j
@RestController
@AllArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api/front/harness")
public class HarnessFrontController {
    private final HarnessChatService harnessChatService;

    private final com.phoenix.agent.harness.turn.HarnessTurnManager turnManager;

    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<org.springframework.http.codec.ServerSentEvent<Map<String, Object>>> confirm(@RequestBody ConfirmRequest confirmRequest) {
        String userId = StpUtil.getLoginIdAsString();
        confirmRequest.setUserId(userId);
        confirmRequest.setChannel("front");
        return com.phoenix.agent.harness.sse.SseSupport.withHeartbeat(turnManager.confirmOrResume(confirmRequest.getSessionId(),
            () -> harnessChatService.confirmStream(confirmRequest).map(output -> {
            Map<String, Object> eventMap = new LinkedHashMap<>();
            eventMap.put("content", "");
            eventMap.put("end", false);
            if (output instanceof StreamingOutput<?> streamingOutput && streamingOutput.chunk() != null) {
                eventMap.put("content", streamingOutput.chunk());
            }
            if (!output.isEND()) {
                output.state().value("error_message", String.class).ifPresent(msg -> eventMap.putIfAbsent("content", msg));
                output.state().value("agent_event", AgentEvent.class).ifPresent(event -> {
                    if (event instanceof TextBlockDeltaEvent textEvent) {
                        eventMap.put("content", textEvent.getDelta());
                    } else if (event instanceof ThinkingBlockDeltaEvent thinkingEvent) {
                        eventMap.put("content", thinkingEvent.getDelta());
                    } else if (event instanceof ThinkingBlockStartEvent || event instanceof ThinkingBlockEndEvent) {
                        // 思考开始/结束事件，无需处理，静默忽略
                    } else if (!(event instanceof CustomEvent)) {
                        log.warn("Unhandled agent_event type in confirm: {}", event.getClass().getSimpleName());
                    }
                });
            }
            if (output.isEND()) {
                eventMap.put("end", true);
            }
            return eventMap;
        })));
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<org.springframework.http.codec.ServerSentEvent<Map<String, Object>>> harnessChat(@RequestBody HarnessRequest  harnessRequest) {
        String userId = StpUtil.getLoginIdAsString();
        HarnessRequest request = HarnessRequest.builder().userId(userId).sessionId(harnessRequest.getSessionId())
                .message(harnessRequest.getMessage())
                // BUG-155：必须转抄寻址字段并改走**统一寻址重载** stream(request) ——
                // 旧写法 stream(harnessRequest.getHarnessSn(), request) 走 sn-only 旧重载（恒静态加载器、
                // 不看 agentId），对库配置智能体（R-08 主路径）恒抛 NoSuchElementException ⇒ 500。
                // 与 HarnessController（admin 端点）保持同一范式。
                .agentId(harnessRequest.getAgentId())
                .harnessSn(harnessRequest.getHarnessSn())
                .enabledSkillIds(harnessRequest.getEnabledSkillIds())
                .attachmentIds(harnessRequest.getAttachmentIds())
                .channel("front")
                .build();
        return com.phoenix.agent.harness.sse.SseSupport.withHeartbeat(turnManager.openOrReject(harnessRequest.getSessionId(),
            () -> harnessChatService.stream(request)
                .map(output -> {
                    Map<String, Object> eventMap = new LinkedHashMap<>();
                    eventMap.put("content", "");
                    eventMap.put("end", false);
                    if (output instanceof StreamingOutput<?> streamingOutput && streamingOutput.chunk() != null) {
                        eventMap.put("content", streamingOutput.chunk());
                    }
                    if (output.isEND()) {
                        eventMap.put("end", true);
                    }
                    output.state().value("agent_event", AgentEvent.class).ifPresent(event -> {
                        if (event instanceof RequireUserConfirmEvent confirmEvent) {
                            eventMap.put("needConfirm", true);
                            eventMap.put("toolCalls", confirmEvent.getToolCalls());
                            List<Map<String, Object>> buttons = new ArrayList<>();
                            Map<String, Object> confirmBtn = new LinkedHashMap<>();
                            confirmBtn.put("text", "确认");
                            confirmBtn.put("action", "confirm");
                            confirmBtn.put("type", "primary");
                            buttons.add(confirmBtn);
                            Map<String, Object> cancelBtn = new LinkedHashMap<>();
                            cancelBtn.put("text", "取消");
                            cancelBtn.put("action", "cancel");
                            cancelBtn.put("type", "danger");
                            buttons.add(cancelBtn);
                            eventMap.put("buttons", buttons);
                        } else if (event instanceof TextBlockDeltaEvent textEvent) {
                            eventMap.put("content", textEvent.getDelta());
                        } else if (event instanceof ThinkingBlockDeltaEvent thinkingEvent) {
                            eventMap.put("content", "");
                        } else if (event instanceof ThinkingBlockStartEvent || event instanceof ThinkingBlockEndEvent) {
                            // 思考开始/结束事件，无需处理，静默忽略
                            eventMap.put("content", "");
                        } else if (!(event instanceof CustomEvent)) {
                            log.warn("Unhandled agent_event type: {}", event.getClass().getSimpleName());
                        }
                    });
                    return eventMap;
                })));
    }

}
