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
}
