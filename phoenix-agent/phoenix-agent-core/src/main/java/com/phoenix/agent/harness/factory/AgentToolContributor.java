package com.phoenix.agent.harness.factory;

import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.data.entity.Agent;

/**
 * 对话智能体工具装配扩展点（T-05，plan 决策2「工具按 agent 实例化并绑定」）。
 *
 * <p>Factory 不写死任何具体工具：每个工具能力（知识库检索 / 数据库取数 / 深度分析）各实现本接口，
 * 由 Factory 在构建时按运行配置 {@link #supports} 判定并 {@link #createTool} **按智能体实例化**，
 * 从而使工具天然绑定自己的 agentId 与数据源，不需要让工具从 RuntimeContext 里猜身份。
 *
 * <p>新增工具只加一个 Spring Bean，Factory 与 Registry 均不需改动。
 */
public interface AgentToolContributor {

    /** 工具名（唯一，用于构建摘要与排障日志） */
    String toolName();

    /** 本智能体是否装配该工具（读运行配置开关，不做 IO） */
    boolean supports(Agent agent, AgentRuntimeConfig config);

    /**
     * 为本智能体实例化工具对象（返回 {@code @Tool} 注解所在的实例，交给 Toolkit 注册）。
     * 实现方在此绑定 agentId / 数据源 / topK 等实例级参数。
     */
    Object createTool(Agent agent, AgentRuntimeConfig config);
}
