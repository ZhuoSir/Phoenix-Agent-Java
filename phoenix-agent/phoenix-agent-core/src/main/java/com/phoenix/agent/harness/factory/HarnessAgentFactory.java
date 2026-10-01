package com.phoenix.agent.harness.factory;

import com.phoenix.agent.enums.FilesystemPolicyEnm;
import com.phoenix.agent.harness.middleware.StopOnAllDeniedMiddleware;
import com.phoenix.agent.harness.skill.AgentScopedSkillRepository;
import com.phoenix.agent.harness.skill.ExplicitSkillMiddleware;
import com.phoenix.agent.mapper.HarnessSkillMapper;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.agent.service.AgentRuntimeConfigService;
import com.phoenix.agent.service.harness.HarnessModelRegistry;
import com.phoenix.data.entity.Agent;
import io.agentscope.core.skill.repository.postgresql.PostgresSkillRepository;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.builtin.TodoTools;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
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

    /** 工作区（与存量自注册智能体保持一致，避免两套目录） */
    /** BL-19/R-01：workspace 根配置化。默认与历史一致（裸机开发零感知）；
     *  交付包经 env 指向 uploads 卷内路径，使产物持久化可备份。旧常量无外部引用，安全收敛。 */
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
        if (agent == null || agent.getId() == null) {
            throw new IllegalArgumentException("智能体不存在或缺少 id，无法构建运行时实例");
        }
        AgentRuntimeConfig config = agentRuntimeConfigService.resolve(agent.getId());
        OpenAIChatModel model = harnessModelRegistry.getOpenAIChatModel(config.getModelConfigId());
        ToolkitBundle bundle = buildToolkit(agent, config);

        HarnessAgent.Builder builder = HarnessAgent.builder()
            .name(runtimeKey(agent))
            .agentId(String.valueOf(agent.getId()))
            .description(agent.getDescription())
            .sysPrompt(sysPrompt(agent))
            .model(model)
            .toolkit(bundle.toolkit())
            .workspace(Path.of(workspaceRoot))
            .enablePlanMode(isOn(config.getPlanMode()))
            .distributedStore(redisDistributedStore)
            .stateStore(postgresAgentStateStore)
            .skillRepository(skillRepository(agent))
            .enablePendingToolRecovery(true)
            .middlewares(List.of(new StopOnAllDeniedMiddleware(), new ExplicitSkillMiddleware()))
            .compaction(defaultCompaction())
            .toolResultEviction(ToolResultEvictionConfig.defaults());

        if (FilesystemPolicyEnm.REMOTE.getCode().equals(config.getFilesystemPolicy())) {
            // 远程共享存储不支持 shell（bugs.md B-07），显式关闭而非留给模型试错
            builder.filesystem(pgRemoteFilesystemSpec).disableShellTool();
        }
        else {
            builder.filesystem(new LocalFilesystemSpec().isolationScope(IsolationScope.USER));
        }

        if (isOn(config.getMemoryEnabled())) {
            builder.memory(memoryConfig(model));
        }
        else {
            builder.disableMemoryTools().disableMemoryHooks();
        }

        HarnessAgent built = builder.build();
        String summary = describe(agent, config, bundle.toolNames());
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
        return StringUtils.hasText(agent.getSn()) ? agent.getSn() : "agent-" + agent.getId();
    }

    private String sysPrompt(Agent agent) {
        if (StringUtils.hasText(agent.getPrompt())) {
            return agent.getPrompt();
        }
        String desc = StringUtils.hasText(agent.getDescription()) ? agent.getDescription() : "";
        return DEFAULT_SYS_PROMPT.formatted(agent.getName(), desc).trim();
    }

    private MemoryConfig memoryConfig(OpenAIChatModel model) {
        return MemoryConfig.builder()
            .model(model)
            .consolidationMaxTokens(2000)
            .consolidationMinGap(Duration.ofMinutes(60))
            .dailyFileRetentionDays(30)
            .sessionRetentionDays(90)
            .flushTrigger(MemoryConfig.FlushTrigger.throttled(Duration.ofSeconds(30)))
            .build();
    }

    private CompactionConfig defaultCompaction() {
        return CompactionConfig.builder()
            .triggerMessages(50)
            .truncateArgs(CompactionConfig.TruncateArgsConfig.builder()
                .maxArgLength(2000)
                .truncationText("... [truncated] ...")
                .build())
            .keepMessages(20)
            .build();
    }

    private boolean isOn(Integer value) {
        return value != null && value == 1;
    }
}
