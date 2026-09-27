package com.phoenix.agent.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 智能体配置 AI 生成出参（只暴露所需字段；不含 api_key 等任何敏感信息）。
 *
 * <p>未请求的字段为 null，前端据此只回填请求过的项。
 */
@Data
public class AgentProfileGenerateVO implements Serializable {

    /** 生成的描述（纯文本） */
    private String description;

    /** 生成的提示词（Markdown，含固定骨架段落） */
    private String prompt;

    /** 本次实际使用的模型配置ID（便于"为什么用它生成"排障） */
    private Integer modelConfigId;

    /** 本次实际使用的模型名 */
    private String modelName;
}
