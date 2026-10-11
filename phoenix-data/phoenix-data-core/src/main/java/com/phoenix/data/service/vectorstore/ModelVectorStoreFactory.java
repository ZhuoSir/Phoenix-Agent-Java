package com.phoenix.data.service.vectorstore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.embedding.EmbeddingModel;
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
			ensureTableDdl(table, dims);
			EmbeddingModel embeddingModel = dynamicModelFactory.createEmbeddingModel(toDto(cfg));
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

	/** 确定性建表（幂等）：镜像既有向量表结构（id uuid 主键 / content / metadata / embedding vector(N)）+ hnsw 索引。 */
	private void ensureTableDdl(String table, int dims) {
		String t = quoteIdent(table);
		jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + t
				+ " (id uuid DEFAULT uuid_generate_v4() PRIMARY KEY, content text, metadata json, embedding vector("
				+ dims + "))");
		jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS " + quoteIdent(table + "_index") + " ON " + t
				+ " USING hnsw (embedding vector_cosine_ops)");
		log.info("确定性建表完成（幂等）: table={}, dims={}", table, dims);
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
