package com.phoenix.agent.harness.tool;

import cn.hutool.core.collection.CollUtil;
import com.phoenix.agent.constant.AgentRuntimeConstant;
import com.phoenix.data.bo.DbConfigBO;
import com.phoenix.data.bo.schema.ColumnInfoBO;
import com.phoenix.data.bo.schema.ForeignKeyInfoBO;
import com.phoenix.data.bo.schema.ResultSetBO;
import com.phoenix.data.bo.schema.TableInfoBO;
import com.phoenix.data.connector.DbQueryParameter;
import com.phoenix.data.connector.accessor.Accessor;
import com.phoenix.data.connector.accessor.AccessorFactory;
import com.phoenix.data.dto.schema.ColumnDTO;
import com.phoenix.data.dto.schema.SchemaDTO;
import com.phoenix.data.dto.schema.TableDTO;
import com.phoenix.data.service.schema.TableMetadataService;
import com.phoenix.data.util.JsonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import tools.jackson.core.type.TypeReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库类工具（T-08 取数 / T-09 深度分析）共享支撑。
 *
 * <p>集中三件事，避免两个工具各写一份：
 * <ol>
 *   <li>数据源 ID 归一：运行时配置是 {@code Long}，而 {@code DatasourceService} / {@code AgentDatasourceService}
 *       全链路收 {@code Integer}，统一在此做 {@code Math.toIntExact}；</li>
 *   <li><b>JDBC 实时内省</b>生成 {@link SchemaDTO}：存量 {@code SchemaService.getTableDocumentsByDatasource}
 *       的过滤条件含 {@code agentId}（文档的 agentId 是「做 schema 初始化的那个 agent」），
 *       对话智能体（尤其新建 sn 为 NULL 的）经常召不回，且其 {@code query} 是死参数，
 *       因此取数工具的 schema 一律走「accessor.showTables + batchEnrichTableMetadata」实时内省；</li>
 *   <li>结果集与长文本的紧凑渲染：只回显有限行列，防止敏感数据 / 提示词双爆。</li>
 * </ol>
 *
 * <p>本类是纯静态无状态工具，不持有连接也不依赖 RuntimeContext，可被并发调用。
 */
@Slf4j
public final class DatabaseToolSupport {

    /** 智能体未配置数据源时的统一提示（两个工具共用同一文案） */
    public static final String NO_DATASOURCE_MESSAGE = "该智能体未配置数据源，请在对话智能体配置中选择数据源后重试";

    /** 单元格值回显的最大字符数（text/blob 类字段可能极长） */
    private static final int MAX_CELL_CHARS = 200;

    /** 每列最多回显的样本值个数（与 PromptHelper 渲染口径一致） */
    private static final int MAX_SAMPLE_VALUES = 3;

    private DatabaseToolSupport() {
    }

    /**
     * 运行时配置的 datasourceId（Long）→ 数据源服务口径（Integer）。
     * 超出 int 范围（生产不可能出现，DB 该列本就是 Integer）时降级为 null，交由调用方给可读文案。
     * @param datasourceId 配置中的数据源 ID，可为 null
     * @return 换算后的 Integer；入参为 null 或溢出时返回 null
     */
    public static Integer toDatasourceId(Long datasourceId) {
        if (datasourceId == null) {
            return null;
        }
        try {
            return Math.toIntExact(datasourceId);
        }
        catch (ArithmeticException e) {
            log.warn("数据源ID超出 Integer 范围: datasourceId={}", datasourceId);
            return null;
        }
    }

    /**
     * JDBC 实时内省数据源，产出可直接喂给 NL2SQL 的 {@link SchemaDTO}。
     *
     * <p>先按 {@link AgentRuntimeConstant#MAX_SCHEMA_TABLES} 截断表、再补齐元数据：
     * 元数据补齐会逐表采样（真实 SQL），对上百张表的库先截断能显著降低成本。
     * {@code SchemaDTO} 的 {@code table} / 每张表的 {@code column} 保证非 null
     * （{@code PromptHelper} 会直接 foreach）。
     *
     * @param accessorFactory 访问器工厂
     * @param tableMetadataService 表元数据服务（补齐 columns / 主键 / 样本值）
     * @param dbConfig 数据源配置
     * @return 非 null 的 SchemaDTO（即使库中没有表也返回空表结构的对象）
     * @throws Exception 内省失败（连接不可用、无匹配 accessor 等）
     */
    public static SchemaDTO introspectSchema(AccessorFactory accessorFactory, TableMetadataService tableMetadataService,
            DbConfigBO dbConfig) throws Exception {
        DbQueryParameter param = DbQueryParameter.from(dbConfig).setSchema(dbConfig.getSchema());
        Accessor accessor = accessorFactory.getAccessorByDbConfig(dbConfig);
        List<TableInfoBO> allTables = accessor.showTables(dbConfig, param);
        if (CollUtil.isEmpty(allTables)) {
            log.warn("schema 内省未发现任何表: schema={}", dbConfig.getSchema());
            return emptySchema(dbConfig);
        }
        // 稳定排序后截断：showTables 的顺序随驱动而异，排序保证同一库每次内省结果一致
        List<TableInfoBO> tables = allTables.stream()
            .filter(t -> t != null && StringUtils.hasText(t.getName()))
            .sorted(Comparator.comparing(TableInfoBO::getName))
            .limit(AgentRuntimeConstant.MAX_SCHEMA_TABLES)
            .collect(Collectors.toCollection(ArrayList::new));
        if (allTables.size() > tables.size()) {
            log.info("schema 表数量超出上限已截断: schema={}, total={}, kept={}", dbConfig.getSchema(), allTables.size(),
                tables.size());
        }
        if (CollUtil.isEmpty(tables)) {
            return emptySchema(dbConfig);
        }
        Map<String, List<String>> foreignKeyMap = loadForeignKeys(accessor, dbConfig, param);
        tableMetadataService.batchEnrichTableMetadata(tables, dbConfig, foreignKeyMap);
        return toSchemaDTO(dbConfig, tables, foreignKeyMap);
    }

    /**
     * 把结果集渲染成给大模型看的紧凑文本：首行行数、次行表头、随后 ` | ` 分隔的数据行。
     * 行值统一按 String 处理（执行层已是 String），null → 空串，换行/制表符压成空格。
     *
     * @param resultSet 执行层返回的结果集，可为 null
     * @return 紧凑文本（永不为 null）
     */
    public static String renderResultSet(ResultSetBO resultSet) {
        if (resultSet == null) {
            return "查询执行失败：执行层未返回结果集";
        }
        List<String> columns = resultSet.getColumn() == null ? List.of() : resultSet.getColumn();
        List<Map<String, String>> data = resultSet.getData() == null ? List.of() : resultSet.getData();
        int total = data.size();
        int shown = Math.min(total, AgentRuntimeConstant.MAX_RESULT_ROWS_SHOWN);
        StringBuilder sb = new StringBuilder();
        sb.append("查询到 ").append(total).append(" 行");
        if (total > shown) {
            sb.append("（已截断至 ").append(shown).append(" 行）");
        }
        sb.append("\n");
        if (columns.isEmpty()) {
            return sb.append("（结果集不含列信息）\n").toString();
        }
        sb.append(String.join(" | ", columns)).append("\n");
        for (int i = 0; i < shown; i++) {
            Map<String, String> row = data.get(i);
            List<String> cells = new ArrayList<>(columns.size());
            for (String column : columns) {
                cells.add(sanitizeCell(row == null ? null : row.get(column)));
            }
            sb.append(String.join(" | ", cells)).append("\n");
        }
        if (total > shown) {
            sb.append("（仅展示前 ").append(shown).append(" 行，如需更多请缩小查询范围）\n");
        }
        return sb.toString();
    }

    /**
     * 文本截断（超长时追加标注），供深度分析报告回显使用。
     * @param text 原文本，可为 null
     * @param maxChars 最大保留字符数
     * @return 截断后的文本（永不为 null）
     */
    public static String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxChars) {
            return text;
        }
        return text.substring(0, maxChars) + "\n…（内容过长已截断，仅保留前 " + maxChars + " 字符）";
    }

    /**
     * 取异常链最内层的可读消息（执行层常用包装异常，直接取 getMessage 会得到 null 或空串）。
     * @param throwable 异常，可为 null
     * @return 可读消息（永不为 null）
     */
    public static String rootMessage(Throwable throwable) {
        if (throwable == null) {
            return "未知错误";
        }
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (!StringUtils.hasText(message)) {
            message = throwable.getMessage();
        }
        return StringUtils.hasText(message) ? message : cause.getClass().getSimpleName();
    }

    /**
     * 单元格清洗：null → 空串，去掉换行/回车/制表符（否则表格文本会被撑坏），超长截断。
     */
    private static String sanitizeCell(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim();
        if (cleaned.length() > MAX_CELL_CHARS) {
            return cleaned.substring(0, MAX_CELL_CHARS) + "…";
        }
        return cleaned;
    }

    /**
     * 拉取外键并转成 {@code 表名 -> 外键描述列表}；外键不可用（部分方言未实现）时降级为空 Map，
     * 不阻断 schema 内省主流程。
     */
    private static Map<String, List<String>> loadForeignKeys(Accessor accessor, DbConfigBO dbConfig,
            DbQueryParameter param) {
        Map<String, List<String>> foreignKeyMap = new LinkedHashMap<>();
        try {
            List<ForeignKeyInfoBO> foreignKeys = accessor.showForeignKeys(dbConfig, param);
            if (CollUtil.isEmpty(foreignKeys)) {
                return foreignKeyMap;
            }
            for (ForeignKeyInfoBO fk : foreignKeys) {
                if (fk == null || !StringUtils.hasText(fk.getTable())) {
                    continue;
                }
                String description = "%s.%s=%s.%s".formatted(fk.getTable(), fk.getColumn(), fk.getReferencedTable(),
                    fk.getReferencedColumn());
                foreignKeyMap.computeIfAbsent(fk.getTable(), k -> new ArrayList<>()).add(description);
            }
        }
        catch (Exception e) {
            log.warn("外键内省失败，已降级为空外键: schema={}, err={}", dbConfig.getSchema(), e.toString());
        }
        return foreignKeyMap;
    }

    /**
     * 内省结果 → SchemaDTO（表数/列数按常量截断，{@code table} 与 {@code column} 保证非 null）。
     */
    private static SchemaDTO toSchemaDTO(DbConfigBO dbConfig, List<TableInfoBO> tables,
            Map<String, List<String>> foreignKeyMap) {
        List<TableDTO> tableDTOs = new ArrayList<>(tables.size());
        List<String> foreignKeys = new ArrayList<>();
        for (TableInfoBO table : tables) {
            TableDTO tableDTO = new TableDTO();
            tableDTO.setName(table.getName());
            tableDTO.setDescription(StringUtils.hasText(table.getDescription()) ? table.getDescription()
                    : table.getName());
            tableDTO.setPrimaryKeys(table.getPrimaryKeys() == null ? new ArrayList<>()
                    : new ArrayList<>(table.getPrimaryKeys()));
            List<ColumnInfoBO> columns = table.getColumns() == null ? List.of() : table.getColumns();
            List<ColumnDTO> columnDTOs = new ArrayList<>(Math.min(columns.size(),
                    AgentRuntimeConstant.MAX_COLUMNS_PER_TABLE));
            columns.stream().filter(c -> c != null && StringUtils.hasText(c.getName()))
                .limit(AgentRuntimeConstant.MAX_COLUMNS_PER_TABLE)
                .forEach(column -> columnDTOs.add(toColumnDTO(column)));
            tableDTO.setColumn(columnDTOs);
            tableDTOs.add(tableDTO);
            List<String> tableForeignKeys = foreignKeyMap.get(table.getName());
            if (CollUtil.isNotEmpty(tableForeignKeys)) {
                foreignKeys.addAll(tableForeignKeys);
            }
        }
        SchemaDTO schemaDTO = emptySchema(dbConfig);
        schemaDTO.setTable(tableDTOs);
        schemaDTO.setTableCount(tableDTOs.size());
        schemaDTO.setForeignKeys(foreignKeys);
        return schemaDTO;
    }

    /**
     * 列内省结果 → ColumnDTO；samples 是 JSON 数组字符串，解析失败只丢样本不丢列。
     */
    private static ColumnDTO toColumnDTO(ColumnInfoBO column) {
        ColumnDTO columnDTO = new ColumnDTO();
        columnDTO.setName(column.getName());
        columnDTO.setDescription(StringUtils.hasText(column.getDescription()) ? column.getDescription()
                : column.getName());
        columnDTO.setType(column.getType());
        if (StringUtils.hasText(column.getSamples())) {
            try {
                List<String> samples = JsonUtil.getObjectMapper().readValue(column.getSamples(),
                        new TypeReference<List<String>>() {
                        });
                if (CollUtil.isNotEmpty(samples)) {
                    columnDTO.setData(new ArrayList<>(
                            samples.subList(0, Math.min(MAX_SAMPLE_VALUES, samples.size()))));
                }
            }
            catch (Exception e) {
                log.debug("列样本解析失败，已忽略: column={}, err={}", column.getName(), e.toString());
            }
        }
        return columnDTO;
    }

    /**
     * 空 schema 骨架（非 null 且 table 非 null，避免 PromptHelper NPE）。
     */
    private static SchemaDTO emptySchema(DbConfigBO dbConfig) {
        SchemaDTO schemaDTO = new SchemaDTO();
        schemaDTO.setName(dbConfig.getSchema());
        schemaDTO.setDescription("数据库实时内省（schema=" + dbConfig.getSchema() + "）");
        schemaDTO.setTableCount(0);
        schemaDTO.setTable(new ArrayList<>());
        schemaDTO.setForeignKeys(new ArrayList<>());
        return schemaDTO;
    }

}
