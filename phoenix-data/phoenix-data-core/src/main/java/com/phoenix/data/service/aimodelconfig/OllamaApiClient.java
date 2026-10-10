package com.phoenix.data.service.aimodelconfig;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Ollama 原生 API 客户端（R-04 / R-05）。
 *
 * <p>为什么需要它：平台既有的模型探测走 **OpenAI 兼容协议**（{@code OpenAiApi}），
 * 而 Ollama 的兼容端点在 {@code /v1} 下、原生端点在根路径（{@code /api/tags}、{@code /api/embeddings}）。
 * provider=ollama 时若沿用兼容层会打到错误路径 —— 因此连接测试与模型列表统一走本客户端。
 *
 * <p>只做两件事：列出本机模型、探活。超时按语义给足（L-09：超时太小会制造"链路不通"假象）。
 */
@Slf4j
@Component
public class OllamaApiClient {

	/** 模型列表接口（原生） */
	private static final String PATH_TAGS = "/api/tags";

	/** 连接超时（秒） */
	private static final int CONNECT_TIMEOUT_SECONDS = 5;

	/** 读超时（秒）：本地服务应很快；给足以区分"慢"与"不通" */
	private static final int READ_TIMEOUT_SECONDS = 60;

	private final HttpClient httpClient = HttpClient.newBuilder()
		.connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
		.build();

	/**
	 * 列出 Ollama 已安装模型名（形如 {@code qwen2.5:7b}）。
	 *
	 * @param baseUrl Ollama 根地址（如 {@code http://host.docker.internal:11434}）
	 * @return 模型名列表（按服务端返回顺序）
	 * @throws OllamaApiException 不可达 / 非 2xx / 响应无法解析
	 */
	public List<String> listModels(String baseUrl) {
		String url = normalize(baseUrl) + PATH_TAGS;
		long start = System.currentTimeMillis();
		try {
			HttpResponse<String> resp = httpClient.send(
					HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS)).GET().build(),
					HttpResponse.BodyHandlers.ofString());
			long cost = System.currentTimeMillis() - start;
			if (resp.statusCode() / 100 != 2) {
				// 可诊断：目标 + 结果码 + 耗时（plan §4 外部调用日志点）
				log.warn("Ollama 模型列表返回非 2xx: url={}, status={}, cost={}ms", url, resp.statusCode(), cost);
				throw new OllamaApiException("Ollama 返回 HTTP " + resp.statusCode() + "（" + url + "）");
			}
			JsonNode root = Json.MAPPER.readTree(resp.body());
			List<String> names = new ArrayList<>();
			for (JsonNode node : root.path("models")) {
				String name = node.path("name").asText(null);
				if (name != null && !name.isEmpty()) {
					names.add(name);
				}
			}
			log.info("Ollama 模型列表获取成功: url={}, 数量={}, 耗时={}ms", url, names.size(), cost);
			return names;
		}
		catch (OllamaApiException e) {
			throw e;
		}
		catch (Exception e) {
			long cost = System.currentTimeMillis() - start;
			log.warn("Ollama 模型列表获取失败: url={}, 耗时={}ms, 原因={}", url, cost, e.toString());
			throw new OllamaApiException("无法连接 Ollama（" + url + "）: " + rootCause(e));
		}
	}

	/**
	 * 探测某个模型的**实际输出维度**（R-08：维度以模型实际输出为准，而非写死 512）。
	 *
	 * @param baseUrl Ollama 根地址
	 * @param modelName 模型名（如 {@code nomic-embed-text}）
	 * @return 向量维度
	 * @throws OllamaApiException 不可达 / 非 2xx / 响应无向量
	 */
	public int probeEmbeddingDimension(String baseUrl, String modelName) {
		String url = normalize(baseUrl) + "/api/embeddings";
		String body = "{\"model\":\"" + modelName + "\",\"prompt\":\"dimension-probe\"}";
		try {
			HttpResponse<String> resp = httpClient.send(
					HttpRequest.newBuilder(URI.create(url))
						.timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
						.header("Content-Type", "application/json")
						.POST(HttpRequest.BodyPublishers.ofString(body))
						.build(),
					HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() / 100 != 2) {
				throw new OllamaApiException("Ollama 返回 HTTP " + resp.statusCode() + "（" + url + "）");
			}
			JsonNode vec = Json.MAPPER.readTree(resp.body()).path("embedding");
			if (!vec.isArray() || vec.isEmpty()) {
				throw new OllamaApiException("Ollama 未返回向量（模型 " + modelName + " 可能不是 embedding 模型）");
			}
			int dims = vec.size();
			log.info("Ollama embedding 维度探测成功: model={}, dimensions={}", modelName, dims);
			return dims;
		}
		catch (OllamaApiException e) {
			throw e;
		}
		catch (Exception e) {
			log.warn("Ollama embedding 维度探测失败: model={}, 原因={}", modelName, e.toString());
			throw new OllamaApiException("无法探测 embedding 维度（模型 " + modelName + "）: " + rootCause(e));
		}
	}

	private static String normalize(String baseUrl) {
		String url = baseUrl == null ? "" : baseUrl.trim();
		while (url.endsWith("/")) {
			url = url.substring(0, url.length() - 1);
		}
		return url;
	}

	private static String rootCause(Throwable e) {
		Throwable cur = e;
		while (cur.getCause() != null) {
			cur = cur.getCause();
		}
		return cur.getClass().getSimpleName() + ": " + cur.getMessage();
	}

	/** Ollama 调用失败（业务可诊断异常） */
	public static class OllamaApiException extends RuntimeException {
		public OllamaApiException(String message) {
			super(message);
		}
	}

	/** 复用 JsonUtil 的 ObjectMapper，避免各处 new */
	private static final class Json {
		private static final tools.jackson.databind.ObjectMapper MAPPER = com.phoenix.data.util.JsonUtil
			.getObjectMapper();
	}

}
