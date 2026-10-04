package com.phoenix.agent.service;

import java.util.List;

import com.mybatisflex.core.paginate.Page;
import com.phoenix.agent.model.McpDetailVO;
import com.phoenix.agent.model.McpListVO;
import com.phoenix.agent.model.McpSaveDTO;
import com.phoenix.agent.model.McpTestDTO;
import com.phoenix.agent.model.McpTestResultVO;
import com.phoenix.tools.vo.ReturnVo;

/**
 * MCP 插件管理服务（admin 侧，镜像 SkillAdminService；mcp-client-tools T-03/T-04）。
 */
public interface McpAdminService {

    ReturnVo<Page<McpListVO>> page(String keyword, int pageNum, int pageSize);

    ReturnVo<McpDetailVO> detail(String id);

    ReturnVo<String> save(McpSaveDTO dto, String operator);

    ReturnVo<Boolean> toggleStatus(String id, String status, String operator);

    ReturnVo<Boolean> delete(String id, String operator);

    ReturnVo<Boolean> grantGroups(String mcpId, List<String> groupIds);

    ReturnVo<McpTestResultVO> testConnection(McpTestDTO dto);

    /** T-07：智能体编辑页绑定选项池（启用池 ∪ 已绑定，镜像技能 options 语义）。 */
    ReturnVo<java.util.List<com.phoenix.agent.model.McpOptionVO>> options(Long agentId);

    /** T-07：智能体已绑定 MCP id 集。 */
    ReturnVo<java.util.List<String>> boundIds(Long agentId);

    /** T-07：覆盖式保存智能体 MCP 绑定（仅启用可新绑；已绑定的停用项可保留）。 */
    ReturnVo<Boolean> bind(Long agentId, java.util.List<String> mcpIds, String operator);
}
