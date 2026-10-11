package com.phoenix.data.service.vectorstore;

import cn.hutool.core.collection.ListUtil;
import com.phoenix.data.constant.Constant;
import com.phoenix.data.constant.DocumentMetadataConstant;
import com.phoenix.data.dto.search.AgentSearchRequest;
import com.phoenix.data.dto.search.HybridSearchRequest;
import com.phoenix.data.entity.KnowledgeBase;
import com.phoenix.data.mapper.AgentKbaseBindMapper;
import com.phoenix.data.mapper.KnowledgeBaseMapper;
import com.phoenix.data.properties.DataAgentProperties;
import com.phoenix.data.service.hybrid.retrieval.HybridRetrievalStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.MapUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.*;

import static com.phoenix.data.service.vectorstore.DynamicFilterService.buildFilterExpressionString;

/**
 * 智能体向量存储服务实现类，提供基于向量检索和混合检索的文档管理能力。
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class AgentVectorStoreServiceImpl implements AgentVectorStoreService {

	/** R-09：写入/检索前校验向量表维度和当前 embedding 模型一致（不一致则显式拒绝） */
	@org.springframework.beans.factory.annotation.Autowired
	private com.phoenix.data.service.aimodelconfig.EmbeddingDimensionResolver embeddingDimensionResolver;

    private static final String DEFAULT = "default";
    @Autowired
    @Qualifier("simpleVectorStore")
    private VectorStore vectorStore;

    /** R-03/T-06：单库按绑定模型路由向量存储（多库扇出见 T-07） */
    @Autowired
    private com.phoenix.data.service.vectorstore.ModelVectorStoreFactory modelVectorStoreFactory;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private AgentKbaseBindMapper agentKbaseBindMapper;

    /** T-08：模型不可达时的关键词兜底数据源（不依赖嵌入模型） */
    @Autowired
    private com.phoenix.data.mapper.AgentKnowledgeMapper agentKnowledgeMapper;

    /** R-02/T-11：按文档归属库解析其绑定模型（写入路由 + 按模型维度校验） */
    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    @Autowired
    private Optional<HybridRetrievalStrategy> hybridRetrievalStrategy;
    @Autowired
    private DataAgentProperties dataAgentProperties;
    @Autowired
    private DynamicFilterService dynamicFilterService;

    /**
     * 执行文档搜索，支持向量检索和混合检索两种模式
     *
     * @param searchRequest 搜索请求参数
     * @return 文档列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Document> search(AgentSearchRequest searchRequest) {
        Assert.hasText(searchRequest.getAgentId(), "AgentId cannot be empty");
        Assert.hasText(searchRequest.getDocVectorType(), "DocVectorType cannot be empty");

        Filter.Expression filter = dynamicFilterService.buildDynamicFilter(searchRequest.getAgentId(),
                searchRequest.getDocVectorType());
        // 根据agentId vectorType找不到要 召回 的业务知识或者智能体知识
        if (filter == null) {
            log.warn(
                    "Dynamic filter returned null (no valid ids), returning empty result directly.AgentId: {}, VectorType: {}",
                    searchRequest.getAgentId(), searchRequest.getDocVectorType());
            return Collections.emptyList();
        }

        HybridSearchRequest hybridRequest = HybridSearchRequest.builder()
                .query(searchRequest.getQuery())
                .topK(searchRequest.getTopK())
                .similarityThreshold(searchRequest.getSimilarityThreshold())
                .filterExpression(filter)
                .build();

        // R-03/T-06：单库 ⇒ 按该库绑定模型选表选模型；R-03/T-07：多库 ⇒ 按模型分组扇出 + RRF 融合
        // 注意：路由必须在混合检索分支**之前**解析，并把结果注入请求，否则混合路径会用策略自带存储（实测绕过）
        Route route = resolveRoute(searchRequest.getAgentId(), searchRequest.getDocVectorType());
        hybridRequest.setRoutingVectorStore(route.store);
        if (route.knowledgeType && route.byModel.size() > 1) {
            return fanOutByModel(route, hybridRequest, searchRequest);
        }
        // T-08/BUG-199：非默认模型先用短超时预检，不可达则直接关键词降级（避免嵌入重试把请求挂住）
        if (route.knowledgeType && route.modelId != null
                && !modelVectorStoreFactory.isReachable(route.modelId)) {
            String rsn = "模型可达性预检失败（短超时探测不可达，BUG-199 场景）";
            log.warn("检索降级触发（预检）: agentId={}, kbIds={}, modelId={}, table={}, 原因={}", searchRequest.getAgentId(),
                    route.kbIds, route.modelId, route.table, rsn);
            return keywordFallback(route.kbIds, hybridRequest.getQuery(),
                    searchRequest.getTopK() == null ? 10 : searchRequest.getTopK(), rsn);
        }
        if (dataAgentProperties.getVectorStore().isEnableHybridSearch() && hybridRetrievalStrategy.isPresent()) {
            log.info("混合检索（按库绑定模型路由）: agentId={}, docVectorType={}, kbId={}, modelId={}, table={}",
                    searchRequest.getAgentId(), searchRequest.getDocVectorType(), route.kbId, route.modelId,
                    route.table);
            try {
                return hybridRetrievalStrategy.get().retrieve(hybridRequest);
            }
            catch (Exception ex) {
                String reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                log.warn("检索降级触发（混合检索路径）: agentId={}, kbIds={}, modelId={}, table={}, 原因={}",
                        searchRequest.getAgentId(), route.kbIds, route.modelId, route.table, reason);
                return keywordFallback(route.kbIds, hybridRequest.getQuery(),
                        searchRequest.getTopK() == null ? 10 : searchRequest.getTopK(), reason);
            }
        }
        log.debug("Hybrid search is not enabled. use vector-search only");
        List<Document> results;
        try {
            results = route.store.similaritySearch(hybridRequest.toVectorSearchRequest());
        }
        catch (Exception ex) {
            // T-08：单库路径同样降级（不静默漏查）
            String reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            log.warn("检索降级触发（单库路径）: agentId={}, kbIds={}, modelId={}, table={}, 原因={}",
                    searchRequest.getAgentId(), route.kbIds, route.modelId, route.table, reason);
            results = keywordFallback(route.kbIds, hybridRequest.getQuery(), searchRequest.getTopK() == null ? 10
                    : searchRequest.getTopK(), reason);
        }
        log.info("检索完成（按库绑定模型路由）: agentId={}, docVectorType={}, kbId={}, modelId={}, table={}, hits={}",
                searchRequest.getAgentId(), searchRequest.getDocVectorType(), route.kbId, route.modelId, route.table,
                results.size());
        return results;

    }

    /**
     * 根据智能体 ID 和向量类型删除文档
     *
     * @param agentId    智能体 ID
     * @param vectorType 向量类型
     * @return 是否删除成功
     * @throws Exception 删除失败时抛出异常
     */
    @Override
    public Boolean deleteDocumentsByVectorType(String agentId, String vectorType) throws Exception {
        Assert.notNull(agentId, "AgentId cannot be null.");
        Assert.notNull(vectorType, "VectorType cannot be null.");

        Map<String, Object> metadata = new HashMap<>(Map.ofEntries(Map.entry(Constant.AGENT_ID, agentId),
                Map.entry(DocumentMetadataConstant.VECTOR_TYPE, vectorType)));

        return this.deleteDocumentsByMetedata(agentId, metadata);
    }

    /**
     * 为指定智能体添加文档，自动验证元数据一致性
     *
     * @param agentId   智能体 ID
     * @param documents 文档列表
     */
    @Override
    public void addDocuments(String agentId, List<Document> documents) {
        // R-09/R-05：维度校验**按库（按模型）**判定，替代原"全表一致"（多模型下不同模型表维度天然可不同）
        Assert.notNull(agentId, "AgentId cannot be null.");
        Assert.notEmpty(documents, "Documents cannot be empty.");

        // 验证文档中 metadata 的一致性
        for (Document document : documents) {
            Assert.notNull(document.getMetadata(), "Document metadata cannot be null.");

            String vectorType = (String) document.getMetadata().get(DocumentMetadataConstant.VECTOR_TYPE);

            // 根据 vectorType 验证不同的字段
            if (DocumentMetadataConstant.TABLE.equals(vectorType)
                    || DocumentMetadataConstant.COLUMN.equals(vectorType)) {
                // 表和列必须包含 datasourceId
                Assert.isTrue(document.getMetadata().containsKey(Constant.DATASOURCE_ID),
                        "Document metadata must contain datasourceId for TABLE/COLUMN type.");
            } else {
                // 知识库和业务术语必须包含 agentId
                Assert.isTrue(document.getMetadata().containsKey(Constant.AGENT_ID),
                        "Document metadata must contain agentId.");
                Assert.isTrue(document.getMetadata().get(Constant.AGENT_ID).equals(agentId),
                        "Document metadata agentId does not match.");
            }
        }
        // R-02/T-11：按文档归属的知识库解析其绑定模型 ⇒ 用该模型专属表与其模型客户端写入（补齐写入路由）
        Integer modelId = resolveWriteModelId(documents);
        String table = modelVectorStoreFactory.tableName(ModelVectorStoreFactory.TABLE_SIMPLE, modelId);
        modelVectorStoreFactory.ensureTableExists(ModelVectorStoreFactory.TABLE_SIMPLE, modelId);
        // R-05/T-11：按"该库绑定模型 ↔ 其向量表"判定（维度与建表同口径，含通用探测）
        embeddingDimensionResolver.assertTableDimsCompatible(table, modelVectorStoreFactory.dimsOf(modelId),
                "该库绑定的模型(modelId=" + modelId + ")");
        org.springframework.ai.vectorstore.VectorStore target = modelVectorStoreFactory
                .forModel(ModelVectorStoreFactory.TABLE_SIMPLE, modelId);
        List<List<Document>> spitDocments = ListUtil.partition(documents, 10);
        for (List<Document> spit : spitDocments) {
            target.add(spit);
        }
        log.info("向量写入（按库绑定模型路由）: agentId={}, modelId={}, table={}, docs={}", agentId, modelId, table,
                documents.size());
    }

    /**
     * 根据元数据条件删除文档
     *
     * @param metadata 元数据条件
     * @return 是否删除成功
     */
    /**
     * 解析该批文档应写入哪个模型的表：metadata.knowledgeBaseId → 该库绑定模型；
     * 无法判定（如库表/字段向量）⇒ 回落默认模型（与改动前行为一致）。
     */
    private Integer resolveWriteModelId(List<Document> documents) {
        try {
            for (Document d : documents) {
                java.util.Map<String, Object> md = d.getMetadata();
                if (md == null) {
                    continue;
                }
                Object kb = md.get("knowledgeBaseId");
                if (kb == null) {
                    kb = md.get("knowledge_base_id");
                }
                if (kb == null && md.get("agentKnowledgeId") != null) {
                    kb = jdbcTemplate.queryForObject(
                            "SELECT knowledge_base_id FROM tbl_data_agent_knowledge WHERE id = ?", Long.class,
                            Long.valueOf(String.valueOf(md.get("agentKnowledgeId"))));
                }
                if (kb != null) {
                    Integer mid = jdbcTemplate.queryForObject(
                            "SELECT embedding_model_id FROM tbl_data_knowledge_base WHERE id = ?", Integer.class,
                            Long.valueOf(String.valueOf(kb)));
                    if (mid != null) {
                        return mid;
                    }
                }
            }
        }
        catch (Exception e) {
            log.warn("写入模型解析失败，回落默认模型: {}", e.toString());
        }
        return modelVectorStoreFactory.defaultEmbeddingModelId();
    }

    /** T-08 关键词兜底（模型不可达时）：不依赖嵌入模型，直接按关键词查该库条目并标注降级提示 */
    private List<Document> keywordFallback(java.util.List<Long> kbIds, String query, int topK, String reason) {
        if (kbIds == null || kbIds.isEmpty() || query == null || query.isBlank()) {
            return java.util.Collections.emptyList();
        }
        String csv = kbIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        java.util.List<com.phoenix.data.entity.AgentKnowledge> items;
        try {
            items = agentKnowledgeMapper.selectKeywordFallback(csv, query, Math.max(1, topK));
        }
        catch (Exception e) {
            log.error("关键词兜底失败: kbIds={}, err={}", kbIds, e.toString());
            return java.util.Collections.emptyList();
        }
        java.util.List<Document> docs = new java.util.ArrayList<>(items.size());
        for (com.phoenix.data.entity.AgentKnowledge it : items) {
            java.util.Map<String, Object> m = new java.util.HashMap<>();
            m.put(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.AGENT_KNOWLEDGE);
            m.put("agentKnowledgeId", it.getId());
            m.put("title", it.getTitle());
            // R-03/T-08：降级必须**显式可见**（不得静默漏查）
            m.put("retrievalDegraded", true);
            m.put("retrievalDegradedReason", reason);
            m.put("retrievalDegradedMode", "keyword");
            m.put("retrievalSourceKbIds", kbIds);
            m.put("retrievalSourceTable", "keyword:tbl_data_agent_knowledge");
            String text = it.getContent() == null ? String.valueOf(it.getTitle()) : it.getContent();
            // Spring AI 的 Document id 必须为 UUID（条目 id 保留在 metadata.agentKnowledgeId）
            docs.add(new Document(java.util.UUID.randomUUID().toString(), text, m));
        }
        log.warn("检索降级（模型不可达 ⇒ 关键词兜底）: kbIds={}, reason={}, 影响条数={}", kbIds, reason, docs.size());
        return docs;
    }

    /** 多模型分组扇出 + RRF 融合（R-03/T-07）：每组用其模型的表/模型独立检索，再按名次融合 */
    private List<Document> fanOutByModel(Route route, HybridSearchRequest hybridRequest,
            com.phoenix.data.dto.search.AgentSearchRequest searchRequest) {
        java.util.List<java.util.List<Document>> groups = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, java.util.List<Long>> e : route.byModel.entrySet()) {
            Integer modelId = e.getKey();
            String table = modelVectorStoreFactory.tableName(ModelVectorStoreFactory.TABLE_SIMPLE, modelId);
            java.util.List<Document> docs;
            try {
                // T-08/BUG-199：先做短超时可达性预检，不可达立即降级（不要等嵌入调用重试挂住）
                if (!modelVectorStoreFactory.isReachable(modelId)) {
                    throw new IllegalStateException("模型可达性预检失败（短超时探测不可达）");
                }
                org.springframework.ai.vectorstore.VectorStore store = modelVectorStoreFactory
                        .forModel(ModelVectorStoreFactory.TABLE_SIMPLE, modelId);
                docs = store.similaritySearch(hybridRequest.toVectorSearchRequest());
            }
            catch (Exception ex) {
                // R-03/T-08：某组不可达不得拖垮整体 ⇒ 该组降级为关键词兜底，并在结果中显式提示
                String reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
                log.warn("检索降级触发: agentId={}, kbIds={}, modelId={}, table={}, 原因={}", searchRequest.getAgentId(),
                        e.getValue(), modelId, table, reason);
                docs = keywordFallback(e.getValue(), hybridRequest.getQuery(), route.kbCount * 5, reason);
                for (Document d : docs) {
                    d.getMetadata().put("retrievalSourceModelId", modelId);
                }
                groups.add(docs);
                continue;
            }
            java.util.List<Document> annotated = new java.util.ArrayList<>(docs.size());
            for (Document d : docs) {
                annotated.add(annotateSource(d, modelId, table, e.getValue()));
            }
            docs = annotated;
            log.info("分组检索: agentId={}, modelId={}, table={}, kbIds={}, hits={}", searchRequest.getAgentId(), modelId,
                    table, e.getValue(), docs.size());
            groups.add(docs);
        }
        int topK = searchRequest.getTopK() == null ? 10 : searchRequest.getTopK();
        java.util.List<Document> fused = rrfFusionStrategy.fuseResults(topK, groups.toArray(new java.util.List[0]));
        log.info("RRF 融合完成: agentId={}, groups={}, fused={}, order={}", searchRequest.getAgentId(), groups.size(),
                fused.size(), fused.stream().map(d -> String.valueOf(d.getId())).toList());
        return fused;
    }

    /** 结果来源标注（R-03：跨模型命中必须可标注来源；元数据不可变时构造新文档） */
    private static Document annotateSource(Document d, Integer modelId, String table, java.util.List<Long> kbIds) {
        try {
            d.getMetadata().put("retrievalSourceModelId", modelId);
            d.getMetadata().put("retrievalSourceTable", table);
            d.getMetadata().put("retrievalSourceKbIds", kbIds);
            return d;
        }
        catch (UnsupportedOperationException e) {
            java.util.Map<String, Object> m = new java.util.HashMap<>(d.getMetadata());
            m.put("retrievalSourceModelId", modelId);
            m.put("retrievalSourceTable", table);
            m.put("retrievalSourceKbIds", kbIds);
            return new Document(d.getId(), d.getText(), m);
        }
    }

    /** RRF 融合器（无 Spring bean，直接复用既有实现，公式 1/(60+rank)） */
    private final com.phoenix.data.service.hybrid.fusion.impl.RrfFusionStrategy rrfFusionStrategy = new com.phoenix.data.service.hybrid.fusion.impl.RrfFusionStrategy();

    /** 检索路由结果（R-03/T-06）；degraded 供 T-08 不可达降级使用 */
    public static class Route {
        public VectorStore store;
        public Long kbId;
        public Integer modelId;
        public String table;
        public int kbCount;
        /** 绑定的库 id 与其模型分组：modelId → kbIds（R-03/T-07 扇出用） */
        public java.util.List<Long> kbIds = new java.util.ArrayList<>();
        public java.util.Map<Integer, java.util.List<Long>> byModel = new java.util.LinkedHashMap<>();
        public boolean knowledgeType;
    }

    /**
     * 解析本次检索应使用的向量存储：仅对知识类向量（agentKnowledge/businessTerm）按绑定库路由；
     * 单库 ⇒ 该库绑定模型对应的表与模型；多库/无库/非知识类 ⇒ 既有默认存储（不改变原有行为）。
     */
    public Route resolveRoute(String agentId, String docVectorType) {
        Route r = new Route();
        r.store = vectorStore;
        r.table = ModelVectorStoreFactory.TABLE_SIMPLE;
        boolean knowledgeType = DocumentMetadataConstant.AGENT_KNOWLEDGE.equalsIgnoreCase(docVectorType)
                || DocumentMetadataConstant.BUSINESS_TERM.equalsIgnoreCase(docVectorType);
        if (!knowledgeType || agentId == null) {
            return r;
        }
        try {
            java.util.List<Long> kbIds = agentKbaseBindMapper.selectKbIdsByAgent(Long.valueOf(agentId));
            r.kbCount = kbIds == null ? 0 : kbIds.size();
            r.knowledgeType = true;
            r.kbIds = kbIds == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(kbIds);
            Integer defId = modelVectorStoreFactory.defaultEmbeddingModelId();
            for (Long kid : r.kbIds) {
                KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kid);
                Integer mid = (kb == null || kb.getEmbeddingModelId() == null) ? defId : kb.getEmbeddingModelId();
                r.byModel.computeIfAbsent(mid, k -> new java.util.ArrayList<>()).add(kid);
            }
            if (r.byModel.size() > 1) {
                log.info("检索路由：agentId={} 绑定 {} 个库、分属 {} 个模型 ⇒ 分组扇出 + RRF 融合（R-03/T-07）", agentId,
                        r.kbCount, r.byModel.size());
            }
            if (r.kbCount == 1) {
                KnowledgeBase kb = knowledgeBaseMapper.selectOneById(kbIds.get(0));
                if (kb != null) {
                    r.kbId = kb.getId();
                    r.modelId = kb.getEmbeddingModelId();
                    r.table = modelVectorStoreFactory.tableName(ModelVectorStoreFactory.TABLE_SIMPLE, r.modelId);
                    r.store = modelVectorStoreFactory.forModel(ModelVectorStoreFactory.TABLE_SIMPLE, r.modelId);
                }
            }
            else if (r.kbCount > 1) {
                log.info("检索路由：agentId={} 绑定 {} 个知识库 ⇒ 本次用默认存储（分组扇出见 T-07）", agentId, r.kbCount);
            }
        }
        catch (Exception e) {
            log.warn("检索路由解析失败，回落默认存储: agentId={}, err={}", agentId, e.toString());
        }
        return r;
    }

    @Override
    public java.util.Map<String, Object> retrievalRoute(String agentId, String docVectorType) {
        Route r = resolveRoute(agentId, docVectorType);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("agentId", agentId);
        out.put("docVectorType", docVectorType);
        out.put("kbCount", r.kbCount);
        out.put("kbId", r.kbId);
        out.put("modelId", r.modelId);
        out.put("table", r.table);
        return out;
    }

    @Override
    public Boolean deleteDocumentsByMetadata(Map<String, Object> metadata) {
        Assert.notNull(metadata, "Metadata cannot be null.");
        String filterExpression = buildFilterExpressionString(metadata);

        if (vectorStore instanceof SimpleVectorStore) {
            batchDelDocumentsWithFilter(filterExpression);
        } else {
            vectorStore.delete(filterExpression);
        }

        return true;
    }

    /**
     * 根据智能体 ID 和元数据删除文档，自动补充 agentId 过滤条件
     *
     * @param agentId  智能体 ID
     * @param metadata 元数据条件
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteDocumentsByMetedata(String agentId, Map<String, Object> metadata) {
        Assert.hasText(agentId, "AgentId cannot be empty.");
        Assert.notNull(metadata, "Metadata cannot be null.");
        // 添加agentId元数据过滤条件, 用于删除指定agentId下的所有数据，因为metadata中用户调用可能忘记添加agentId
        metadata.put(Constant.AGENT_ID, agentId);
        String filterExpression = buildFilterExpressionString(metadata);
        vectorStore.delete(filterExpression);
        return true;
    }

    /**
     * 分批删除满足过滤条件的文档（SimpleVectorStore 专用）
     *
     * @param filterExpression 过滤表达式
     */
    private void batchDelDocumentsWithFilter(String filterExpression) {
        Set<String> seenDocumentIds = new HashSet<>();
        // 分批获取，因为Milvus等向量数据库的topK有限制
        List<Document> batch;
        int newDocumentsCount;
        int totalDeleted = 0;

        do {
            batch = vectorStore.similaritySearch(SearchRequest.builder()
                    .query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
                    .filterExpression(filterExpression)
                    .similarityThreshold(0.0)// 设置最低相似度阈值以获取元数据匹配的所有文档
                    .topK(dataAgentProperties.getVectorStore().getBatchDelTopkLimit())
                    .build());

            // 过滤掉已经处理过的文档，只删除未处理的文档
            List<String> idsToDelete = new ArrayList<>();
            newDocumentsCount = 0;

            for (Document doc : batch) {
                if (seenDocumentIds.add(doc.getId())) {
                    // 如果add返回true，表示这是一个新的文档ID
                    idsToDelete.add(doc.getId());
                    newDocumentsCount++;
                }
            }

            // 删除这批新文档
            if (!idsToDelete.isEmpty()) {
                vectorStore.delete(idsToDelete);
                totalDeleted += idsToDelete.size();
            }

        }
        while (newDocumentsCount > 0); // 只有当获取到新文档时才继续循环

        log.info("Deleted {} documents with filter expression: {}", totalDeleted, filterExpression);
    }

    /**
     * 为指定智能体检索文档（使用默认 topK 和相似度阈值）
     *
     * @param agentId    智能体 ID
     * @param query      查询条件
     * @param vectorType 向量类型
     * @return 文档列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType) {
        // 使用全局默认配置
        int defaultTopK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
        double defaultThreshold = dataAgentProperties.getVectorStore().getDefaultSimilarityThreshold();

        return getDocumentsForAgent(agentId, query, vectorType, defaultTopK, defaultThreshold);
    }

    /**
     * 为指定智能体检索文档（指定 topK 和相似度阈值）
     *
     * @param agentId    智能体 ID
     * @param query      查询条件
     * @param vectorType 向量类型
     * @param topK       返回数量上限
     * @param threshold  相似度阈值
     * @return 文档列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Document> getDocumentsForAgent(String agentId, String query, String vectorType, int topK,
                                               double threshold) {
        AgentSearchRequest searchRequest = AgentSearchRequest.builder()
                .agentId(agentId)
                .docVectorType(vectorType)
                .query(query)
                .topK(topK) // 使用传入的参数
                .similarityThreshold(threshold) // 使用传入的参数
                .build();
        return search(searchRequest);
    }

    /**
     * 通过元数据过滤表达式精确查找文档
     *
     * @param filterExpression 过滤表达式
     * @param topK             返回数量上限
     * @return 文档列表
     */
    @Override
    @Transactional(readOnly = true)
    public List<Document> getDocumentsOnlyByFilter(Filter.Expression filterExpression, Integer topK) {
        Assert.notNull(filterExpression, "filterExpression cannot be null.");
        if (topK == null)
            topK = dataAgentProperties.getVectorStore().getDefaultTopkLimit();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(DEFAULT)
                .topK(topK)
                .filterExpression(filterExpression)
                .similarityThreshold(0.0)
                .build();
        return vectorStore.similaritySearch(searchRequest);
    }

    /**
     * 检查指定智能体是否存在文档
     *
     * @param agentId 智能体 ID
     * @return 是否存在文档
     */
    @Override
    @Transactional(readOnly = true)
    public boolean hasDocuments(String agentId) {
        // 类似 MySQL 的 LIMIT 1,只检查是否存在文档
        List<Document> docs = vectorStore.similaritySearch(SearchRequest.builder()
                .query(DEFAULT)// 使用默认的查询字符串，因为有的嵌入模型不支持空字符串
                .filterExpression(buildFilterExpressionString(Map.of(Constant.AGENT_ID, agentId)))
                .topK(1) // 只获取1个文档
                .similarityThreshold(0.0)
                .build());
        return !docs.isEmpty();
    }

}
