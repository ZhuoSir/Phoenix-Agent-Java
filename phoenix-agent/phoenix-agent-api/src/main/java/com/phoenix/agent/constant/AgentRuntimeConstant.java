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

    /** 数据库取数工具：JDBC 实时 schema 内省最多纳入提示词的表数量（防止提示词爆炸） */
    public static final int MAX_SCHEMA_TABLES = 30;

    /** 数据库取数工具：每张表最多纳入提示词的列数量（防止提示词爆炸） */
    public static final int MAX_COLUMNS_PER_TABLE = 40;

    /** 数据库取数工具：结果集最多回显给模型的行数（执行层硬限制 1000 行，这里只做展示截断） */
    public static final int MAX_RESULT_ROWS_SHOWN = 50;

    /** 数据库取数工具：NL→SQL 生成的超时时间（秒） */
    public static final int SQL_GENERATE_TIMEOUT_SECONDS = 60;

    /** 深度分析工具：等待状态图跑完的超时时间（秒） */
    public static final int DEEP_ANALYSIS_TIMEOUT_SECONDS = 180;

    /** 深度分析工具：返回给模型的报告最大字符数（超出部分截断并标注） */
    public static final int DEEP_ANALYSIS_MAX_CHARS = 6000;

    /** 深度分析工具：同一智能体同时进行的深度分析数量上限（工具侧拿不到 sessionId，故按智能体限流） */
    public static final int MAX_DEEP_ANALYSIS_CONCURRENT = 2;

    private AgentRuntimeConstant() {
    }
}
