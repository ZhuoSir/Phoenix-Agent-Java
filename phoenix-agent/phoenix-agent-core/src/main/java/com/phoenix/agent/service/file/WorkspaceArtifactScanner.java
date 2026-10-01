package com.phoenix.agent.service.file;

import com.phoenix.agent.enums.AgentFileBackendEnm;
import com.phoenix.agent.enums.AgentFileSourceEnm;
import com.phoenix.agent.model.AgentFile;
import com.phoenix.agent.service.file.AgentFileService.RegisterCmd;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 会话轮末 workspace 扫描（BL-19 主捕获通路，plan 风险②结论落地）。
 *
 * <p>实测（2026-10-01 探针）：workspace 布局为 {root}/{userId}/agents/{agentKey}/...；
 * LOCAL 策略产物（write_file/edit/轮末直写）可在此捕获。REMOTE 策略（Redis store）产物
 * **不落盘**，本扫描器不覆盖——该范围调整经需求方追认后 R-02 由 materialize 通道兜底。
 *
 * <p>排除清单：会话转录（*.jsonl/sessions.json）、内部索引（.index/）、任务标记（_sweep.marker）、
 * 半写文件（mtime<2s 跳过下轮再采）。storeKey=相对路径+大小 去重（同名文件被覆盖会再登记一次，
 * 内容变了就该被看到）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceArtifactScanner {

    private final AgentFileService agentFileService;

    @Value("${phoenix.agent.workspace-root:.agentscope/workspace}")
    private String workspaceRoot;

    private static final Set<String> SKIP_NAME_PREFIX = Set.of(".");
    private static final Set<String> SKIP_DIRS = Set.of("sessions", "tasks", ".index");
    private static final Set<String> SKIP_SUFFIX = Set.of(".jsonl", ".marker", ".db");
    /** 框架内部状态文件（记忆固化/会话索引等），不是用户产物（实测 2026-10-01 捕获到 MEMORY.md/consolidation_state）。 */
    private static final Set<String> INTERNAL_FILES = Set.of("MEMORY.md", "consolidation_state", "sessions.json",
            "memory.md", "AGENTS.md.bak");

    /** 扫描并登记本会话新产物；返回新登记的 AgentFile 列表（空=无新文件）。异常吞掉只 WARN，不阻断会话。 */
    public List<AgentFile> scanAndRegister(Long agentId, String sn, String userId, String sessionId) {
        return scanAndRegister(agentId, sn, userId, sessionId, null);
    }

    /** turnStart 非空时只登记该时刻后落盘的产出；null=全量补登（storeKey 去重防重复）。 */
    public List<AgentFile> scanAndRegister(Long agentId, String sn, String userId, String sessionId,
            Instant turnStart) {
        List<AgentFile> registered = new ArrayList<>();
        try {
            Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
            List<Path> agentDirs = candidateAgentDirs(root, userId, agentId, sn);
            for (Path dir : agentDirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.filter(Files::isRegularFile)
                        .filter(f -> !isInternal(f, dir))
                        .filter(this::writeSettled)
                        .filter(f -> afterTurn(f, turnStart))
                        .forEach(f -> registerQuietly(registered, root, f, agentId, sessionId, userId));
                }
            }
        }
        catch (Exception e) {
            log.warn("会话产物扫描失败（不阻断对话）: agent={}, session={}, err={}", agentId, sessionId, e.toString());
        }
        return registered;
    }

    /** 实测(2026-10-01)：LOCAL write_file 相对 workspace 根解析 → 产物直接落 {root}/{userId}/...
     *  故扫整棵用户目录（含任意子层），并兼容 {root}/agents/{key} 历史布局；agent 归属由调用轮次决定。 */
    private List<Path> candidateAgentDirs(Path root, String userId, Long agentId, String sn) {
        List<Path> dirs = new ArrayList<>();
        if (userId != null && !userId.isBlank()) {
            dirs.add(root.resolve(userId));
        }
        List<String> keys = new ArrayList<>();
        if (agentId != null) {
            keys.add("agent-" + agentId);
            keys.add(String.valueOf(agentId));
        }
        if (sn != null && !sn.isBlank()) {
            keys.add(sn);
        }
        for (String key : keys) {
            dirs.add(root.resolve("agents").resolve(key));
        }
        return dirs;
    }

    private boolean isInternal(Path file, Path agentDir) {
        String name = file.getFileName().toString();
        if (INTERNAL_FILES.contains(name)) {
            return true;
        }
        if (SKIP_NAME_PREFIX.stream().anyMatch(name::startsWith) && !name.contains(".")) {
            return true;
        }
        for (String s : SKIP_SUFFIX) {
            if (name.endsWith(s)) {
                return true;
            }
        }
        Path rel = agentDir.relativize(file);
        for (Path p : rel) {
            if (SKIP_DIRS.contains(p.toString())) {
                return true;
            }
        }
        return false;
    }

    /** turnStart 窗口：仅收本轮（或补登全量）产出；容差 3s 吸收时钟抖动。 */
    private boolean afterTurn(Path file, Instant turnStart) {
        if (turnStart == null) {
            return true;
        }
        try {
            return Files.getLastModifiedTime(file).toInstant().isAfter(turnStart.minusSeconds(3));
        }
        catch (IOException e) {
            return false;
        }
    }

    /** 最后修改距今 <2s 视为可能仍在写，本轮跳过（下轮扫描再收）。 */
    private boolean writeSettled(Path file) {
        try {
            Instant mtime = Files.getLastModifiedTime(file).toInstant();
            return Duration.between(mtime, Instant.now()).toMillis() >= 2000;
        }
        catch (IOException e) {
            return false;
        }
    }

    private void registerQuietly(List<AgentFile> out, Path root, Path file, Long agentId, String sessionId,
            String userId) {
        try {
            long size = Files.size(file);
            String relFromRoot = root.relativize(file).toString().replace('\\', '/');
            String storeKey = relFromRoot + ":" + size;
            if (agentFileService instanceof AgentFileServiceImpl impl && impl.existsByStoreKey(storeKey)) {
                return;
            }
            byte[] content = Files.readAllBytes(file);
            AgentFile af = agentFileService.register(RegisterCmd.builder()
                    .agentId(agentId)
                    .sessionId(sessionId)
                    .fileName(file.getFileName().toString())
                    .content(content)
                    .source(AgentFileSourceEnm.SCAN)
                    .backend(AgentFileBackendEnm.LOCAL)
                    .storeKey(storeKey)
                    .creator(userId)
                    .build());
            out.add(af);
        }
        catch (AgentFileException e) {
            log.info("会话产物跳过登记（超限/非法名）: {}, {}", file, e.getMessage());
        }
        catch (Exception e) {
            log.warn("会话产物登记失败: {}, {}", file, e.toString());
        }
    }
}
