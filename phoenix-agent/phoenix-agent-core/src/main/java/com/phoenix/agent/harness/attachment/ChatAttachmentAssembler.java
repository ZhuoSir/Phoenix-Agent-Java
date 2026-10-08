package com.phoenix.agent.harness.attachment;

import com.phoenix.agent.service.harness.HarnessModelRegistry;
import com.phoenix.data.component.AdminRoleGuard;
import com.phoenix.data.entity.ChatAttachment;
import com.phoenix.data.service.chat.ChatAttachmentService;
import com.phoenix.data.service.chat.ChatDocumentExtractor;
import com.phoenix.data.service.file.FileStorageService;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 对话附件装配器（chat-attachment-understanding T-06，plan 决策2 v1.3.0 **两阶段**）。
 *
 * <p>**阶段1**：图片交 MULTIMODAL 模型（qwen3.8-max）做结构化视觉理解 → 描述文本；
 * **阶段2**：把「图片描述 + 文档抽取文本 + 用户提示词」交给**正常 agent 循环**
 * （技能/工具/记忆/会话工作区全部保留 —— AgentScope 无 per-call 模型覆盖，故不在此换模型）。
 *
 * <p>R-07：MULTIMODAL 未配置/失败 ⇒ **降级但显式告知**，绝不产出"假装看过图"的内容；
 * 只发图片而无文本可答时，明确说明无法理解图片（不编造）。
 * <p>共享面 S1'：attachmentIds 为空即**短路返回**，既有调用零额外开销、行为逐字节不变。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAttachmentAssembler {

	/** 阶段1 单图字节上限（base64 会膨胀 ~33%，过大直接跳过并告知） */
	private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

	/** 结构化视觉理解提示词：要求只描述确实存在的内容，抑制编造 */
	private static final String DESCRIBE_PROMPT = """
			请对这张图片做结构化视觉理解，供后续对话使用。依次输出：
			1) 图片类型（界面截图/照片/图表/文档扫描/其他）；
			2) 版面与关键元素（位置关系、图标、按钮、表格结构等）；
			3) 图中出现的文字（原样转录，保留数字与编号）；
			4) 若为图表：坐标轴、图例、趋势与关键数值；
			5) 值得注意的细节或异常。
			只描述图片中确实存在的内容，不要推测或编造。""";

	private final ChatAttachmentService chatAttachmentService;

	private final ChatDocumentExtractor documentExtractor;

	private final FileStorageService fileStorageService;

	private final AdminRoleGuard adminRoleGuard;

	private final HarnessModelRegistry harnessModelRegistry;

	/** 装配结果：注入文本 + 用户可见告知 + 是否降级 */
	public record Context(String injectedText, List<String> notices, boolean degraded) {

		public boolean empty() {
			return (injectedText == null || injectedText.isBlank()) && notices.isEmpty();
		}

		public static Context none() {
			return new Context(null, List.of(), false);
		}

	}

	/** 单个附件的载入结果（阶段1 之前） */
	private record Loaded(ChatAttachment att, String docText, String docNotice, byte[] imageBytes) {
	}

	/**
	 * 装配附件上下文。attachmentIds 为空 ⇒ 立即返回空上下文（S1' 短路，不做任何 IO）。
	 */
	public Mono<Context> prepare(List<Long> attachmentIds, String viewerId) {
		if (attachmentIds == null || attachmentIds.isEmpty()) {
			return Mono.just(Context.none());
		}
		boolean superAdmin = viewerId != null && adminRoleGuard.isAdmin(viewerId);
		return Mono.fromCallable(() -> load(attachmentIds, viewerId, superAdmin))
			.subscribeOn(Schedulers.boundedElastic())
			.flatMap(this::describeImages);
	}

	/**
	 * 把附件材料追加到用户原文之后（react 族复用；harness 族走 buildUserMessage）。
	 * **ctx 为空/无注入文本时原样返回 content** ⇒ 不带 attachmentIds 的既有调用行为不变（S1'）。
	 */
	public String enrich(String content, Context ctx) {
		if (ctx == null || ctx.injectedText() == null || ctx.injectedText().isBlank()) {
			return content;
		}
		return (content == null ? "" : content) + "\n\n" + ctx.injectedText();
	}

	/** 同步载入 + 鉴权 + 文档抽取（弹性线程） */
	private List<Loaded> load(List<Long> ids, String viewerId, boolean superAdmin) {
		List<Loaded> out = new ArrayList<>();
		for (Long id : ids) {
			if (id == null) {
				continue;
			}
			try {
				// R-11 / L-58：逐个校验归属（非本人且非超管 → 403 由 service 抛）
				ChatAttachment att = chatAttachmentService.requireAccessible(id, viewerId, superAdmin);
				Resource res = chatAttachmentService.openResource(att);
				byte[] bytes = res.getContentAsByteArray();
				if ("IMAGE".equals(att.getKind())) {
					if (bytes.length > MAX_IMAGE_BYTES) {
						out.add(new Loaded(att, null, "图片过大（超过 " + (MAX_IMAGE_BYTES / 1024 / 1024)
								+ "MB），本次未参与图片理解", null));
					}
					else {
						out.add(new Loaded(att, null, null, bytes));
					}
				}
				else {
					// 文档：EXTRACT_FAILED 或需重新抽取（进程重启后内存无缓存）⇒ 就地抽取
					if ("EXTRACT_FAILED".equals(att.getStatus())) {
						out.add(new Loaded(att, null, "附件《" + att.getFileName() + "》无法解析，其内容未参与本次回答", null));
						continue;
					}
					ChatDocumentExtractor.ExtractResult er = documentExtractor.extract(bytes, att.getMime(), att.getFileName());
					if (!er.isOk()) {
						out.add(new Loaded(att, null,
								"附件《" + att.getFileName() + "》解析失败：" + er.getFailureReason(), null));
						continue;
					}
					String notice = er.isTruncated() ? ("附件《" + att.getFileName() + "》内容较长，已按顺序截断至前 "
							+ ChatDocumentExtractor.MAX_EXTRACT_CHARS + " 字符，超出部分未参与理解") : null;
					out.add(new Loaded(att, er.getText(), notice, null));
				}
			}
			catch (Exception e) {
				// 单个附件失败不拖垮整轮：记告知，继续装配其余附件
				log.warn("附件装配失败: id={}, reason={}", id, e.getMessage());
				out.add(new Loaded(null, null, "附件(id=" + id + ")不可用：" + e.getMessage(), null));
			}
		}
		return out;
	}

	/** 阶段1：图片 → 多模态结构化描述（无 MULTIMODAL 配置 ⇒ 降级并显式告知，R-07） */
	private Mono<Context> describeImages(List<Loaded> loaded) {
		List<Loaded> images = loaded.stream().filter(l -> l.imageBytes() != null).toList();
		if (images.isEmpty()) {
			return Mono.just(build(loaded, List.of(), false));
		}
		OpenAIChatModel model = harnessModelRegistry.getOpenAIMultimodalModel();
		if (model == null) {
			// R-07：降级但显式告知；绝不暗示"看过图"
			List<String> notices = new ArrayList<>();
			for (Loaded l : images) {
				notices.add("图片《" + l.att().getFileName() + "》未被理解（多模态模型不可用），本次仅根据文本内容作答");
			}
			log.warn("含图请求但未配置 MULTIMODAL 模型 ⇒ 按 R-07 降级并显式告知（{} 张图）", images.size());
			return Mono.just(build(loaded, notices, true));
		}
		return Flux.fromIterable(images)
			.concatMap(l -> describe(model, l).map(desc -> new String[] { l.att().getFileName(), desc })
				.onErrorResume(e -> {
					log.warn("图片理解失败，按 R-07 降级: name={}, reason={}", l.att().getFileName(), e.getMessage());
					return Mono.just(new String[] { l.att().getFileName(), null });
				}))
			.collectList()
			.map(results -> {
				List<String> notices = new ArrayList<>();
				List<String[]> ok = new ArrayList<>();
				boolean degraded = false;
				for (String[] r : results) {
					if (r[1] == null || r[1].isBlank()) {
						notices.add("图片《" + r[0] + "》未被理解（多模态调用失败），本次仅根据文本内容作答");
						degraded = true;
					}
					else {
						ok.add(r);
					}
				}
				return build(loaded, ok, notices, degraded);
			});
	}

	private Mono<String> describe(OpenAIChatModel model, Loaded l) {
		List<ContentBlock> blocks = List.of(TextBlock.builder().text(DESCRIBE_PROMPT).build(),
				ImageBlock.builder()
					.source(Base64Source.builder()
						.mediaType(l.att().getMime())
						.data(Base64.getEncoder().encodeToString(l.imageBytes()))
						.build())
					.build());
		UserMessage msg = new UserMessage(blocks);
		return model.stream(List.of(msg), List.of(), GenerateOptions.builder().build())
			.map(r -> textOf(r.getContent()))
			.reduce(new StringBuilder(), StringBuilder::append)
			.map(sb -> sb.toString().trim());
	}

	private String textOf(List<ContentBlock> blocks) {
		if (blocks == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (ContentBlock b : blocks) {
			if (b instanceof TextBlock t && t.getText() != null) {
				sb.append(t.getText());
			}
		}
		return sb.toString();
	}

	private Context build(List<Loaded> loaded, List<String> notices, boolean degraded) {
		return build(loaded, List.of(), notices, degraded);
	}

	/** 组装注入文本：文档原文 + 图片描述 + 反编造约束 */
	private Context build(List<Loaded> loaded, List<String[]> imageDescs, List<String> extraNotices, boolean degraded) {
		List<String> notices = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		int idx = 1;
		for (Loaded l : loaded) {
			if (l.docNotice() != null) {
				notices.add(l.docNotice());
			}
			if (l.docText() != null && !l.docText().isBlank()) {
				sb.append(idx++).append(") 文档《").append(l.att().getFileName()).append("》(").append(l.att().getExt())
					.append(") 内容：\n").append(l.docText()).append("\n\n");
			}
		}
		for (String[] d : imageDescs) {
			sb.append(idx++).append(") 图片《").append(d[0]).append("》的视觉理解（由多模态模型生成）：\n").append(d[1]).append("\n\n");
		}
		notices.addAll(extraNotices);
		String injected = null;
		if (sb.length() > 0) {
			injected = "[附件材料]\n" + sb.toString().trim()
					+ "\n[附件材料结束]\n请仅基于上述附件材料与用户问题作答；材料中没有的信息不要编造，如材料不足请明确说明。";
		}
		else if (!notices.isEmpty()) {
			// 全部附件都不可用：把原因作为材料注入，要求模型如实告知（R-09：不得用空内容继续生成）
			injected = "[附件状态]\n" + String.join("\n", notices)
					+ "\n[附件状态结束]\n请向用户明确说明上述附件不可用的原因，不要编造其内容。";
		}
		return new Context(injected, List.copyOf(notices), degraded);
	}

}
