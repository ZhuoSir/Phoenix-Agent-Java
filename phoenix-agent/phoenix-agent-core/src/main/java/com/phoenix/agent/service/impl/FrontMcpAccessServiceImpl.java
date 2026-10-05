package com.phoenix.agent.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import com.phoenix.agent.model.McpServerInfo;
import com.phoenix.agent.service.FrontMcpAccessService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 有效集判定实现（镜像 FrontSkillAccessServiceImpl：组数据同库同 schema，只读 SQL 直查）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrontMcpAccessServiceImpl implements FrontMcpAccessService {

    /** 三重交集：绑定该智能体 ∧ 授权给账号所属任一组 ∧ 启用（镜像 MY_SKILLS_SQL） */
    private static final String EFFECTIVE_FRONT_SQL = """
            select distinct m.id, m.name, m.transport, m.config, m.status
              from tbl_mcp_server m
              join tbl_data_agent_mcp_info b on b.mcp_id = m.id and b.del_flag = 0
              join tbl_platform_group_mcp_info gm on gm.mcp_id = m.id and gm.del_flag = 0
              join tbl_platform_account_group_info ag on ag.group_id = gm.group_id and ag.del_flag = 0
             where ag.account_id = ?
               and b.agent_id = ?
               and m.status = 'enabled'
               and m.del_flag = 0
            """;

    /** admin 口径：绑定 ∧ 启用（无组过滤） */
    private static final String EFFECTIVE_ADMIN_SQL = """
            select distinct m.id, m.name, m.transport, m.config, m.status
              from tbl_mcp_server m
              join tbl_data_agent_mcp_info b on b.mcp_id = m.id and b.del_flag = 0
             where b.agent_id = ?
               and m.status = 'enabled'
               and m.del_flag = 0
            """;

    @Override
    public List<McpServerInfo> effectiveForFront(String accountId, Long agentId) {
        if (accountId == null || accountId.isBlank() || agentId == null) {
            return List.of();
        }
        return Db.selectListBySql(EFFECTIVE_FRONT_SQL, accountId, agentId).stream().map(this::toEntity).toList();
    }

    @Override
    public List<McpServerInfo> effectiveForAdmin(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        return Db.selectListBySql(EFFECTIVE_ADMIN_SQL, agentId).stream().map(this::toEntity).toList();
    }

    private McpServerInfo toEntity(Row row) {
        McpServerInfo e = new McpServerInfo();
        e.setId(row.getString("id"));
        e.setName(row.getString("name"));
        e.setTransport(row.getString("transport"));
        e.setConfig(row.getString("config"));
        e.setStatus(row.getString("status"));
        return e;
    }
}
