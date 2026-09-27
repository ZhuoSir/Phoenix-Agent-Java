package com.phoenix.agent.controller.support;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.CustomEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockEndEvent;
import io.agentscope.core.event.ThinkingBlockStartEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Harness SSE 事件 → 前端事件 Map 的统一映射（后台/前台通道共用，防两处行为漂移）。
 */
@Slf4j
public final class HarnessEventMapper {

    private HarnessEventMapper() {
    }

    public static Map<String, Object> toEventMap(NodeOutput output) {
        Map<String, Object> eventMap = new LinkedHashMap<>();
        eventMap.put("content", "");
        eventMap.put("end", false);
        if (output instanceof StreamingOutput<?> streamingOutput && streamingOutput.chunk() != null) {
            eventMap.put("content", streamingOutput.chunk());
        }
        if (output.isEND()) {
            eventMap.put("end", true);
        }
        // 显式技能：拒绝原因（R-05）与本轮实际加载技能（R-05 可见性）
        output.state().value("error_message", String.class).ifPresent(msg -> eventMap.putIfAbsent("content", msg));
        output.state().value("loaded_skills", String.class).ifPresent(skills -> eventMap.put("loadedSkills", skills));
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
            } else if (event instanceof ThinkingBlockStartEvent || event instanceof ThinkingBlockEndEvent) {
                // 思考开始/结束事件，无需处理
            } else if (event instanceof ThinkingBlockDeltaEvent || event instanceof CustomEvent) {
                // 思考增量与自定义事件不透出（与后台既有行为一致）
            } else {
                log.warn("Unhandled agent_event type: {}", event.getClass().getSimpleName());
            }
        });
        return eventMap;
    }
}
