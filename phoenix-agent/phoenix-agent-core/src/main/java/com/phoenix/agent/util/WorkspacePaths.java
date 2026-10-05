package com.phoenix.agent.util;

import java.nio.file.Path;

/**
 * workspace 路径规则单一实现（workspace-isolation T-01）。
 * factory 与 scanner 同源引用，杜绝两处规则漂移（R-01：workspace 根按智能体隔离）。
 */
public final class WorkspacePaths {

    private WorkspacePaths() {
    }

    /** 与 HarnessAgentFactory.runtimeKey 原规则逐字一致：sn 有值用 sn，否则 agent-{id}。 */
    public static String runtimeKey(Long agentId, String sn) {
        return (sn != null && !sn.isBlank()) ? sn : "agent-" + agentId;
    }

    /** 智能体专属 workspace 根 = {workspaceRoot}/{runtimeKey}（框架内部布局原样下沉一层）。 */
    /**
     * R-06（workspace-isolation v1.1.0）：会话级工作区根 = {root}/{agentKey}/{sessionId}。
     * 所有工具读写（file/shell/脚本/下载）与面板扫描统一以该目录为根；会话间互不可见。
     *
     * <p>注：框架会在其下再拼一层 {userId}（实测 file 工具落 {root}/{uid}/），
     * 故实际产物位于 {@code .../{sessionId}/{uid}/}；面板与扫描需递归。
     */
    public static Path sessionRoot(String workspaceRoot, String runtimeKey, String sessionId) {
        Path base = agentRoot(workspaceRoot, runtimeKey);
        return sessionId == null || sessionId.isBlank() ? base : base.resolve(sessionId);
    }

    public static Path agentRoot(String workspaceRoot, String runtimeKey) {
        return Path.of(workspaceRoot).resolve(runtimeKey);
    }
}
