package com.phoenix.agent.service.profile;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 智能体配置生成的话术与 Markdown 骨架（**段落集合的唯一来源**，plan 决策7）。
 *
 * <p>骨架端点返回的模板与 meta-prompt 里"必含段落"的要求都由 {@link #SECTIONS} 生成，
 * 避免前端一份、后端另一份漂移导致"手工有骨架、生成没骨架"。
 */
public final class AgentProfilePromptTemplates {

    /** 提示词必含的骨架段落（R-06） */
    public static final List<String> SECTIONS = List.of("角色", "描述", "能力", "安全范围");

    /** 描述长度上限（R-06/A-05：一句话） */
    public static final int DESCRIPTION_MAX_CHARS = 120;

    /** 提示词长度区间（A-05） */
    public static final int PROMPT_MIN_CHARS = 300;

    public static final int PROMPT_MAX_CHARS = 600;

    /** 名称入参上限（与库表 varchar 及界面输入一致） */
    public static final int NAME_MAX_LENGTH = 64;

    private static final String[] SECTION_HINTS = {
        "用一两句话说明它是谁、服务谁、在什么场景下工作",
        "说明它面向的使用者与典型任务场景（谁来问、问什么）",
        "分条列出它能做的事，并标注依赖的工具/知识库/数据源；不夸饰未具备的能力",
        "明确不得做的事：不编造数据、不外传用户信息、超出能力范围时的兜底回答方式",
    };

    private AgentProfilePromptTemplates() {
    }

    /**
     * Markdown 骨架（R-08）：每个段落都带实际引导语，不留空段、不写 TODO。
     */
    public static String skeleton() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < SECTIONS.size(); i++) {
            sb.append("## ").append(SECTIONS.get(i)).append('\n')
                .append(SECTION_HINTS[i]).append("\n\n");
        }
        return sb.toString().trim();
    }

    /** 段落清单文本（供 meta-prompt 拼接） */
    public static String sectionList() {
        return SECTIONS.stream().map(s -> "## " + s).collect(Collectors.joining("、"));
    }

    /**
     * 生成提示词的 meta-prompt。
     *
     * @param name        智能体名称（必填）
     * @param withDescription 是否同时生成描述
     * @param currentDescription 已填描述（可空，作为上下文）
     * @param currentPrompt      已填提示词（可空，作为上下文/润色依据）
     */
    public static String metaPrompt(String name, boolean withDescription, boolean withPrompt,
            String currentDescription, String currentPrompt) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是智能体配置助手。请根据智能体名称，产出可直接使用的配置内容。\n\n");
        sb.append("【智能体名称】").append(name).append('\n');
        if (hasText(currentDescription)) {
            sb.append("【已填写的描述（可参考或改进）】").append(currentDescription.trim()).append('\n');
        }
        if (hasText(currentPrompt)) {
            sb.append("【已填写的提示词（可参考或改进）】\n").append(currentPrompt.trim()).append('\n');
        }
        sb.append('\n');
        int item = 1;
        if (withDescription) {
            sb.append(item++).append(") 描述：中文纯文本，不超过 ").append(DESCRIPTION_MAX_CHARS)
                .append(" 字，一句话说明这个智能体做什么、给谁用；")
                .append("不要 Markdown 标记、不要引号包裹、不要复述名称。\n");
        }
        sb.append(item > 1 ? "2) " : "")
            .append("提示词：必须是 **Markdown 语法文本**，中文，长度 ")
            .append(PROMPT_MIN_CHARS).append('~').append(PROMPT_MAX_CHARS).append(" 字，")
            .append("并必须包含以下四个二级标题段落（顺序可调，每段都要有实际内容，禁止留空段或 TODO/占位符）：")
            .append(sectionList()).append("。");
        sb.append(" 可在其后追加与名称语义相关的段落（如 ## 输出要求、## 工具使用）。");
        sb.append(" 段落内用无序列表或有序列表表达要点；不要输出代码块包裹整篇内容，不要解释你在做什么。\n\n");
        sb.append(outputContract(withDescription, withPrompt));
        return sb.toString();
    }

    /**
     * 输出协议按目标项决定：两项才要 JSON；单项时只要那一项的正文。
     *
     * <p>曾经的实测缺陷：只生成「描述」时仍要求模型输出 {"description":...,"prompt":...}，
     * 于是整段 JSON 被原样塞进描述字段（用户在界面上看到的就是 JSON）。
     */
    public static String outputContract(boolean withDescription, boolean withPrompt) {
        if (withDescription && withPrompt) {
            return "【输出格式】只输出一个 JSON 对象，不要任何额外文字与 Markdown 代码块标记：\n"
                + "{\"description\":\"...\",\"prompt\":\"...\"}\n"
                + "其中 prompt 字段内的换行用 \\n 表示，Markdown 标题写作 ## 段落名。\n";
        }
        if (withPrompt) {
            return "【输出格式】只输出提示词正文本身（Markdown），不要 JSON、不要代码块包裹、不要任何解释。\n";
        }
        return "【输出格式】只输出描述这一句话的纯文本本身，不要 JSON、不要引号、不要 Markdown 标记、不要换行。\n";
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
