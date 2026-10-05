package com.phoenix.agent.harness.mcp;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoenix.agent.harness.factory.HarnessAgentRegistry;
import com.phoenix.agent.model.McpServerInfo;
import com.phoenix.agent.util.McpSecretCipher;

import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.agentscope.core.tool.mcp.McpTool;
import io.agentscope.harness.agent.HarnessAgent;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * MCP 挂载服务（mcp-client-tools T-05，spike 定案路线乙）。
 * 变体实例按 (agentId+交集签名) 缓存；无 MCP 交集→原单例零变化路径。
 * 单 server 注册失败仅 WARN 跳过（R-04 故障隔离）；变体淘汰时关闭其全部 wrapper。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpMountService {

    private static final int MAX_VARIANTS = 64;
    private static final long DEFAULT_TIMEOUT_MS = 30000L;
    private static final long DEFAULT_INIT_TIMEOUT_MS = 15000L;

    private final HarnessAgentRegistry harnessAgentRegistry;
    /** WebFlux 无自动装配 ObjectMapper（T-03 教训），自持 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Map<String, Variant> variants = new LinkedHashMap<>(16, 0.75f, true);

    /** BUG-69 缓解：变体闲置 TTL——超时即重建，封顶任何陈旧状态的生命周期 */
    private static final long VARIANT_TTL_MS = 30 * 60 * 1000L;

    private static final class Variant {
        final HarnessAgent agent;
        final List<McpClientWrapper> wrappers;
        final List<String> toolNames;
        volatile long lastAccess = System.currentTimeMillis();

        Variant(HarnessAgent agent, List<McpClientWrapper> wrappers, List<String> toolNames) {
            this.agent = agent;
            this.wrappers = wrappers;
            this.toolNames = toolNames;
        }

        boolean expired() {
            return System.currentTimeMillis() - lastAccess > VARIANT_TTL_MS;
        }
    }

    /** 对话轮次入口：交集空→原实例；否则→变体（boundedElastic 上构建，绝不在事件循环 block）。 */
    public Mono<HarnessAgent> withMcp(HarnessAgent base, Long agentId, List<McpServerInfo> effective) {
        if (effective == null || effective.isEmpty() || agentId == null) {
            return Mono.just(base);
        }
        String sig = signature(agentId, effective);
        return Mono.fromCallable(() -> getOrBuild(agentId, sig, effective)).subscribeOn(Schedulers.boundedElastic());
    }

    private String signature(Long agentId, List<McpServerInfo> effective) {
        StringBuilder sb = new StringBuilder().append(agentId);
        effective.stream().sorted((a, b) -> String.valueOf(a.getId()).compareTo(String.valueOf(b.getId())))
            // name 必须入签名：前缀由 name 净化而来，改名后旧变体不得复用（实测缓存漂移）
            .forEach(e -> sb.append('|').append(e.getId()).append(':').append(e.getName()).append(':')
                .append(String.valueOf(e.getConfig()).hashCode()).append(':').append(e.getStatus()));
        return Integer.toHexString(sb.toString().hashCode()) + "-" + effective.size();
    }

    private HarnessAgent getOrBuild(Long agentId, String sig, List<McpServerInfo> effective) {
        String key = agentId + "@" + sig;
        List<Variant> stale = new ArrayList<>();
        synchronized (variants) {
            Variant cached = variants.get(key);
            if (cached != null) {
                if (!cached.expired()) {
                    cached.lastAccess = System.currentTimeMillis();
                    log.debug("MCP 变体命中: key={}", key);
                    return cached.agent;
                }
                // BUG-69 缓解：闲置超 TTL——弃旧重建（旧 wrapper 关闭）
                variants.remove(key);
                stale.add(cached);
                log.info("MCP 变体闲置超 TTL，重建: key={}", key);
            }
        }
        for (Variant v : stale) {
            closeQuietly(v);
        }
        HarnessAgent fresh = harnessAgentRegistry.buildUncached(agentId);
        List<McpClientWrapper> wrappers = new ArrayList<>();
        List<String> toolNames = new ArrayList<>();
        Toolkit toolkit = fresh.getToolkit();
        for (McpServerInfo server : effective) {
            try {
                registerServer(toolkit, server, wrappers, toolNames);
            }
            catch (Exception ex) {
                // R-04 故障隔离：单 server 失败不拖垮变体与对话
                log.warn("MCP server 注册失败，跳过: id={}, name={}, err={}", server.getId(), server.getName(),
                    ex.toString());
            }
        }
        Variant built = new Variant(fresh, wrappers, toolNames);
        List<Variant> evicted = new ArrayList<>();
        synchronized (variants) {
            Variant race = variants.get(key);
            if (race != null) {
                // 并发竞争：先用者胜，本次产物就地关闭
                evicted.add(built);
                built = race;
            }
            else {
                variants.put(key, built);
                while (variants.size() > MAX_VARIANTS) {
                    var it = variants.entrySet().iterator();
                    if (!it.hasNext()) {
                        break;
                    }
                    var en = it.next();
                    it.remove();
                    evicted.add(en.getValue());
                }
            }
        }
        for (Variant v : evicted) {
            closeQuietly(v);
        }
        log.info("MCP 变体构建: key={}, servers={}, tools={}", key, effective.size(), toolNames);
        return built.agent;
    }

    @SuppressWarnings("unchecked")
    private void registerServer(Toolkit toolkit, McpServerInfo server, List<McpClientWrapper> wrappers,
            List<String> toolNames) {
        Map<String, Object> cfg = parseConfig(server.getConfig());
        long timeoutMs = cfg.get("timeoutMs") instanceof Number n ? n.longValue() : DEFAULT_TIMEOUT_MS;
        long initMs = cfg.get("initTimeoutMs") instanceof Number n ? n.longValue() : DEFAULT_INIT_TIMEOUT_MS;
        McpClientBuilder b = McpClientBuilder.create(server.getName());
        switch (server.getTransport()) {
            case "stdio" -> b = b.stdioTransport(str(cfg.get("command")), asList(cfg.get("args")),
                decryptMap(asMap(cfg.get("env"))));
            case "sse" -> {
                b = b.sseTransport(str(cfg.get("url")));
                Map<String, String> h = decryptMap(asMap(cfg.get("headers")));
                if (!h.isEmpty()) {
                    b = b.headers(h);
                }
            }
            default -> {
                b = b.streamableHttpTransport(str(cfg.get("url")));
                Map<String, String> h = decryptMap(asMap(cfg.get("headers")));
                if (!h.isEmpty()) {
                    b = b.headers(h);
                }
            }
        }
        b = b.timeout(Duration.ofMillis(timeoutMs)).initializationTimeout(Duration.ofMillis(initMs));
        McpClientWrapper wrapper = b.buildAsync().block(Duration.ofMillis(initMs + 5000));
        if (wrapper == null) {
            throw new IllegalStateException("MCP 客户端构建失败: " + server.getName());
        }
        boolean ok = false;
        try {
            wrapper.initialize().block(Duration.ofMillis(initMs + 5000));
            List<McpSchema.Tool> tools = wrapper.listTools().block(Duration.ofMillis(timeoutMs + 5000));
            List<String> enable = asList(cfg.get("enableTools"));
            String prefix = sanitize(server.getName()) + "__";
            int registered = 0;
            for (McpSchema.Tool t : tools == null ? List.<McpSchema.Tool>of() : tools) {
                if (!enable.isEmpty() && !enable.contains(t.name())) {
                    continue; // enableTools 注册时过滤（spike 问⑤）
                }
                Map<String, Object> schema = objectMapper.convertValue(t.inputSchema(),
                    new TypeReference<Map<String, Object>>() {
                    });
                McpTool raw = new McpTool(t.name(), t.description(), schema, wrapper);
                String prefixed = prefix + sanitize(t.name());
                toolkit.registerAgentTool(new PrefixedAgentTool(prefixed, raw));
                toolNames.add(prefixed);
                registered++;
            }
            wrappers.add(wrapper);
            ok = true;
            log.info("MCP server 挂载: name={}, transport={}, tools={}", server.getName(), server.getTransport(),
                registered);
        }
        finally {
            if (!ok) {
                try {
                    wrapper.close();
                }
                catch (Exception ignored) {
                }
            }
        }
    }

    private void closeQuietly(Variant v) {
        for (McpClientWrapper w : v.wrappers) {
            try {
                w.close();
            }
            catch (Exception ignored) {
            }
        }
        try {
            v.agent.close();
        }
        catch (Exception ignored) {
        }
    }

    /**
     * 工具名净化：仅 [a-zA-Z0-9_]（A/B 实证：连字符工具名令模型回传 name=null → 框架 NPE，
     * mcd 真服务器两轮复现；T-05 全下划线 stub 正常）。连续下划线折叠，前导非字母补 m。
     */
    private String sanitize(String s) {
        String out = s == null ? "srv" : s.replaceAll("[^a-zA-Z0-9_]", "_").replaceAll("_+", "_");
        if (out.isEmpty()) {
            out = "srv";
        }
        if (!Character.isLetter(out.charAt(0))) {
            out = "m" + out;
        }
        return out.length() > 24 ? out.substring(0, 24) : out;
    }

    private Map<String, String> decryptMap(Map<String, String> stored) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> en : stored.entrySet()) {
            try {
                out.put(en.getKey(), McpSecretCipher.decrypt(en.getValue()));
            }
            catch (Exception e) {
                log.warn("MCP 敏感值解密失败，按空处理: key={}", en.getKey());
            }
        }
        return out;
    }

    private Map<String, Object> parseConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        }
        catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, String> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, String> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
            return out;
        }
        return Map.of();
    }

    private List<String> asList(Object o) {
        if (o instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
