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
 * 半写文件（mtime<2s 跳过下轮再采）。storeKey=相对路径+大小 按会话维度去重（BUG-60；同名文件被覆盖会再登记一次，
 * 内容变了就该被看到）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceArtifactScanner {

    private final AgentFileService agentFileService;

    /** runtimeKey 以库中智能体为准（请求未必带 sn，BUG-78 根因之一） */
    private final com.phoenix.data.service.agent.AgentService agentService;

    @Value("${phoenix.agent.workspace-root:.agentscope/workspace}")
    private String workspaceRoot;

    private static final Set<String> SKIP_NAME_PREFIX = Set.of(".");
    private static final Set<String> SKIP_DIRS = Set.of("sessions", "tasks", ".index", "memory", "large_tool_results");
    private static final Set<String> SKIP_SUFFIX = Set.of(".jsonl", ".marker", ".db");
    /** 工具调用 ID 形态的占位文件（BUG-78 广扫副产物：`call_*` 不是用户产物，面板噪音）。 */
    private static final Set<String> SKIP_FILE_PREFIX = Set.of("call_");
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
            // R-06：扫描根=**会话目录**（与 factory 同源 WorkspacePaths）；智能体根用于历史只读回落与 storeKey 归一路径
            String agentKey = resolveAgentKey(agentId, sn);
            Path agentRoot = com.phoenix.agent.util.WorkspacePaths.agentRoot(workspaceRoot, agentKey)
                .toAbsolutePath().normalize();
            Path sessionRoot = com.phoenix.agent.util.WorkspacePaths.sessionRoot(workspaceRoot, agentKey, sessionId)
                .toAbsolutePath().normalize();
            List<Path> agentDirs = candidateAgentDirs(agentRoot, sessionRoot, userId, agentId, sn, sessionId);
            for (Path dir : agentDirs) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.filter(Files::isRegularFile)
                        .filter(f -> !isInternal(f, dir))
                        .filter(f -> !otherSessionArtifact(agentRoot, f, sessionId))
                        .filter(this::writeSettled)
                        .filter(f -> afterTurn(f, turnStart))
                        .forEach(f -> registerQuietly(registered, agentRoot, agentKey, f, agentId, sessionId, userId));
                }
            }
        }
        catch (Exception e) {
            log.warn("会话产物扫描失败（不阻断对话）: agent={}, session={}, err={}", agentId, sessionId, e.toString());
        }
        return registered;
    }

    /**
     * runtimeKey 解析：**以库中智能体为准**（factory 用 DB 实体构建工作区；请求侧 sn 可能缺省，
     * 两者不一致会导致扫描根与写入根错位——BUG-78 根因之一）。查库失败回落入参 sn。
     */
    private String resolveAgentKey(Long agentId, String sn) {
        if (agentId != null) {
            try {
                com.phoenix.data.entity.Agent agent = agentService.findById(agentId);
                if (agent != null) {
                    return com.phoenix.agent.util.WorkspacePaths.runtimeKey(agentId, agent.getSn());
                }
            }
            catch (RuntimeException e) {
                log.warn("智能体查询失败，runtimeKey 回落入参 sn: agentId={}, err={}", agentId, e.toString());
            }
        }
        return com.phoenix.agent.util.WorkspacePaths.runtimeKey(agentId, sn);
    }

    /**
     * 扫描候选目录（R-06）：
     * <ol>
     *   <li>会话目录已建立 → <b>只扫会话目录</b>（递归含框架内部 {uid} 层），会话间零交叉；</li>
     *   <li>会话目录尚未建立（升级前老会话 / 本轮无落盘）→ 回落智能体级历史多根做只读兼容，
     *       仍受 turnStart 时间窗 + 他会话目录排除双重约束，不会把老产物灌进新会话。</li>
     * </ol>
     */
    private List<Path> candidateAgentDirs(Path agentRoot, Path sessionRoot, String userId, Long agentId, String sn,
            String sessionId) {
        List<Path> dirs = new ArrayList<>();
        if (Files.isDirectory(sessionRoot)) {
            dirs.add(sessionRoot);
            return dirs;
        }
        // 历史布局兼容（实测 2026-10-01：LOCAL write_file 落 {root}/{userId}/...；亦兼容 {root}/agents/{key}）
        dirs.add(agentRoot);
        if (userId != null && !userId.isBlank()) {
            dirs.add(agentRoot.resolve(userId));
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
            dirs.add(agentRoot.resolve("agents").resolve(key));
        }
        return dirs;
    }

    /** 回落扫描时排除属于**其他会话**的产物目录（会话ID 为 UUID 形态）；防并发会话互收编。 */
    private boolean otherSessionArtifact(Path agentRoot, Path file, String sessionId) {
        Path rel = agentRoot.relativize(file);
        if (rel.getNameCount() < 2) {
            return false;
        }
        String first = rel.getName(0).toString();
        if (first.equals(sessionId)) {
            return false;
        }
        return first.length() == 36 && first.indexOf('-') == 8
                && first.chars().filter(c -> c == '-').count() == 4;
    }

    private boolean isInternal(Path file, Path agentDir) {
        String name = file.getFileName().toString();
        if (INTERNAL_FILES.contains(name)) {
            return true;
        }
        if (SKIP_FILE_PREFIX.stream().anyMatch(name::startsWith)) {
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
        // 末段是文件本身，只判中间的目录段
        int dirCount = rel.getNameCount() - 1;
        for (int i = 0; i < dirCount; i++) {
            String seg = rel.getName(i).toString();
            if (SKIP_DIRS.contains(seg)) {
                return true;
            }
            // R-06 实测收尾：框架内部目录（.skills-cache 技能包缓存/.index/.agentscope/.pylibs…）不是用户产物。
            // 漏掉后每个会话都会把整包技能脚本注册成"产物"（实测单会话 28 条噪音：catalog.json/search_library.py…）
            if (seg.startsWith(".")) {
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
            return Duration.between(mtime, Instant.now()).toMillis() >= 1000;
        }
        catch (IOException e) {
            return false;
        }
    }

    private void registerQuietly(List<AgentFile> out, Path agentRoot, String agentKey, Path file, Long agentId,
            String sessionId, String userId) {
        try {
            long size = Files.size(file);
            String relFromAgentRoot = agentRoot.relativize(file).toString().replace('\\', '/');
            // storeKey = runtimeKey + 智能体根相对路径 + 大小：与扫描根（会话目录）无关，跨会话/跨扫描稳定，
            // 全局首占去重语义不因 R-06 换根而漂移（workspace-isolation T-02）
            String storeKey = agentKey + "/" + relFromAgentRoot + ":" + size;
            // BUG-67：去重从按会话改全局首占——已被任何会话登记的文件不再收编
            if (agentFileService instanceof AgentFileServiceImpl impl && impl.existsByStoreKeyAnySession(storeKey)) {
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
