package com.phoenix.agent.constant;

/**
 * 对话智能体运行时常量（阈值集中维护，禁散落魔法值）
 */
public final class AgentRuntimeConstant {

    /** 单智能体可同时开启的工具数量上限（当前工具面：知识库 / 数据库取数 / 数据库深度分析） */
    public static final int MAX_TOOL_COUNT = 3;

    /** 知识库检索召回条数默认值（= 原 RulesRagTool 写死值，保证存量行为不变） */
    public static final int DEFAULT_KNOWLEDGE_TOP_K = 10;

    /** 知识库检索召回条数下限 */
    public static final int MIN_KNOWLEDGE_TOP_K = 1;

    /** 知识库检索召回条数上限（防 context 爆炸，plan 风险节） */
    public static final int MAX_KNOWLEDGE_TOP_K = 50;

    /** 知识库检索相似度阈值默认值（= 原 RulesRagTool 写死值） */
    public static final double DEFAULT_KNOWLEDGE_SIMILARITY_THRESHOLD = 0.65;

    private AgentRuntimeConstant() {
    }
}
