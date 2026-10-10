package com.phoenix.data.service.aimodelconfig;

import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.enums.ModelProvider;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 向量维度解析器（R-08 存储侧 / R-09 一致性校验）。
 *
 * <p>背景：向量表列类型是 {@code vector(N)}，**维度是表结构的一部分**——此前 4 处装配点全部写死 512，
 * 换成 768/1024 维的 embedding 模型会因表结构不匹配而写入失败（且失败信息不可读）。
 *
 * <p>本解析器把"维度"的唯一来源统一为**当前启用的 EMBEDDING 模型**：
 * <ul>
 *   <li>provider=ollama → 探测模型实际输出维度（{@link OllamaApiClient#probeEmbeddingDimension}）</li>
 *   <li>其余 provider → 沿用既有 512（DashScopeTextEmbedding 装配口径），**行为零变化**</li>
 *   <li>解析失败 → 回退 512 并 WARN（不阻断启动；L-09 不得把小超时当"链路不通"）</li>
 * </ul>
 *
 * <p>R-09：{@link #assertTableCompatible(String)} 供写入/检索入口调用——当目标表既有维度与当前模型维度
 * 不一致时**显式拒绝并给出处置建议**，绝不静默写入或返回空结果（存量重建不在本期范围）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmbeddingDimensionResolver {

	/** 既有默认维度（非 ollama provider 的历史口径） */
	public static final int DEFAULT_DIMENSIONS = 512;

	private final ModelConfigDataService modelConfigDataService;

	private final OllamaApiClient ollamaApiClient;

	private final JdbcTemplate jdbcTemplate;

	/** 解析结果缓存（模型切换需重启生效——与 PgVectorStore 在启动期建 bean 的语义一致，已在文档写明） */
	private volatile Integer cachedDimensions;

	/**
	 * 使缓存失效（模型配置变更时调用）。
	 *
	 * <p>否则运行中切换 embedding 模型后，校验/装配仍用旧维度 ⇒ 出现"校验放行但 INSERT 维度不符"的裸 SQL 错误。
	 */
	public void invalidate() {
		cachedDimensions = null;
	}

	/**
	 * 解析当前生效的向量维度。
	 */
	public int resolve() {
		Integer cached = cachedDimensions;
		if (cached != null) {
			return cached;
		}
		synchronized (this) {
			if (cachedDimensions != null) {
				return cachedDimensions;
			}
			int dims = DEFAULT_DIMENSIONS;
			try {
				// R-08：先取**默认**行（与 CHAT/MULTIMODAL 的默认机制一致）；无默认再退"任一启用行"
				ModelConfigDTO config = modelConfigDataService.getDefaultConfigByType(ModelType.EMBEDDING);
				if (config == null) {
					config = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
				}
				if (config != null && ModelProvider.OLLAMA.getCode().equalsIgnoreCase(config.getProvider())) {
					dims = ollamaApiClient.probeEmbeddingDimension(config.getBaseUrl(), config.getModelName());
					log.info("向量维度解析: provider=ollama, model={}, dimensions={}", config.getModelName(), dims);
				}
				else {
					log.info("向量维度解析: provider={}, dimensions={}（沿用既有口径）",
							config == null ? "未配置" : config.getProvider(), dims);
				}
			}
			catch (Exception e) {
				log.warn("向量维度解析失败，回退 {}（R-08）: {}", DEFAULT_DIMENSIONS, e.toString());
			}
			cachedDimensions = dims;
			return dims;
		}
	}

	/**
	 * R-09：校验目标向量表的既有维度与当前模型维度是否一致。
	 *
	 * @param tableName 向量表名（如 {@code tbl_harness_vector_store_knowledge}）
	 * @throws DimensionMismatchException 不一致（含两个维度与处置建议）
	 */
	public void assertTableCompatible(String tableName) {
		Integer tableDims = tableDimensions(tableName);
		if (tableDims == null) {
			// 表尚未创建：由 PgVectorStore 按当前维度自动建表，无需拒绝
			return;
		}
		int modelDims = resolve();
		if (tableDims != modelDims) {
			String msg = "向量维度不一致：表 " + tableName + " 为 " + tableDims + " 维，当前 embedding 模型为 "
					+ modelDims + " 维。本期不支持自动重建：请改用与原表维度一致的 embedding 模型，"
					+ "或在确认可丢弃/重建既有向量后由运维执行迁移（参见 BL-34）。";
			log.warn("{}（R-09 显式拒绝）", msg);
			throw new DimensionMismatchException(msg);
		}
	}

	/**
	 * R-09（表无关版）：校验**所有**向量表的既有维度是否与当前模型维度一致。
	 *
	 * <p>用于写入/检索入口——这些入口由 Spring 注入具体 {@code VectorStore} bean，代码层拿不到表名；
	 * 与其猜表，不如校验全部向量表：当前 embedding 模型必须能适配**将被写入的任意表**，
	 * 任一表维度不一致即拒绝（含表名与处置建议），避免"静默写坏/检索空结果"。
	 */
	public void assertAllTablesCompatible() {
		// R-09：必须用**实时**维度（缓存可能在模型切换后滞后，导致校验放行而 INSERT 失败）
		invalidate();
		int modelDims = resolve();
		java.util.List<String> tables = jdbcTemplate.queryForList(
				"select c.table_name from information_schema.columns c "
						+ "where c.column_name = 'embedding' and c.udt_name = 'vector'", String.class);
		for (String table : tables) {
			Integer tableDims = tableDimensions(table);
			if (tableDims != null && tableDims != modelDims) {
				String msg = "向量维度不一致：表 " + table + " 为 " + tableDims + " 维，当前 embedding 模型为 "
						+ modelDims + " 维。本期不支持自动重建：请改用与原表维度一致的 embedding 模型，"
						+ "或在确认可丢弃/重建既有向量后由运维执行迁移（参见 BL-34）。";
				log.warn("{}（R-09 显式拒绝，写入/检索前拦截）", msg);
				throw new DimensionMismatchException(msg);
			}
		}
	}

	/** 读取表 embedding 列的实际维度；表或列不存在返回 null */
	private Integer tableDimensions(String tableName) {
		try {
			String type = jdbcTemplate.queryForObject(
					"select format_type(a.atttypid, a.atttypmod) from pg_attribute a "
							+ "join pg_class t on t.oid = a.attrelid "
							+ "where t.relname = ? and a.attname = 'embedding' and a.attnum > 0",
					String.class, tableName);
			if (type == null) {
				return null;
			}
			java.util.regex.Matcher m = java.util.regex.Pattern.compile("vector\\((\\d+)\\)").matcher(type);
			return m.find() ? Integer.valueOf(m.group(1)) : null;
		}
		catch (Exception e) {
			log.debug("读取表维度失败（按未知处理）: table={}, err={}", tableName, e.toString());
			return null;
		}
	}

	/** 维度不一致（业务可诊断异常，R-09） */
	public static class DimensionMismatchException extends RuntimeException {
		public DimensionMismatchException(String message) {
			super(message);
		}
	}

}
