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
}
