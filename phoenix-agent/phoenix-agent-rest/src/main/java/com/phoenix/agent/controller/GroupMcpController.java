package com.phoenix.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.agent.dto.McpIdsDTO;
import com.phoenix.agent.service.GroupMcpInfoService;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组 × MCP 授权端点（R-04；入口=组管理页「分配 MCP」dialog，同构 group-kbase/group-agent）。
 *
 * <p>补齐前只有资源侧单向入口（{@code PUT /api/mcp/{id}/groups}），组管理页无法管理 MCP 授权。
 */
@RestController
@RequestMapping("/platform/group-mcp")
@RequiredArgsConstructor
public class GroupMcpController {

	private final GroupMcpInfoService groupMcpInfoService;

	/** 全量替换某组的 MCP 授权（差集幂等）。 */
	@PutMapping("/{groupId}/assign")
	public ReturnVo<Boolean> assign(@PathVariable String groupId, @RequestBody(required = false) McpIdsDTO dto) {
		groupMcpInfoService.assignMcps(groupId, dto == null ? null : dto.getMcpIds(),
				StpUtil.getLoginIdAsString());
		return ReturnVo.ok(Boolean.TRUE);
	}

	/** 某组已授权的 MCP id 清单（dialog 回显）。 */
	@GetMapping("/{groupId}/mcps")
	public ReturnVo<List<String>> mcpsOfGroup(@PathVariable String groupId) {
		return ReturnVo.ok(groupMcpInfoService.getMcpIdsByGroup(groupId));
	}

	/** 反向入口：某 MCP 已授权给哪些组（插件页预选用）。 */
	@GetMapping("/mcp/{mcpId}/groups")
	public ReturnVo<List<String>> groupsOfMcp(@PathVariable String mcpId) {
		return ReturnVo.ok(groupMcpInfoService.getGroupIdsByMcp(mcpId));
	}

}
