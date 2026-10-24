package com.phoenix.agent.harness.attachment;

import com.phoenix.agent.harness.attachment.DocumentChunker.Chunk;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * 超长文档分批处理器（long-doc-chunking T-04 / R-02、R-03、R-06、R-08）。
 *
 * <p>map：逐块调用模型抽「本块要点 + 关键原文摘录」；reduce：按顺序聚合成结构化摘要。
 * <ul>
 *   <li>串行处理（成本与限流可控）；单块失败重试 {@code retry} 次，仍失败则跳过并记录；</li>
 *   <li>护栏：块数 > {@code maxChunks} 或累计输出 > {@code budgetChars} ⇒ 降级（只保留已处理块 + 覆盖率说明）；</li>
 *   <li>进度：通过 {@code progress} 回调上报「正在处理第 x/N 批」，由上层转成 notice 帧；</li>
 *   <li>纯编排、无 Spring 依赖：模型调用以 {@link ChatFn} 注入，便于单测与复用。</li>
 * </ul>
 */
@Slf4j
public final class DocumentBatchProcessor {

	/** 模型调用抽象（真实实现见装配器；测试用 stub） */
	public interface ChatFn {
		String call(String prompt) throws Exception;
	}

	/** 处理结果 */
	public record BatchResult(int totalChunks, int processedChunks, boolean degraded, String digest,
			String coverageNote, long elapsedMs) {
	}

	private DocumentBatchProcessor() {
	}

	public static BatchResult process(String fullText, int chunkSize, int overlap, int maxChunks, int budgetChars,
			int retry, ChatFn chat, BiConsumer<Integer, Integer> progress) {
		long t0 = System.currentTimeMillis();
		List<Chunk> chunks = DocumentChunker.chunk(fullText, chunkSize, overlap);
		int total = chunks.size();
		log.info("[doc-batch] 开始 chars={}, chunkCount={}, chunkSize={}, maxChunks={}", fullText == null ? 0 : fullText.length(),
				total, chunkSize, maxChunks);

		List<String> parts = new ArrayList<>();
		int processed = 0;
		int outChars = 0;
		boolean degraded = false;
		List<Integer> failed = new ArrayList<>();

		for (Chunk c : chunks) {
			// 护栏①：批数超上限
			if (processed >= maxChunks) {
				degraded = true;
				log.warn("[doc-batch] 降级: 达到最大批数 maxChunks={}, processed={}/{}", maxChunks, processed, total);
				break;
			}
			// 护栏②：累计输出超预算
			if (outChars >= budgetChars) {
				degraded = true;
				log.warn("[doc-batch] 降级: 累计输出超预算 budgetChars={}, outChars={}, processed={}/{}", budgetChars, outChars,
						processed, total);
				break;
			}
			if (progress != null) {
				progress.accept(c.index() + 1, total);
			}
			String part = mapWithRetry(c, total, retry, chat, failed);
			if (part == null) {
				continue;
			}
			processed++;
			outChars += part.length();
			parts.add("### 第 " + (c.index() + 1) + "/" + total + " 块\n" + part);
			log.debug("[doc-batch] 单批完成 index={}, outChars={}", c.index(), part.length());
		}

		String digest = String.join("\n\n", parts);
		// 文案必须"不撒谎"：有失败块时不能说"已全部处理"（T-04 断言 ⑤ 曾暴露此矛盾）
		String coverage;
		if (!failed.isEmpty()) {
			coverage = "共 " + total + " 块，成功 " + processed + " 块；第 " + failed + " 块处理失败";
		}
		else if (degraded) {
			coverage = "已按摘要方式处理：" + processed + "/" + total + " 块（约 " + pct(processed, total) + "% 内容）";
		}
		else {
			coverage = "共 " + total + " 块，已全部处理";
		}
		long ms = System.currentTimeMillis() - t0;
		log.info("[doc-batch] 结束 chunkCount={}, processed={}, degraded={}, failed={}, outChars={}, totalMs={}", total,
				processed, degraded, failed, outChars, ms);
		return new BatchResult(total, processed, degraded, digest, coverage, ms);
	}

	private static String mapWithRetry(Chunk c, int total, int retry, ChatFn chat, List<Integer> failed) {
		int attempts = Math.max(0, retry) + 1;
		for (int i = 1; i <= attempts; i++) {
			try {
				String out = chat.call(buildMapPrompt(c, total));
				if (out != null && !out.isBlank()) {
					return out.trim();
				}
				throw new IllegalStateException("模型返回空内容");
			}
			catch (Exception e) {
				log.warn("[doc-batch] 单批失败 index={}, attempt={}/{}, error={}", c.index(), i, attempts, e.toString());
			}
		}
		failed.add(c.index() + 1);
		return null;
	}

	static String buildMapPrompt(Chunk c, int total) {
		return """
				你是文档分析助手。下面是长文档的第 %d/%d 块（可能不完整，属正常）。
				请输出两项，务必简洁、只依据本块原文：
				1) 【本块要点】不超过 120 字；
				2) 【关键原文摘录】3~5 条，逐字摘录（每条不超过 40 字，不要改写）。
				不要编造本块没有的内容。

				--- 第 %d/%d 块开始 ---
				%s
				--- 第 %d/%d 块结束 ---
				""".formatted(c.index() + 1, total, c.index() + 1, total, c.text(), c.index() + 1, total);
	}

	private static int pct(int a, int b) {
		return b == 0 ? 0 : (int) Math.round(a * 100.0 / b);
	}

}
