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

    /**
     * Get agent list
     */
    @GetMapping("/list")
    public ReturnVo<List<Agent>> list(@RequestParam(value = "status", required = false) String status,
                                      @RequestParam(value = "keyword", required = false) String keyword) {
        // 只列平台内创建的智能体（sn 为空）：Java 自注册的存量智能体不再出现在列表（可逆取舍）
        return ReturnVo.ok(agentService.listCreatedInPlatform(status, keyword));
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
     */
    @PostMapping
    public Agent create(@RequestBody Agent agent) {
        // Set default status
        if (StringUtils.isBlank(agent.getStatus())) {
            agent.setStatus("draft");
        }
        // R-01：新建一律为对话智能体（type=harness），前端不再选择类型；服务端强制优于入参
        agent.setType(AgentTypeEnm.HARNESS.getCode());
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
    public Agent publish(@PathVariable Long id) {
        Agent agent = checkAgentExists(id);
        agent.setStatus("published");
        return agentService.saveAgent(agent);
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
     * 检查智能体是否存在，不存在则抛出异常
     *
     * @param id 智能体ID
     * @return 智能体实体
     */
    private Agent checkAgentExists(Long id) {
        Agent agent = agentService.findById(id);
        if (agent == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "agent with id: %d not found".formatted(id));
        }
        return agent;
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
