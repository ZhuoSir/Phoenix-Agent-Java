package com.phoenix.agent.harness.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HarnessRequest implements Serializable {

    /** 渠道标记：admin=管理端 / front=前台（T-05 MCP 挂载口径分流；null 按 admin 兼容既有调用方） */
    private String channel;
    @NotBlank
    private String userId;
    @NotBlank
    private String sessionId;
    @NotBlank
    private String message;
    @NotBlank
    private String harnessSn;
    /** 前台通道按智能体 id 寻址（后台通道用 harnessSn，二者其一） */
    private Long agentId;
    /** 显式执行技能 id 列表（R-05：不传=维持模型自主匹配） */
    private List<Long> enabledSkillIds;
    /**
     * 本轮可用技能范围提示（R-09 风险①缓解）：前台通道注入"仅可使用以下技能"约束，
     * 抑制未授权技能被自主匹配加载；后台通道不设置。
     */
    private String skillScopeHint;

    /**
     * 对话附件 id 列表（chat-attachment-understanding T-06；**可选**）。
     * 不传/为空 ⇒ 装配器短路，行为与改动前逐字节一致（共享面 S1'）。
     */
    private java.util.List<Long> attachmentIds;
}
