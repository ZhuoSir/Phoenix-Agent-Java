package com.phoenix.agent.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.agent.model.AgentMcpInfo;

/** AgentMcpInfo Mapper（mcp-client-tools T-03）。 */
@Mapper
public interface AgentMcpInfoMapper extends BaseMapper<AgentMcpInfo> {
}
