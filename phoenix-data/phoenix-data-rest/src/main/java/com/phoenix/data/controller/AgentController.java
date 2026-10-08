package com.phoenix.data.controller;

import com.phoenix.common.enm.AgentTypeEnm;
import com.phoenix.data.entity.Agent;
import com.phoenix.data.service.agent.AgentService;
import com.phoenix.data.vo.ApiKeyResponse;
import com.phoenix.data.vo.ApiResponse;
import com.phoenix.tools.vo.ReturnVo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Agent Management Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
@AllArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /** R-17（v2.6.0）：超管判定复用 R-08 建立的守卫（口径：超管角色 id 或 upper(sn)=ROLE_ADMIN） */
    private final com.phoenix.data.component.AdminRoleGuard adminRoleGuard;

    /**
     * Get agent list
     *
     * <p>R-17（v2.6.0）：**按创建人可见** —— 超管看全部；其余用户只看自己创建的（`admin_id = 当前用户`）。
     */
    @GetMapping("/list")
    public ReturnVo<List<Agent>> list(@RequestParam(value = "status", required = false) String status,
                                      @RequestParam(value = "keyword", required = false) String keyword) {
        // 只列平台内创建的智能体（sn 为空）：Java 自注册的存量智能体不再出现在列表（可逆取舍）
        // R-17：非超管一律附加创建人过滤
        Long ownerId = isSuperAdmin() ? null : currentUserIdAsLong();
        return ReturnVo.ok(agentService.listCreatedInPlatform(status, keyword, ownerId));
    }

    /**
     * Get agent details by ID
     */
    @GetMapping("/{id}")
    public Agent get(@PathVariable Long id) {
        return checkAgentExists(id);
    }

    /**
     * Create agent
     *
     * <p>R-17（v2.6.0）：创建人由**服务端**写入 `admin_id = 当前登录用户`，不信任入参。
     */
    @PostMapping
    public Agent create(@RequestBody Agent agent) {
        // Set default status
        if (StringUtils.isBlank(agent.getStatus())) {
            agent.setStatus("draft");
        }
        // R-01：新建一律为对话智能体（type=harness），前端不再选择类型；服务端强制优于入参
        agent.setType(AgentTypeEnm.HARNESS.getCode());
        // R-17：创建人一律取当前登录用户（覆盖前端可能传入的值）
        agent.setAdminId(currentUserIdAsLong());
        return agentService.saveAgent(agent);
    }

    /**
     * Update agent
     */
    @PutMapping("/{id}")
    public Agent update(@PathVariable Long id, @RequestBody Agent agent) {
        Agent existing = checkAgentExists(id);
        agent.setId(id);
        // R-01：类型不可通过编辑改写（存量 sql/agent/workflow 保持原类型）
        agent.setType(existing.getType());
        return agentService.saveAgent(agent);
    }

    /**
     * Delete agent
     */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        checkAgentExists(id);
        agentService.deleteById(id);
    }

    /**
     * Publish agent
     */
    @PostMapping("/{id}/publish")
    public Agent publish(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody(required = false) com.phoenix.data.dto.AgentPublishDTO dto) {
        Agent agent = checkAgentExists(id);
        // agent-publish-group-grant R-01：body 带 groupIds 则发布同时覆盖式授权；无 body=老行为（A-1 向后兼容）
        if (dto != null && dto.getGroupIds() != null) {
            agentService.replaceGroupGrants(id, dto.getGroupIds(), currentOperator());
        }
        agent.setStatus("published");
        return agentService.saveAgent(agent);
    }

    /** 已授权组回显（R-02 编辑面） */
    @GetMapping("/{id}/groups")
    public ReturnVo<java.util.List<String>> getGroups(@PathVariable Long id) {
        checkAgentExists(id);
        return ReturnVo.ok(agentService.getGrantGroupIds(id));
    }

    /** 覆盖式调整授权（R-02；技能 /{id}/groups 同构） */
    @PutMapping("/{id}/groups")
    public ReturnVo<Boolean> updateGroups(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody com.phoenix.data.dto.AgentPublishDTO dto) {
        checkAgentExists(id);
        try {
            agentService.replaceGroupGrants(id, dto.getGroupIds(), currentOperator());
        }
        catch (org.springframework.web.server.ResponseStatusException e) {
            // BUG-64 绕行：不让校验错掉进全局 500 包装，直接回可读 fail
            return ReturnVo.fail(e.getReason() == null ? "授权失败" : e.getReason(), 400);
        }
        return ReturnVo.ok(Boolean.TRUE);
    }

    private String currentOperator() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        }
        catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Offline agent
     */
    @PostMapping("/{id}/offline")
    public Agent offline(@PathVariable Long id) {
        Agent agent = checkAgentExists(id);
        agent.setStatus("offline");
        return agentService.saveAgent(agent);
    }

    /**
     * Get masked API Key status
     */
    @GetMapping("/{id}/api-key")
    public ApiResponse<ApiKeyResponse> getApiKey(@PathVariable Long id) {
        Agent agent = checkAgentExists(id);
        String masked = agentService.getApiKeyMasked(id);
        return buildApiKeyResponse(masked, agent.getApiKeyEnabled(), "获取 API Key 成功");
    }

    /**
     * Generate API Key
     */
    @PostMapping("/{id}/api-key/generate")
    public ApiResponse<ApiKeyResponse> generateApiKey(@PathVariable Long id) {
        checkAgentExists(id);
        Agent agent = agentService.generateApiKey(id);
        return buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "生成 API Key 成功");
    }

    /**
     * Reset API Key
     */
    @PostMapping("/{id}/api-key/reset")
    public ApiResponse<ApiKeyResponse> resetApiKey(@PathVariable Long id) {
        checkAgentExists(id);
        Agent agent = agentService.resetApiKey(id);
        return buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "重置 API Key 成功");
    }

    /**
     * Delete API Key
     */
    @DeleteMapping("/{id}/api-key")
    public ApiResponse<ApiKeyResponse> deleteApiKey(@PathVariable Long id) {
        checkAgentExists(id);
        Agent agent = agentService.deleteApiKey(id);
        return buildApiKeyResponse(agent.getApiKey(), agent.getApiKeyEnabled(), "删除 API Key 成功");
    }

    /**
     * Toggle API Key enable flag
     */
    @PostMapping("/{id}/api-key/enable")
    public ApiResponse<ApiKeyResponse> toggleApiKey(@PathVariable Long id, @RequestParam("enabled") boolean enabled) {
        checkAgentExists(id);
        Agent agent = agentService.toggleApiKey(id, enabled);
        return buildApiKeyResponse(agent.getApiKey() == null ? null : "****", agent.getApiKeyEnabled(),
                "更新 API Key 状态成功");
    }

    /**
     * 检查智能体是否存在，不存在则抛出异常；**R-17 起同时校验归属**（非本人且非超管 ⇒ 403）。
     *
     * <p>所有单对象端点（详情/编辑/删除/发布/下线/授权/API Key）都经过本方法 ⇒ 归属防线成组生效。
     *
     * @param id 智能体ID
     * @return 智能体实体
     */
    private Agent checkAgentExists(Long id) {
        Agent agent = agentService.findById(id);
        if (agent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agent with id: %d not found".formatted(id));
        }
        if (!isSuperAdmin() && !isOwner(agent)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问他人创建的智能体");
        }
        return agent;
    }

    /** 当前登录用户 id（取不到返回 null） */
    private String currentUserId() {
        try {
            return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        }
        catch (Exception e) {
            return null;
        }
    }

    /** 当前登录用户 id（数值型，用于 admin_id 比较/写入；非数值返回 null） */
    private Long currentUserIdAsLong() {
        String uid = currentUserId();
        if (uid == null) {
            return null;
        }
        try {
            return Long.valueOf(uid);
        }
        catch (NumberFormatException e) {
            log.warn("[R-17] 登录 id 非数值，无法用于智能体归属: {}", uid);
            return null;
        }
    }

    /** 当前用户是否超管（R-17：超管可见全部智能体） */
    private boolean isSuperAdmin() {
        String uid = currentUserId();
        return uid != null && adminRoleGuard.isAdmin(uid);
    }

    /** 该智能体是否属当前登录用户（R-17） */
    private boolean isOwner(Agent agent) {
        Long uid = currentUserIdAsLong();
        return uid != null && uid.equals(agent.getAdminId());
    }

    /**
     * 构建API Key响应对象
     *
     * @param apiKey        API Key值
     * @param apiKeyEnabled 是否启用
     * @param message       响应消息
     * @return API响应
     */
    private ApiResponse<ApiKeyResponse> buildApiKeyResponse(String apiKey, Integer apiKeyEnabled, String message) {
        return ApiResponse.success(message, new ApiKeyResponse(apiKey, apiKeyEnabled));
    }

}
