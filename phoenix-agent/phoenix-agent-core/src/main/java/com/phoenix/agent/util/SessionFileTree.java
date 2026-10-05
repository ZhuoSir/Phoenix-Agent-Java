package com.phoenix.agent.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话文件树路径解析（v1.7.0 T-04）：把 {@code tbl_data_agent_file.store_key} 还原为**会话内相对路径**。
 *
 * <p>实测三代 store_key 形态（workspace-isolation 历程）：
 * <table border="1">
 *   <caption>store_key 世代</caption>
 *   <tr><th>代</th><th>形态</th><th>归位</th></tr>
 *   <tr><td>三代（R-06 后）</td><td>{@code {agentKey}/{sessionId}/{uid}/…:size}</td><td>本会话（相对路径=其后全部）</td></tr>
 *   <tr><td>二代（agent 分根后）</td><td>{@code {agentKey}/{uid}/…:size}</td><td>历史（无会话维度）</td></tr>
 *   <tr><td>一代（最早）</td><td>{@code {uid}/…:size}</td><td>历史</td></tr>
 * </table>
 *
 * <p>**非本会话**的会话段（他会话 UUID）同样归历史——防跨会话误归（隔离铁律）。
 * 解析失败（空键/异常形态）**不抛异常**，一律归历史并保留原始文件名，绝不静默丢弃。
 */
public final class SessionFileTree {

    private SessionFileTree() {
    }

    /** 归属：本会话 / 历史（无会话维度或他会话） */
    public enum Attribution {
        SESSION, HISTORY
    }

    /**
     * 解析结果。
     *
     * @param attribution 归属
     * @param relativePath 会话内相对路径（斜杠分隔；历史件为尽力还原的相对路径或原始键）
     * @param fileName 文件名（末段）
     * @param sizeBytes store_key 尾部大小；缺失=-1
     */
    public record Parsed(Attribution attribution, String relativePath, String fileName, long sizeBytes) {

        public boolean isSession() {
            return attribution == Attribution.SESSION;
        }

        /** 中间目录段（不含文件名）；供内部件判定使用 */
        public List<String> dirSegments() {
            List<String> segments = segments(relativePath);
            return segments.size() <= 1 ? List.of() : segments.subList(0, segments.size() - 1);
        }

        /** 是否框架内部件（与扫描器同源判据，面板树据此隐藏） */
        public boolean internal() {
            return SessionWorkspaceFilters.isInternalPath(dirSegments(), fileName);
        }

        /** 首层目录名（用于「历史文件」等聚合展示）；无目录层则返回空串 */
        public String firstSegment() {
            List<String> segments = segments(relativePath);
            return segments.size() <= 1 ? "" : segments.get(0);
        }
    }

    /**
     * 解析 store_key。
     *
     * @param storeKey 形如 {@code agent-33/{sessionId}/461…/a/b.svg:1234}
     * @param agentKey 该智能体的 runtimeKey（如 {@code agent-33} / {@code owl-kids}）
     * @param sessionId 当前会话ID
     */
    public static Parsed parse(String storeKey, String agentKey, String sessionId) {
        if (storeKey == null || storeKey.isBlank()) {
            return new Parsed(Attribution.HISTORY, "", "", -1L);
        }
        long size = -1L;
        String path = storeKey.trim();
        int colon = path.lastIndexOf(':');
        if (colon > 0 && colon < path.length() - 1) {
            String tail = path.substring(colon + 1);
            if (tail.chars().allMatch(Character::isDigit)) {
                try {
                    size = Long.parseLong(tail);
                    path = path.substring(0, colon);
                }
                catch (NumberFormatException ignored) {
                    // 超大数字：保留原串，大小记 -1（不因此丢弃）
                }
            }
        }
        List<String> segments = segments(path);
        String fileName = segments.isEmpty() ? path : segments.get(segments.size() - 1);

        // 以**会话段为锚**（T-04 实现细化）：前缀（agentKey/显示名）不参与归属判定——
        // BUG-78 类现场存在"入库前缀 ≠ 库中 sn"的历史，按前缀判定会把本会话文件误归历史；
        // 同时只看第 0/1 段，任何**他会话 UUID**落在这两段一律归历史（隔离优先，不放宽）。
        int sessionAt = -1;
        if (!segments.isEmpty() && sessionId != null && sessionId.equals(segments.get(0))) {
            sessionAt = 0;
        }
        else if (segments.size() >= 2 && sessionId != null && sessionId.equals(segments.get(1))) {
            sessionAt = 1;
        }
        if (sessionAt >= 0) {
            return build(Attribution.SESSION, segments, sessionAt + 1, size);
        }
        // 历史件：剥掉可能存在的 agentKey 前缀便于展示（不改归属语义）
        int from = (!segments.isEmpty() && agentKey != null && !agentKey.isBlank()
                && agentKey.equals(segments.get(0))) ? 1 : 0;
        return build(Attribution.HISTORY, segments, from, size);
    }

    private static Parsed build(Attribution attribution, List<String> segments, int from, long size) {
        if (from >= segments.size()) {
            return new Parsed(attribution, "", "", size);
        }
        String relative = String.join("/", segments.subList(from, segments.size()));
        String fileName = segments.get(segments.size() - 1);
        return new Parsed(attribution, relative, fileName, size);
    }

    private static List<String> segments(String path) {
        List<String> out = new ArrayList<>();
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }
}
