package com.phoenix.data.dto.knowledge.kb;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 创建知识库（R-01：名称必填 ≤64，查重在服务层）。 */
@Data
public class KnowledgeBaseCreateDTO {
    @NotBlank(message = "名称不能为空")
    @Size(max = 64, message = "名称不超过 64 字")
    private String name;
    @Size(max = 512, message = "描述不超过 512 字")
    private String description;
}
