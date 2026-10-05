package com.phoenix.agent.harness.mcp;

import java.util.Map;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.AgentTool;
import io.agentscope.core.tool.ToolCallParam;
import reactor.core.publisher.Mono;

/**
 * 前缀委托工具（mcp-client-tools T-05 / R-03 来源标识）。
 * spike 实证：McpTool.callAsync 用自己的 getName() 调远端——直接改名必挂；
 * 故外壳对模型暴露 server__tool 前缀名，callAsync 原样委托内层 McpTool（其内部仍用原始名调 MCP 服务器）。
 */
public class PrefixedAgentTool implements AgentTool {

    private final String prefixedName;
    private final AgentTool delegate;

    public PrefixedAgentTool(String prefixedName, AgentTool delegate) {
        this.prefixedName = prefixedName;
        this.delegate = delegate;
    }

    @Override
    public String getName() {
        return prefixedName;
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public Map<String, Object> getParameters() {
        return delegate.getParameters();
    }

    @Override
    public Boolean getStrict() {
        return delegate.getStrict();
    }

    @Override
    public Map<String, Object> getOutputSchema() {
        return delegate.getOutputSchema();
    }

    @Override
    public boolean isReadOnly() {
        return delegate.isReadOnly();
    }

    @Override
    public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
        return delegate.callAsync(param);
    }
}
