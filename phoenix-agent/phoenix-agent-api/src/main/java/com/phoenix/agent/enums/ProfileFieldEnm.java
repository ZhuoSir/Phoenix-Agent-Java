package com.phoenix.agent.enums;

/**
 * 可生成的智能体配置字段（R-01/R-03）。
 *
 * <p>生成入口按 target 决定"只生成一项"还是"两项一起生成"，非法取值在入参校验阶段即被拒。
 */
public enum ProfileFieldEnm {

    /** 智能体描述（纯文本，一句话） */
    DESCRIPTION,
    /** 系统提示词（Markdown，含固定骨架段落） */
    PROMPT
}
