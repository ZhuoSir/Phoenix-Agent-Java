package com.phoenix.agent.service.file;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import com.phoenix.agent.util.WorkspacePaths;

import lombok.extern.slf4j.Slf4j;

/**
 * workspace 存量归档迁移（workspace-isolation T-04 / R-04）。
 * 启动时把共享根的历史条目（用户 home/散文件/旧 agents 树/框架缓存）整迁 `_legacy_shared/`；
 * 允许集 = _legacy_shared ∪ DB 全量智能体 runtimeKey（实时查询）。
 * 纪律：只 move 不删除；幂等（二次启动允许集全命中=零动作）；任何失败 WARN 不阻塞启动。
 */
@Slf4j
@Component
public class WorkspaceMigrationRunner implements ApplicationRunner {

    static final String LEGACY_DIR = "_legacy_shared";

    @Value("${phoenix.agent.workspace-root:.agentscope/workspace}")
    private String workspaceRoot;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Path root = Paths.get(workspaceRoot).toAbsolutePath().normalize();
            if (!Files.isDirectory(root)) {
                return;
            }
            Set<String> allowed = new HashSet<>();
            allowed.add(LEGACY_DIR);
            try {
                for (Row r : Db.selectListBySql("select id, sn from tbl_data_agent")) {
                    allowed.add(WorkspacePaths.runtimeKey(r.getLong("id"), r.getString("sn")));
                }
            }
            catch (Exception e) {
                // 允许集拿不到=宁可不迁（误搬风险 > 晚一轮再迁）
                log.warn("workspace 迁移允许集查询失败，本轮跳过迁移: {}", e.toString());
                return;
            }
            Path legacy = root.resolve(LEGACY_DIR);
            int moved = 0;
            try (Stream<Path> entries = Files.list(root)) {
                for (Path p : entries.toList()) {
                    String name = p.getFileName().toString();
                    if (allowed.contains(name)) {
                        continue;
                    }
                    Files.createDirectories(legacy);
                    Path target = legacy.resolve(name);
                    if (Files.exists(target)) {
                        // 半程失败重跑的名字冲突：加时间戳并存，绝不覆盖
                        target = legacy.resolve(name + "." + System.currentTimeMillis());
                    }
                    Files.move(p, target);
                    moved++;
                    log.info("workspace 存量归档: {} → {}/", name, LEGACY_DIR);
                }
            }
            if (moved > 0) {
                log.info("workspace 迁移完成: {} 项归档至 {}（记忆重置提示见 UPGRADE）", moved, LEGACY_DIR);
            }
            else {
                log.info("workspace 迁移: 无存量条目（幂等零动作），允许集={} 项", allowed.size());
            }
        }
        catch (Exception e) {
            log.warn("workspace 迁移失败（不阻塞启动，下轮重试）: {}", e.toString());
        }
    }
}
