package com.phoenix.data.entity;

import java.time.LocalDateTime;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 知识库向量重建任务（vector-dim-flexibility R-04 / T-10）。
 *
 * <p>
 * status：PENDING / RUNNING / SUCCEEDED（全部成功并已原子切换绑定）/ PARTIAL（有失败项，未切换）/ FAILED。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("tbl_data_kb_rebuild_job")
public class KbRebuildJob {

	@Id(keyType = KeyType.Auto)
	private Long id;

	private Long kbId;

	/** 切换前绑定的模型（可追溯） */
	private Integer fromModelId;

	/** 本次重建目标模型 */
	private Integer toModelId;

	private String status;

	private Integer totalItems;

	private Integer processedItems;

	/** 失败清单（jsonb 文本，元素 {id, error}）——失败不静默跳过 */
	private String failedItems;

	/** 断点续跑游标（按条目 id 升序处理） */
	private Integer cursorAgentKnowledgeId;

	private String errorMsg;

	private String operator;

	/** 是否已执行原子切换（绑定更新为单条事务语句） */
	private Boolean switchApplied;

	private LocalDateTime startedTime;

	private LocalDateTime finishedTime;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;
}
