package com.phoenix.agent.constant;

/**
 * 对话智能体运行时常量（阈值集中维护，禁散落魔法值）
 */
public final class AgentRuntimeConstant {

    /** 单智能体可同时开启的工具数量上限（当前工具面：知识库 / 数据库取数 / 数据库深度分析） */
    public static final int MAX_TOOL_COUNT = 3;

    private AgentRuntimeConstant() {
    }
}
