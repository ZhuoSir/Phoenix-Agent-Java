package com.phoenix.agent.harness.tool;

import cn.hutool.core.collection.CollUtil;
import com.phoenix.agent.constant.AgentRuntimeConstant;
import com.phoenix.data.constant.Constant;
import com.phoenix.data.dto.GraphRequest;
import com.phoenix.data.entity.AgentDatasource;
import com.phoenix.data.service.datasource.AgentDatasourceService;
import com.phoenix.data.service.datasource.DatasourceService;
import com.phoenix.data.service.graph.GraphService;
import com.phoenix.data.service.schema.SchemaService;
import com.phoenix.data.vo.GraphNodeResponse;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 数据库深度分析工具（T-09，R-06）：把复杂问题丢给既有 NL2SQL 状态图，收集并返回最终分析报告。
 *
 * <p>**按智能体实例化**（由 {@code DeepAnalysisToolContributor} 构造时绑定 agentId 与 datasourceId），
 * 内部复用 {@link GraphService#graphStreamProcess}，不重写任何取数/分析逻辑。
 *
 * <p>三处必须说明的取舍：
 * <ol>
 *   <li><b>前置「确保就绪」</b>：图内部按 {@code DatabaseUtil.getAgentDbConfig(agentId)} 解析数据源，
 *       要求 {@code tbl_data_agent_datasource} 里存在该 agent 的关联且 schema 文档已为该 agent 初始化。
 *       工具内先做幂等补绑 + 缺文档时初始化，失败一律返回可读文案而不是抛栈
 *       （{@code addDatasourceToAgent} 内部会 {@code disableAllByAgentId}，属预期行为）。</li>
 *   <li><b>threadId 隔离</b>：每次调用用 {@code tool-deep-<agentId>-<uuid>}，避免与前端会话的
 *       {@code StreamContext} 串流（图按 threadId 缓存上下文）。</li>
 *   <li><b>并发限制是「每智能体」而不是「单会话」</b>：原计划按 sessionId 限流，但设计要求工具不依赖
 *       RuntimeContext，工具侧根本拿不到 sessionId；而工具实例本身就是「每个智能体一个」，
 *       所以用**实例级信号量**自然获得每智能体并发上限
 *       （{@link AgentRuntimeConstant#MAX_DEEP_ANALYSIS_CONCURRENT}）：
 *       同一智能体的不同会话共抢同一信号量，拿不到直接返回可读文案，不排队。</li>
 * </ol>
 *
 * <p>工具只做「扇出 + 收集文本 + 截断」，不改库、不写库（{@code readOnly = true}）；
 * 另因内部会拉起一次完整图运行且已自带并发闸门，声明为非并发安全，避免框架再并行放大。
 */
@Slf4j
public class DeepAnalysisTool {

    /** 单次报告原始累积的硬上限（最终还会按 DEEP_ANALYSIS_MAX_CHARS 截断），防止流式文本撑爆内存 */
    private static final int MAX_RAW_REPORT_CHARS = AgentRuntimeConstant.DEEP_ANALYSIS_MAX_CHARS * 4;

    /** schema 就绪探测用的占位 query（该参数不参与检索，但不能为 null，见 ensureReady 注释） */
    private static final String SCHEMA_READINESS_QUERY = "schema-readiness-probe";

    private final Long agentId;

    private final Long datasourceId;

    private final DatasourceService datasourceService;

    private final AgentDatasourceService agentDatasourceService;

    private final SchemaService schemaService;

    private final GraphService graphService;

    /** 实例级信号量 = 该智能体的并发闸门（工具实例按智能体创建） */
    private final Semaphore concurrency = new Semaphore(AgentRuntimeConstant.MAX_DEEP_ANALYSIS_CONCURRENT, true);

    public DeepAnalysisTool(Long agentId, Long datasourceId, DatasourceService datasourceService,
            AgentDatasourceService agentDatasourceService, SchemaService schemaService, GraphService graphService) {
        this.agentId = agentId;
        this.datasourceId = datasourceId;
        this.datasourceService = datasourceService;
        this.agentDatasourceService = agentDatasourceService;
        this.schemaService = schemaService;
        this.graphService = graphService;
    }

    @Tool(name = "deepAnalyze",
            description = "针对复杂、多步骤的取数与分析问题做深度分析：内部会调用完整的 NL2SQL 分析流程"
                    + "（意图识别、证据召回、schema 召回、可行性评估、计划拆解、多步执行、报告生成），"
                    + "返回一段分析报告文本。适用于需要多表关联、多轮试错、归因分析或需要文字结论的问题；"
                    + "如果只是单条 SQL 就能回答的简单取数，请改用 queryDatabase。耗时较长（数十秒到数分钟）。",
            readOnly = true, concurrencySafe = false)
    public String deepAnalyze(
            @ToolParam(name = "question",
                    description = "需要深度分析的完整问题，包含分析目标、时间范围、对比维度与期望结论形式") String question) {
        if (datasourceId == null) {
            return DatabaseToolSupport.NO_DATASOURCE_MESSAGE;
        }
        if (!StringUtils.hasText(question)) {
            return "未给出需要深度分析的问题，请先明确分析目标";
        }
        Integer datasourceIdValue = DatabaseToolSupport.toDatasourceId(datasourceId);
        if (!concurrency.tryAcquire()) {
            return "已有 " + AgentRuntimeConstant.MAX_DEEP_ANALYSIS_CONCURRENT + " 个深度分析在进行中，请稍后再试";
        }
        long start = System.currentTimeMillis();
        try {
            String notReady = ensureReady(datasourceIdValue);
            if (notReady != null) {
                return notReady;
            }
            String threadId = "tool-deep-" + agentId + "-" + UUID.randomUUID();
            String report = runGraph(question, threadId);
            log.info("深度分析完成: agentId={}, datasourceId={}, elapsedMs={}, chars={}", agentId, datasourceIdValue,
                System.currentTimeMillis() - start, report.length());
            return report;
        }
        finally {
            concurrency.release();
        }
    }

    /**
     * 幂等「确保就绪」：补绑数据源 + 缺 schema 文档时初始化。
     * @return null 表示就绪；非 null 为可直接返回给模型的失败文案
     */
    private String ensureReady(Integer datasourceIdValue) {
        if (datasourceIdValue == null) {
            return "深度分析暂不可用：数据源配置无效，请重新选择数据源（可稍后重试）";
        }
        try {
            List<AgentDatasource> bound = agentDatasourceService.getAgentDatasource(agentId);
            boolean alreadyBound = CollUtil.isNotEmpty(bound)
                    && bound.stream().anyMatch(a -> a != null && datasourceIdValue.equals(a.getDatasourceId()));
            if (!alreadyBound) {
                agentDatasourceService.addDatasourceToAgent(agentId, datasourceIdValue);
                log.info("深度分析-已补绑数据源: agentId={}, datasourceId={}", agentId, datasourceIdValue);
            }
            // 注意：SchemaService 内部会用该 query 构造 SearchRequest（虽未真正用于检索），
            // 传 null 会被其断言拒绝（实测 IllegalArgumentException: Query can not be null），故传占位串。
            List<Document> tableDocuments = schemaService.getTableDocumentsByDatasource(datasourceIdValue, agentId,
                SCHEMA_READINESS_QUERY);
            if (CollUtil.isEmpty(tableDocuments)) {
                List<String> allTables = datasourceService.getDatasourceTables(datasourceIdValue);
                if (CollUtil.isEmpty(allTables)) {
                    return "深度分析暂不可用：数据源中未发现任何表（可稍后重试）";
                }
                agentDatasourceService.initializeSchemaForAgentWithDatasource(agentId, datasourceIdValue, allTables);
                log.info("深度分析-已初始化 schema 文档: agentId={}, datasourceId={}, tables={}", agentId, datasourceIdValue,
                    allTables.size());
            }
            return null;
        }
        catch (Exception e) {
            log.warn("深度分析-前置就绪检查失败: agentId={}, datasourceId={}, err={}", agentId, datasourceIdValue,
                e.toString());
            return "深度分析暂不可用：" + DatabaseToolSupport.rootMessage(e) + "（可稍后重试）";
        }
    }

    /**
     * 同线程阻塞收集状态图的最终文本：**先订阅** sink 再调用 {@code graphStreamProcess}，
     * 否则 unicast sink 的 complete/error 事件会丢。
     */
    private String runGraph(String question, String threadId) {
        Sinks.Many<ServerSentEvent<GraphNodeResponse>> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder report = new StringBuilder();
        CompletableFuture<String> done = new CompletableFuture<>();
        Disposable subscription = sink.asFlux().subscribe(sse -> {
            if (sse == null) {
                return;
            }
            if (Constant.STREAM_EVENT_COMPLETE.equals(sse.event())) {
                done.complete(report.toString());
            }
            else if (Constant.STREAM_EVENT_ERROR.equals(sse.event())) {
                String text = sse.data() == null ? null : sse.data().getText();
                done.completeExceptionally(new IllegalStateException(
                        StringUtils.hasText(text) ? text : "状态图执行失败且未返回错误详情"));
            }
            else if (sse.data() != null && sse.data().getText() != null) {
                if (report.length() < MAX_RAW_REPORT_CHARS) {
                    report.append(sse.data().getText());
                }
            }
        }, done::completeExceptionally,
                // 兜底：sink 正常 complete 但没走 complete 事件（或事件被丢弃）时，用已收集内容结束等待
                () -> done.complete(report.toString()));
        GraphRequest request = GraphRequest.builder()
            .agentId(String.valueOf(agentId))
            .threadId(threadId)
            .query(question)
            .build();
        try {
            graphService.graphStreamProcess(sink, request);
        }
        catch (Exception e) {
            // 同步抛出（如上下文校验失败）时不进入等待，直接快速失败
            done.completeExceptionally(e);
        }
        try {
            String text = done.get(AgentRuntimeConstant.DEEP_ANALYSIS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!StringUtils.hasText(text)) {
                return "深度分析已结束但未产出可读结论，请把问题描述得更具体一些后重试";
            }
            return DatabaseToolSupport.truncate(text, AgentRuntimeConstant.DEEP_ANALYSIS_MAX_CHARS);
        }
        catch (TimeoutException e) {
            stopQuietly(threadId);
            log.warn("深度分析超时: agentId={}, threadId={}, timeoutSeconds={}", agentId, threadId,
                AgentRuntimeConstant.DEEP_ANALYSIS_TIMEOUT_SECONDS);
            return "深度分析超时（超过 " + AgentRuntimeConstant.DEEP_ANALYSIS_TIMEOUT_SECONDS
                    + " 秒），请把问题拆解得更聚焦一些后重试";
        }
        catch (ExecutionException e) {
            stopQuietly(threadId);
            String message = DatabaseToolSupport.rootMessage(e);
            log.warn("深度分析失败: agentId={}, threadId={}, err={}", agentId, threadId, message);
            return "深度分析失败：" + message;
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stopQuietly(threadId);
            return "深度分析被中断，请稍后重试";
        }
        finally {
            subscription.dispose();
        }
    }

    /**
     * 超时/异常兜底清理图侧 StreamContext（成功路径图自己已 remove，重复清理是幂等的空操作）。
     */
    private void stopQuietly(String threadId) {
        try {
            graphService.stopStreamProcessing(threadId);
        }
        catch (Exception e) {
            log.warn("深度分析-清理 StreamContext 失败: agentId={}, threadId={}, err={}", agentId, threadId, e.toString());
        }
    }

}
