package com.phoenix.agent.harness.tool;

import cn.hutool.core.collection.CollUtil;
import com.phoenix.agent.constant.AgentRuntimeConstant;
import com.phoenix.data.dto.search.AgentSearchRequest;
import com.phoenix.data.entity.AgentKnowledge;
import com.phoenix.data.mapper.AgentKnowledgeMapper;
import com.phoenix.data.service.vectorstore.AgentVectorStoreService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.phoenix.data.constant.DocumentMetadataConstant.AGENT_KNOWLEDGE;

/**
 * 知识库检索工具（T-07，R-04）：泛化自 {@code RulesRagTool}，**按智能体实例化**。
 *
 * <p>与写死 {@code agentId=18} 的存量参考实现的关键差异：
 * <ul>
 *   <li>检索范围 = 本实例绑定的 agentId（由 HarnessAgentFactory 在构建时注入），不读全局也不猜身份；</li>
 *   <li>topK 与相似度阈值来自运行配置（`tbl_data_agent_runtime_config`），默认值与原实现一致；</li>
 *   <li>召回后按 documentId 去重、按 score 倒序取前 topK 条（原实现固定取 5 条，与 topK 语义冲突）。</li>
 * </ul>
 *
 * <p>无状态、可并发（只读查询）。
 */
@Slf4j
public class KnowledgeRetrievalTool {

    private final Long agentId;

    private final int topK;

    private final double similarityThreshold;

    private final AgentVectorStoreService agentVectorStoreService;

    private final AgentKnowledgeMapper agentKnowledgeMapper;

    public KnowledgeRetrievalTool(Long agentId, Integer topK, Double similarityThreshold,
            AgentVectorStoreService agentVectorStoreService, AgentKnowledgeMapper agentKnowledgeMapper) {
        this.agentId = agentId;
        this.topK = topK == null || topK <= 0 ? AgentRuntimeConstant.DEFAULT_KNOWLEDGE_TOP_K : topK;
        this.similarityThreshold = similarityThreshold == null
            ? AgentRuntimeConstant.DEFAULT_KNOWLEDGE_SIMILARITY_THRESHOLD : similarityThreshold;
        this.agentVectorStoreService = agentVectorStoreService;
        this.agentKnowledgeMapper = agentKnowledgeMapper;
    }

    /**
     * BUG-49：QA/FAQ 向量文本只存问题（答案设计上留在关系库），命中后必须回表拼「问题+答案」，
     * 否则答案永远到不了模型。DOCUMENT 类型正文本就在向量文本，原样返回。
     */
    private String renderDocument(Document document) {
        Object type = document.getMetadata().get(com.phoenix.data.constant.DocumentMetadataConstant.CONCRETE_AGENT_KNOWLEDGE_TYPE);
        Object kidObj = document.getMetadata().get(com.phoenix.data.constant.DocumentMetadataConstant.DB_AGENT_KNOWLEDGE_ID);
        if (("QA".equals(type) || "FAQ".equals(type)) && kidObj instanceof Number kid) {
            AgentKnowledge knowledge = agentKnowledgeMapper.selectOneById(kid.intValue());
            if (knowledge != null && knowledge.getContent() != null && !knowledge.getContent().isBlank()) {
                return "问题：" + knowledge.getQuestion() + "\n答案：" + knowledge.getContent();
            }
        }
        return document.getText();
    }

    @Tool(name = "getRagInfo", description = "根据用户的自然语言生成的关键词获取知识库向量列表，如果查询到数据就不需要再次调用了", readOnly = true, concurrencySafe = true)
    public String getRagInfo(@ToolParam(name = "query", description = "查询向量的关键词") List<String> query) {
        if (CollUtil.isEmpty(query)) {
            return "没有给出查询关键词，请重新理解用户的话";
        }
        log.info("知识库检索: agentId={}, topK={}, threshold={}, 关键词数={}", agentId, topK, similarityThreshold,
            query.size());
        List<Document> documents = new ArrayList<>();
        for (String keyword : query) {
            if (keyword == null || keyword.isBlank()) {
                continue;
            }
            AgentSearchRequest searchRequest = AgentSearchRequest.builder()
                .query(keyword)
                .docVectorType(AGENT_KNOWLEDGE)
                .agentId(String.valueOf(agentId))
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();
            List<Document> found;
            try {
                found = agentVectorStoreService.search(searchRequest);
            }
            catch (RuntimeException e) {
                // R-05 / L-34：把**可诊断**的失败原因回传给模型，避免它把"检索服务不可用"误当成"知识库没有资料"
                // 而改去翻文件系统（BUG-103 实测的退化路径）。
                String reason = rootCauseMessage(e);
                log.error("知识库检索失败: agentId={}, keyword={}, reason={}", agentId, keyword, reason, e);
                return "检索失败（注意：这不是「没有相关资料」）: " + reason
                        + "。请如实告知用户知识库检索服务当前不可用，并停止用文件系统工具查找知识库内容。";
            }
            if (found != null) {
                documents.addAll(found);
            }
        }
        List<Document> topDocuments = documents.stream()
            .collect(Collectors.collectingAndThen(
                    Collectors.toCollection(() -> new TreeSet<>(Comparator.comparing(Document::getId))),
                    ArrayList::new))
            .stream()
            .sorted(Comparator.comparingDouble(Document::getScore).reversed())
            .limit(topK)
            .collect(Collectors.toList());
        if (CollUtil.isEmpty(topDocuments)) {
            return "没有找到相关文档，请重新理解用户的话";
        }
        StringBuilder sb = new StringBuilder();
        for (Document document : topDocuments) {
            sb.append(renderDocument(document)).append("\n");
        }
        return """
                找到 %s 条相关文档 无需调用tool，直接输出
                相关文档如下： \n
                """.formatted(topDocuments.size()) + sb;
    }

    /** 取根因摘要（类名 + message），供工具返回给模型（R-05 / L-34）。 */
    private static String rootCauseMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return cur.getClass().getSimpleName() + (msg == null ? "" : (": " + msg));
    }
}
