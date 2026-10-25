package com.phoenix.agent.harness.formatter;

import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.extensions.model.ollama.dto.OllamaMessage;
import io.agentscope.extensions.model.ollama.formatter.OllamaChatFormatter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * chat-runtime-hardening T-11（BUG-201）——ollama 表单器的 ThinkingBlock 兼容层。
 *
 * <p><b>背景</b>：框架自带的 {@link OllamaChatFormatter} 在格式化历史消息时，对 {@link ThinkingBlock}
 * 直接跳过并打 WARN：{@code Unsupported block type ThinkingBlock in the message, skipped}
 * ⇒ 思考内容被**静默丢弃**（客户现场图1 即此告警），且下一轮模型看不到自己此前的推理。
 *
 * <p><b>做法</b>：在真正格式化前，把 {@link ThinkingBlock} **降级为 {@link TextBlock}**（保留全部内容，
 * 加可辨识前缀），既不告警也不丢信息；不改动其它块、不改动消息角色与元数据。
 *
 * <p><b>局限（如实记录）</b>：这是"降级为文本"的折中，并非让 ollama 原生理解 thinking 块；
 * 若上游框架后续支持 thinking，可去掉本层（届时 T-11 应改为直接用原生能力）。
 */
@Slf4j
public class ThinkingPreservingOllamaChatFormatter extends OllamaChatFormatter {

	/** 降级后的可辨识前缀（便于模型/人区分"这是上一轮推理摘要"） */
	private static final String THINKING_PREFIX = "[上一轮思考] ";

	@Override
	protected List<OllamaMessage> doFormat(List<Msg> msgs) {
		return super.doFormat(hoistThinking(msgs));
	}

	/** 把每条消息里的 ThinkingBlock 换成等价的 TextBlock（保留顺序与其它块） */
	private List<Msg> hoistThinking(List<Msg> msgs) {
		if (msgs == null || msgs.isEmpty()) {
			return msgs;
		}
		List<Msg> out = new ArrayList<>(msgs.size());
		boolean changed = false;
		for (Msg m : msgs) {
			List<ContentBlock> blocks = (m == null) ? null : m.getContent();
			if (blocks == null || blocks.isEmpty()) {
				out.add(m);
				continue;
			}
			List<ContentBlock> converted = new ArrayList<>(blocks.size());
			boolean hit = false;
			for (ContentBlock b : blocks) {
				if (b instanceof ThinkingBlock tb) {
					String t = tb.getThinking();
					converted.add(TextBlock.builder().text(THINKING_PREFIX + (t == null ? "" : t)).build());
					hit = true;
				}
				else {
					converted.add(b);
				}
			}
			if (!hit) {
				out.add(m);
				continue;
			}
			changed = true;
			out.add(Msg.builder()
				.id(m.getId())
				.name(m.getName())
				.role(m.getRole())
				.content(converted)
				.metadata(m.getMetadata())
				.timestamp(m.getTimestamp())
				.usage(m.getUsage())
				.build());
		}
		if (changed) {
			log.debug("[thinking-hoist] 历史消息中的 ThinkingBlock 已降级为文本（不丢弃、不再告警）");
		}
		return out;
	}

}
