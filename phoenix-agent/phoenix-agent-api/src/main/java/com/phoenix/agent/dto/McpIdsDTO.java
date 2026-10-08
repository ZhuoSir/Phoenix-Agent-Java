package com.phoenix.agent.dto;

import lombok.Data;

import java.util.List;

/** MCP id 集合载荷（组侧「分配 MCP」用，全量替换语义；镜像 KbaseIdsDTO）。 */
@Data
public class McpIdsDTO {

	private List<String> mcpIds;

}
