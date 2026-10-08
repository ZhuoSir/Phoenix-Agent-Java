package com.phoenix.agent.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mybatisflex.core.paginate.Page;
import com.phoenix.agent.model.McpDetailVO;
import com.phoenix.agent.model.McpListVO;
import com.phoenix.agent.model.McpSaveDTO;
import com.phoenix.agent.model.McpTestDTO;
import com.phoenix.agent.model.McpTestResultVO;
import com.phoenix.agent.service.McpAdminService;
import com.phoenix.tools.vo.ReturnVo;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import cn.dev33.satoken.stp.StpUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 插件管理端点（admin，镜像 SkillController 路径惯例 /api/mcp；mcp-client-tools T-03）。
 * 仅管理端可达（/api 前缀走 privilege 鉴权链，前台 token 天然 401/403——T-03 验收断言）。
 */
@Slf4j
@RestController
@RequestMapping("/api/mcp")
@RequiredArgsConstructor
public class McpAdminController {

    private final McpAdminService mcpAdminService;
    private final com.phoenix.agent.service.FrontMcpAccessService frontMcpAccessService;

    /** R-18（CR-01/T-25）：超管判定复用 R-08/R-17 守卫（单一口径） */
    private final com.phoenix.data.component.AdminRoleGuard adminRoleGuard;

    private boolean isSuperAdmin() {
        return adminRoleGuard.isAdmin(operator());
    }

    /** R-18：列表 owner 过滤值（超管 null=全部） */
    private String ownerFilter() {
        return isSuperAdmin() ? null : operator();
    }

    /** R-18：单对象归属校验（非本人且非超管 → 403） */
    private void assertOwner(String id) {
        if (isSuperAdmin()) {
            return;
        }
        String creator = mcpAdminService.getCreatorById(id);
        if (creator == null || !creator.equals(operator())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "无权访问他人创建的 MCP");
        }
    }

    @GetMapping
    public ReturnVo<Page<McpListVO>> page(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return mcpAdminService.page(keyword, pageNum, pageSize, ownerFilter());
    }

    @GetMapping("/{id}")
    public ReturnVo<McpDetailVO> detail(@PathVariable String id) {
        assertOwner(id);
        return mcpAdminService.detail(id);
    }

    @PostMapping
    public ReturnVo<String> save(@RequestBody McpSaveDTO dto) {
        return mcpAdminService.save(dto, operator());
    }

    @PutMapping("/{id}/status")
    public ReturnVo<Boolean> toggleStatus(@PathVariable String id, @RequestParam String status) {
        assertOwner(id);
        return mcpAdminService.toggleStatus(id, status, operator());
    }

    @DeleteMapping("/{id}")
    public ReturnVo<Boolean> delete(@PathVariable String id) {
        assertOwner(id);
        return mcpAdminService.delete(id, operator());
    }

    @PutMapping("/{id}/groups")
    public ReturnVo<Boolean> grantGroups(@PathVariable String id, @RequestBody List<String> groupIds) {
        assertOwner(id);
        return mcpAdminService.grantGroups(id, groupIds);
    }

    /** Reactor 事件循环线程禁 block()（实测 IllegalStateException）——跳 boundedElastic，镜像 AgentFileController 的 Mono.fromCallable 先例 */
    @PostMapping("/test")
    public Mono<ReturnVo<McpTestResultVO>> test(@RequestBody McpTestDTO dto) {
        return Mono.fromCallable(() -> mcpAdminService.testConnection(dto))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /** 诊断端点（T-04 验证 + 运维排障）：有效集判定，只回 id/name/transport，config 不出防泄漏 */
    @GetMapping("/effective")
    public ReturnVo<List<Map<String, String>>> effective(@RequestParam Long agentId,
            @RequestParam(required = false) String accountId, @RequestParam(defaultValue = "admin") String channel) {
        List<com.phoenix.agent.model.McpServerInfo> list = "front".equals(channel)
            ? frontMcpAccessService.effectiveForFront(accountId, agentId)
            : frontMcpAccessService.effectiveForAdmin(agentId);
        List<Map<String, String>> vos = list.stream()
            .map(e -> Map.of("id", String.valueOf(e.getId()), "name", String.valueOf(e.getName()), "transport",
                String.valueOf(e.getTransport())))
            .toList();
        return ReturnVo.ok(vos);
    }

    /** T-07：智能体编辑页绑定三端点（options/bound/bind，镜像技能绑定惯例） */
    @GetMapping("/options")
    public ReturnVo<List<com.phoenix.agent.model.McpOptionVO>> options(@RequestParam Long agentId) {
        return mcpAdminService.options(agentId, operator(), isSuperAdmin());
    }

    @GetMapping("/bound")
    public ReturnVo<List<String>> bound(@RequestParam Long agentId) {
        return mcpAdminService.boundIds(agentId);
    }

    @PutMapping("/bind")
    public ReturnVo<Boolean> bind(@RequestBody com.phoenix.agent.model.McpBindDTO dto) {
        return mcpAdminService.bind(dto.getAgentId(), dto.getMcpIds(), operator());
    }

    private String operator() {
        try {
            return StpUtil.getLoginIdAsString();
        }
        catch (Exception e) {
            return "unknown";
        }
    }
}
