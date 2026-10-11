package com.phoenix.data.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库展示 VO（列表/详情；组标签与条目数为读侧聚合）。
 */
@Data
@Builder
public class KnowledgeBaseVO {
    private Long id;
    private String name;
    private String description;
    private Integer status;
    private String creator;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 已授权组名（展示用，写侧在组管理页） */
    private List<String> groupNames;
    private long itemCount;
    /** 详情态：绑定的智能体名（删除保护提示源数据） */
    private List<String> boundAgentNames;

    /** 绑定的向量模型 id（R-01） */
    private Long embeddingModelId;

    /** 向量空间键（R-06） */
    private String modelSpaceKey;

    /** 换模型提示：非空表示请求要求换绑但未确认，绑定未变更（R-01 附加） */
    private String rebuildHint;
}
