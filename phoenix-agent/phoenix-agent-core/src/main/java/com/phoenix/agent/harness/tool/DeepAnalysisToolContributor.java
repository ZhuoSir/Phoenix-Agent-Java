package com.phoenix.agent.harness.tool;

import com.phoenix.agent.harness.factory.AgentToolContributor;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.data.entity.Agent;
import com.phoenix.data.service.datasource.AgentDatasourceService;
import com.phoenix.data.service.datasource.DatasourceService;
import com.phoenix.data.service.graph.GraphService;
import com.phoenix.data.service.schema.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 数据库深度分析工具装配（T-09，R-06）：dbDeepAnalysisEnabled=1 时按智能体实例化。
 *
 * <p>一个 Contributor 只对应一个工具；工具实例在 {@code createTool} 里绑定 agentId 与数据源，
 * 因此工具内部不需要 RuntimeContext（代价是并发限制只能按智能体做，见 {@link DeepAnalysisTool} 类注释）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepAnalysisToolContributor implements AgentToolContributor {

    private final DatasourceService datasourceService;

    private final AgentDatasourceService agentDatasourceService;

    private final SchemaService schemaService;

    private final GraphService graphService;

    /** 单次深度分析阻塞等待上限（秒）；默认取常量，可按环境调 */
    @Value("${phoenix.agent.tool.deep-analysis-timeout-seconds:180}")
    private int timeoutSeconds = 180;

    /** 报告最终截断字符数 */
    @Value("${phoenix.agent.tool.deep-analysis-max-chars:6000}")
    private int maxChars = 6000;

    /** 同一智能体的深度分析并发上限 */
    @Value("${phoenix.agent.tool.deep-analysis-max-concurrent:2}")
    private int maxConcurrent = 2;

    @Override
    public String toolName() {
        return "deep_analysis";
    }

    @Override
    public boolean supports(Agent agent, AgentRuntimeConfig config) {
        return config.getDbDeepAnalysisEnabled() != null && config.getDbDeepAnalysisEnabled() == 1;
    }

    @Override
    public Object createTool(Agent agent, AgentRuntimeConfig config) {
        log.info("装配数据库深度分析工具: agentId={}, datasourceId={}, timeout={}s, maxChars={}, maxConcurrent={}",
            agent.getId(), config.getDatasourceId(), timeoutSeconds, maxChars, maxConcurrent);
        return new DeepAnalysisTool(agent.getId(), config.getDatasourceId(), datasourceService,
            agentDatasourceService, schemaService, graphService, timeoutSeconds, maxChars, maxConcurrent);
    }

}
