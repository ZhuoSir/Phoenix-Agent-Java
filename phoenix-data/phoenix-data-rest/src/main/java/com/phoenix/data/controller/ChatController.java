package com.phoenix.data.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.data.dto.ChatMessageDTO;
import com.phoenix.data.entity.ChatMessage;
import com.phoenix.data.entity.ChatSession;
import com.phoenix.data.service.chat.ChatMessageService;
import com.phoenix.data.service.chat.ChatSessionService;
import com.phoenix.data.service.chat.SessionTitleService;
import com.phoenix.data.util.ReportTemplateUtil;
import com.phoenix.data.vo.ApiResponse;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Chat Controller
 */
@Slf4j
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class ChatController {

    private final ChatSessionService chatSessionService;

    private final ChatMessageService chatMessageService;

    private final SessionTitleService sessionTitleService;

    private final ReportTemplateUtil reportTemplateUtil;

    /** T-06（共享面 S9）：附件↔消息绑定 */
    private final com.phoenix.data.service.chat.ChatAttachmentService chatAttachmentService;

    /** T-06：超管判定复用 R-08/R-17/R-18 同一守卫（单一口径） */
    private final com.phoenix.data.component.AdminRoleGuard adminRoleGuard;

    /** metadata 解析用（复用实例，避免每次新建） */
    private static final com.fasterxml.jackson.databind.ObjectMapper METADATA_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * Get session list for an agent
     */
    @GetMapping("/agent/{id}/sessions")
    public ReturnVo<List<ChatSession>> getAgentSessions(@PathVariable(value = "id") Integer id,
            @RequestParam(value = "scope", required = false) String scope) {
        // CR-03（R-13）：按空间过滤；旧客户端不传 scope ⇒ 默认 FRONT_CHAT
        String source = ChatSessionService.normalizeSource(scope);
        List<ChatSession> sessions = chatSessionService.findByAgentIdAndUserId(id, StpUtil.getLoginIdAsString(), source);
        log.info("会话列表: agentId={}, scope={}, 命中={}, userId={}", id, source, sessions.size(), StpUtil.getLoginIdAsString());
        return ReturnVo.ok(sessions);
    }

//	/**
//	 * Get session list for an agent
//	 */
//	@GetMapping("/agent/{id}/sessions")
//	public ReturnVo<List<ChatSession>> getAgentSessions(@PathVariable(value = "id") Integer id) {
//		List<ChatSession> sessions = chatSessionService.findByAgentId(id);
//		return ReturnVo.ok(sessions);
//	}

    /**
     * Create a new session
     */
    @PostMapping("/agent/{id}/sessions")
    public ReturnVo<ChatSession> createSession(@PathVariable(value = "id") Integer id,
                                               @RequestParam(value = "scope", required = false) String scopeParam,
                                               @RequestBody(required = false) Map<String, Object> request) {
        String title = request != null ? (String) request.get("title") : null;
        // CR-03：scope 优先 body、其次 query、缺省 FRONT_CHAT
        Object bodyScope = request != null ? request.get("scope") : null;
        String source = ChatSessionService.normalizeSource(bodyScope != null ? String.valueOf(bodyScope) : scopeParam);
        ChatSession session = chatSessionService.createSession(id, title, StpUtil.getLoginIdAsString(), source);
        log.info("会话创建: sessionId={}, source={}, userId={}", session != null ? session.getId() : null, source,
                StpUtil.getLoginIdAsString());
        return ReturnVo.ok(session);
    }

    /**
     * Clear all sessions for an agent
     */
    @DeleteMapping("/agent/{id}/sessions")
    public ReturnVo<ApiResponse> clearAgentSessions(@PathVariable(value = "id") Integer id,
            @RequestParam(value = "scope", required = false) String scope) {
        // CR-03（R-13）：仅清声明空间，防运行页清空误删聊天历史
        String source = ChatSessionService.normalizeSource(scope);
        chatSessionService.clearSessionsByAgentIdAndSource(id, source);
        log.info("会话清空: agentId={}, scope={}, userId={}", id, source, StpUtil.getLoginIdAsString());
        return ReturnVo.ok(ApiResponse.success("会话已清空"));
    }

    /**
     * Get message list for a session
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ReturnVo<List<ChatMessage>> getSessionMessages(@PathVariable(value = "sessionId") String sessionId,
            @RequestParam(value = "scope", required = false) String scope) {
        if (crossSpace(sessionId, scope)) {
            return ReturnVo.ok(new java.util.ArrayList<>()); // CR-03：404-as-不存在（R-11 补充）
        }
        List<ChatMessage> messages = chatMessageService.findBySessionId(sessionId);
        return ReturnVo.ok(messages);
    }

    /**
     * Save message to session
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public ReturnVo<ChatMessage> saveMessage(@PathVariable(value = "sessionId") String sessionId,
                                             @RequestParam(value = "scope", required = false) String scope,
                                             @RequestBody ChatMessageDTO request) {
        if (crossSpace(sessionId, scope)) {
            return ReturnVo.fail("会话不存在"); // CR-03：404-as-不存在
        }
        try {
            if (request == null) {
                return ReturnVo.error("参数不能为空！");
            }
            ChatMessage message = ChatMessage.builder()
                    .sessionId(sessionId)
                    .role(request.getRole())
                    .content(request.getContent())
                    .messageType(request.getMessageType())
                    .metadata(request.getMetadata())
                    .build();

            ChatMessage savedMessage = chatMessageService.saveMessage(message);

            // T-06（共享面 S9）：metadata 含 attachmentIds 时回填附件 message_id（R-10）。
            // **不含该键 ⇒ 快路径直接返回，既有行为逐字节不变**；解析失败仅 WARN，不得影响消息保存。
            bindAttachmentsIfPresent(request.getMetadata(), savedMessage);

            // Update session activity time
            chatSessionService.updateSessionTime(sessionId);

            if (request.isTitleNeeded()) {
                sessionTitleService.scheduleTitleGeneration(sessionId, message.getContent());
            }

            return ReturnVo.ok(savedMessage);
        } catch (Exception e) {
            log.error("Save message error for session {}: {}", sessionId, e.getMessage(), e);
            return ReturnVo.error(e.getMessage());
        }
    }

    /**
     * CR-03（R-11 补充）：跨空间访问判定 —— 声明 scope 与库内 source 不符 ⇒ true（调用方按 404-as-不存在 处理）；
     * 会话本身不存在 ⇒ false（走原有不存在分支，不改变既有语义）。
     */
    private boolean crossSpace(String sessionId, String scope) {
        String actual = chatSessionService.findSourceBySessionId(sessionId);
        if (actual == null) {
            return false;
        }
        String declared = ChatSessionService.normalizeSource(scope);
        boolean mismatch = !actual.equals(declared);
        if (mismatch) {
            log.warn("跨空间访问被拒: sessionId={}, 声明scope={}, 库内source={}, userId={}", sessionId, declared, actual,
                    StpUtil.getLoginIdAsString());
        }
        return mismatch;
    }

    /**
     * T-06（S9）：按 metadata.attachmentIds 把附件绑定到刚保存的消息（回填 message_id）。
     *
     * <p>纪律：① metadata 不含该键 ⇒ **零额外行为**（既有四种身份不受影响）；
     * ② 任何解析/绑定异常 ⇒ 仅 WARN，**消息保存不受影响**（不 500）；
     * ③ 只能绑定属于本人（或超管）的附件 —— 由 service 侧校验，防把他人附件挂到自己消息上。
     */
    private void bindAttachmentsIfPresent(String metadata, ChatMessage savedMessage) {
        if (!StringUtils.hasText(metadata) || savedMessage == null || savedMessage.getId() == null) {
            return;
        }
        if (!metadata.contains("attachmentIds")) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = METADATA_MAPPER.readTree(metadata).get("attachmentIds");
            if (node == null || !node.isArray() || node.isEmpty()) {
                return;
            }
            java.util.List<Long> ids = new java.util.ArrayList<>();
            node.forEach(n -> {
                if (n.canConvertToLong()) {
                    ids.add(n.asLong());
                }
            });
            if (ids.isEmpty()) {
                return;
            }
            String viewer = StpUtil.getLoginIdAsString();
            boolean superAdmin = adminRoleGuard.isAdmin(viewer);
            int bound = chatAttachmentService.bindToMessage(ids, savedMessage.getId(), viewer, superAdmin);
            log.info("附件已绑定到消息: messageId={}, 请求 {} 个, 实际回填 {} 个", savedMessage.getId(), ids.size(), bound);
        }
        catch (Exception e) {
            log.warn("解析 metadata.attachmentIds 失败（消息已保存，忽略绑定）: messageId={}, reason={}",
                    savedMessage.getId(), e.getMessage());
        }
    }

    /**
     * 置顶/取消置顶会话
     */
    @PutMapping("/sessions/{sessionId}/pin")
    public ReturnVo<ApiResponse> pinSession(@PathVariable(value = "sessionId") String sessionId,
                                            @RequestParam(value = "scope", required = false) String scope,
                                            @RequestParam(value = "isPinned") Boolean isPinned) {
        if (crossSpace(sessionId, scope)) {
            return ReturnVo.fail("会话不存在");
        }
        try {
            chatSessionService.pinSession(sessionId, isPinned);
            String message = isPinned ? "会话已置顶" : "会话已取消置顶";
            return ReturnVo.ok(ApiResponse.success(message));
        } catch (Exception e) {
            log.error("Pin session error for session {}: {}", sessionId, e.getMessage(), e);
            return ReturnVo.error("操作失败");
        }
    }

    /**
     * Rename session
     */
    @PutMapping("/sessions/{sessionId}/rename")
    public ReturnVo<ApiResponse> renameSession(@PathVariable(value = "sessionId") String sessionId,
                                               @RequestParam(value = "scope", required = false) String scope,
                                               @RequestParam(value = "title") String title) {
        if (crossSpace(sessionId, scope)) {
            return ReturnVo.fail("会话不存在");
        }
        try {
            if (!StringUtils.hasText(title)) {
                return ReturnVo.error("标题不能为空");
            }

            chatSessionService.renameSession(sessionId, title.trim());
            return ReturnVo.ok(ApiResponse.success("会话已重命名"));
        } catch (Exception e) {
            log.error("Rename session error for session {}: {}", sessionId, e.getMessage(), e);
            return ReturnVo.error("重命名失败");
        }
    }

    /**
     * Delete a single session
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ReturnVo<ApiResponse> deleteSession(@PathVariable(value = "sessionId") String sessionId,
            @RequestParam(value = "scope", required = false) String scope) {
        if (crossSpace(sessionId, scope)) {
            return ReturnVo.fail("会话不存在");
        }
        try {
            chatSessionService.deleteSession(sessionId);
            return ReturnVo.ok(ApiResponse.success("会话已删除"));
        } catch (Exception e) {
            log.error("Delete session error for session {}: {}", sessionId, e.getMessage(), e);
            return ReturnVo.error("删除失败");
        }
    }

    /**
     * Download HTML report
     */
    @PostMapping("/sessions/{sessionId}/reports/html")
    public ResponseEntity<byte[]> convertAndDownloadHtml(@PathVariable(value = "sessionId") String sessionId,
                                                         @RequestBody String content) {
        try {
            if (!StringUtils.hasText(content)) {
                return ResponseEntity.badRequest().build();
            }
            log.debug("Download HTML report for session {}", sessionId);
            StringBuilder htmlContent = new StringBuilder();
            htmlContent.append(reportTemplateUtil.getHeader());
            htmlContent.append(content);
            htmlContent.append(reportTemplateUtil.getFooter());
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String filename = "report_" + timestamp + ".html";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("text", "html", StandardCharsets.UTF_8));
            headers.setContentDispositionFormData("attachment", filename);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(htmlContent.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("Download HTML report error for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

}
