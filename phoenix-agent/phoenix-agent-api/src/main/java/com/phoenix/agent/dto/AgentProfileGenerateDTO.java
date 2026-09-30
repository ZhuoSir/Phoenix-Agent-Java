package com.phoenix.agent.dto;

import com.phoenix.agent.enums.ProfileFieldEnm;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 智能体配置 AI 生成入参（R-01/R-03）。
 *
 * <p>只读生成：**不写库**，结果回填表单由管理员自行保存。
 */
@Data
public class AgentProfileGenerateDTO implements Serializable {

    /** 智能体名称（生成依据，必填） */
    private String name;

    /** 已填写的描述（可空；作为改进/润色上下文） */
    private String description;

    /** 已填写的提示词（可空；作为改进/润色上下文） */
    private String prompt;

    /** 生成目标；含两项则一次调用同时产出（R-03） */
    private List<ProfileFieldEnm> targets;
}
