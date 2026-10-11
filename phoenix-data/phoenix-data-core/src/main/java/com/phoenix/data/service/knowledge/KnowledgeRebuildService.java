package com.phoenix.data.service.knowledge;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.ai.document.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoenix.data.entity.KbRebuildJob;
import com.phoenix.data.mapper.KbRebuildJobMapper;
import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import com.phoenix.data.service.vectorstore.ModelVectorStoreFactory;

import lombok.extern.slf4j.Slf4j;

/**
 * 知识库向量重建任务（R-04 / T-10）：后台执行、进度可查、失败清单可重试、断点续跑、
 * 全部成功后再**原子切换绑定**（单条事务 UPDATE）。
 *
 * <p>
 * 设计要点：
 * <ul>
 * <li>按条目 id 升序处理，游标逐条落库 ⇒ 进程中断后从游标继续（已处理条目天然跳过，写入亦幂等）；</li>
 * <li>单条失败**记录并继续**（不静默跳过）：失败清单持久化，可 `retryFailed` 只重试这些条目；</li>
 * <li>只要存在失败项就**不切换绑定**（旧模型继续可用，检索不受影响）；全部成功才切换；</li>
 * <li>切换 = `UPDATE tbl_data_knowledge_base SET embedding_model_id/model_space_key …`（单语句事务，
 * 失败则保持旧绑定，用户可见状态始终自洽）。</li>
 * </ul>
 */
@Slf4j
@Service
public class KnowledgeRebuildService {

	private static final int BATCH = 20;

	private final KbRebuildJobMapper jobMapper;

	private final JdbcTemplate jdbcTemplate;

	private final ModelVectorStoreFactory modelVectorStoreFactory;

	private final ModelConfigDataService modelConfigDataService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "kb-rebuild");
		t.setDaemon(true);
		return t;
	});

	public KnowledgeRebuildService(KbRebuildJobMapper jobMapper, JdbcTemplate jdbcTemplate,
			ModelVectorStoreFactory modelVectorStoreFactory, ModelConfigDataService modelConfigDataService) {
		this.jobMapper = jobMapper;
		this.jdbcTemplate = jdbcTemplate;
		this.modelVectorStoreFactory = modelVectorStoreFactory;
		this.modelConfigDataService = modelConfigDataService;
	}

	/** 创建重建任务（后台执行），返回 jobId */
	public Long start(Long kbId, Integer toModelId, String operator) {
		Map<String, Object> kb = jdbcTemplate.queryForMap(
				"SELECT id, embedding_model_id FROM tbl_data_knowledge_base WHERE id = ? AND del_flag = 0", kbId);
		if (modelConfigDataService.findById(toModelId) == null) {
			throw new IllegalArgumentException("目标向量模型不存在：" + toModelId);
		}
		Integer running = jdbcTemplate.queryForObject(
				"SELECT count(*)::int FROM tbl_data_kb_rebuild_job WHERE kb_id = ? AND status IN ('PENDING','RUNNING')",
				Integer.class, kbId);
		if (running != null && running > 0) {
			throw new IllegalStateException("该知识库已有进行中的重建任务，请等待完成或重试");
		}
		Integer total = jdbcTemplate.queryForObject(
				"SELECT count(*)::int FROM tbl_data_agent_knowledge WHERE knowledge_base_id = ? AND is_deleted = 0",
				Integer.class, kbId);
		KbRebuildJob job = new KbRebuildJob();
		job.setKbId(kbId);
		// 注意：embedding_model_id 列为 bigint ⇒ 驱动返回 Long（曾因此 ClassCastException）
		Object fromModel = kb.get("embedding_model_id");
		job.setFromModelId(fromModel == null ? null : ((Number) fromModel).intValue());
		job.setToModelId(toModelId);
		job.setStatus("PENDING");
		job.setTotalItems(total == null ? 0 : total);
		job.setProcessedItems(0);
		job.setFailedItems("[]");
		job.setOperator(operator);
		job.setSwitchApplied(false);
		job.setCreateTime(LocalDateTime.now());
		job.setUpdateTime(LocalDateTime.now());
		jobMapper.insert(job);
		log.info("重建任务已创建: jobId={}, kbId={}, from={}, to={}, total={}, operator={}", job.getId(), kbId,
				job.getFromModelId(), toModelId, job.getTotalItems(), operator);
		final Long jobId = job.getId();
		pool.submit(() -> run(jobId));
		return jobId;
	}

	/** 最新任务（进度查询） */
	public KbRebuildJob latest(Long kbId) {
		List<KbRebuildJob> list = jobMapper.selectListByQuery(
				com.mybatisflex.core.query.QueryWrapper.create().where("kb_id = ?", kbId).orderBy("id DESC").limit(1));
		return list.isEmpty() ? null : list.get(0);
	}

	public KbRebuildJob byId(Long jobId) {
		return jobMapper.selectOneById(jobId);
	}

	/** 只重试失败条目（成功条目不再重复处理） */
	public void retryFailed(Long jobId) {
		KbRebuildJob job = jobMapper.selectOneById(jobId);
		if (job == null) {
			throw new IllegalArgumentException("任务不存在：" + jobId);
		}
		List<Map<String, Object>> failures = parseFailures(job.getFailedItems());
		if (failures.isEmpty()) {
			throw new IllegalStateException("该任务没有失败条目");
		}
		// 把游标回退到失败清单中最小 id 之前，保证这些条目会被再次扫描
		int minId = failures.stream().mapToInt(f -> Integer.parseInt(String.valueOf(f.get("id")))).min().orElse(0);
		job.setCursorAgentKnowledgeId(minId - 1);
		job.setStatus("PENDING");
		job.setFinishedTime(null);
		job.setUpdateTime(LocalDateTime.now());
		jobMapper.update(job);
		log.info("重建任务重试失败条目: jobId={}, 失败条数={}, 游标回退至={}", jobId, failures.size(),
				job.getCursorAgentKnowledgeId());
		final Long id = jobId;
		pool.submit(() -> run(id));
	}

	/** 断点续跑（进程重启后由启动钩子自动调用；也可手工触发） */
	public void resume(Long jobId) {
		KbRebuildJob job = jobMapper.selectOneById(jobId);
		if (job == null) {
			throw new IllegalArgumentException("任务不存在：" + jobId);
		}
		if (!"PENDING".equals(job.getStatus()) && !"RUNNING".equals(job.getStatus())) {
			throw new IllegalStateException("任务状态为 " + job.getStatus() + "，无需续跑");
		}
		log.info("重建任务断点续跑: jobId={}, kbId={}, 游标={}, 已处理={}/{}", jobId, job.getKbId(),
				job.getCursorAgentKnowledgeId(), job.getProcessedItems(), job.getTotalItems());
		final Long id = jobId;
		pool.submit(() -> run(id));
	}

	/** 启动时自动续跑未完成任务（R-04 断点续跑；L-97 部署/重启不丢任务） */
	@EventListener(ApplicationReadyEvent.class)
	public void resumeUnfinishedOnStartup() {
		try {
			List<Long> ids = jdbcTemplate.queryForList(
					"SELECT id FROM tbl_data_kb_rebuild_job WHERE status IN ('PENDING','RUNNING') ORDER BY id",
					Long.class);
			if (!ids.isEmpty()) {
				log.warn("发现 {} 个未完成的重建任务，启动后自动续跑: {}", ids.size(), ids);
				ids.forEach(this::resume);
			}
		}
		catch (Exception e) {
			log.warn("启动续跑检查失败（不影响启动）: {}", e.toString());
		}
	}

	private void run(Long jobId) {
		KbRebuildJob job = jobMapper.selectOneById(jobId);
		if (job == null) {
			return;
		}
		job.setStatus("RUNNING");
		if (job.getStartedTime() == null) {
			job.setStartedTime(LocalDateTime.now());
		}
		job.setUpdateTime(LocalDateTime.now());
		jobMapper.update(job);
		String table = modelVectorStoreFactory.tableName(ModelVectorStoreFactory.TABLE_SIMPLE, job.getToModelId());
		// 先确保目标模型专属表存在（首绑即建 + **自愈**：不依赖缓存），否则后续幂等 DELETE 会因"表不存在"失败（实测踩坑）
		try {
			modelVectorStoreFactory.ensureTableExists(ModelVectorStoreFactory.TABLE_SIMPLE, job.getToModelId());
			modelVectorStoreFactory.forModel(ModelVectorStoreFactory.TABLE_SIMPLE, job.getToModelId());
		}
		catch (Exception e) {
			job.setStatus("FAILED");
			job.setErrorMsg("目标模型不可用，无法准备专属表：" + e.getMessage());
			job.setFinishedTime(LocalDateTime.now());
			job.setUpdateTime(LocalDateTime.now());
			jobMapper.update(job);
			log.error("重建任务准备失败（目标模型不可用）: jobId={}, to={}", jobId, job.getToModelId(), e);
			return;
		}
		List<Map<String, Object>> failures = parseFailures(job.getFailedItems());
		int cursor = job.getCursorAgentKnowledgeId() == null ? 0 : job.getCursorAgentKnowledgeId();
		int processed = job.getProcessedItems() == null ? 0 : job.getProcessedItems();
		log.info("重建任务开跑: jobId={}, kbId={}, to={}, table={}, 起点游标={}, 已处理={}/{}", jobId, job.getKbId(),
				job.getToModelId(), table, cursor, processed, job.getTotalItems());
		try {
			while (true) {
				List<Map<String, Object>> items = jdbcTemplate.queryForList(
						"SELECT id, content, title, agent_id FROM tbl_data_agent_knowledge WHERE knowledge_base_id = ? "
								+ "AND is_deleted = 0 AND id > ? ORDER BY id LIMIT " + BATCH,
						job.getKbId(), cursor);
				if (items.isEmpty()) {
					break;
				}
				for (Map<String, Object> it : items) {
					int itemId = ((Number) it.get("id")).intValue();
					try {
						rebuildOne(job, table, itemId, it);
						processed++;
						final int fid = itemId;
						failures.removeIf(f -> String.valueOf(f.get("id")).equals(String.valueOf(fid)));
					}
					catch (Exception e) {
						failures.add(failure(itemId, e));
						log.warn("重建条目失败（已记入失败清单，不静默跳过）: jobId={}, itemId={}, err={}", jobId, itemId,
								e.toString());
					}
					cursor = itemId;
					job.setProcessedItems(processed);
					job.setCursorAgentKnowledgeId(cursor);
					job.setFailedItems(toJson(failures));
					job.setUpdateTime(LocalDateTime.now());
					jobMapper.update(job); // 逐条落库 ⇒ 进度可查 + 断点续跑
				}
			}
			finish(job, failures);
		}
		catch (Exception e) {
			job.setStatus("FAILED");
			job.setErrorMsg(e.getClass().getSimpleName() + ": " + e.getMessage());
			job.setFinishedTime(LocalDateTime.now());
			job.setUpdateTime(LocalDateTime.now());
			jobMapper.update(job);
			log.error("重建任务整体失败: jobId={}, kbId={}", jobId, job.getKbId(), e);
		}
	}

	private void rebuildOne(KbRebuildJob job, String table, int itemId, Map<String, Object> item) {
		String text = item.get("content") == null ? String.valueOf(item.get("title")) : String.valueOf(item.get("content"));
		// 幂等：先删目标表中该条目的旧向量，再按目标模型重嵌（重试/续跑不会产生重复向量）
		jdbcTemplate.update("DELETE FROM \"" + table + "\" WHERE metadata->>'agentKnowledgeId' = ?",
				String.valueOf(itemId));
		Map<String, Object> md = new HashMap<>();
		md.put("vectorType", "agentKnowledge");
		md.put("agentKnowledgeId", itemId);
		md.put("knowledgeBaseId", job.getKbId());
		md.put("agentId", item.get("agent_id") == null ? "0" : String.valueOf(item.get("agent_id")));
		md.put("rebuildJobId", job.getId());
		modelVectorStoreFactory.forModel(ModelVectorStoreFactory.TABLE_SIMPLE, job.getToModelId())
				.add(List.of(new Document(java.util.UUID.randomUUID().toString(), text, md)));
	}

	/** 收尾：全部成功 ⇒ 原子切换绑定；有失败 ⇒ PARTIAL 且**不切换** */
	private void finish(KbRebuildJob job, List<Map<String, Object>> failures) {
		if (failures.isEmpty()) {
			int updated = jdbcTemplate.update(
					"UPDATE tbl_data_knowledge_base SET embedding_model_id = ?, model_space_key = ?, updator = ?, "
							+ "update_time = now() WHERE id = ? AND del_flag = 0",
					job.getToModelId(), modelVectorStoreFactory.spaceKeyFor(job.getToModelId()), job.getOperator(),
					job.getKbId());
			job.setSwitchApplied(updated > 0);
			if (updated > 0) {
				job.setStatus("SUCCEEDED");
				// 计数口径：重试会重新处理同一条目，processed 可能超过 total ⇒ 收尾时按 total 收敛（避免出现 4/3）
				if (job.getProcessedItems() != null && job.getTotalItems() != null
						&& job.getProcessedItems() > job.getTotalItems()) {
					log.info("重建计数收敛: jobId={}, 原始 processed={} > total={}（重试重复计数）", job.getId(),
							job.getProcessedItems(), job.getTotalItems());
					job.setProcessedItems(job.getTotalItems());
				}
				log.info("重建完成并原子切换绑定: jobId={}, kbId={}, from={}, to={}, spaceKey={}, processed={}/{}",
						job.getId(), job.getKbId(), job.getFromModelId(), job.getToModelId(),
						modelVectorStoreFactory.spaceKeyFor(job.getToModelId()), job.getProcessedItems(),
						job.getTotalItems());
			}
			else {
				job.setStatus("FAILED");
				job.setErrorMsg("原子切换失败：目标知识库不存在或已删除");
				log.error("原子切换失败: jobId={}, kbId={}", job.getId(), job.getKbId());
			}
		}
		else {
			job.setStatus("PARTIAL");
			if (job.getProcessedItems() != null && job.getTotalItems() != null
					&& job.getProcessedItems() > job.getTotalItems()) {
				job.setProcessedItems(job.getTotalItems());
			}
			job.setFailedItems(toJson(failures));
			log.warn("重建部分失败（未切换绑定，旧模型继续可用）: jobId={}, kbId={}, 失败条数={}, 清单={}", job.getId(),
					job.getKbId(), failures.size(), failures);
		}
		job.setFinishedTime(LocalDateTime.now());
		job.setUpdateTime(LocalDateTime.now());
		jobMapper.update(job);
	}

	private Map<String, Object> failure(int itemId, Exception e) {
		Map<String, Object> f = new HashMap<>();
		f.put("id", itemId);
		f.put("error", e.getClass().getSimpleName() + ": " + e.getMessage());
		return f;
	}

	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> parseFailures(String json) {
		if (json == null || json.isBlank()) {
			return new ArrayList<>();
		}
		try {
			return objectMapper.readValue(json, List.class);
		}
		catch (Exception e) {
			log.warn("失败清单解析失败，按空处理: {}", e.toString());
			return new ArrayList<>();
		}
	}

	private String toJson(Object o) {
		try {
			return objectMapper.writeValueAsString(o);
		}
		catch (Exception e) {
			return "[]";
		}
	}

}
