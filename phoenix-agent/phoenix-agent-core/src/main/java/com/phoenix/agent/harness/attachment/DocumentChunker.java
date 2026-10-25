package com.phoenix.agent.harness.attachment;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档语义切片器（long-doc-chunking T-03 / R-01）。
 *
 * <p>目标：把超长文本按**语义边界**切成大小可控的块，供分批（map-reduce）使用。
 * <ul>
 *   <li>优先在标题行（第X章 / 一、/ 1. / #）与段落空行处切；</li>
 *   <li>单段过长时退到句末标点（。！？；.!?;）切，绝不从句子中间硬切（除极端无标点长串的兜底）；</li>
 *   <li>块间保留 {@code overlap} 字符重叠，避免跨块语义被切断；</li>
 *   <li>输出带 index 与原文偏移，便于日志与追溯。</li>
 * </ul>
 *
 * <p>纯函数、无状态，便于单测与复用。
 */
public final class DocumentChunker {

	/** 句末标点（中英） */
	private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？；!?;])|(?<=\\.\\s)");

	/** 标题行：第X章/节/条、一、二、…、（一）、1. / 1.1 、# 开头 */
	private static final Pattern HEADING = Pattern
		.compile("^\\s*(第[0-9一二三四五六七八九十百零]+[章节条]|[一二三四五六七八九十]+、|（[一二三四五六七八九十]+）|#+|\\d+(\\.\\d+)*[.、]?)");

	public record Chunk(int index, String text, int startOffset, int endOffset) {
	}

	private DocumentChunker() {
	}

	/**
	 * 切片。
	 * @param text 全文
	 * @param chunkSize 每块最大字符数（>0）
	 * @param overlap 块间重叠字符数（>=0，且 < chunkSize）
	 */
	public static List<Chunk> chunk(String text, int chunkSize, int overlap) {
		List<Chunk> out = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return out;
		}
		if (chunkSize <= 0) {
			throw new IllegalArgumentException("chunkSize 必须 > 0");
		}
		int ov = Math.max(0, Math.min(overlap, chunkSize / 2));

		String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
		// 1) 先按"段落 + 标题"切成最小语义单元
		List<String> units = splitUnits(normalized, chunkSize);
		// 2) 贪心装箱
		StringBuilder cur = new StringBuilder();
		int curStart = 0;
		int cursor = 0;
		int index = 0;
		for (String unit : units) {
			int unitStart = normalized.indexOf(unit, cursor);
			if (unitStart < 0) {
				unitStart = cursor;
			}
			if (cur.length() > 0 && cur.length() + unit.length() > chunkSize) {
				// 收口当前块
				out.add(new Chunk(index++, cur.toString(), curStart, cursor));
				// 生成重叠尾巴作为下一块开头；若尾巴 + 本单元会超限，则丢弃尾巴（保证块 ≤ chunkSize）
				String tail = tailOf(cur.toString(), ov);
				if (tail.length() + unit.length() > chunkSize) {
					tail = "";
				}
				cur.setLength(0);
				cur.append(tail);
				curStart = Math.max(0, cursor - tail.length());
			}
			cur.append(unit);
			cursor = unitStart + unit.length();
		}
		if (cur.length() > 0) {
			String tailText = cur.toString();
			while (tailText.length() > chunkSize) {
				out.add(new Chunk(index++, tailText.substring(0, chunkSize), curStart, normalized.length()));
				tailText = tailText.substring(chunkSize);
				curStart = Math.max(0, normalized.length() - tailText.length());
			}
			out.add(new Chunk(index, tailText, curStart, normalized.length()));
		}
		return out;
	}

	/** 切成最小语义单元：段落优先级最高；超长段落再按句子切；仍超长则按 chunkSize 兜底切 */
	private static List<String> splitUnits(String text, int chunkSize) {
		List<String> units = new ArrayList<>();
		int i = 0;
		int len = text.length();
		while (i < len) {
			int nl = text.indexOf("\n\n", i);
			int end = (nl < 0) ? len : nl + 2;
			String para = text.substring(i, end);
			units.addAll(splitOversized(para, chunkSize));
			i = end;
		}
		return units;
	}

	private static List<String> splitOversized(String para, int chunkSize) {
		List<String> out = new ArrayList<>();
		if (para.length() <= chunkSize) {
			out.add(para);
			return out;
		}
		// 按句子切
		List<String> sentences = new ArrayList<>();
		Matcher m = SENTENCE_END.matcher(para);
		int last = 0;
		while (m.find()) {
			if (m.end() > last) {
				sentences.add(para.substring(last, m.end()));
				last = m.end();
			}
		}
		if (last < para.length()) {
			sentences.add(para.substring(last));
		}
		StringBuilder sb = new StringBuilder();
		for (String s : sentences) {
			if (s.length() > chunkSize) {
				// 极端：单句超长（无标点长串）⇒ 硬切兜底
				if (sb.length() > 0) {
					out.add(sb.toString());
					sb.setLength(0);
				}
				for (int p = 0; p < s.length(); p += chunkSize) {
					out.add(s.substring(p, Math.min(s.length(), p + chunkSize)));
				}
				continue;
			}
			if (sb.length() + s.length() > chunkSize && sb.length() > 0) {
				out.add(sb.toString());
				sb.setLength(0);
			}
			sb.append(s);
		}
		if (sb.length() > 0) {
			out.add(sb.toString());
		}
		return out;
	}

	private static String tailOf(String s, int n) {
		if (n <= 0 || s.isEmpty()) {
			return "";
		}
		return s.length() <= n ? s : s.substring(s.length() - n);
	}

}
