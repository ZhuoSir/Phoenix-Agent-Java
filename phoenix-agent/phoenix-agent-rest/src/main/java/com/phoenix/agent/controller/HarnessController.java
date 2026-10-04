package com.phoenix.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoenix.agent.harness.request.ConfirmRequest;
import com.phoenix.agent.harness.request.HarnessRequest;
import com.phoenix.agent.controller.support.HarnessEventMapper;
import com.phoenix.agent.harness.sse.SseSupport;
import org.springframework.http.codec.ServerSentEvent;
import com.phoenix.agent.harness.send.HarnessChatService;
import com.phoenix.privilege.entity.PrivilegeUser;
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

import static com.phoenix.privilege.constant.CommonConstant.LOGIN_USER_INFO;
import com.phoenix.tools.vo.ReturnVo;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * @author burce.liu
 */
@Slf4j
@RestController
@AllArgsConstructor
@CrossOrigin(origins = "*")
@RequestMapping("/api/admin/harness")
public class HarnessController {
    private final HarnessChatService harnessChatService;

    private final com.phoenix.agent.harness.turn.HarnessTurnManager turnManager;


    @PostMapping(value = "/confirm", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> confirm(@RequestBody ConfirmRequest confirmRequest) {
        String userId = StpUtil.getLoginIdAsString();
        confirmRequest.setUserId(userId);
        confirmRequest.setChannel("admin");
        // 诊断留痕（确认链路排障期）：入参原样记录
        log.info("[confirm-in] session={} agentId={} allowed={} user={}", confirmRequest.getSessionId(),
                confirmRequest.getAgentId(), confirmRequest.isAllowed(), userId);
        return SseSupport.withHeartbeat(turnManager.confirmOrResume(confirmRequest.getSessionId(),
                () -> harnessChatService.confirmStream(confirmRequest).map(output -> {
            Map<String, Object> eventMap = new LinkedHashMap<>();
            eventMap.put("content", "");
            eventMap.put("end", false);
            // detached-stream 收口补洞：admin confirm 流同步 thinking 通道（原缺，与 chat 流对齐）
            output.state().value("thinking_text", String.class).ifPresent(th -> eventMap.put("thinking", th));
            if (output instanceof StreamingOutput<?> streamingOutput && streamingOutput.chunk() != null) {
                eventMap.put("content", streamingOutput.chunk());
            }
            if (!output.isEND()) {
                output.state().value("error_message", String.class).ifPresent(msg -> eventMap.putIfAbsent("content", msg));
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

    /** 断线续传（detached-stream T-03）：追流 join——重放全帧+live */
    @GetMapping(value = "/turn/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> turnJoin(@RequestParam String sessionId) {
        return SseSupport.withHeartbeat(turnManager.join(sessionId));
    }

    /** 会话是否有进行中轮次 */
    @GetMapping("/turn/status")
    public ReturnVo<Boolean> turnStatus(@RequestParam String sessionId) {
        return ReturnVo.ok(turnManager.hasActive(sessionId));
    }

    /** 显式停止（R-06：断≠停，停止走这里） */
    @PostMapping("/turn/cancel")
    public ReturnVo<Boolean> turnCancel(@RequestParam String sessionId) {
        return ReturnVo.ok(turnManager.cancel(sessionId));
    }

    /**
     * 后台对话（R-08 寻址）：传 agentId 走运行时注册表（库配置智能体）；
     * 传 harnessSn 为存量兼容路径，二者都传时 agentId 优先。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> harnessChat(@RequestBody HarnessRequest  harnessRequest) {
        String userId = StpUtil.getLoginIdAsString();
        HarnessRequest request = HarnessRequest.builder().userId(userId).sessionId(harnessRequest.getSessionId())
                .message(harnessRequest.getMessage())
                .harnessSn(harnessRequest.getHarnessSn())
                .agentId(harnessRequest.getAgentId())
                .enabledSkillIds(harnessRequest.getEnabledSkillIds())
                .channel("admin")
                .build();
        return SseSupport.withHeartbeat(turnManager.openOrReject(harnessRequest.getSessionId(),
                () -> harnessChatService.stream(request).map(HarnessEventMapper::toEventMap)));
    }


    private PrivilegeUser getCurrentUser() {
        Object value = StpUtil.getSession().get(LOGIN_USER_INFO);
        if (value instanceof PrivilegeUser pUser) {
            return pUser;
        }
        ObjectMapper mapper = new ObjectMapper();
        if (value instanceof Map<?, ?> map) {
            return mapper.convertValue(map, PrivilegeUser.class);
        }
        if (value instanceof String str) {
            try {
                return mapper.readValue(str, PrivilegeUser.class);
            } catch (Exception e) {
                log.warn("Failed to parse PrivilegeUser from session string", e);
            }
        }
        return null;
    }
}
