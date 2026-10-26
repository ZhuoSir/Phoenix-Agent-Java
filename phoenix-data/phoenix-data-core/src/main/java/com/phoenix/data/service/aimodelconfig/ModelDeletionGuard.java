package com.phoenix.data.service.aimodelconfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.phoenix.data.entity.ModelConfig;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.service.vectorstore.ModelVectorStoreFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * 模型删除前置校验与护栏（vector-dim-flexibility R-04 / T-09）。
 *
 * <p>
 * 护栏：
 * <ol>
 * <li>默认模型不可删除（须先切换默认）；</li>
 * <li>该类型最后一个**可用**模型不可删除（否则整类能力失效）；</li>
 * <li>仍被知识库引用 ⇒ 必须**显式确认**（受控流程），并返回影响面清单支撑决策。</li>
 * </ol>
 */
@Slf4j
@Component
public class ModelDeletionGuard {

	private final JdbcTemplate jdbcTemplate;

	private final ModelConfigDataService modelConfigDataService;

	private final ModelVectorStoreFactory modelVectorStoreFactory;

	public ModelDeletionGuard(JdbcTemplate jdbcTemplate, ModelConfigDataService modelConfigDataService,
			ModelVectorStoreFactory modelVectorStoreFactory) {
		this.jdbcTemplate = jdbcTemplate;
		this.modelConfigDataService = modelConfigDataService;
		this.modelVectorStoreFactory = modelVectorStoreFactory;
	}

	/** 影响面清单：库/文档数/分块数/预计耗时 + 可删判定与阻塞原因（R-04）。 */
	public Map<String, Object> deleteImpact(Integer modelId) {
		ModelConfig cfg = modelConfigDataService.findById(modelId);
		Map<String, Object> out = new LinkedHashMap<>();
		if (cfg == null) {
			out.put("exists", false);
			out.put("deletable", false);
			out.put("blockedReasons", List.of("模型不存在"));
			return out;
		}
		out.put("exists", true);
		out.put("modelId", cfg.getId());
		out.put("provider", cfg.getProvider());
		out.put("modelName", cfg.getModelName());
		out.put("modelType", cfg.getModelType() == null ? null : cfg.getModelType().getCode());
		out.put("isDefault", Boolean.TRUE.equals(cfg.getIsDefault()));
		out.put("isActive", Boolean.TRUE.equals(cfg.getIsActive()));

		// 引用的知识库 + 其条目数（文档数）与分块数（向量条数）
		List<Map<String, Object>> kbs = jdbcTemplate.queryForList(
				"SELECT id, name FROM tbl_data_knowledge_base WHERE embedding_model_id = ? AND del_flag = 0 ORDER BY id",
				modelId);
		long docCount = 0;
		long chunkCount = 0;
		List<Map<String, Object>> kbRows = new ArrayList<>();
		for (Map<String, Object> kb : kbs) {
			Long kbId = ((Number) kb.get("id")).longValue();
			Integer docs = jdbcTemplate.queryForObject(
					"SELECT count(*)::int FROM tbl_data_agent_knowledge WHERE knowledge_base_id = ? AND is_deleted = 0",
					Integer.class, kbId);
			docCount += docs == null ? 0 : docs;
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("kbId", kbId);
			row.put("name", kb.get("name"));
			row.put("documentCount", docs == null ? 0 : docs);
			// 向量元数据中**不含知识库标识**（实测：仅 agentKnowledgeId/vectorType/source 等）⇒ 无法按库拆分分块数；
			// 但"该模型专属表内的向量必然由该模型生成" ⇒ 模型表总条数就是真实待重建规模（R-04 规模预估口径）
			row.put("chunkCount", null);
			row.put("chunkCountNote", "向量元数据无库标识，分块数按模型表总量计（见 chunkCount）");
			kbRows.add(row);
		}
		// 模型专属表总条数（simple + rag）：该表内向量必然由本模型生成 ⇒ 真实待重建规模
		long tableChunks = 0;
		for (String base : new String[] { ModelVectorStoreFactory.TABLE_SIMPLE, ModelVectorStoreFactory.TABLE_RAG }) {
			long c = modelVectorStoreFactory.vectorCount(base, modelId);
			if (c > 0) {
				tableChunks += c;
			}
		}
		chunkCount = tableChunks;
		out.put("boundKbs", kbRows);
		out.put("kbCount", kbRows.size());
		out.put("documentCount", docCount);
		out.put("chunkCount", chunkCount);
		out.put("chunkCountSource", "模型专属表总条数（simple+rag）");
		// 预计耗时（R-04：重建规模预估）——按 60 分块/分钟的重嵌吞吐保守估算，向上取整，最少 1 分钟
		out.put("estimatedRebuildMinutes", chunkCount == 0 ? 0 : Math.max(1, (chunkCount + 59) / 60));

		List<String> reasons = new ArrayList<>();
		if (Boolean.TRUE.equals(cfg.getIsDefault())) {
			reasons.add("默认模型不可删除：请先到模型管理切换默认模型");
		}
		if (Boolean.TRUE.equals(cfg.getIsActive())) {
			reasons.add("该模型当前处于启用状态：请先停用再删除");
		}
		int otherAvailable = countOtherAvailable(cfg);
		out.put("otherAvailableCount", otherAvailable);
		if (otherAvailable == 0) {
			reasons.add("这是该类型最后一个可用模型：删除后该类型将无模型可用");
		}
		out.put("blockedReasons", reasons);
		out.put("deletable", reasons.isEmpty());
		out.put("requiresConfirm", !kbRows.isEmpty());
		log.info("模型删除影响面: modelId={}, modelType={}, 引用库数={}, 文档数={}, 分块数={}, 可删={}, 阻塞原因数={}",
				modelId, out.get("modelType"), kbRows.size(), docCount, chunkCount, reasons.isEmpty(), reasons.size());
		return out;
	}

	/** 同类型其它可用模型数量（启用、未删、非本模型） */
	private int countOtherAvailable(ModelConfig cfg) {
		Integer n = jdbcTemplate.queryForObject(
				"SELECT count(*)::int FROM tbl_data_model_config WHERE model_type = ? AND is_active = true "
						+ "AND (is_deleted IS NULL OR is_deleted = 0) AND id <> ?",
				Integer.class, cfg.getModelType() == null ? null : cfg.getModelType().getCode(), cfg.getId());
		return n == null ? 0 : n;
	}

	/**
	 * 受控删除：护栏全部通过且（无引用或已显式确认）才执行软删除。
	 *
	 * @param modelId       模型 id
	 * @param confirmImpact 是否已确认"影响面清单"（有引用时必填 true）
	 */
	public Map<String, Object> deleteWithGuard(Integer modelId, boolean confirmImpact) {
		Map<String, Object> impact = deleteImpact(modelId);
		if (!Boolean.TRUE.equals(impact.get("exists"))) {
			throw new IllegalArgumentException("模型不存在：" + modelId);
		}
		@SuppressWarnings("unchecked")
		List<String> reasons = (List<String>) impact.get("blockedReasons");
		if (!reasons.isEmpty()) {
			log.warn("模型删除被拒绝: modelId={}, 原因={}", modelId, reasons);
			throw new IllegalStateException("删除被拒绝：" + String.join("；", reasons));
		}
		boolean hasRefs = ((Number) impact.get("kbCount")).intValue() > 0;
		if (hasRefs && !confirmImpact) {
			log.warn("模型删除需确认影响面: modelId={}, 引用库数={}, 分块数={}", modelId, impact.get("kbCount"),
					impact.get("chunkCount"));
			throw new IllegalStateException("该模型被 " + impact.get("kbCount") + " 个知识库引用（文档 "
					+ impact.get("documentCount") + " / 分块 " + impact.get("chunkCount") + "），删除后这些库需按默认模型重建；"
					+ "请确认后带 confirmImpact=true 再执行");
		}
		modelConfigDataService.deleteConfig(modelId);
		log.info("模型已删除: modelId={}, 引用库数={}, 分块数={}, 待重建库={}", modelId, impact.get("kbCount"),
				impact.get("chunkCount"), impact.get("boundKbs"));
		return impact;
	}

	/** 供上层拼装提示：模型类型中文/编码无关，仅返回 code */
	public static String typeCode(ModelType t) {
		return t == null ? null : t.getCode();
	}

}
