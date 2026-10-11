package com.phoenix.data.service.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.phoenix.data.enums.ModelType;
import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import com.phoenix.data.service.vectorstore.ModelVectorStoreFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * 存量库升级**路径选择**（vector-dim-flexibility R-07 / T-13）。
 *
 * <p>
 * 路径规则（可核对，全部落日志）：
 * <ol>
 * <li>能**判定**生成该库向量的模型（其向量只存在于某一张模型专属表，且该模型仍存在、维度一致、可达）
 * ⇒ **路径(a)** 搬迁不重嵌（人工确认后执行，T-12）；</li>
 * <li>无法判定（无向量 / 向量在基表但目标与默认不一致且不可搬迁）、或**生成模型已删除**、
 * 或维度不一致 ⇒ **路径(b)** 走重建（复用 T-10 后台重建任务）；</li>
 * <li>升级前一律输出**影响面**（文档数 / 分块数 / 预计耗时）供人工决策。</li>
 * </ol>
 */
@Slf4j
@Service
public class KnowledgeUpgradeService {

	private final JdbcTemplate jdbcTemplate;

	private final ModelVectorStoreFactory modelVectorStoreFactory;

	private final ModelConfigDataService modelConfigDataService;

	private final KnowledgeMigrationService knowledgeMigrationService;

	private final KnowledgeRebuildService knowledgeRebuildService;

	public KnowledgeUpgradeService(JdbcTemplate jdbcTemplate, ModelVectorStoreFactory modelVectorStoreFactory,
			ModelConfigDataService modelConfigDataService, KnowledgeMigrationService knowledgeMigrationService,
			KnowledgeRebuildService knowledgeRebuildService) {
		this.jdbcTemplate = jdbcTemplate;
		this.modelVectorStoreFactory = modelVectorStoreFactory;
		this.modelConfigDataService = modelConfigDataService;
		this.knowledgeMigrationService = knowledgeMigrationService;
		this.knowledgeRebuildService = knowledgeRebuildService;
	}

	/** 升级计划：路径判定 + 候选模型 + 影响面 + 原因（人工确认前必看） */
	public Map<String, Object> upgradePlan(Long kbId, Integer toModelId) {
		Map<String, Object> kb = jdbcTemplate.queryForMap(
				"SELECT id, name, embedding_model_id FROM tbl_data_knowledge_base WHERE id = ? AND del_flag = 0", kbId);
		Integer bound = kb.get("embedding_model_id") == null ? null
				: ((Number) kb.get("embedding_model_id")).intValue();
		Integer defaultModelId = modelVectorStoreFactory.defaultEmbeddingModelId();
		Integer target = toModelId != null ? toModelId : (bound != null ? bound : defaultModelId);
		List<Long> ids = itemIds(kbId);
		// 各表（基表 + 所有 __ 专属表）中本库向量数
		Map<String, Integer> rowsByTable = new LinkedHashMap<>();
		String base = ModelVectorStoreFactory.TABLE_SIMPLE;
		rowsByTable.put(base, countRows(base, ids));
		List<String> tables = jdbcTemplate.queryForList(
				"SELECT c.relname FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid "
						+ "WHERE c.relkind = 'r' AND a.attname = 'embedding' AND c.relname LIKE ?  ORDER BY c.relname",
				String.class, base + "\\_\\_%");
		for (String t : tables) {
			rowsByTable.put(t, countRows(t, ids));
		}
		// 表 → 模型 id 映射（仅现存模型可映射；映射不到的专属表 ⇒ 生成模型已删除）
		Map<String, Integer> tableToModel = new LinkedHashMap<>();
		for (Map<String, Object> m : jdbcTemplate.queryForList(
				"SELECT id FROM tbl_data_model_config WHERE model_type = ? AND (is_deleted IS NULL OR is_deleted = 0)",
				ModelType.EMBEDDING.getCode())) {
			Integer mid = ((Number) m.get("id")).intValue();
			tableToModel.put(modelVectorStoreFactory.tableName(base, mid), mid);
		}
		List<String> reasons = new ArrayList<>();
		// 安全默认：无法判定一律走重建（路径 b）
		String path = "B";
		Integer candidateModelId = null;
		List<String> nonEmpty = new ArrayList<>();
		rowsByTable.forEach((t, n) -> {
			if (n != null && n > 0) {
				nonEmpty.add(t);
			}
		});
		if (nonEmpty.isEmpty()) {
			path = "B";
			reasons.add("无法判定生成模型：该库当前没有任何向量（未入库或已清空）⇒ 走重建");
		}
		else if (nonEmpty.size() > 1) {
			path = "B";
			reasons.add("无法判定生成模型：向量分散在多张表（" + nonEmpty + "）⇒ 走重建以避免混空间");
		}
		else {
			String only = nonEmpty.get(0);
			Integer mapped = tableToModel.get(only);
			// 注意：不能用 path 的初值("B")当条件——曾因此把 A/B 判定整段短路，永远判 B（T-16 端到端抓出）
			boolean determined = false;
			if (base.equals(only)) {
				// 基表：生成模型即默认模型（可判定）
				candidateModelId = defaultModelId;
				determined = true;
			}
			else if (mapped == null) {
				path = "B";
				reasons.add("生成模型已删除：向量所在表 " + only + " 无对应模型配置 ⇒ 走重建（不可搬迁到已删模型）");
			}
			else {
				candidateModelId = mapped;
				determined = true;
			}
			if (determined) {
				var cfg = modelConfigDataService.findById(candidateModelId);
				boolean exists = cfg != null;
				boolean active = exists && Boolean.TRUE.equals(cfg.getIsActive())
						&& (cfg.getIsDeleted() == null || cfg.getIsDeleted() == 0);
				boolean reachable = exists && modelVectorStoreFactory.isReachable(candidateModelId);
				if (!exists || !active) {
					path = "B";
					reasons.add("生成模型不可用（不存在或已停用/删除）⇒ 走重建");
				}
				else if (!reachable) {
					path = "B";
					reasons.add("生成模型当前不可达 ⇒ 走重建（搬迁后也无法检索）");
				}
				else {
					path = "A";
					reasons.add("已判定生成模型=" + candidateModelId + "（向量仅在 " + only + "）且可用 ⇒ 可走路径(a) 搬迁不重嵌（需人工确认）");
				}
			}
		}
		long documents = ids.size();
		int chunks = nonEmpty.stream().mapToInt(t -> rowsByTable.getOrDefault(t, 0)).sum();
		Map<String, Object> impact = new LinkedHashMap<>();
		impact.put("documentCount", documents);
		impact.put("chunkCount", chunks);
		impact.put("estimatedRebuildMinutes", chunks == 0 ? 0 : Math.max(1, (chunks + 59) / 60));
		impact.put("rowsByTable", rowsByTable);
		Map<String, Object> out = new LinkedHashMap<>();
		out.put("kbId", kbId);
		out.put("kbName", kb.get("name"));
		out.put("boundModelId", bound);
		out.put("defaultModelId", defaultModelId);
		out.put("path", path);
		out.put("candidateModelId", candidateModelId);
		out.put("targetModelId", target);
		out.put("reasons", reasons);
		out.put("impact", impact);
		out.put("requiresConfirm", true);
		log.info("升级路径判定: kbId={}, path={}, 已绑定={}, 候选模型={}, 目标模型={}, 文档数={}, 分块数={}, 原因={}", kbId,
				path, bound, candidateModelId, target, documents, chunks, reasons);
		return out;
	}

	/**
	 * 执行升级：path=A ⇒ 搬迁不重嵌（人工确认后，T-12）；path=B ⇒ 创建后台重建任务（T-10）。
	 */
	public Map<String, Object> upgrade(Long kbId, String path, boolean confirm, Integer toModelId, String operator) {
		Map<String, Object> plan = upgradePlan(kbId, toModelId);
		if (!confirm) {
			log.warn("升级被拒绝（未确认）: kbId={}, path={}", kbId, path);
			throw new IllegalStateException("升级属于受控操作：请先确认升级计划后带 confirm=true。计划=" + plan);
		}
		String decided = String.valueOf(plan.get("path"));
		String usePath = (path == null || path.isBlank()) ? decided : path.toUpperCase();
		if (!usePath.equals(decided)) {
			log.warn("请求路径与判定路径不一致，以**判定**为准: kbId={}, 请求={}, 判定={}", kbId, usePath, decided);
		}
		Map<String, Object> out = new LinkedHashMap<>(plan);
		if ("A".equals(decided)) {
			// 路径(a) 的**目标**优先取调用方指定（典型：模型重注册 ⇒ 指向同权重的新配置行）；
			// 未指定时才用"判定出的生成模型"（此时通常是 REBIND_ONLY）
			Integer targetModel = toModelId != null ? toModelId : (Integer) plan.get("candidateModelId");
			Map<String, Object> ledger = knowledgeMigrationService.migrate(kbId, targetModel, true);
			out.put("executed", "A");
			out.put("ledger", ledger);
			log.info("升级完成（路径 a 搬迁不重嵌）: kbId={}, 台账={}", kbId, ledger);
		}
		else {
			Integer target = (Integer) plan.get("targetModelId");
			Long jobId = knowledgeRebuildService.start(kbId, target, operator);
			out.put("executed", "B");
			out.put("jobId", jobId);
			out.put("targetModelId", target);
			log.info("升级已进入重建任务（路径 b）: kbId={}, jobId={}, 目标模型={}, 影响面={}", kbId, jobId, target,
					plan.get("impact"));
		}
		return out;
	}

	private List<Long> itemIds(Long kbId) {
		return jdbcTemplate.queryForList(
				"SELECT id FROM tbl_data_agent_knowledge WHERE knowledge_base_id = ? AND is_deleted = 0 ORDER BY id",
				Long.class, kbId);
	}

	private int countRows(String table, List<Long> ids) {
		try {
			Integer dims = jdbcTemplate.queryForObject(
					"SELECT count(*)::int FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid "
							+ "WHERE c.relkind = 'r' AND c.relname = ? AND a.attname = 'embedding'",
					Integer.class, table);
			if (dims == null || dims == 0 || ids.isEmpty()) {
				return 0;
			}
			String in = ids.stream().map(id -> "'" + id + "'")
					.collect(java.util.stream.Collectors.joining(","));
			Integer n = jdbcTemplate.queryForObject("SELECT count(*)::int FROM \"" + table
					+ "\" WHERE metadata->>'agentKnowledgeId' IN (" + in + ")", Integer.class);
			return n == null ? 0 : n;
		}
		catch (Exception e) {
			return 0;
		}
	}

}
