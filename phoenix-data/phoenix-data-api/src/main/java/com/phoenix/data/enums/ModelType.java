package com.phoenix.data.enums;

import lombok.Getter;

/**
 * 模型类型枚举
 */
@Getter
public enum ModelType {

	/**
	 * 对话模型
	 */
	CHAT("CHAT", "对话模型"),

	/**
	 * 嵌入模型
	 */
	EMBEDDING("EMBEDDING", "向量模型"),

	/**
	 * 多模态大模型（图片 + 文本理解；chat-attachment-understanding R-06 / CR-01 裁定模型 qwen3.8-max）。
	 * 与 CHAT 分离，避免纯文本对话也走多模态模型（成本/延迟）。
	 */
	MULTIMODAL("MULTIMODAL", "多模态模型"),

	AUDIO("AUDIO", "语音模型");

	private final String code;

	/**
	 * 类型的**展示名**（BUG-159：管理面文案一律用中文标签，避免把枚举英文名当报错读）
	 */
	private final String label;

	/**
	 * 构造模型类型枚举
	 *
	 * @param code 类型编码
	 * @param label 展示名（中文）
	 */
	ModelType(String code, String label) {
		this.code = code;
		this.label = label;
	}

	/**
	 * 根据代码获取枚举
	 */
	public static ModelType fromCode(String code) {
		for (ModelType type : values()) {
			// 严格比对
			if (type.getCode().equals(code)) {
				return type;
			}
		}
		throw new IllegalArgumentException("未知的模型类型代码: " + code);
	}

}
