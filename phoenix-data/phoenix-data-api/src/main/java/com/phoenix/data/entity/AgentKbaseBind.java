package com.phoenix.data.entity;

import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体↔知识库绑定（R-08，多对多）。删智能体仅删绑定，不动库与条目（plan P6）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("tbl_data_agent_kbase_bind")
public class AgentKbaseBind {

	@Id(keyType = KeyType.Auto)
	private Long id;

	private Long agentId;

	private Long knowledgeBaseId;

	private String creator;

	private LocalDateTime createTime;
}
