package com.phoenix.agent.service;

import java.util.List;

import com.phoenix.agent.model.McpServerInfo;

/**
 * MCP 有效集判定（镜像 FrontSkillAccessService；mcp-client-tools T-04）。
 * 前台口径=三重交集：绑定该智能体 ∧ 授权给账号所属任一组 ∧ 启用（同技能 mySkills 语义）。
 * admin 口径=绑定 ∧ 启用（无组过滤——实勘 A-5：admin 对话链技能走显式选择无组交集，同构对齐）。
 */
public interface FrontMcpAccessService {

    /** 前台对话：账号可见的有效 MCP 集（含 config 密文，供挂载侧构建客户端）。 */
    List<McpServerInfo> effectiveForFront(String accountId, Long agentId);

    /** admin 对话：绑定且启用的 MCP 集（无组过滤，同技能 admin 口径）。 */
    List<McpServerInfo> effectiveForAdmin(Long agentId);
}
