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
        if (dataAgentProperties.getVectorStore().isEnableHybridSearch() && hybridRetrievalStrategy.isPresent()) {
            log.info("混合检索（按库绑定模型路由）: agentId={}, docVectorType={}, kbId={}, modelId={}, table={}",
                    searchRequest.getAgentId(), searchRequest.getDocVectorType(), route.kbId, route.modelId,
                    route.table);
            return hybridRetrievalStrategy.get().retrieve(hybridRequest);
        }
        log.debug("Hybrid search is not enabled. use vector-search only");
        List<Document> results = route.store.similaritySearch(hybridRequest.toVectorSearchRequest());
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
        // R-09：维度不一致必须显式拒绝（防脏数据/静默失败），先于任何写入
        embeddingDimensionResolver.assertAllTablesCompatible();
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
        List<List<Document>> spitDocments = ListUtil.partition(documents, 10);
        for (List<Document> spit : spitDocments) {
            vectorStore.add(spit);
        }
    }

    /**
     * 根据元数据条件删除文档
     *
     * @param metadata 元数据条件
     * @return 是否删除成功
     */
    /** 多模型分组扇出 + RRF 融合（R-03/T-07）：每组用其模型的表/模型独立检索，再按名次融合 */
    private List<Document> fanOutByModel(Route route, HybridSearchRequest hybridRequest,
            com.phoenix.data.dto.search.AgentSearchRequest searchRequest) {
        java.util.List<java.util.List<Document>> groups = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, java.util.List<Long>> e : route.byModel.entrySet()) {
            Integer modelId = e.getKey();
            String table = modelVectorStoreFactory.tableName(ModelVectorStoreFactory.TABLE_SIMPLE, modelId);
            java.util.List<Document> docs;
            try {
                org.springframework.ai.vectorstore.VectorStore store = modelVectorStoreFactory
                        .forModel(ModelVectorStoreFactory.TABLE_SIMPLE, modelId);
                docs = store.similaritySearch(hybridRequest.toVectorSearchRequest());
            }
            catch (Exception ex) {
                // R-03：某组不可达不得拖垮整体；显式告警（T-08 会补关键词兜底与结果提示）
                log.warn("分组检索失败（该组已跳过）: agentId={}, modelId={}, table={}, err={}", searchRequest.getAgentId(),
                        modelId, table, ex.toString());
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
