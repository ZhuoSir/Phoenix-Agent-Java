package com.phoenix.agent.service.file;

import com.mybatisflex.core.query.QueryChain;
import com.phoenix.agent.enums.AgentFileErrorCodeEnm;
import com.phoenix.agent.mapper.AgentFileMapper;
import com.phoenix.agent.model.AgentFile;
import com.phoenix.agent.service.file.AgentFileService.RegisterCmd;
import com.phoenix.agent.vo.AgentFileVO;
import com.phoenix.data.entity.ChatSession;
import com.phoenix.data.mapper.ChatSessionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 会话产物文件门面实现（BL-19）。tee 副本恒为下载权威；属主=会话 user_id。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentFileServiceImpl implements AgentFileService {

    private final AgentFileMapper agentFileMapper;
    private final ChatSessionMapper chatSessionMapper;
    /** v1.7.0 R-02：解析 runtimeKey 以剥离历史行前缀（与扫描器同口径，DB 为准）。 */
    private final com.phoenix.data.service.agent.AgentService agentService;

    /** 与 FileStorageProperties 的 uploads 根同目录（默认 ./uploads；交付包指到卷内）。 */
    @Value("${phoenix.agent.files.root:./uploads}")
    private String filesRoot;

    @Value("${phoenix.agent.files.max-size-bytes:52428800}")
    private long maxFileSizeBytes;

    private static final Map<String, String> MIME_BY_EXT = Map.ofEntries(
            Map.entry("html", "text/html"), Map.entry("htm", "text/html"),
            Map.entry("svg", "image/svg+xml"), Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"),
            Map.entry("txt", "text/plain"), Map.entry("md", "text/markdown"),
            Map.entry("csv", "text/csv"), Map.entry("json", "application/json"),
            Map.entry("xml", "application/xml"), Map.entry("pdf", "application/pdf"));

    @Override
    public AgentFile register(RegisterCmd cmd) {
        if (cmd.content() == null || cmd.content().length == 0) {
            throw new AgentFileException(AgentFileErrorCodeEnm.FILE_NOT_FOUND);
        }
        if (cmd.content().length > maxFileSizeBytes) {
            throw new AgentFileException(AgentFileErrorCodeEnm.FILE_TOO_LARGE);
        }
        String safeName = sanitizeFileName(cmd.fileName());
        String relPath = cmd.agentId() + "/" + cmd.sessionId() + "/"
                + UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;
        Path root = rootPath();
        Path target = root.resolve(relPath).normalize();
        // canonical 前缀校验（R-06）：清洗后仍双保险，杜绝穿越
        if (!target.startsWith(root)) {
            throw new AgentFileException(AgentFileErrorCodeEnm.FILE_NAME_INVALID);
        }
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, cmd.content());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        AgentFile entity = AgentFile.builder()
                .agentId(cmd.agentId())
                .sessionId(cmd.sessionId())
                .fileName(safeName)
                .relPath(relPath)
                .sizeBytes((long) cmd.content().length)
                .mime(probeMime(safeName, target))
                .source(cmd.source().getCode())
                .backend(cmd.backend().getCode())
                .storeKey(cmd.storeKey())
                .creator(cmd.creator())
                .delFlag(0)
                .createTime(LocalDateTime.now())
                .build();
        agentFileMapper.insert(entity);
        log.info("会话文件登记: id={}, agent={}, session={}, name={}, size={}, source={}", entity.getId(),
                cmd.agentId(), cmd.sessionId(), safeName, cmd.content().length, cmd.source().getCode());
        return entity;
    }

    @Override
    public List<AgentFileVO> listBySession(String sessionId, String requesterUserId) {
        requireSessionOwner(sessionId, requesterUserId);
        return QueryChain.of(agentFileMapper)
                .eq(AgentFile::getSessionId, sessionId)
                .eq(AgentFile::getDelFlag, 0)
                .orderBy(AgentFile::getCreateTime, false)
                .list()
                .stream()
                .map(f -> AgentFileVO.builder()
                        .id(f.getId()).fileName(f.getFileName()).sizeBytes(f.getSizeBytes())
                        .mime(f.getMime()).source(f.getSource()).createTime(f.getCreateTime())
                        .build())
                .toList();
    }

    @Override
    public Download download(String fileId, String requesterUserId, boolean inline) {
        AgentFile f = requireLiveFile(fileId);
        requireSessionOwner(f.getSessionId(), requesterUserId, f);
        if (inline && !AgentFileService.inlineAllowed(f.getMime())) {
            throw new AgentFileException(AgentFileErrorCodeEnm.INLINE_FORBIDDEN);
        }
        Path path = rootPath().resolve(f.getRelPath()).normalize();
        if (!path.startsWith(rootPath()) || !Files.exists(path)) {
            log.error("tee 副本缺失: id={}, relPath={}", fileId, f.getRelPath());
            throw new AgentFileException(AgentFileErrorCodeEnm.FILE_NOT_FOUND);
        }
        try {
            return Download.builder().fileName(f.getFileName()).mime(f.getMime())
                    .content(Files.readAllBytes(path)).build();
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void logicalDelete(String fileId, String requesterUserId) {
        AgentFile f = requireLiveFile(fileId);
        requireSessionOwner(f.getSessionId(), requesterUserId, f);
        f.setDelFlag(1);
        agentFileMapper.update(f);
        log.info("会话文件逻辑删除: id={}, by={}", fileId, requesterUserId);
    }

    /** 同会话同 storeKey 是否已登记（BUG-60：去重按会话维度——旧会话登记过的文件被本会话
     复用/覆写时必须重新可见；全量补扫窗口已收窄到会话创建时间，不会灌入历史文件）。 */
    public boolean existsByStoreKey(String sessionId, String storeKey) {
        if (storeKey == null || storeKey.isBlank()) {
            return false;
        }
        return QueryChain.of(agentFileMapper)
                .eq(AgentFile::getStoreKey, storeKey)
                .eq(AgentFile::getSessionId, sessionId)
                .eq(AgentFile::getDelFlag, 0)
                .count() > 0;
    }

    /** workspace-isolation T-02（BUG-67）：storeKey 全局首占——任何会话已登记即不再收编。 */
    public boolean existsByStoreKeyAnySession(String storeKey) {
        if (storeKey == null || storeKey.isBlank()) {
            return false;
        }
        return QueryChain.of(agentFileMapper)
                .eq(AgentFile::getStoreKey, storeKey)
                .eq(AgentFile::getDelFlag, 0)
                .count() > 0;
    }

    @Override
    public boolean canAccessSession(ChatSession session, String requesterUserId) {
        if (session == null || requesterUserId == null) {
            return false;
        }
        if (requesterUserId.equals(session.getUserId())) {
            return true;
        }
        if (isBackendAdmin(requesterUserId)) {
            // BUG-80：管理侧（admin 运行页）跨属主访问留痕，便于审计
            log.info("管理员访问会话产物: sessionId={}, admin={}, owner={}", session.getId(), requesterUserId,
                    session.getUserId());
            return true;
        }
        return false;
    }

    /** 后台用户表命中即为管理员（前台用户表与后台用户表 id 空间独立，不存在误判）。 */
    private boolean isBackendAdmin(String loginId) {
        try {
            return com.mybatisflex.core.row.Db
                .selectObject("select id from tbl_privilege_user where id = ? limit 1", loginId) != null;
        }
        catch (RuntimeException e) {
            // 判定失败按非管理员处理（宁可拒绝，不放行）
            log.warn("管理员判定失败（按非管理员处理）: loginId={}, err={}", loginId, e.toString());
            return false;
        }
    }

    private AgentFile requireLiveFile(String fileId) {
        AgentFile f = agentFileMapper.selectOneById(fileId);
        if (f == null || Integer.valueOf(1).equals(f.getDelFlag())) {
            throw new AgentFileException(AgentFileErrorCodeEnm.FILE_NOT_FOUND);
        }
        return f;
    }

    private void requireSessionOwner(String sessionId, String requesterUserId) {
        requireSessionOwner(sessionId, requesterUserId, null);
    }

    /** 属主/管理员校验；fileForOrphanFallback 非空时，会话行缺失（历史/测试残留）允许创建者本人访问。 */
    private void requireSessionOwner(String sessionId, String requesterUserId, AgentFile fileForOrphanFallback) {
        ChatSession session = chatSessionMapper.selectOneById(sessionId);
        if (session == null) {
            // BUG-80 副产物：孤儿行（会话已不存在）此前一律 42031，创建者连自己的残留文件都清不掉
            if (fileForOrphanFallback != null && requesterUserId != null
                    && requesterUserId.equals(fileForOrphanFallback.getCreator())) {
                log.info("会话不存在，按产物创建者放行: fileId={}, sessionId={}, user={}",
                        fileForOrphanFallback.getId(), sessionId, requesterUserId);
                return;
            }
            throw new AgentFileException(AgentFileErrorCodeEnm.FILE_FORBIDDEN);
        }
        if (!canAccessSession(session, requesterUserId)) {
            throw new AgentFileException(AgentFileErrorCodeEnm.FILE_FORBIDDEN);
        }
    }

    private Path rootPath() {
        return Paths.get(filesRoot).toAbsolutePath().normalize();
    }

    private String probeMime(String name, Path path) {
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
        String byExt = MIME_BY_EXT.get(ext);
        if (byExt != null) {
            return byExt;
        }
        try {
            String probed = Files.probeContentType(path);
            return probed != null ? probed : "application/octet-stream";
        }
        catch (IOException e) {
            return "application/octet-stream";
        }
    }

    /** 清洗（R-06）：仅 basename、控制字符/分隔符/危险符号→下划线、去前导点、截断 200。 */
    static String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            return "file";
        }
        String base;
        try {
            base = Paths.get(raw).getFileName().toString();
        }
        catch (Exception e) {
            base = raw;
        }
        base = base.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").replaceAll("\\s+", " ").trim();
        base = base.replaceAll("^\\.+", "");
        if (base.isBlank()) {
            base = "file";
        }
        return base.length() > 200 ? base.substring(0, 200) : base;
    }

    // ==================== v1.7.0 R-02：会话文件树（单层懒加载） ====================

    @Override
    public TreeLevel treeLevel(String sessionId, String requestedPath, String requesterUserId) {
        requireSessionOwner(sessionId, requesterUserId);
        ChatSession session = chatSessionMapper.selectOneById(sessionId);
        String agentKey = resolveAgentKey(session);
        String want = normalizePath(requestedPath);
        boolean historyLevel = AgentFileService.HISTORY_PATH.equals(want);
        List<String> base = historyLevel ? List.of() : segments(want);

        List<AgentFile> rows = QueryChain.of(agentFileMapper)
                .eq(AgentFile::getSessionId, sessionId)
                .eq(AgentFile::getDelFlag, 0)
                .orderBy(AgentFile::getCreateTime, false)
                .list();

        Map<String, DirAgg> dirs = new java.util.LinkedHashMap<>();
        List<TreeNode> files = new java.util.ArrayList<>();
        int historyTotal = 0;
        for (AgentFile f : rows) {
            if (f.getStoreKey() == null || f.getStoreKey().isBlank()) {
                continue;
            }
            com.phoenix.agent.util.SessionFileTree.Parsed parsed =
                    com.phoenix.agent.util.SessionFileTree.parse(f.getStoreKey(), agentKey, sessionId);
            boolean history = !parsed.isSession();
            if (history) {
                historyTotal++;
                if (!historyLevel) {
                    continue;
                }
            }
            else if (historyLevel) {
                continue;
            }
            // R-02.3：框架内部件一律不出现在树里（与扫描器同源判据）
            if (parsed.internal()) {
                continue;
            }
            List<String> segs = segments(parsed.relativePath());
            if (segs.size() <= base.size() || !segs.subList(0, base.size()).equals(base)) {
                continue;
            }
            if (segs.size() == base.size() + 1) {
                files.add(TreeNode.builder()
                        .type("file").name(f.getFileName()).path(parsed.relativePath())
                        .id(f.getId()).sizeBytes(f.getSizeBytes()).mime(f.getMime())
                        .source(f.getSource())
                        .createTime(f.getCreateTime())
                        .build());
            }
            else {
                String dirName = segs.get(base.size());
                String dirPath = joinPath(base, dirName);
                DirAgg agg = dirs.computeIfAbsent(dirName, k -> new DirAgg(dirPath));
                agg.fileCount++;
                if (segs.size() == base.size() + 2) {
                    agg.childDirs.add(segs.get(base.size() + 1));
                }
            }
        }

        List<TreeNode> entries = new java.util.ArrayList<>();
        dirs.values().stream()
                .sorted(java.util.Comparator.comparing(a -> a.path))
                .forEach(a -> entries.add(TreeNode.builder()
                        .type("dir").name(lastSegment(a.path)).path(a.path)
                        .dirCount(a.childDirs.size()).fileCount(a.fileCount)
                        .build()));
        if (historyTotal > 0 && !historyLevel && want.isEmpty()) {
            entries.add(TreeNode.builder()
                    .type("history").name(AgentFileService.HISTORY_NAME)
                    .path(AgentFileService.HISTORY_PATH).fileCount(historyTotal)
                    .build());
        }
        files.sort(java.util.Comparator.comparing(TreeNode::name));
        entries.addAll(files);

        return TreeLevel.builder()
                .rootName(sessionId)
                .path(historyLevel ? AgentFileService.HISTORY_PATH : want)
                .parentPath(historyLevel ? "" : parentOf(want))
                .dirTotal(dirs.size())
                .fileTotal(files.size())
                .historyTotal(historyTotal)
                .entries(entries)
                .build();
    }

    private String resolveAgentKey(ChatSession session) {
        if (session == null || session.getAgentId() == null) {
            return null;
        }
        Long agentId = session.getAgentId().longValue();
        try {
            com.phoenix.data.entity.Agent agent = agentService.findById(agentId);
            if (agent != null) {
                return com.phoenix.agent.util.WorkspacePaths.runtimeKey(agentId, agent.getSn());
            }
        }
        catch (RuntimeException e) {
            log.warn("文件树 runtimeKey 解析失败，回落 agentId: agentId={}, err={}", agentId, e.toString());
        }
        return com.phoenix.agent.util.WorkspacePaths.runtimeKey(agentId, null);
    }

    private static final class DirAgg {
        private final String path;
        private final java.util.Set<String> childDirs = new java.util.LinkedHashSet<>();
        private int fileCount;

        private DirAgg(String path) {
            this.path = path;
        }
    }

    private static String normalizePath(String p) {
        if (p == null || p.isBlank()) {
            return "";
        }
        String s = p.trim().replace('\\', '/');
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String parentOf(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx);
    }

    private static String joinPath(List<String> base, String name) {
        return base.isEmpty() ? name : String.join("/", base) + "/" + name;
    }

    private static String lastSegment(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    private static List<String> segments(String path) {
        List<String> out = new java.util.ArrayList<>();
        if (path == null) {
            return out;
        }
        for (String part : path.split("/")) {
            if (!part.isEmpty()) {
                out.add(part);
            }
        }
        return out;
    }
}
