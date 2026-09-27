package com.phoenix.agent.harness.tool;

import com.phoenix.agent.constant.AgentRuntimeConstant;
import com.phoenix.data.bo.DbConfigBO;
import com.phoenix.data.bo.schema.ResultSetBO;
import com.phoenix.data.connector.DbQueryParameter;
import com.phoenix.data.connector.accessor.Accessor;
import com.phoenix.data.connector.accessor.AccessorFactory;
import com.phoenix.data.dto.prompt.SqlGenerationDTO;
import com.phoenix.data.dto.schema.SchemaDTO;
import com.phoenix.data.entity.Datasource;
import com.phoenix.data.service.datasource.DatasourceService;
import com.phoenix.data.service.nl2sql.Nl2SqlService;
import com.phoenix.data.service.schema.TableMetadataService;
import com.phoenix.tools.util.SqlSecurityValidator;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 数据库取数工具（T-08，R-05）：自然语言 → 单条只读 SQL → 执行 → 紧凑结果文本。
 *
 * <p>**按智能体实例化**（由 {@code DatabaseQueryToolContributor} 构造时绑定 agentId 与 datasourceId），
 * 因此工具自身不需要从 RuntimeContext 里猜身份，也不依赖会话。
 *
 * <p>关键设计（与「向量召回 schema」的存量路径不同）：
 * <ul>
 *   <li>schema 走 JDBC 实时内省（见 {@link DatabaseToolSupport#introspectSchema}），
 *       规避 {@code SchemaService.getTableDocumentsByDatasource} 的 agentId 过滤导致召回为空；</li>
 *   <li>生成 SQL 后**必须**过 {@link SqlSecurityValidator} 只读校验，未通过则不执行；</li>
 *   <li>执行层自带 {@code setMaxRows(1000)} / {@code setQueryTimeout(30)} 硬限制，
 *       本工具只做「回显行数」截断与单元格清洗，避免把敏感数据与超长文本灌进提示词；</li>
 *   <li>任何失败都返回可读文本（不抛栈给模型），只打行数/耗时/agentId，不打印结果全文。</li>
 * </ul>
 */
@Slf4j
public class DatabaseQueryTool {

    private final Long agentId;

    private final Long datasourceId;

    private final DatasourceService datasourceService;

    private final AccessorFactory accessorFactory;

    private final TableMetadataService tableMetadataService;

    private final Nl2SqlService nl2SqlService;

    public DatabaseQueryTool(Long agentId, Long datasourceId, DatasourceService datasourceService,
            AccessorFactory accessorFactory, TableMetadataService tableMetadataService, Nl2SqlService nl2SqlService) {
        this.agentId = agentId;
        this.datasourceId = datasourceId;
        this.datasourceService = datasourceService;
        this.accessorFactory = accessorFactory;
        this.tableMetadataService = tableMetadataService;
        this.nl2SqlService = nl2SqlService;
    }

    @Tool(name = "queryDatabase",
            description = "把用户的自然语言问题转换成一条只读 SQL 并在该智能体已配置的数据源上执行，返回查询结果表格。"
                    + "适用于一次取数即可回答的问题，例如明细查询、按条件过滤、分组统计、排序取 TopN。"
                    + "只读，不会修改数据库；如果问题复杂、需要多步骤取数或多轮试错，请改用 deepAnalyze。",
            readOnly = true, concurrencySafe = true)
    public String queryDatabase(
            @ToolParam(name = "question",
                    description = "用自然语言完整描述要查什么，尽量给出表/业务口径、时间范围、过滤条件、分组维度与排序条数") String question) {
        if (datasourceId == null) {
            return DatabaseToolSupport.NO_DATASOURCE_MESSAGE;
        }
        if (!StringUtils.hasText(question)) {
            return "未给出要查询的问题，请先明确需要查询的数据内容";
        }
        Integer datasourceIdValue = DatabaseToolSupport.toDatasourceId(datasourceId);
        long start = System.currentTimeMillis();
        DbConfigBO dbConfig;
        try {
            dbConfig = resolveDbConfig(datasourceIdValue);
        }
        catch (Exception e) {
            log.warn("数据库取数-数据源解析失败: agentId={}, datasourceId={}, err={}", agentId, datasourceIdValue,
                e.toString());
            return "查询执行失败：读取数据源配置失败（" + DatabaseToolSupport.rootMessage(e) + "）";
        }
        if (dbConfig == null) {
            return "查询执行失败：数据源不存在或已删除，请在对话智能体配置中重新选择数据源";
        }
        SchemaDTO schemaDTO;
        try {
            schemaDTO = DatabaseToolSupport.introspectSchema(accessorFactory, tableMetadataService, dbConfig);
        }
        catch (Exception e) {
            log.warn("数据库取数-schema 内省失败: agentId={}, datasourceId={}, err={}", agentId, datasourceIdValue,
                e.toString());
            return "查询执行失败：获取数据库表结构失败（" + DatabaseToolSupport.rootMessage(e) + "）";
        }
        String sql;
        try {
            sql = generateSql(question, dbConfig, schemaDTO);
        }
        catch (Exception e) {
            log.warn("数据库取数-SQL 生成失败: agentId={}, datasourceId={}, err={}", agentId, datasourceIdValue,
                e.toString());
            return "查询执行失败：生成查询SQL失败（" + DatabaseToolSupport.rootMessage(e) + "）";
        }
        if (!StringUtils.hasText(sql)) {
            return "未能生成可执行的查询SQL，请把问题描述得更具体一些（补充时间范围、统计口径或目标字段）";
        }
        SqlSecurityValidator.ValidationResult validation = SqlSecurityValidator.validate(sql);
        if (!validation.isSafe()) {
            log.warn("数据库取数-SQL 未通过只读校验: agentId={}, datasourceId={}, reason={}", agentId, datasourceIdValue,
                validation.getMessage());
            return "生成的SQL未通过只读安全校验：" + validation.getMessage();
        }
        ResultSetBO resultSet;
        try {
            DbQueryParameter queryParameter = new DbQueryParameter().setSql(sql).setSchema(dbConfig.getSchema());
            Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);
            resultSet = accessor.executeSqlAndReturnObject(dbConfig, queryParameter);
        }
        catch (Exception e) {
            log.warn("数据库取数-执行失败: agentId={}, datasourceId={}, elapsedMs={}, err={}", agentId, datasourceIdValue,
                System.currentTimeMillis() - start, e.toString());
            return "查询执行失败：" + DatabaseToolSupport.rootMessage(e);
        }
        List<Map<String, String>> rows = resultSet == null || resultSet.getData() == null ? List.of()
                : resultSet.getData();
        log.info("数据库取数完成: agentId={}, datasourceId={}, rows={}, elapsedMs={}", agentId, datasourceIdValue,
            rows.size(), System.currentTimeMillis() - start);
        return DatabaseToolSupport.renderResultSet(resultSet);
    }

    /**
     * 解析数据源配置（datasource 不存在或 ID 非法时返回 null，由调用方转成可读文案）。
     */
    private DbConfigBO resolveDbConfig(Integer datasourceIdValue) {
        if (datasourceIdValue == null) {
            return null;
        }
        Datasource datasource = datasourceService.getDatasourceById(datasourceIdValue);
        if (datasource == null) {
            return null;
        }
        return datasourceService.getDbConfig(datasource);
    }

    /**
     * NL→SQL：schema 非 null 且 table 非 null，否则 PromptHelper 会 NPE。
     * 超时由 {@link AgentRuntimeConstant#SQL_GENERATE_TIMEOUT_SECONDS} 控制（block 超时抛异常，由上层兜底）。
     */
    private String generateSql(String question, DbConfigBO dbConfig, SchemaDTO schemaDTO) {
        SqlGenerationDTO generationDTO = SqlGenerationDTO.builder()
            .query(question)
            .executionDescription("根据用户问题生成单条只读查询SQL")
            .schemaDTO(schemaDTO)
            .dialect(dbConfig.getDialectType())
            .sql(null)
            .evidence("无")
            .build();
        String sql = nl2SqlService.generateSql(generationDTO)
            .collect(StringBuilder::new, StringBuilder::append)
            .map(StringBuilder::toString)
            .block(Duration.ofSeconds(AgentRuntimeConstant.SQL_GENERATE_TIMEOUT_SECONDS));
        if (sql == null) {
            // sqlTrim 内部直接对入参做 length()，null 会 NPE；此处提前返回，让上层走「未能生成 SQL」的友好文案
            return null;
        }
        return nl2SqlService.sqlTrim(sql);
    }

}
