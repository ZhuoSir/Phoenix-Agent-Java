package com.phoenix.agent.service;

import java.util.List;

/**
 * 组 × MCP 授权（组侧入口，R-04）。
 *
 * <p>与资源侧 {@code McpAdminServiceImpl.grantGroups} 操作同一张
 * {@code tbl_platform_group_mcp_info}；资源侧为物理删后重建，本服务按差集逻辑删，
 * 两者都以「最终集合」为准，互为反向视图。
 */
public interface GroupMcpInfoService {

	/**
	 * 全量替换某组的 MCP 授权（差集幂等）。
	 * @param groupId 组 id
	 * @param mcpIds 期望授权的 MCP id 全量集合（null 视为空）
	 * @param operator 操作人（写审计列）
	 */
	void assignMcps(String groupId, List<String> mcpIds, String operator);

	/** 某组当前已授权的 MCP id 清单。 */
	List<String> getMcpIdsByGroup(String groupId);

	/** 某 MCP 已授权给的组 id 清单（反向回显用）。 */
	List<String> getGroupIdsByMcp(String mcpId);

}
