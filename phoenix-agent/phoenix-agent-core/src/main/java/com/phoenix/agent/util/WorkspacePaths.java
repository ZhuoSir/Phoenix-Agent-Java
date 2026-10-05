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
    public static Path agentRoot(String workspaceRoot, String runtimeKey) {
        return Path.of(workspaceRoot).resolve(runtimeKey);
    }
}
