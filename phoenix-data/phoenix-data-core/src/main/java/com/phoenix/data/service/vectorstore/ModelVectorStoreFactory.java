package com.phoenix.data.service.vectorstore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.entity.ModelConfig;
import com.phoenix.data.enums.ModelProvider;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.service.aimodelconfig.EmbeddingDimensionResolver;
import com.phoenix.data.service.aimodelconfig.DynamicModelFactory;
import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import com.phoenix.data.service.aimodelconfig.OllamaApiClient;

import lombok.extern.slf4j.Slf4j;

/**
 * 每向量模型一套向量表（vector-dim-flexibility R-02）。
 *
 * <p>
 * 设计要点：
 * <ul>
 * <li>默认模型（或未绑定）⇒ **复用既有 bean**（单模型部署下解析到与改动前同一张表 ⇒ 行为等价）；</li>
 * <li>非默认模型 ⇒ 使用 `<原表名>__<模型键短哈希>` 表（列上保留该模型维度 + hnsw 索引），首次绑定即建（R-02）；</li>
 * <li>表按模型物理隔离，绝无跨向量空间混存（pgvector 实测：无维度列无法建 hnsw 索引）。</li>
 * </ul>
 */
@Slf4j
@Component
public class ModelVectorStoreFactory {

	/** 知识库 RAG 存储（检索侧） */
	public static final String TABLE_RAG = "tbl_vector_store_rag";

	/** 知识库条目存储（写入侧：AgentVectorStoreServiceImpl 使用 simpleVectorStore） */
	public static final String TABLE_SIMPLE = "tbl_vector_store_simple_data";

	private final JdbcTemplate jdbcTemplate;

	private final ModelConfigDataService modelConfigDataService;

	private final EmbeddingDimensionResolver embeddingDimensionResolver;

	private final OllamaApiClient ollamaApiClient;

	private final DynamicModelFactory dynamicModelFactory;

	private final VectorStore defaultRagStore;

	private final VectorStore defaultSimpleStore;

	/** 查询嵌入调用/命中计数（R-03/T-07：按模型缓存，供验证与运维观测） */
	private final Map<String, java.util.concurrent.atomic.AtomicInteger> embedCalls = new ConcurrentHashMap<>();

	private final Map<String, java.util.concurrent.atomic.AtomicInteger> embedCacheHits = new ConcurrentHashMap<>();

	/** 缓存：baseTable#modelKey → 已装配的向量存储 */
	private final Map<String, PgVectorStore> cache = new ConcurrentHashMap<>();

	public ModelVectorStoreFactory(JdbcTemplate jdbcTemplate, ModelConfigDataService modelConfigDataService,
			EmbeddingDimensionResolver embeddingDimensionResolver, OllamaApiClient ollamaApiClient,
			DynamicModelFactory dynamicModelFactory,
			@Qualifier("ragVectorStore") VectorStore defaultRagStore,
			@Qualifier("simpleVectorStore") VectorStore defaultSimpleStore) {
		this.jdbcTemplate = jdbcTemplate;
		this.modelConfigDataService = modelConfigDataService;
		this.embeddingDimensionResolver = embeddingDimensionResolver;
		this.ollamaApiClient = ollamaApiClient;
		this.dynamicModelFactory = dynamicModelFactory;
		this.defaultRagStore = defaultRagStore;
		this.defaultSimpleStore = defaultSimpleStore;
	}

	/** 默认 embedding 模型 id（无默认返回 null） */
	public Integer defaultEmbeddingModelId() {
		var def = modelConfigDataService.getDefaultConfigByType(ModelType.EMBEDDING);
		return def == null ? null : def.getId();
	}

	/** 按"库/条目所属模型"解析向量存储：未绑定或默认模型 ⇒ 既有 bean；否则 ⇒ 该模型专属表。 */
	public VectorStore forModel(String baseTable, Integer modelId) {
		Integer defId = defaultEmbeddingModelId();
		if (modelId == null || (defId != null && defId.equals(modelId))) {
			return defaultStore(baseTable);
		}
		return ensureModelTable(baseTable, modelId);
	}

	/**
	 * 幂等确保专属表存在（**不依赖内存缓存**）：即使 store 已缓存、或表被外部删除，也能自愈（运维工具箱/断点续跑）。
	 */
	public void ensureTableExists(String baseTable, Integer modelId) {
		if (modelId == null) {
			return;
		}
		Integer defId = defaultEmbeddingModelId();
		if (defId != null && defId.equals(modelId)) {
			return; // 默认模型用基表
		}
		ModelConfig cfg = modelConfigDataService.getById(modelId);
		if (cfg == null) {
			throw new IllegalArgumentException("指定的向量模型不存在：" + modelId);
		}
		String modelKey = modelKey(cfg);
		ensureTableDdl(baseTable + "__" + shortHash(modelKey), resolveDimensions(cfg), spaceKeyFor(modelId));
	}

	/** 确保某模型的表存在（首绑即建；幂等，可重复调用）。 */
	public PgVectorStore ensureModelTable(String baseTable, Integer modelId) {
		ModelConfig cfg = modelId == null ? null : modelConfigDataService.getById(modelId);
		if (cfg == null) {
			throw new IllegalArgumentException("指定的向量模型不存在：" + modelId);
		}
		String modelKey = modelKey(cfg);
		String key = baseTable + "#" + modelKey;
		PgVectorStore cached = cache.get(key);
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			cached = cache.get(key);
			if (cached != null) {
				return cached;
			}
			int dims = resolveDimensions(cfg);
			String table = baseTable + "__" + shortHash(modelKey);
			ensureTableDdl(table, dims, spaceKeyFor(modelId));
			EmbeddingModel embeddingModel = cachingEmbeddingModel(modelKey,
					dynamicModelFactory.createEmbeddingModel(toDto(cfg)));
			PgVectorStore store = PgVectorStore.builder(jdbcTemplate, embeddingModel).vectorTableName(table)
					.dimensions(dims)
					.distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
					.indexType(PgVectorStore.PgIndexType.HNSW).initializeSchema(true).maxDocumentBatchSize(200).build();
			cache.put(key, store);
			log.info("模型专属向量表已就绪: baseTable={}, modelId={}, modelKey={}, table={}, dims={}", baseTable, modelId,
					modelKey, table, dims);
			return store;
		}
	}

	/** 首绑某模型时建齐知识库相关表（写入侧 + 检索侧），返回 baseTable → 表名。 */
	public Map<String, String> ensureKnowledgeTables(Integer modelId) {
		Map<String, String> r = new LinkedHashMap<>();
		Integer defId = defaultEmbeddingModelId();
		boolean isDefault = (modelId == null || (defId != null && defId.equals(modelId)));
		for (String base : new String[] { TABLE_SIMPLE, TABLE_RAG }) {
			if (!isDefault) {
				ensureModelTable(base, modelId);
			}
			r.put(base, tableName(base, modelId));
		}
		if (isDefault) {
			log.info("绑定的是默认模型（modelId={}），复用既有向量表，不新建：{}", modelId, r.values());
		}
		return r;
	}

	/** 向量空间键：provider-model-dims（R-06 归属与失效体检用）。维度过探测/回落口径，与建表一致。 */
	public String spaceKeyFor(Integer modelId) {
		if (modelId == null) {
			return null;
		}
		ModelConfig cfg = modelConfigDataService.getById(modelId);
		if (cfg == null) {
			return null;
		}
		return cfg.getProvider() + "-" + cfg.getModelName() + "-" + resolveDimensions(cfg);
	}

	/** 该模型的向量表是否存在（体检用；未建表 = 尚无向量空间）。 */
	public boolean tableExists(String baseTable, Integer modelId) {
		String t = tableName(baseTable, modelId);
		Integer n = jdbcTemplate.queryForObject("select count(*)::int from pg_class where relkind='r' and relname = ?",
				Integer.class, t);
		return n != null && n > 0;
	}

	/** 该模型专属表中**指定知识库**的向量条数（T-09 影响面清单用；表不存在返回 0）。 */
	public long vectorCountForKb(String baseTable, Integer modelId, Long kbId) {
		String t = tableName(baseTable, modelId);
		try {
			Integer n = jdbcTemplate.queryForObject(
					"SELECT count(*)::int FROM " + quoteIdent(t)
							+ " WHERE (metadata->>'knowledgeBaseId') = ? OR (metadata->>'knowledge_base_id') = ?",
					Integer.class, String.valueOf(kbId), String.valueOf(kbId));
			return n == null ? 0L : n.longValue();
		}
		catch (Exception e) {
			return 0L;
		}
	}

	/** 该模型的向量条数（体检用；表不存在返回 -1 表示"未建表"）。 */
	public long vectorCount(String baseTable, Integer modelId) {
		if (!tableExists(baseTable, modelId)) {
			return -1L;
		}
		Long c = jdbcTemplate.queryForObject("select count(*) from " + quoteIdent(tableName(baseTable, modelId)),
				Long.class);
		return c == null ? 0L : c;
	}


	/** 表名（不建表，仅计算/查询用）。 */
	public String tableName(String baseTable, Integer modelId) {
		Integer defId = defaultEmbeddingModelId();
		if (modelId == null || (defId != null && defId.equals(modelId))) {
			return baseTable;
		}
		ModelConfig cfg = modelConfigDataService.getById(modelId);
		return cfg == null ? baseTable : baseTable + "__" + shortHash(modelKey(cfg));
	}

	private VectorStore defaultStore(String baseTable) {
		if (TABLE_RAG.equals(baseTable)) {
			return defaultRagStore;
		}
		if (TABLE_SIMPLE.equals(baseTable)) {
			return defaultSimpleStore;
		}
		throw new IllegalArgumentException("未知向量表基名（无默认存储可复用）：" + baseTable);
	}

	/** 模型键：provider + modelName（版本/摘要为后续扩展位） */
	private static String modelKey(ModelConfig cfg) {
		return String.valueOf(cfg.getProvider()) + "|" + String.valueOf(cfg.getModelName());
	}

	private static String shortHash(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 6; i++) {
				sb.append(String.format("%02x", d[i]));
			}
			return sb.toString();
		}
		catch (Exception e) {
			throw new IllegalStateException("模型键哈希失败: " + e.getMessage(), e);
		}
	}

	/** 该模型向量的维度：ollama ⇒ 实际探测；其它 provider ⇒ 回落平台维度并告警（维度来源缺口见 BUG-198） */
	private int resolveDimensions(ModelConfig cfg) {
		try {
			if (ModelProvider.OLLAMA.getCode().equalsIgnoreCase(cfg.getProvider())) {
				int d = ollamaApiClient.probeEmbeddingDimension(cfg.getBaseUrl(), cfg.getModelName());
				log.info("模型维度探测成功: modelId={}, model={}, dims={}", cfg.getId(), cfg.getModelName(), d);
				return d;
			}
		}
		catch (Exception e) {
			log.warn("模型维度探测失败，回落平台维度: modelId={}, err={}", cfg.getId(), e.toString());
		}
		int d = embeddingDimensionResolver.resolve();
		log.warn("模型维度沿用平台维度（provider={} 无探测手段）: modelId={}, dims={}", cfg.getProvider(), cfg.getId(), d);
		return d;
	}

	/**
	 * 查询嵌入按模型缓存（R-03/T-07）：同一模型同一文本只调一次模型；计数供验证"调用次数正确"。
	 */
	private EmbeddingModel cachingEmbeddingModel(String modelKey, EmbeddingModel delegate) {
		final Map<String, float[]> cache = java.util.Collections
				.synchronizedMap(new java.util.LinkedHashMap<>(32, 0.75f, true) {
					@Override
					protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
						return size() > 256;
					}
				});
		return new EmbeddingModel() {
			@Override
			public EmbeddingResponse call(EmbeddingRequest request) {
				java.util.List<String> ins = request.getInstructions();
				if (ins != null && ins.size() == 1) {
					String text = ins.get(0);
					float[] hit = cache.get(text);
					if (hit != null) {
						embedCacheHits.computeIfAbsent(modelKey, k -> new java.util.concurrent.atomic.AtomicInteger())
								.incrementAndGet();
						log.debug("查询嵌入命中缓存: modelKey={}", modelKey);
						return new EmbeddingResponse(java.util.List.of(new Embedding(hit, 0)));
					}
					embedCalls.computeIfAbsent(modelKey, k -> new java.util.concurrent.atomic.AtomicInteger())
							.incrementAndGet();
					EmbeddingResponse resp = delegate.call(request);
					if (resp != null && resp.getResults() != null && !resp.getResults().isEmpty()) {
						cache.put(text, resp.getResults().get(0).getOutput());
					}
					return resp;
				}
				embedCalls.computeIfAbsent(modelKey, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();
				return delegate.call(request);
			}

			@Override
			public float[] embed(Document document) {
				return delegate.embed(document);
			}

			@Override
			public int dimensions() {
				return delegate.dimensions();
			}
		};
	}

	/** 可达性缓存：modelId → [时间戳, 是否可达]（T-08；避免每次检索都做网络预检） */
	private final Map<Integer, long[]> reachabilityCache = new ConcurrentHashMap<>();

	/**
	 * T-08 模型可达性预检：短超时（1.5s）+ 30s 缓存。
	 *
	 * <p>
	 * 背景（BUG-199 实测）：嵌入端点不可达时 Spring AI 会**重试 + 长超时**，直接把检索请求挂住，
	 * 降级逻辑根本等不到 ⇒ 必须先做短超时预检再决定"走模型"还是"降级为关键词"。
	 */
	public boolean isReachable(Integer modelId) {
		if (modelId == null) {
			return true;
		}
		long now = System.currentTimeMillis();
		long[] c = reachabilityCache.get(modelId);
		if (c != null && now - c[0] < 30_000L) {
			return c[1] == 1L;
		}
		boolean ok = false;
		try {
			ModelConfig cfg = modelConfigDataService.getById(modelId);
			if (cfg != null && Boolean.TRUE.equals(cfg.getIsActive())
					&& (cfg.getIsDeleted() == null || cfg.getIsDeleted() == 0)) {
				String path = (cfg.getEmbeddingsPath() == null || cfg.getEmbeddingsPath().isBlank())
						? "/v1/embeddings"
						: cfg.getEmbeddingsPath();
				String base = cfg.getBaseUrl() == null ? "" : cfg.getBaseUrl().replaceAll("/+$", "");
				java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI
						.create(base + path).toURL().openConnection();
				conn.setConnectTimeout(1500);
				conn.setReadTimeout(1500);
				conn.setRequestMethod("POST");
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", "application/json");
				String body = "{\"input\":\"ping\",\"model\":\"" + cfg.getModelName() + "\"}";
				try (java.io.OutputStream os = conn.getOutputStream()) {
					os.write(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				}
				int code = conn.getResponseCode();
				// 任何 HTTP 状态码都说明主机有响应 ⇒ 可达（4xx 只是参数/路径问题，不代表不可达）
				ok = code > 0;
				conn.disconnect();
			}
		}
		catch (Exception e) {
			ok = false;
			log.warn("模型可达性预检失败（将走降级）: modelId={}, err={}", modelId, e.toString());
		}
		reachabilityCache.put(modelId, new long[] { now, ok ? 1L : 0L });
		return ok;
	}

	/** 嵌入调用统计（模型键 → 实际调用次数 / 缓存命中次数） */
	public Map<String, Object> embeddingStats() {
		Map<String, Object> m = new LinkedHashMap<>();
		Map<String, Integer> calls = new LinkedHashMap<>();
		embedCalls.forEach((k, v) -> calls.put(k, v.get()));
		Map<String, Integer> hits = new LinkedHashMap<>();
		embedCacheHits.forEach((k, v) -> hits.put(k, v.get()));
		m.put("embeddingCalls", calls);
		m.put("embeddingCacheHits", hits);
		return m;
	}

	/** 确定性建表（幂等）：镜像既有向量表结构（id uuid 主键 / content / metadata / embedding vector(N)）+ hnsw 索引。 */
	private void ensureTableDdl(String table, int dims, String spaceKey) {
		String t = quoteIdent(table);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + t
				+ " (id uuid DEFAULT uuid_generate_v4() PRIMARY KEY, content text, metadata json, embedding vector("
				+ dims + "))");
		jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + quoteIdent(table + "_index") + " ON " + t
				+ " USING hnsw (embedding vector_cosine_ops)");
		log.info("确定性建表完成（幂等）: table={}, dims={}", table, dims);
		try {
			jdbcTemplate.execute("COMMENT ON TABLE " + t + " IS 'phoenix vector space: " + spaceKey
					+ "'");
		}
		catch (Exception e) {
			log.warn("表注释写入失败（不影响功能）: table={}, err={}", table, e.toString());
		}
	}

	private static String quoteIdent(String s) {
		return "\"" + s.replace("\"", "\"\"") + "\"";
	}

	/** 实体 → DTO（DynamicModelFactory 需要 DTO 构造模型客户端；仅映射构造所需字段） */
	private static ModelConfigDTO toDto(ModelConfig cfg) {
		ModelConfigDTO dto = new ModelConfigDTO();
		dto.setId(cfg.getId());
		dto.setProvider(cfg.getProvider());
		dto.setApiKey(cfg.getApiKey());
		dto.setBaseUrl(cfg.getBaseUrl());
		dto.setModelName(cfg.getModelName());
		dto.setModelType(cfg.getModelType() == null ? null : cfg.getModelType().getCode());
		dto.setTemperature(cfg.getTemperature());
		dto.setMaxTokens(cfg.getMaxTokens());
		dto.setCompletionsPath(cfg.getCompletionsPath());
		dto.setEmbeddingsPath(cfg.getEmbeddingsPath());
		return dto;
	}

}
