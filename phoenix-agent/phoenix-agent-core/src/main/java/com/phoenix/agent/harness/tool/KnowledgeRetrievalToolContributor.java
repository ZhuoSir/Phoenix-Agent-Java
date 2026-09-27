package com.phoenix.agent.harness.tool;

import com.phoenix.agent.harness.factory.AgentToolContributor;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.data.entity.Agent;
import com.phoenix.data.service.vectorstore.AgentVectorStoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 知识库检索工具装配（T-07，R-04）：knowledgeEnabled=1 时按智能体实例化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRetrievalToolContributor implements AgentToolContributor {

    private final AgentVectorStoreService agentVectorStoreService;

    @Override
    public String toolName() {
        return "knowledge_retrieval";
    }

    @Override
    public boolean supports(Agent agent, AgentRuntimeConfig config) {
        return config.getKnowledgeEnabled() != null && config.getKnowledgeEnabled() == 1;
    }

    @Override
    public Object createTool(Agent agent, AgentRuntimeConfig config) {
        log.info("装配知识库检索工具: agentId={}, topK={}, threshold={}", agent.getId(), config.getKnowledgeTopK(),
            config.getKnowledgeSimilarityThreshold());
        return new KnowledgeRetrievalTool(agent.getId(), config.getKnowledgeTopK(),
            config.getKnowledgeSimilarityThreshold(), agentVectorStoreService);
    }
}
