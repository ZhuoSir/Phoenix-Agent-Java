package com.phoenix.agent.controller;

import com.phoenix.agent.dto.AgentRuntimeConfigDTO;
import com.phoenix.agent.service.AgentRuntimeConfigService;
import com.phoenix.agent.vo.AgentRuntimeConfigVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 对话智能体运行配置接口（R-03/R-05/R-09）。
 * /api/** 由 Sa-Token 全局过滤器拦截（未登录返回未授权）。
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentRuntimeConfigController {

    private final AgentRuntimeConfigService agentRuntimeConfigService;

    /** 读取运行配置（无配置返回默认值） */
    @GetMapping("/{id}/runtime-config")
    public ReturnVo<AgentRuntimeConfigVO> get(@PathVariable Long id) {
        return agentRuntimeConfigService.get(id);
    }

    /** 保存运行配置（校验数据源/工具上限/策略合法性） */
    @PutMapping("/{id}/runtime-config")
    public ReturnVo<Boolean> save(@PathVariable Long id, @RequestBody AgentRuntimeConfigDTO dto) {
        return agentRuntimeConfigService.save(id, dto);
    }
}
