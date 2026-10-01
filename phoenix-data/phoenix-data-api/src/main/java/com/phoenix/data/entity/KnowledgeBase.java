package com.phoenix.data.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 独立知识库实体（knowledge-base spec R-01）。
 * description 双用：普通库=用户描述；迁移库=MIGRATED_FROM_AGENT:{id}（幂等锚/溯源）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("tbl_data_knowledge_base")
public class KnowledgeBase {

	@Id(keyType = KeyType.Auto)
	private Long id;

	private String name;

	private String description;

	/** 1启用 0停用；停用库整体不参与召回（R-09/A-03） */
	private Integer status;

	private String creator;

	private String updator;

	private Integer delFlag;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
