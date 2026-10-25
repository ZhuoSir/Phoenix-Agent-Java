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
		int[] fallback = { 0 };

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
			String part = mapWithRetry(c, total, retry, chat, failed, fallback);
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
			coverage = "共 " + total + " 块，已全部处理"
					+ (fallback[0] > 0 ? "（其中 " + fallback[0] + " 块模型输出不合格式，已用原文摘录+规则扫描兜底）" : "");
		}
		long ms = System.currentTimeMillis() - t0;
		log.info("[doc-batch] 结束 chunkCount={}, processed={}, degraded={}, failed={}, outChars={}, totalMs={}", total,
				processed, degraded, failed, outChars, ms);
		return new BatchResult(total, processed, degraded, digest, coverage, ms);
	}

	/** 规则扫描关键标识（不依赖模型）：编号/代码、日期、时限、条款号 */
	private static final java.util.regex.Pattern[] ID_PATTERNS = {
			java.util.regex.Pattern.compile("\\b[A-Z]{2,}[A-Z0-9]*(?:-\\d+){1,3}\\b"),   // TC-8899 / FD-2026-0117
			java.util.regex.Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b"),                 // 2026-07-01
			java.util.regex.Pattern.compile("\\d+\\s*个工作日"),                             // 7 个工作日
			java.util.regex.Pattern.compile("第[0-9一二三四五六七八九十百零]+[章条]") };

	static java.util.List<String> scanIdentifiers(String text) {
		java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
		if (text == null) {
			return java.util.List.of();
		}
		for (java.util.regex.Pattern p : ID_PATTERNS) {
			java.util.regex.Matcher m = p.matcher(text);
			while (m.find()) {
				String v = m.group().trim();
				if (!v.isEmpty()) {
					out.add(v);
				}
				if (out.size() >= 40) {
					return new java.util.ArrayList<>(out);
				}
			}
		}
		return new java.util.ArrayList<>(out);
	}

	/** 模型输出是否"像模板回声"（实测 qwen3 会原样回抄【要点】… 这种占位） */
	private static boolean looksLikeEcho(String out) {
		if (out == null) {
			return true;
		}
		String t = out.replace(" ", "");
		return t.contains("【要点】…") || t.contains("【要点】...") || out.contains("… / 【标识】") || out.length() < 12;
	}

	private static String mapWithRetry(Chunk c, int total, int retry, ChatFn chat, List<Integer> failed, int[] fallback) {
		int attempts = Math.max(0, retry) + 1;
		for (int i = 1; i <= attempts; i++) {
			try {
				String out = chat.call(buildMapPrompt(c, total));
				if (out == null || out.isBlank()) {
					throw new IllegalStateException("模型返回空内容");
				}
				// 模型可能不守格式（实测：答成评审意见 / 回抄模板）⇒ 不注入垃圾，按失败重试
				if (!out.contains("【") || looksLikeEcho(out)) {
					throw new IllegalStateException("模型输出不合格式或为模板回声，拒绝注入");
				}
				// 关键标识由**规则扫描**兜底拼接（模型漏抄也不丢编号/日期/时限）
				List<String> ids = scanIdentifiers(c.text());
				log.info("[doc-batch] 块{}/{} 规则扫描标识数={} 明细={}", c.index() + 1, total, ids.size(),
						ids.isEmpty() ? "无" : String.join(",", ids.subList(0, Math.min(12, ids.size()))));
				String scanned = ids.isEmpty() ? "无" : String.join("；", ids);
				return out.trim() + "\n【标识·规则扫描】" + scanned;
			}
			catch (Exception e) {
				log.warn("[doc-batch] 单批失败 index={}, attempt={}/{}, error={}", c.index(), i, attempts, e.toString());
			}
		}
		// 模型不守格式/失败 ⇒ **确定性兜底**（不判失败、不丢内容）：原文前 400 字 + 规则扫描标识
		fallback[0]++;
		log.warn("[doc-batch] 块{}/{} 模型输出不可用，改用确定性兜底（原文摘录+规则扫描）", c.index() + 1, total);
		String head = c.text().replace("\n", " ").trim();
		if (head.length() > 400) {
			head = head.substring(0, 400) + "…";
		}
		List<String> ids = scanIdentifiers(c.text());
		return "【要点】原文摘录：" + head + "\n【标识】" + (ids.isEmpty() ? "无" : String.join("；", ids))
				+ "\n【摘录】" + head;
	}

	static String buildMapPrompt(Chunk c, int total) {
		String instruction = """
				【任务】只做信息抽取，不做任何评价、分析、建议、纠错或格式意见。
				【输出格式】严格三行，行首标签不可改：
				【要点】<不超过 120 字>
				【标识】<把本块出现的编号/代码/文号/日期/时限/金额/比例/责任人/部门，逐条原样抄录，用「；」分隔；没有则写 无>
				【摘录】<3~5 条原文逐字摘录，每条不超过 40 字，用「；」分隔>
				【硬约束】编号类短标识必须出现在【标识】里；不得输出除这三行以外的任何文字。
				""";
		return instruction + "\n--- 第 %d/%d 块原文开始 ---\n".formatted(c.index() + 1, total)
				+ c.text()
				+ "\n--- 第 %d/%d 块原文结束 ---\n".formatted(c.index() + 1, total)
				+ "【重复一遍】只输出三行：【要点】… / 【标识】… / 【摘录】…。禁止评价或建议。";
	}


	private static int pct(int a, int b) {
		return b == 0 ? 0 : (int) Math.round(a * 100.0 / b);
	}

}
