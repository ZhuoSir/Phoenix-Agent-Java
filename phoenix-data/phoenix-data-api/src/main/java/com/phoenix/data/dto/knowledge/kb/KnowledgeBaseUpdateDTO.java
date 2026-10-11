package com.phoenix.data.dto.knowledge.kb;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 编辑知识库（R-03；status 可空=不改动）。 */
@Data
public class KnowledgeBaseUpdateDTO {
    @NotNull
    private Long id;
    @Size(max = 64, message = "名称不超过 64 字")
    private String name;
    @Size(max = 512, message = "描述不超过 512 字")
    private String description;
    private Integer status;

    /** 请求更换的向量模型 id（为空=不变更绑定）；变更需走重建流程（R-01 附加） */
    private Long embeddingModelId;

    /** 是否已确认"更换模型将重建该知识库"；未确认时仅返回提示、绑定不变（R-01 附加） */
    private Boolean confirmRebuild;
}
