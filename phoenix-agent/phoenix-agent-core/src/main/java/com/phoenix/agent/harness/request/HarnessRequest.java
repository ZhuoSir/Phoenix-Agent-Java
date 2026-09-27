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
    /** 显式执行技能 id 列表（R-05：不传=维持模型自主匹配） */
    private List<Long> enabledSkillIds;
}
