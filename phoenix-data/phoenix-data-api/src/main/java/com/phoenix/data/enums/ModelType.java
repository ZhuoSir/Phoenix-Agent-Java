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
	CHAT("CHAT"),

	/**
	 * 嵌入模型
	 */
	EMBEDDING("EMBEDDING"),

	/**
	 * 多模态大模型（图片 + 文本理解；chat-attachment-understanding R-06 / CR-01 裁定模型 qwen3.8-max）。
	 * 与 CHAT 分离，避免纯文本对话也走多模态模型（成本/延迟）。
	 */
	MULTIMODAL("MULTIMODAL"),

	AUDIO("AUDIO");

	private final String code;

	/**
	 * 构造模型类型枚举
	 *
	 * @param code 类型编码
	 */
	ModelType(String code) {
		this.code = code;
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
