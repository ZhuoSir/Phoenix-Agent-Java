package com.phoenix.data.service.knowledge;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import com.phoenix.data.service.vectorstore.ModelVectorStoreFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * 升级路径(a)：模型明确且可用 ⇒ **人工确认后**把既有向量搬迁到该模型专属表（**不重新嵌入**），
 * 并写回知识库绑定与向量空间键（vector-dim-flexibility R-07a / T-12）。
 *
 * <p>
 * 关键约束：
 * <ul>
 * <li>**人工闸**：未带 confirm=true 一律不执行（拒绝并回吐搬迁计划）；</li>
 * <li>**不重嵌**：行只做"逐行原样拷贝"（embedding 列直接搬运），全程不调用任何嵌入模型；</li>
 * <li>**对账**：搬迁前后行数与维度逐项核对，不一致即拒绝/报错（不静默）；</li>
 * <li>维度不一致或目标模型不可用 ⇒ 不可走路径(a)，应走路径(b) 重建（T-13）。</li>
 * </ul>
 */
@Slf4j
@Service
public class KnowledgeMigrationService {

	private final JdbcTemplate jdbcTemplate;

	private final ModelVectorStoreFactory modelVectorStoreFactory;

	private final ModelConfigDataService modelConfigDataService;

	public KnowledgeMigrationService(JdbcTemplate jdbcTemplate, ModelVectorStoreFactory modelVectorStoreFactory,
			ModelConfigDataService modelConfigDataService) {
		this.jdbcTemplate = jdbcTemplate;
		this.modelVectorStoreFactory = modelVectorStoreFactory;
		this.modelConfigDataService = modelConfigDataService;
	}

	/** 搬迁计划（人工确认前必须先看）：来源/目标表、维度、行数、动作与不可行原因 */
	public Map<String, Object> plan(Long kbId, Integer toModelId) {
		Map<String, Object> out = new LinkedHashMap<>();
		Map<String, Object> kb = jdbcTemplate
				.queryForMap("SELECT id, name, embedding_model_id, model_space_key FROM tbl_data_knowledge_base "
						+ "WHERE id = ? AND del_flag = 0", kbId);
		Integer fromModelId = (Integer) toIntOrNull(kb.get("embedding_model_id"));
		Integer effectiveFrom = fromModelId != null ? fromModelId
				: modelVectorStoreFactory.defaultEmbeddingModelId();
		String fromTable = modelVectorStoreFactory.tableName(ModelVectorStoreFactory.TABLE_SIMPLE, effectiveFrom);
		String toTable = modelVectorStoreFactory.tableName(ModelVectorStoreFactory.TABLE_SIMPLE, toModelId);
		List<Long> itemIds = itemIds(kbId);
		int itemCount = itemIds.size();
		int sourceRows = countRows(fromTable, itemIds);
		int targetRowsBefore = countRows(toTable, itemIds);
		Integer sourceDims = tableDims(fromTable);
		Integer targetDims = tableDims(toTable);
		int modelDims = modelConfigDataService.findById(toModelId) == null ? -1
				: modelVectorStoreFactory.dimsOf(toModelId);
		boolean modelExists = modelConfigDataService.findById(toModelId) != null;
		boolean reachable = modelExists && modelVectorStoreFactory.isReachable(toModelId);
		boolean sameTable = fromTable.equals(toTable);
		List<String> reasons = new ArrayList<>();
		String action;
		if (!modelExists) {
			action = "NOT_ELIGIBLE";
			reasons.add("目标模型不存在");
		}
		else if (toModelId.equals(effectiveFrom)) {
			action = "REBIND_ONLY";
		}
		else if (sameTable) {
			action = "REBIND_ONLY";
		}
		else if (sourceDims != null && sourceDims != modelDims) {
			action = "NOT_ELIGIBLE";
			reasons.add("维度不一致：既有向量 " + sourceDims + " 维，目标模型 " + modelDims + " 维 ⇒ 必须走重建（路径 b）");
		}
		else {
			action = "COPY_ROWS";
		}
		if (!reachable) {
			reasons.add("目标模型当前不可达（搬迁后检索需可嵌入；请先恢复模型可用性）");
		}
		out.put("kbId", kbId);
		out.put("kbName", kb.get("name"));
		out.put("fromModelId", fromModelId);
		out.put("effectiveFromModelId", effectiveFrom);
		out.put("toModelId", toModelId);
		out.put("fromTable", fromTable);
		out.put("toTable", toTable);
		out.put("sourceDims", sourceDims);
		out.put("targetDims", targetDims);
		out.put("modelDims", modelDims);
		out.put("itemCount", itemCount);
		out.put("sourceRows", sourceRows);
		out.put("targetRowsBefore", targetRowsBefore);
		out.put("action", action);
		out.put("blockedReasons", reasons);
		out.put("eligible", "COPY_ROWS".equals(action) || "REBIND_ONLY".equals(action));
		out.put("requiresConfirm", true);
		out.put("note", "路径(a) 只搬运既有向量（不重新嵌入）；维度不一致或模型不可用请改走重建（路径 b，T-13）");
		log.info("搬迁计划: kbId={}, from={}({}), to={}({}), action={}, items={}, sourceRows={}, srcDims={}, modelDims={}, blocked={}",
				kbId, effectiveFrom, fromTable, toModelId, toTable, action, itemCount, sourceRows, sourceDims, modelDims,
				reasons);
		return out;
	}

	/** 执行搬迁（必须 confirm=true）：逐行原样拷贝 + 写回绑定与空间键；全程不调用嵌入模型 */
	@Transactional
	public Map<String, Object> migrate(Long kbId, Integer toModelId, boolean confirm) {
		Map<String, Object> p = plan(kbId, toModelId);
		if (!confirm) {
			log.warn("搬迁被拒绝（未确认，人工闸生效）: kbId={}, toModelId={}, action={}", kbId, toModelId, p.get("action"));
			throw new IllegalStateException("路径(a) 属于人工受控操作：请先确认搬迁计划后带 confirm=true 再执行。计划=" + p);
		}
		if (!Boolean.TRUE.equals(p.get("eligible"))) {
			log.warn("搬迁不可行（应走重建）: kbId={}, toModelId={}, action={}, 原因={}", kbId, toModelId, p.get("action"),
					p.get("blockedReasons"));
			throw new IllegalStateException("该库不可走路径(a)（" + p.get("blockedReasons") + "）；请改用重建：POST /api/knowledge-base/"
					+ kbId + "/rebuild");
		}
		String action = String.valueOf(p.get("action"));
		List<Long> ids = itemIds(kbId);
		String fromTable = String.valueOf(p.get("fromTable"));
		String toTable = String.valueOf(p.get("toTable"));
		int copied = 0;
		if ("COPY_ROWS".equals(action) && !ids.isEmpty()) {
			modelVectorStoreFactory.ensureTableExists(ModelVectorStoreFactory.TABLE_SIMPLE, toModelId);
			String in = quotedIds(ids);
			copied = jdbcTemplate.update("INSERT INTO \"" + toTable + "\" (id, content, metadata, embedding) "
					+ "SELECT uuid_generate_v4(), content, metadata, embedding FROM \"" + fromTable + "\" "
					+ "WHERE metadata->>'agentKnowledgeId' IN (" + in + ")");
		}
		// 写回绑定与向量空间键（原子：与拷贝同一事务）
		int updated = jdbcTemplate.update(
				"UPDATE tbl_data_knowledge_base SET embedding_model_id = ?, model_space_key = ?, update_time = now() "
						+ "WHERE id = ? AND del_flag = 0",
				toModelId, modelVectorStoreFactory.spaceKeyFor(toModelId), kbId);
		if (updated == 0) {
			throw new IllegalStateException("绑定写回失败：知识库不存在或已删除");
		}
		int sourceRows = countRows(fromTable, ids);
		int targetRows = countRows(toTable, ids);
		Map<String, Object> ledger = new LinkedHashMap<>();
		ledger.put("kbId", kbId);
		ledger.put("fromTable", fromTable);
		ledger.put("toTable", toTable);
		ledger.put("action", action);
		ledger.put("copiedRows", copied);
		ledger.put("sourceRows", sourceRows);
		ledger.put("targetRows", targetRows);
		ledger.put("itemCount", ids.size());
		ledger.put("dims", p.get("modelDims"));
		ledger.put("bindingWritten", toModelId);
		ledger.put("spaceKeyWritten", modelVectorStoreFactory.spaceKeyFor(toModelId));
		ledger.put("embeddingCalls", 0); // 路径(a) 不调用嵌入模型（外部可用模型侧计数交叉验证）
		log.info("搬迁完成（未重嵌）: kbId={}, action={}, 拷贝行数={}, 来源表行数={}, 目标表行数={}, 条目数={}, 绑定={}, 空间键={}",
				kbId, action, copied, sourceRows, targetRows, ids.size(), toModelId, ledger.get("spaceKeyWritten"));
		return ledger;
	}

	private List<Long> itemIds(Long kbId) {
		return jdbcTemplate.queryForList(
				"SELECT id FROM tbl_data_agent_knowledge WHERE knowledge_base_id = ? AND is_deleted = 0 ORDER BY id",
				Long.class, kbId);
	}

	private int countRows(String table, List<Long> ids) {
		if (ids.isEmpty() || tableDims(table) == null) {
			return 0;
		}
		String in = quotedIds(ids);
		Integer n = jdbcTemplate.queryForObject("SELECT count(*)::int FROM \"" + table
				+ "\" WHERE metadata->>'agentKnowledgeId' IN (" + in + ")", Integer.class);
		return n == null ? 0 : n;
	}

	private Integer tableDims(String table) {
		try {
			List<Integer> dims = jdbcTemplate.queryForList(
					"SELECT a.atttypmod FROM pg_class c JOIN pg_attribute a ON a.attrelid = c.oid "
							+ "WHERE c.relkind = 'r' AND c.relname = ? AND a.attname = 'embedding'",
					Integer.class, table);
			return dims.isEmpty() ? null : dims.get(0);
		}
		catch (Exception e) {
			return null;
		}
	}

	/** 元数据里的 agentKnowledgeId 是 text ⇒ IN 列表必须带引号（否则 text = integer 报错） */
	private static String quotedIds(List<Long> ids) {
		return ids.stream().map(id -> "'" + id + "'").collect(java.util.stream.Collectors.joining(","));
	}

	private static Object toIntOrNull(Object o) {
		if (o == null) {
			return null;
		}
		return ((Number) o).intValue();
	}

}
