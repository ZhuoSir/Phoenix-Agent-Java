package com.phoenix.data.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.vectorstore.filter.Filter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 混合搜索请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchRequest implements Serializable {

	// === 基础参数 ===
	private String query;

	private Integer topK;

	private double similarityThreshold = 0.0;

	private Filter.Expression filterExpression;

	/**
	 * R-03/T-06：调用方按"命中库绑定模型"路由后注入的向量存储；为空则用策略默认存储。
	 * transient：不参与序列化（VectorStore 不可序列化，仅进程内传递）。
	 */
	private transient org.springframework.ai.vectorstore.VectorStore routingVectorStore;

	// 向量检索权重
	@Builder.Default
	private Double vectorWeight = 0.5;

	// 关键词检索权重
	@Builder.Default
	private Double keywordWeight = 0.5;

	// 是否开启重排序模型
	@Builder.Default
	private boolean useRerank = false;

	// 扩展参数包将来某种数据库的特有参数
	@Builder.Default
	private Map<String, Object> extraParams = new HashMap<>();

	/**
	 * 转换为 Spring AI 向量搜索请求
	 */
	public org.springframework.ai.vectorstore.SearchRequest toVectorSearchRequest() {
		return org.springframework.ai.vectorstore.SearchRequest.builder()
			.query(this.query)
			.topK(this.topK)
			.similarityThreshold(this.similarityThreshold)
			.filterExpression(this.filterExpression)
			.build();
	}

}
