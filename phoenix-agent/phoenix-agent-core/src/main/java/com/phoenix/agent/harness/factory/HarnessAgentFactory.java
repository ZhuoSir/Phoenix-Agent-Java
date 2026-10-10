package com.phoenix.agent.harness.factory;

import com.phoenix.agent.constant.AgentRuntimeConstant;
import com.phoenix.agent.enums.FilesystemPolicyEnm;
import com.phoenix.agent.harness.middleware.ReasoningRepairMiddleware;
import com.phoenix.agent.harness.middleware.ToolLoopBreakerMiddleware;
import com.phoenix.agent.harness.middleware.StopOnAllDeniedMiddleware;
import com.phoenix.agent.harness.middleware.KnowledgePathGuardMiddleware;
import com.phoenix.agent.harness.middleware.KnowledgeGuidanceMiddleware;
import com.phoenix.agent.harness.skill.AgentScopedSkillRepository;
import com.phoenix.agent.harness.skill.ExplicitSkillMiddleware;
import com.phoenix.agent.mapper.HarnessSkillMapper;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.agent.service.AgentRuntimeConfigService;
import com.phoenix.agent.service.harness.HarnessModelRegistry;
import com.phoenix.agent.util.WorkspacePaths;
import com.phoenix.data.entity.Agent;
import io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import io.agentscope.core.model.ChatModelBase;
import io.agentscope.extensions.postgresql.state.PostgresAgentStateStore;
import io.agentscope.extensions.redis.RedisDistributedStore;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.filesystem.spec.LocalFilesystemSpec;
import io.agentscope.harness.agent.filesystem.spec.RemoteFilesystemSpec;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话智能体工厂（T-05，R-03）：**只有数据库配置能决定智能体的能力**，构建过程不依赖任何 Java 自注册类。
 *
 * <p>构建输入 = {@code tbl_data_agent}（身份/提示词）+ {@code tbl_data_agent_runtime_config}（能力开关），
 * 产物 = 一个绑定该 agentId 的 {@link HarnessAgent} 实例。工具经 {@link AgentToolContributor} 按配置装配，
 * 技能仓库按「已发布 ∧ 已绑定本智能体」隔离（与存量 sn 路径共用同一装饰器实现）。
 *
 * <p>实例缓存与失效由 Registry（T-06）负责，本类只做无状态构建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HarnessAgentFactory {

    /** BUG-165：同一工具连续重复调用多少次后熔断（默认 3） */
    @org.springframework.beans.factory.annotation.Value("${phoenix.agent.tool-repeat-breaker-threshold:3}")
    private int toolRepeatBreakerThreshold;

    /** 工作区（与存量自注册智能体保持一致，避免两套目录） */
    /** BL-19/R-01：workspace 根配置化。默认与历史一致（裸机开发零感知）；
     *  交付包经 env 指向 uploads 卷内路径，使产物持久化可备份。旧常量无外部引用，安全收敛。 */
    /** R-05 上下文治理全局默认（DSH 对标换算：0.8×128k≈102400 / keep 20 / pruner 8192） */
    @Value("${phoenix.agent.compaction-trigger-tokens:102400}")
    private int compactionTriggerTokensDefault;

    @Value("${phoenix.agent.compaction-keep-messages:20}")
    private int compactionKeepMessagesDefault;

    /**
     * BUG-158：轮末 pending 异步工具 drain 的等待上限。开启 enablePendingToolRecovery 后，框架在
     * POST_REASONING→POST_CALL 之间会等待 pending 异步工具，**未设超时即用框架默认（实测 ≈24s 空尾**，
     * 用户感知为"输出已结束但一直提示正在执行"）。默认 5s：把空尾压到可感知阈值内；
     * 确需更长异步工具等待的智能体可经配置调大。
     */
    @Value("${phoenix.agent.async-tool-timeout-seconds:5}")
    private long asyncToolTimeoutSeconds;

    @Value("${phoenix.agent.tool-result-max-chars:8192}")
    private int toolResultMaxCharsDefault;

    @Value("${phoenix.agent.workspace-root:.agentscope/workspace}")
    private String workspaceRoot = ".agentscope/workspace";

    /** 默认系统提示词：工具能力由各 @Tool 描述自述，此处只约束「不得编造」这一底线 */
    private static final String DEFAULT_SYS_PROMPT = """
            你是「%s」智能体。%s
            回答必须基于工具返回的真实结果，不得编造；工具查不到时如实告知用户，不要臆测。
            """;

    private final AgentRuntimeConfigService agentRuntimeConfigService;

    private final HarnessModelRegistry harnessModelRegistry;

    private final PostgresSkillRepository postgresSkillRepository;

    private final HarnessSkillMapper harnessSkillMapper;

    private final PostgresAgentStateStore postgresAgentStateStore;

    private final RedisDistributedStore redisDistributedStore;

    private final RemoteFilesystemSpec pgRemoteFilesystemSpec;

    /**
     * 工具装配扩展点；用 ObjectProvider 以允许「暂无实现」时正常启动。
     */
    private final ObjectProvider<AgentToolContributor> toolContributors;

    /**
     * 按运行配置构建该智能体的 HarnessAgent 实例。
     *
     * @param agent 智能体（须有 id 与 sn）
     */
    public HarnessAgent build(Agent agent) {
        return buildWithSummary(agent).agent();
    }

    /**
     * 构建并返回构建摘要（T-05 验证入口 / 管理端「构建预演」）。
     */
    public BuildResult buildWithSummary(Agent agent) {
        return buildWithSummary(agent, null);
    }

    /**
     * R-06（workspace-isolation v1.1.0）：按**会话**构建。
     *
     * <p>sessionId 非空 → workspace 下沉一层到 {@code {root}/{agentKey}/{sessionId}}（框架内再拼 uid），
     * 会话之间互不可见；sessionId 为空保持存量行为（{@code {root}/{agentKey}}，管理端构建预演等无会话场景）。
     *
     * @param agent 智能体（须有 id 与 sn）
     * @param sessionId 会话ID；空=存量智能体级工作区
     */
    public BuildResult buildWithSummary(Agent agent, String sessionId) {
        if (agent == null || agent.getId() == null) {
            throw new IllegalArgumentException("智能体不存在或缺少 id，无法构建运行时实例");
        }
        AgentRuntimeConfig config = agentRuntimeConfigService.resolve(agent.getId());
        ChatModelBase model = harnessModelRegistry.getOpenAIChatModel(config.getModelConfigId());
        ToolkitBundle bundle = buildToolkit(agent, config);
        // R-06：会话级工作区（会话空则回落智能体级，零行为变化）
        Path workspace = WorkspacePaths.sessionRoot(workspaceRoot, runtimeKey(agent), sessionId);
        if (StringUtils.hasText(sessionId)) {
            // T-12 前置：会话目录必须先存在——框架只在 namespace 分支 mkdir，而 shellCwd 命中时直接
            // 作为 ProcessBuilder.directory()；目录缺失会让**每条 shell 命令**抛 IOException（实测 error=2）
            Path abs = workspace.toAbsolutePath().normalize();
            try {
                java.nio.file.Files.createDirectories(abs);
            }
            catch (java.io.IOException e) {
                log.warn("会话工作区目录创建失败（shell 命令将不可用）: path={}, err={}", abs, e.toString());
            }
        }

        HarnessAgent.Builder builder = HarnessAgent.builder()
            .name(runtimeKey(agent))
            .agentId(String.valueOf(agent.getId()))
            .description(agent.getDescription())
            .sysPrompt(sysPrompt(agent))
            .model(model)
            .toolkit(bundle.toolkit())
            // workspace-isolation R-01/R-06：根按智能体+会话隔离（{root}/{runtimeKey}/{sessionId}），
            // 记忆/产物/索引全落专属子树（BUG-68 主刀，R-06 下沉会话层）
            .workspace(workspace)
            .enablePlanMode(isOn(config.getPlanMode()))
            .distributedStore(redisDistributedStore)
            .stateStore(postgresAgentStateStore)
            .skillRepository(skillRepository(agent))
            .enablePendingToolRecovery(true)
            // BUG-158：给轮末 pending 工具 drain 设上限（否则框架默认 ≈24s 空尾）
            .asyncToolTimeout(java.time.Duration.ofSeconds(asyncToolTimeoutSeconds))
            .middlewares(List.of(
                  // BUG-163：必须放在最外层——每次模型调用（含框架压缩调用）前补 reasoning 块
                  new ReasoningRepairMiddleware(),
                  // BUG-165：同一工具连续重复调用即熔断停轮（防本地小模型死循环）
                  new ToolLoopBreakerMiddleware(toolRepeatBreakerThreshold),
                  new StopOnAllDeniedMiddleware(), new ExplicitSkillMiddleware(),
                // T-08/R-02/R-03：知识库原件访问护栏（默认 observe 只记日志；PHOENIX_KB_PATH_GUARD=enforce 执行拒绝）
                new KnowledgePathGuardMiddleware(workspace.toString()),
                // T-10/R-04/R-05：在系统提示词末尾注入本项目权威知识库指引（对抗框架"工作区 knowledge/ 是事实源"的矛盾段）
                new KnowledgeGuidanceMiddleware()))
            .compaction(compactionFor(config))
            .toolResultEviction(toolResultEvictionFor(config));

        if (FilesystemPolicyEnm.REMOTE.getCode().equals(config.getFilesystemPolicy())) {
            // 远程共享存储不支持 shell（bugs.md B-07），显式关闭而非留给模型试错
            builder.filesystem(pgRemoteFilesystemSpec).disableShellTool();
        }
        else {
            LocalFilesystemSpec spec = new LocalFilesystemSpec().isolationScope(IsolationScope.USER);
            if (StringUtils.hasText(sessionId)) {
                // T-12/BUG-79：框架 shell cwd 取 LocalFilesystemSpec.project，缺省回落到 user.dir（容器内=/app，
                // shell 产物逃出工作区且面板不可见）。显式置为会话目录 → shell 与 file 工具同根。
                spec.project(workspace.toAbsolutePath().normalize());
            }
            builder.filesystem(spec);
        }

        if (isOn(config.getMemoryEnabled())) {
            builder.memory(memoryConfig(model));
        }
        else {
            builder.disableMemoryTools().disableMemoryHooks();
        }

        // runtime-max-iterations R-02：仅配置存在且合法时注入；否则不触框架默认（行为零变化）
        Integer maxIters = config.getMaxIterations();
        if (maxIters != null && maxIters >= AgentRuntimeConstant.MIN_TOOL_ITERATIONS
                && maxIters <= AgentRuntimeConstant.MAX_TOOL_ITERATIONS) {
            builder.maxIters(maxIters);
        }
        HarnessAgent built = builder.build();
        String summary = describe(agent, config, bundle.toolNames()) + ", workspace=" + workspace;
        log.info("对话智能体构建完成: {}", summary);
        return new BuildResult(built, summary, bundle.toolNames());
    }

    /**
     * 组装工具集：TodoTools 常驻 + 各扩展点按配置装配（工具异常不影响其余工具，降级为 ERROR 日志）。
     */
    public ToolkitBundle buildToolkit(Agent agent, AgentRuntimeConfig config) {
        Toolkit toolkit = new Toolkit();
        List<String> toolNames = new ArrayList<>();
        toolkit.registerTool(new TodoTools());
        toolNames.add("todo");
        for (AgentToolContributor contributor : toolContributors.orderedStream().toList()) {
            boolean supported;
            try {
                supported = contributor.supports(agent, config);
            }
            catch (RuntimeException e) {
                log.error("工具装配判定失败，已跳过: agentId={}, tool={}, err={}", agent.getId(),
                    contributor.toolName(), e.toString());
                continue;
            }
            if (!supported) {
                continue;
            }
            try {
                toolkit.registerTool(contributor.createTool(agent, config));
                toolNames.add(contributor.toolName());
            }
            catch (RuntimeException e) {
                log.error("工具实例化失败，已跳过: agentId={}, tool={}, err={}", agent.getId(),
                    contributor.toolName(), e.toString());
            }
        }
        return new ToolkitBundle(toolkit, List.copyOf(toolNames));
    }

    /**
     * 构建摘要（T-05 验证方式：启动/调用日志中打印，替代单元测试）。
     */
    public String describe(Agent agent, AgentRuntimeConfig config, List<String> toolNames) {
        return "agentId=%d, runtimeKey=%s, modelConfigId=%s, planMode=%s, memory=%s, policy=%s, tools=%s, skillPool=%d"
            .formatted(agent.getId(), runtimeKey(agent), config.getModelConfigId(), isOn(config.getPlanMode()),
                isOn(config.getMemoryEnabled()), config.getFilesystemPolicy(), toolNames, skillPoolSize(agent));
    }

    /**
     * 运行时技能池大小 = 已发布 ∧ 已绑定本智能体（查库失败降级为 0，不阻断构建）。
     * 有 agentId 走 agentId 路径（T-10）；仅存量仅有 sn 的场景回落 sn 路径。
     */
    public int skillPoolSize(Agent agent) {
        return skillPoolNames(agent).size();
    }

    /** 运行时技能池清单（已发布 ∧ 已绑定本智能体），供管理端回显与排障 */
    public List<String> skillPoolNames(Agent agent) {
        if (agent == null) {
            return List.of();
        }
        try {
            List<String> names = agent.getId() != null
                ? harnessSkillMapper.selectAllowedSkillNamesByAgentId(agent.getId())
                : harnessSkillMapper.selectAllowedSkillNamesByAgentSn(agent.getSn());
            return names == null ? List.of() : List.copyOf(names);
        }
        catch (RuntimeException e) {
            log.warn("技能池统计失败: agentId={}, sn={}, err={}", agent.getId(), agent.getSn(), e.toString());
            return List.of();
        }
    }

    /** 工具集与已装配工具名 */
    public record ToolkitBundle(Toolkit toolkit, List<String> toolNames) {
    }

    /** 构建结果（实例 + 摘要），供 Registry 缓存与预览接口复用 */
    public record BuildResult(HarnessAgent agent, String summary, List<String> toolNames) {
    }

    /**
     * 库配置路径的技能仓库：按 **agentId** 隔离（T-10）；存量 Java 自注册类仍走 sn 路径
     * （见 AbstractHarnessAgent.skillRepositoryForCurrentAgent）。
     */
    private AgentScopedSkillRepository skillRepository(Agent agent) {
        return new AgentScopedSkillRepository(postgresSkillRepository, agent.getId(), harnessSkillMapper);
    }

    /**
     * 运行时身份：存量自注册智能体用 sn（静态加载器按其寻址）；库中新建的智能体没有 sn，
     * 用 agentId 派生一个稳定身份（R-08 agentId 寻址），从而不依赖 Java 自注册。
     */
    public String runtimeKey(Agent agent) {
        // 规则单一实现移至 WorkspacePaths（scanner 同源引用，防漂移）
        return WorkspacePaths.runtimeKey(agent.getId(), agent.getSn());
    }

    private String sysPrompt(Agent agent) {
        if (StringUtils.hasText(agent.getPrompt())) {
            return agent.getPrompt();
        }
        String desc = StringUtils.hasText(agent.getDescription()) ? agent.getDescription() : "";
        return DEFAULT_SYS_PROMPT.formatted(agent.getName(), desc).trim();
    }

    private MemoryConfig memoryConfig(ChatModelBase model) {
        return MemoryConfig.builder()
            .model(model)
            .consolidationMaxTokens(2000)
            .consolidationMinGap(Duration.ofMinutes(60))
            .dailyFileRetentionDays(30)
            .sessionRetentionDays(90)
            .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofSeconds(30)))
            .build();
    }

    /** R-05：智能体配置 → 全局默认 两级回退（DSH compaction 语义对标） */
    private CompactionConfig compactionFor(AgentRuntimeConfig config) {
        int triggerTokens = config.getCompactionTriggerTokens() == null ? compactionTriggerTokensDefault
                : config.getCompactionTriggerTokens();
        int keepMessages = config.getCompactionKeepMessages() == null ? compactionKeepMessagesDefault
                : config.getCompactionKeepMessages();
        return CompactionConfig.builder()
            .triggerMessages(50)
            .triggerTokens(triggerTokens)
            .truncateArgs(CompactionConfig.TruncateArgsConfig.builder()
                .maxArgLength(2000)
                .truncationText("... [truncated] ...")
                .build())
            .keepMessages(keepMessages)
            .build();
    }

    /** R-05：工具结果回收阈值两级回退（其余参数保持框架默认） */
    private ToolResultEvictionConfig toolResultEvictionFor(AgentRuntimeConfig config) {
        int maxChars = config.getToolResultMaxChars() == null ? toolResultMaxCharsDefault
                : config.getToolResultMaxChars();
        ToolResultEvictionConfig d = ToolResultEvictionConfig.defaults();
        return ToolResultEvictionConfig.builder()
            .maxResultChars(maxChars)
            .previewChars(d.getPreviewChars())
            .evictionPath(d.getEvictionPath())
            .excludedToolNames(d.getExcludedToolNames())
            .build();
    }

    private boolean isOn(Integer value) {
        return value != null && value == 1;
    }
}
