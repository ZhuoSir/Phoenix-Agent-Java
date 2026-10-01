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
}
