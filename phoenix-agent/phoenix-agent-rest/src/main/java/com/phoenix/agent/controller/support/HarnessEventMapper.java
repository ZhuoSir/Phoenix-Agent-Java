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
        // BL-19：本轮新产物文件（JSON 数组字符串），前端收到即刷新文件面板；旧客户端忽略该键无副作用
        output.state().value("agent_files", String.class).ifPresent(filesJson -> eventMap.put("agentFiles", filesJson));
        // thinking-display R-01：思考增量独立键（旧客户端只读 content，天然无感）
        output.state().value("thinking_text", String.class).ifPresent(t -> eventMap.put("thinking", t));
        // T-05：分批进度/告知（notice 独立键，不进 content）
        output.state().value("notice", String.class).ifPresent(n -> eventMap.put("notice", n));
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
            } else if (event instanceof io.agentscope.core.event.TextBlockStartEvent
                    || event instanceof io.agentscope.core.event.TextBlockEndEvent) {
                // BUG 修复：文本块的开始/结束事件无需处理（此前落到 else 打 `Unhandled agent_event type` 噪音 WARN）
            } else if (event instanceof ThinkingBlockStartEvent || event instanceof ThinkingBlockEndEvent) {
                // 思考开始/结束事件，无需处理
            } else if (event instanceof ThinkingBlockDeltaEvent thinkingEvent) {
                // 思考增量**必须透出**（此前被静默吞掉 ⇒ 前端拿不到 thinking，只能依赖 state 通道；
                // 一旦某条链路没带 thinking_text state 就彻底看不到思考）。与 state 通道同值时天然幂等。
                eventMap.put("thinking", thinkingEvent.getDelta());
            } else if (event instanceof CustomEvent) {
                // 自定义事件不透出（与后台既有行为一致）
            } else {
                // 生命周期类事件（AgentStart/AgentEnd、ModelCall*、ToolCall*、ToolResult*）本身无内容，
                // 未映射是**预期行为** ⇒ 静默；只有真正未知的类型才告警（避免刷屏误导现场排查）。
                String simple = event.getClass().getSimpleName();
                if (!(simple.startsWith("Agent") || simple.startsWith("ModelCall")
                        || simple.startsWith("ToolCall") || simple.startsWith("ToolResult"))) {
                    log.warn("Unhandled agent_event type: {}", simple);
                }
            }
        });
        return eventMap;
    }
}
