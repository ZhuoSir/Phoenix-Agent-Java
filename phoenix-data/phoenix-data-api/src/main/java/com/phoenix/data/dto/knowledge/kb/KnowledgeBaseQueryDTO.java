package com.phoenix.data.dto.knowledge.kb;

import lombok.Data;

/** 知识库分页查询条件（对齐 AgentKnowledgeQueryDTO 命名法）。 */
@Data
public class KnowledgeBaseQueryDTO {
    /** 名称模糊 */
    private String name;
    /** 1启用 0停用；null=全部 */
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
