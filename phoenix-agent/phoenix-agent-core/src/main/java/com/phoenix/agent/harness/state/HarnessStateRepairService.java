package com.phoenix.agent.harness.state;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mybatisflex.core.row.Db;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * BUG-77 根治：轮次开始前修复历史中「仅 tool_use、无 thinking 块」的助手消息。
 *
 * <p>背景：DeepSeek 思考模式硬性要求——带 tool_calls 的助手消息必须回传 reasoning_content。
 * 中断/重启后框架 pending-tool-recovery 合成的 tool_use 消息不带 thinking 块，导致后续请求 400
 * （{@code The reasoning_content in the thinking mode must be passed back to the API}）。
 *
 * <p>做法：直接修复框架状态表 {@code tbl_harness_store_state} 的 JSON——为这类消息补一个
 * thinking 块（占位文案），使报文满足 provider 要求；失败不阻断轮次（自动重试仍是兜底）。
 */
@Slf4j
@Component
public class HarnessStateRepairService {

    /** reasoning 占位文案（必须非空，provider 需要该字段有值） */
    private static final String PLACEHOLDER = "[reasoning unavailable: turn was interrupted]";

    private static final String SELECT_SQL = "select state_data from tbl_harness_store_state "
            + "where session_id like ? and state_key = 'agent_state' order by updated_at desc limit 1";

    private static final String UPDATE_SQL = "update tbl_harness_store_state set state_data = ?, updated_at = now() "
            + "where session_id like ? and state_key = 'agent_state'";

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 修复指定会话状态中缺失 reasoning 的 tool_use 助手消息。
     *
     * @return 实际修复的消息条数（0=无需修复或修复失败）
     */
    public int repairMissingReasoning(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return 0;
        }
        try {
            Object raw = Db.selectObject(SELECT_SQL, "%:" + sessionId);
            String data = raw == null ? null : String.valueOf(raw);
            if (data == null || data.isEmpty()) {
                return 0;
            }
            JsonNode root = mapper.readTree(data);
            JsonNode context = root.get("context");
            if (context == null || !context.isArray()) {
                return 0;
            }
            int fixed = 0;
            for (JsonNode msg : context) {
                if (!"ASSISTANT".equals(msg.path("role").asText())) {
                    continue;
                }
                JsonNode content = msg.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                boolean hasToolUse = false;
                boolean hasThinking = false;
                for (JsonNode block : content) {
                    String type = block.path("type").asText();
                    if ("tool_use".equals(type)) {
                        hasToolUse = true;
                    }
                    else if ("thinking".equals(type)) {
                        hasThinking = true;
                    }
                }
                if (hasToolUse && !hasThinking) {
                    ObjectNode thinking = mapper.createObjectNode();
                    thinking.put("type", "thinking");
                    thinking.put("thinking", PLACEHOLDER);
                    thinking.set("metadata", mapper.nullNode());
                    ((ArrayNode) content).insert(0, thinking);
                    fixed++;
                }
            }
            if (fixed > 0) {
                Db.updateBySql(UPDATE_SQL, mapper.writeValueAsString(root), "%:" + sessionId);
                log.warn("[state-repair] session={} 补全 {} 条缺失 reasoning 的 tool_use 助手消息（BUG-77 根治）",
                        sessionId, fixed);
            }
            return fixed;
        }
        catch (Exception e) {
            // 修复失败不阻断轮次：自动重试（onError 签名识别）仍可兜底
            log.warn("[state-repair] 修复失败（忽略） session={}: {}", sessionId, e.toString());
            return 0;
        }
    }
}
