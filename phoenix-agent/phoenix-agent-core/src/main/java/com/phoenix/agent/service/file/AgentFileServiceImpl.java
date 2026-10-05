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
        requireSessionOwner(f.getSessionId(), requesterUserId);
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
        requireSessionOwner(f.getSessionId(), requesterUserId);
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

    private AgentFile requireLiveFile(String fileId) {
        AgentFile f = agentFileMapper.selectOneById(fileId);
        if (f == null || Integer.valueOf(1).equals(f.getDelFlag())) {
            throw new AgentFileException(AgentFileErrorCodeEnm.FILE_NOT_FOUND);
        }
        return f;
    }

    private void requireSessionOwner(String sessionId, String requesterUserId) {
        ChatSession session = chatSessionMapper.selectOneById(sessionId);
        if (session == null || requesterUserId == null || !requesterUserId.equals(session.getUserId())) {
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
}
