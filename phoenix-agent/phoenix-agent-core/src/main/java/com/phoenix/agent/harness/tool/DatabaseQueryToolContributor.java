package com.phoenix.agent.harness.tool;

import com.phoenix.agent.harness.factory.AgentToolContributor;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.data.connector.accessor.AccessorFactory;
import com.phoenix.data.entity.Agent;
import com.phoenix.data.service.datasource.DatasourceService;
import com.phoenix.data.service.nl2sql.Nl2SqlService;
import com.phoenix.data.service.schema.TableMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据库取数工具装配（T-08，R-05）：dbQueryEnabled=1 时按智能体实例化。
 *
 * <p>一个 Contributor 只对应一个工具；工具实例在 {@code createTool} 里绑定 agentId 与数据源，
 * 因此工具内部不需要 RuntimeContext。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseQueryToolContributor implements AgentToolContributor {

    private final DatasourceService datasourceService;

    private final AccessorFactory accessorFactory;

    private final TableMetadataService tableMetadataService;

    private final Nl2SqlService nl2SqlService;

    @Override
    public String toolName() {
        return "database_query";
    }

    @Override
    public boolean supports(Agent agent, AgentRuntimeConfig config) {
        return config.getDbQueryEnabled() != null && config.getDbQueryEnabled() == 1;
    }

    @Override
    public Object createTool(Agent agent, AgentRuntimeConfig config) {
        log.info("装配数据库取数工具: agentId={}, datasourceId={}", agent.getId(), config.getDatasourceId());
        return new DatabaseQueryTool(agent.getId(), config.getDatasourceId(), datasourceService, accessorFactory,
            tableMetadataService, nl2SqlService);
    }

}
