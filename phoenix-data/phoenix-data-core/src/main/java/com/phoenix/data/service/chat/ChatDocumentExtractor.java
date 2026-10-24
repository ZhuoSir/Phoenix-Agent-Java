package com.phoenix.data.service.chat;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.exception.WriteLimitReachedException;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

/**
 * 对话附件文档文本抽取（chat-attachment-understanding T-04）。
 *
 * <p>复用既有依赖 {@code spring-ai-tika-document-reader}（不引新库，plan 决策5）。
 *
 * <p>**刻意不用 {@code Tika.parseToString}**：它会吞掉 {@code TikaException} 并返回空串，
 * 那样"加密文档"会被误判成"扫描件无文本层"（R-09 要求原因类别**明确**）。
 * 这里直接用 {@link AutoDetectParser} + 带写上限的 {@link BodyContentHandler}，
 * 使三类失败可区分：加密/受保护、损坏不可解析、无文本层；写上限触达即"截断"。
 *
 * <p>R-08：超长**顺序截断保留开头**并回传 truncated=true，由调用方在回答中显式告知（不得静默）。
 * <p>扫描件**不自动转视觉模型**（Q6 裁定）。
 */
@Slf4j
@Component
public class ChatDocumentExtractor {

	/**
	 * 抽取字符上限（R-08 顺序截断阈值）。24000 字符在中文场景约 1.2~2.4 万 token，
	 * 给提示词与回答留余量；超限即截断并告知。
	 */
	/** 默认值（仅作配置默认，运行时以配置项为准；BL-72/T-01） */
	public static final int DEFAULT_MAX_EXTRACT_CHARS = 24000;

	/** 触发分批的字符阈值（原"截断阈值"，语义见 plan §六） */
	@org.springframework.beans.factory.annotation.Value("${phoenix.agent.chat.attachment-max-extract-chars:24000}")
	private int maxExtractChars = DEFAULT_MAX_EXTRACT_CHARS;

	/** 抽取安全上限（防 OOM），超过才真截断 */
	@org.springframework.beans.factory.annotation.Value("${phoenix.agent.chat.attachment-safe-max-chars:2000000}")
	private int safeMaxChars = 2_000_000;

	/** 每批字符数 */
	@org.springframework.beans.factory.annotation.Value("${phoenix.agent.chat.attachment-chunk-size:6000}")
	private int chunkSize = 6000;

	/** 块间重叠字符 */
	@org.springframework.beans.factory.annotation.Value("${phoenix.agent.chat.attachment-chunk-overlap:300}")
	private int chunkOverlap = 300;

	/** 最大批数（护栏） */
	@org.springframework.beans.factory.annotation.Value("${phoenix.agent.chat.attachment-max-chunks:20}")
	private int maxChunks = 20;

	/** 单批模型调用超时（秒） */
	@org.springframework.beans.factory.annotation.Value("${phoenix.agent.chat.attachment-chunk-timeout-seconds:60}")
	private int chunkTimeoutSeconds = 60;

	@jakarta.annotation.PostConstruct
	void logEffectiveConfig() {
		log.info("[attachment-batch] 生效配置: maxExtractChars={}, safeMaxChars={}, chunkSize={}, chunkOverlap={}, maxChunks={}, chunkTimeoutSeconds={}",
				maxExtractChars, safeMaxChars, chunkSize, chunkOverlap, maxChunks, chunkTimeoutSeconds);
	}

	public int getMaxExtractChars() {
		return maxExtractChars;
	}

	public int getSafeMaxChars() {
		return safeMaxChars;
	}

	public int getChunkSize() {
		return chunkSize;
	}

	public int getChunkOverlap() {
		return chunkOverlap;
	}

	public int getMaxChunks() {
		return maxChunks;
	}

	public int getChunkTimeoutSeconds() {
		return chunkTimeoutSeconds;
	}

	private final Parser parser = new AutoDetectParser();

	/**
	 * 抽取文档文本。
	 * @param content 文件字节
	 * @param mime 已判定的真实内容类型（仅用于日志与 metadata 提示）
	 * @param fileName 原始文件名（仅用于日志）
	 */
	public ExtractResult extract(byte[] content, String mime, String fileName) {
		// 多要 1 字符用于区分"刚好等于上限"与"被上限截断"
		BodyContentHandler handler = new BodyContentHandler(maxExtractChars + 1);
		Metadata metadata = new Metadata();
		metadata.set(Metadata.CONTENT_TYPE, mime);
		if (fileName != null) {
			metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
		}
		boolean truncated = false;
		try (InputStream in = new ByteArrayInputStream(content)) {
			parser.parse(in, handler, metadata, new ParseContext());
		}
		catch (EncryptedDocumentException e) {
			log.warn("文档受保护/加密: name={}", fileName);
			return ExtractResult.fail("文档受保护或已加密，无法解析（请去除密码后重新上传）");
		}
		catch (WriteLimitReachedException e) {
			// R-08：写上限触达 = 内容超长 ⇒ 顺序截断（保留开头），并告知
			truncated = true;
		}
		catch (TikaException | SAXException | java.io.IOException e) {
			log.warn("文档解析失败: name={}, reason={}", fileName, e.getMessage());
			return ExtractResult.fail("文档无法解析（文件可能已损坏或格式不完整）");
		}
		String text = handler.toString();
		if (text == null || text.isBlank()) {
			log.warn("文档无可提取文本（疑似扫描件/图片型）: name={}, mime={}", fileName, mime);
			return ExtractResult.fail("文档无可提取的文本层（疑似扫描件/图片型 PDF）；本期不自动转图片理解");
		}
		String cleaned = text.replace("\u0000", "").trim();
		if (cleaned.length() > maxExtractChars) {
			truncated = true;
			cleaned = cleaned.substring(0, maxExtractChars);
		}
		return ExtractResult.ok(cleaned, truncated);
	}

	/** 抽取结果：ok / text / chars / truncated / failureReason */
	public static class ExtractResult {

		private final boolean ok;

		private final String text;

		private final boolean truncated;

		private final String failureReason;

		private ExtractResult(boolean ok, String text, boolean truncated, String failureReason) {
			this.ok = ok;
			this.text = text;
			this.truncated = truncated;
			this.failureReason = failureReason;
		}

		static ExtractResult ok(String text, boolean truncated) {
			return new ExtractResult(true, text, truncated, null);
		}

		static ExtractResult fail(String reason) {
			return new ExtractResult(false, null, false, reason);
		}

		public boolean isOk() {
			return this.ok;
		}

		public String getText() {
			return this.text;
		}

		public int getChars() {
			return this.text == null ? 0 : this.text.length();
		}

		public boolean isTruncated() {
			return this.truncated;
		}

		public String getFailureReason() {
			return this.failureReason;
		}

	}

}
