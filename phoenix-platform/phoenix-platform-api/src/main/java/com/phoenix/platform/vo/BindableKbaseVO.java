package com.phoenix.platform.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 智能体绑定候选知识库（R-14：不可选项带置灰原因）。
 */
@Data
@Builder
public class BindableKbaseVO {
    private Long id;
    private String name;
    private Integer status;
    private long itemCount;
    /** 已绑定 */
    private boolean bound;
    /** 组校验通过（agent 无组时全量可选——plan 风险3 兜底） */
    private boolean selectable;
    private String disabledReason;
}
