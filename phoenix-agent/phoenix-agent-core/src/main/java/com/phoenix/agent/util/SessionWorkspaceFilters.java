package com.phoenix.agent.util;

import java.util.List;
import java.util.Set;

/**
 * 会话工作区「框架内部件」判定**单一实现**（v1.7.0 T-04）。
 *
 * <p>面板树（{@link SessionFileTree}）与产物扫描器（{@code WorkspaceArtifactScanner}）同源引用本类，
 * 杜绝两处规则漂移（L-19：同一事实只允许一个解析源）。
 *
 * <p>判定口径与扫描器既有实测口径逐字一致（BUG-78/82 收尾结论）：
 * <ul>
 *   <li>目录：显式名单（sessions/tasks/.index/memory/large_tool_results）∪ **点开头**（.pylibs/.skills-cache/.agentscope…）</li>
 *   <li>文件：内部状态文件（MEMORY.md/consolidation_state/…）∪ {@code call_*} 占位 ∪ 点开头且无扩展名 ∪ 内部后缀（.jsonl/.marker/.db）</li>
 * </ul>
 */
public final class SessionWorkspaceFilters {

    private SessionWorkspaceFilters() {
    }

    /** 框架内部目录（显式名单；点开头目录另行判定） */
    public static final Set<String> SKIP_DIRS = Set.of("sessions", "tasks", ".index", "memory",
            "large_tool_results");

    /** 工具调用 ID 形态的占位文件（广扫副产物，不是用户产物） */
    public static final Set<String> SKIP_FILE_PREFIX = Set.of("call_");

    /** 框架内部后缀（会话转录/任务标记/索引库） */
    public static final Set<String> SKIP_SUFFIX = Set.of(".jsonl", ".marker", ".db");

    /** 框架内部状态文件（记忆固化/会话索引等） */
    public static final Set<String> INTERNAL_FILES = Set.of("MEMORY.md", "consolidation_state", "sessions.json",
            "memory.md", "AGENTS.md.bak");

    /** 目录段是否内部件（显式名单 ∪ 点开头） */
    public static boolean isInternalDirSegment(String segment) {
        if (segment == null || segment.isEmpty()) {
            return false;
        }
        return SKIP_DIRS.contains(segment) || segment.startsWith(".");
    }

    /** 文件名是否内部件（与扫描器原判据逐字一致） */
    public static boolean isInternalFile(String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        if (INTERNAL_FILES.contains(name)) {
            return true;
        }
        if (SKIP_FILE_PREFIX.stream().anyMatch(name::startsWith)) {
            return true;
        }
        if (name.startsWith(".") && !name.contains(".")) {
            return true;
        }
        for (String suffix : SKIP_SUFFIX) {
            if (name.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }

    /** 路径判定：中间目录段任一内部件 **或** 文件名内部件 → 内部件 */
    public static boolean isInternalPath(List<String> dirSegments, String fileName) {
        if (dirSegments != null) {
            for (String segment : dirSegments) {
                if (isInternalDirSegment(segment)) {
                    return true;
                }
            }
        }
        return isInternalFile(fileName);
    }

    /** 会话ID形态（36 位 UUID：8-4-4-4-12 连字符分布）——扫描器与树解析共用同一判据 */
    public static boolean looksLikeSessionId(String segment) {
        return segment != null && segment.length() == 36 && segment.indexOf('-') == 8
                && segment.chars().filter(c -> c == '-').count() == 4;
    }
}
