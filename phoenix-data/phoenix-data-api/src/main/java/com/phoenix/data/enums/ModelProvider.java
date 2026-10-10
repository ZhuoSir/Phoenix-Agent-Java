package com.phoenix.data.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 模型提供商枚举（R-01：Ollama 作为一等提供商）。
 *
 * <p>取值域由**服务端**收敛（L-63：类型判别列不得信任前端 DTO）：前端下拉只是 UI，
 * 未知 provider 一律拒绝，绝不静默落入默认分支。
 */
@Getter
public enum ModelProvider {

	/** OpenAI 官方（OpenAI 兼容协议） */
	OPENAI("openai", "OpenAI"),

	/** DeepSeek */
	DEEPSEEK("deepseek", "DeepSeek"),

	/** 通义千问（DashScope 兼容模式） */
	QWEN("qwen", "通义千问"),

	/** 硅基流动 */
	SILICONFLOW("siliconflow", "硅基流动"),

	/** Ollama 本地/内网（R-01/R-03；**无需 API Key**，见 {@link #requiresApiKey()}） */
	OLLAMA("ollama", "Ollama（本地/内网）"),

	/** 自定义 OpenAI 兼容端点 */
	CUSTOM("custom", "自定义");

	private final String code;

	/** 展示名（管理面文案统一用中文标签） */
	private final String label;

	ModelProvider(String code, String label) {
		this.code = code;
		this.label = label;
	}

	/**
	 * 由配置值解析提供商（容错：去空白 + 转小写）。
	 *
	 * @param code 前端/配置传入的 provider
	 * @return 命中的枚举；**未知或为空返回 null**（由调用方决定拒绝策略）
	 */
	public static ModelProvider fromCode(String code) {
		if (code == null) {
			return null;
		}
		String normalized = code.trim().toLowerCase();
		for (ModelProvider provider : values()) {
			if (provider.code.equals(normalized)) {
				return provider;
			}
		}
		return null;
	}

	/** 全部可选值（用于错误提示，避免用户猜） */
	public static String codes() {
		return Arrays.stream(values()).map(ModelProvider::getCode).collect(Collectors.joining(" / "));
	}

	/**
	 * 该提供商是否需要 API Key。
	 *
	 * <p>Ollama 本地服务默认无鉴权 ⇒ 允许留空（R-02）；其余提供商沿用既有语义，本方法**不用于强制校验**，
	 * 仅表达产品语义（避免影响存量允许空 key 的配置行）。
	 */
	public boolean requiresApiKey() {
		return this != OLLAMA;
	}

}
