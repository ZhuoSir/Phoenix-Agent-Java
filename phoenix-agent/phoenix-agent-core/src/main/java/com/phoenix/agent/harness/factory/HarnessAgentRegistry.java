package com.phoenix.agent.harness.factory;

import com.phoenix.agent.harness.agent.HarnessStaticLoader;
import com.phoenix.agent.mapper.HarnessSkillMapper;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.agent.service.AgentRuntimeConfigService;
import com.phoenix.data.entity.Agent;
import com.phoenix.data.service.agent.AgentService;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 对话智能体运行实例注册表（T-06，R-02/R-03/R-08）。
 *
 * <p>两条并存路径，由「该智能体的 sn 是否命中 Java 静态加载器」确定性判定（不是 {@code sn || id} 兜底猜测）：
 * <ul>
 *   <li><b>存量自注册路径</b>：命中 {@link HarnessStaticLoader} → 直接返回 Java 类构建的内存实例，功能不变；</li>
 *   <li><b>库配置路径</b>：未命中 → {@link HarnessAgentFactory} 按运行配置构建并缓存，键为 agentId。</li>
 * </ul>
 *
 * <p>缓存以「配置指纹」失效：指纹 = agent.update_time + 运行配置.update_time + 工具/模型/策略开关 + 技能绑定版本。
 * 指纹变化即重建，旧实例 {@code close()}（其 close 只释放实例自有资源，共享 stateStore/distributedStore/技能仓库不受影响）。
 * 容量按 LRU 上限淘汰（默认 {@code phoenix.agent.runtime.max-instances=200}），淘汰同样 close 释放。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessAgentRegistry {

    private final HarnessAgentFactory harnessAgentFactory;

    private final AgentService agentService;

    private final AgentRuntimeConfigService agentRuntimeConfigService;

    private final HarnessSkillMapper harnessSkillMapper;

    private final HarnessStaticLoader harnessStaticLoader;

    private final com.phoenix.agent.service.harness.AiModelConfigEpoch aiModelConfigEpoch;

    @Value("${phoenix.agent.runtime.max-instances:200}")
    private int maxInstances = 200;

    /** LRU：accessOrder=true，最近使用移到队尾，淘汰队首 */
    private final Map<Long, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    private final AtomicLong hitCount = new AtomicLong();

    private final AtomicLong buildCount = new AtomicLong();

    private final AtomicLong legacyCount = new AtomicLong();

    private final AtomicLong evictCount = new AtomicLong();

    /**
     * 取得该智能体的可用运行实例（含来源与摘要，供对话入口与构建预演共用）。
     *
     * @param agentId 智能体ID（R-08 统一寻址）
     */
    public RuntimeHandle acquire(Long agentId) {
        Agent agent = agentId == null ? null : agentService.findById(agentId);
        if (agent == null) {
            throw new NoSuchElementException("智能体不存在: " + agentId);
        }
        // 路径①：存量 Java 自注册实例（本期保留，不迁移）
        HarnessAgent legacy = harnessStaticLoader.findAgent(agent.getSn());
        if (legacy != null) {
            legacyCount.incrementAndGet();
            log.debug("命中存量自注册实例: agentId={}, sn={}, legacyCount={}", agentId, agent.getSn(),
                legacyCount.get());
            return new RuntimeHandle(agentId, null, legacy,
                "存量自注册实例（Java 类承载）: sn=" + agent.getSn() + ", name=" + agent.getName(),
                List.of("(由 Java 类注册)"), "legacy");
        }
        // 路径②：库配置驱动
        AgentRuntimeConfig config = agentRuntimeConfigService.resolve(agentId);
        String fingerprint = fingerprint(agent, config);
        synchronized (cache) {
            CacheEntry cached = cache.get(agentId);
            if (cached != null && cached.fingerprint().equals(fingerprint)) {
                hitCount.incrementAndGet();
                log.info("运行实例命中缓存: agentId={}, hits={}, builds={}, evictions={}", agentId,
                    hitCount.get(), buildCount.get(), evictCount.get());
                return cached.toHandle("cached");
            }
        }
        HarnessAgentFactory.BuildResult built = harnessAgentFactory.buildWithSummary(agent);
        buildCount.incrementAndGet();
        CacheEntry entry = new CacheEntry(agentId, fingerprint, built.agent(), built.summary(), built.toolNames());
        List<HarnessAgent> evicted = new ArrayList<>();
        boolean reuseWinner = false;
        synchronized (cache) {
            CacheEntry current = cache.get(agentId);
            if (current != null && current.fingerprint().equals(fingerprint)) {
                // 并发构建竞争：已有同指纹实例，放弃本次构建产物
                reuseWinner = true;
                entry = current;
            }
            else {
                if (current != null) {
                    evicted.add(current.agent());
                    log.info("运行配置已变更，重建实例: agentId={}, 旧指纹={}, 新指纹={}", agentId,
                        current.fingerprint(), fingerprint);
                }
                cache.put(agentId, entry);
                while (cache.size() > maxInstances) {
                    Iterator<Map.Entry<Long, CacheEntry>> it = cache.entrySet().iterator();
                    if (!it.hasNext()) {
                        break;
                    }
                    Map.Entry<Long, CacheEntry> eldest = it.next();
                    it.remove();
                    evicted.add(eldest.getValue().agent());
                    evictCount.incrementAndGet();
                    log.info("运行实例超 LRU 上限({})淘汰: agentId={}, evictions={}", maxInstances, eldest.getKey(),
                        evictCount.get());
                }
            }
        }
        if (reuseWinner) {
            closeQuietly(built.agent(), agentId);
        }
        evicted.forEach(agent0 -> closeQuietly(agent0, agentId));
        return entry.toHandle(reuseWinner ? "cached" : "built");
    }

    /** 对话入口：只要实例 */
    public HarnessAgent get(Long agentId) {
        return acquire(agentId).agent();
    }

    /**
     * MCP 变体专用（mcp-client-tools T-05 路线乙）：绕过主缓存构建全新实例，
     * 由 McpMountService 按 (agentId+交集签名) 自行缓存与淘汰。
     * 存量自注册实例不支持变体（工具面由 Java 类固定），原样返回并 WARN。
     */
    public HarnessAgent buildUncached(Long agentId) {
        Agent agent = agentId == null ? null : agentService.findById(agentId);
        if (agent == null) {
            throw new java.util.NoSuchElementException("智能体不存在: " + agentId);
        }
        HarnessAgent legacy = harnessStaticLoader.findAgent(agent.getSn());
        if (legacy != null) {
            log.warn("存量自注册实例不支持 MCP 变体挂载，按无 MCP 处理: agentId={}", agentId);
            return legacy;
        }
        return harnessAgentFactory.buildWithSummary(agent).agent();
    }

    /** 主动失效（如智能体被删除/下线）；下次访问重建 */
    public void invalidate(Long agentId) {
        CacheEntry removed;
        synchronized (cache) {
            removed = cache.remove(agentId);
        }
        if (removed != null) {
            log.info("运行实例已主动失效: agentId={}", agentId);
            closeQuietly(removed.agent(), agentId);
        }
    }

    /** 缓存与构建计数（T-06 验证：命中/重建/淘汰可观测） */
    public String stats() {
        synchronized (cache) {
            return "cached=%d, hits=%d, builds=%d, legacy=%d, evictions=%d, maxInstances=%d"
                .formatted(cache.size(), hitCount.get(), buildCount.get(), legacyCount.get(), evictCount.get(),
                    maxInstances);
        }
    }

    /**
     * 配置指纹：任一组成项变化 → 视为需要重建。
     */
    public String fingerprint(Agent agent, AgentRuntimeConfig config) {
        String bindingVersion;
        try {
            bindingVersion = harnessSkillMapper.selectSkillBindingVersion(agent.getId());
        }
        catch (RuntimeException e) {
            // 指纹降级为不含绑定版本：宁可少重建（技能池读取本身是实时的），不可因统计失败阻断对话
            log.warn("技能绑定版本查询失败，指纹降级: agentId={}, err={}", agent.getId(), e.toString());
            bindingVersion = "unknown";
        }
        return String.join("|", String.valueOf(aiModelConfigEpoch.current()), String.valueOf(agent.getUpdateTime()), String.valueOf(config.getUpdateTime()),
            String.valueOf(config.getModelConfigId()), String.valueOf(config.getPlanMode()),
            String.valueOf(config.getMemoryEnabled()), String.valueOf(config.getKnowledgeEnabled()),
            String.valueOf(config.getDbQueryEnabled()), String.valueOf(config.getDbDeepAnalysisEnabled()),
            String.valueOf(config.getCompactionTriggerTokens()), String.valueOf(config.getCompactionKeepMessages()),
            String.valueOf(config.getToolResultMaxChars()),
            String.valueOf(config.getDatasourceId()), String.valueOf(config.getFilesystemPolicy()),
            String.valueOf(agent.getPrompt() == null ? null : agent.getPrompt().hashCode()), bindingVersion);
    }

    private void closeQuietly(HarnessAgent agent, Long agentId) {
        if (agent == null) {
            return;
        }
        try {
            agent.close();
        }
        catch (RuntimeException e) {
            log.warn("运行实例关闭失败: agentId={}, err={}", agentId, e.toString());
        }
    }

    private record CacheEntry(Long agentId, String fingerprint, HarnessAgent agent, String summary,
            List<String> toolNames) {

        RuntimeHandle toHandle(String source) {
            return new RuntimeHandle(agentId, fingerprint, agent, summary, toolNames, source);
        }
    }

    /**
     * 运行时实例句柄。
     *
     * @param agentId 库路径下的智能体ID（存量路径为 null，实际以 input agentId 为准）
     * @param source 来源：legacy（存量自注册）/ cached（注册表命中）/ built（本次构建）
     */
    public record RuntimeHandle(Long agentId, String fingerprint, HarnessAgent agent, String summary,
            List<String> toolNames, String source) {
    }

}
