package com.phoenix.agent.controller;

import java.util.List;

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

    @GetMapping
    public ReturnVo<Page<McpListVO>> page(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return mcpAdminService.page(keyword, pageNum, pageSize);
    }

    @GetMapping("/{id}")
    public ReturnVo<McpDetailVO> detail(@PathVariable String id) {
        return mcpAdminService.detail(id);
    }

    @PostMapping
    public ReturnVo<String> save(@RequestBody McpSaveDTO dto) {
        return mcpAdminService.save(dto, operator());
    }

    @PutMapping("/{id}/status")
    public ReturnVo<Boolean> toggleStatus(@PathVariable String id, @RequestParam String status) {
        return mcpAdminService.toggleStatus(id, status, operator());
    }

    @DeleteMapping("/{id}")
    public ReturnVo<Boolean> delete(@PathVariable String id) {
        return mcpAdminService.delete(id, operator());
    }

    @PutMapping("/{id}/groups")
    public ReturnVo<Boolean> grantGroups(@PathVariable String id, @RequestBody List<String> groupIds) {
        return mcpAdminService.grantGroups(id, groupIds);
    }

    /** Reactor 事件循环线程禁 block()（实测 IllegalStateException）——跳 boundedElastic，镜像 AgentFileController 的 Mono.fromCallable 先例 */
    @PostMapping("/test")
    public Mono<ReturnVo<McpTestResultVO>> test(@RequestBody McpTestDTO dto) {
        return Mono.fromCallable(() -> mcpAdminService.testConnection(dto))
            .subscribeOn(Schedulers.boundedElastic());
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
