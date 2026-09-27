package com.phoenix.agent.service.profile;

import com.phoenix.agent.dto.AgentProfileGenerateDTO;
import com.phoenix.agent.enums.ProfileFieldEnm;
import com.phoenix.agent.enums.ProfileGenerationErrorCodeEnm;
import com.phoenix.agent.vo.AgentProfileGenerateVO;
import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.service.aimodelconfig.AiModelRegistry;
import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 智能体配置 AI 生成（T-08，R-01/R-03/R-04/R-06/R-18）。
 *
 * <p>要点：
 * <ul>
 *   <li>模型固定取 **CHAT 默认**（严格：无默认即 42011），与"某智能体运行配置选的模型"无关（A-08）；</li>
 *   <li>两项一起生成 = 一次调用、JSON 协议；单项生成 = 一次调用、纯文本（R-03）；</li>
 *   <li>提示词必须含骨架四段，缺段即判失败（R-06），原文只进日志不进响应（不泄漏半截内容）；</li>
 *   <li>任何异常都转成可展示错误码，不抛裸异常（R-07/R-18）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentProfileGenerationService {

    private final ModelConfigDataService modelConfigDataService;

    private final AiModelRegistry aiModelRegistry;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReturnVo<AgentProfileGenerateVO> generate(AgentProfileGenerateDTO dto) {
        AgentProfileGenerateDTO req = dto == null ? new AgentProfileGenerateDTO() : dto;
        String name = req.getName() == null ? "" : req.getName().trim();
        if (name.isEmpty() || name.length() > AgentProfilePromptTemplates.NAME_MAX_LENGTH) {
            return fail(ProfileGenerationErrorCodeEnm.NAME_INVALID);
        }
        List<ProfileFieldEnm> targets = req.getTargets() == null ? List.of()
            : req.getTargets().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (targets.isEmpty()) {
            return fail(ProfileGenerationErrorCodeEnm.TARGETS_INVALID);
        }
        boolean withDescription = targets.contains(ProfileFieldEnm.DESCRIPTION);
        boolean withPrompt = targets.contains(ProfileFieldEnm.PROMPT);

        ModelConfigDTO defaultChat = modelConfigDataService.findDefaultConfigByType(ModelType.CHAT);
        if (defaultChat == null) {
            log.warn("AI 生成被拒：CHAT 类型未设置默认模型, name={}", name);
            return fail(ProfileGenerationErrorCodeEnm.NO_DEFAULT_CHAT_MODEL);
        }

        String metaPrompt = AgentProfilePromptTemplates.metaPrompt(name, withDescription, withPrompt,
            req.getDescription(), req.getPrompt());
        String raw;
        try {
            raw = aiModelRegistry.getChatClient().prompt().user(metaPrompt).call().content();
        }
        catch (RuntimeException e) {
            log.error("AI 生成调用模型失败: configId={}, modelName={}, err={}", defaultChat.getId(),
                defaultChat.getModelName(), e.toString());
            return fail(ProfileGenerationErrorCodeEnm.MODEL_CALL_FAILED);
        }
        if (raw == null || raw.isBlank()) {
            log.error("AI 生成返回空内容: configId={}, modelName={}", defaultChat.getId(), defaultChat.getModelName());
            return fail(ProfileGenerationErrorCodeEnm.RESULT_PARSE_FAILED);
        }

        AgentProfileGenerateVO vo = new AgentProfileGenerateVO();
        vo.setModelConfigId(defaultChat.getId());
        vo.setModelName(defaultChat.getModelName());
        if (withDescription && withPrompt) {
            ReturnVo<AgentProfileGenerateVO> parsed = parseJsonPair(raw, vo);
            if (parsed != null) {
                return parsed;
            }
        }
        else if (withPrompt) {
            String prompt = stripFence(raw);
            String missing = missingSections(prompt);
            if (missing != null) {
                log.error("AI 生成的提示词缺少骨架段落: {}, 原文长度={}, configId={}", missing, prompt.length(),
                    defaultChat.getId());
                return fail(ProfileGenerationErrorCodeEnm.RESULT_PARSE_FAILED);
            }
            vo.setPrompt(prompt);
        }
        else {
            // 单项描述：容忍模型仍然回 JSON 的情况（取 description 字段，不把整段塞进输入框）
            String desc = stripFence(raw);
            String obj = extractJsonObject(desc);
            if (obj != null) {
                try {
                    String fromJson = objectMapper.readTree(obj).path("description").asString("");
                    if (!fromJson.isBlank()) {
                        desc = fromJson;
                    }
                }
                catch (RuntimeException ignore) {
                    // 不是合法 JSON 就按原文处理
                }
            }
            vo.setDescription(oneLine(desc));
        }
        log.info("AI 生成完成: name={}, targets={}, configId={}, modelName={}, 描述字数={}, 提示词字数={}", name, targets,
            defaultChat.getId(), defaultChat.getModelName(),
            vo.getDescription() == null ? 0 : vo.getDescription().length(),
            vo.getPrompt() == null ? 0 : vo.getPrompt().length());
        return ReturnVo.ok(vo);
    }

    /** 两项一次出：解析 JSON；返回 null 表示成功（结果已写入 vo） */
    private ReturnVo<AgentProfileGenerateVO> parseJsonPair(String raw, AgentProfileGenerateVO vo) {
        String json = extractJsonObject(raw);
        if (json == null) {
            log.error("AI 生成结果未找到 JSON 对象, 原文前 200 字: {}", abbrev(raw));
            return fail(ProfileGenerationErrorCodeEnm.RESULT_PARSE_FAILED);
        }
        String description;
        String prompt;
        try {
            JsonNode node = objectMapper.readTree(json);
            description = node.path("description").asString("");
            prompt = node.path("prompt").asString("");
        }
        catch (RuntimeException e) {
            log.error("AI 生成结果 JSON 解析失败: {}, 原文前 200 字: {}", e.toString(), abbrev(raw));
            return fail(ProfileGenerationErrorCodeEnm.RESULT_PARSE_FAILED);
        }
        if (description == null || description.isBlank() || prompt == null || prompt.isBlank()) {
            log.error("AI 生成结果 JSON 缺字段: description空={}, prompt空={}",
                description == null || description.isBlank(), prompt == null || prompt.isBlank());
            return fail(ProfileGenerationErrorCodeEnm.RESULT_PARSE_FAILED);
        }
        String missing = missingSections(prompt);
        if (missing != null) {
            log.error("AI 生成的提示词缺少骨架段落: {}", missing);
            return fail(ProfileGenerationErrorCodeEnm.RESULT_PARSE_FAILED);
        }
        vo.setDescription(oneLine(description));
        vo.setPrompt(prompt.trim());
        return null;
    }

    /** 必含段落校验（R-06）：缺失返回可读说明，全部齐备返回 null */
    private String missingSections(String prompt) {
        List<String> missing = AgentProfilePromptTemplates.SECTIONS.stream()
            .filter(s -> !prompt.contains("## " + s))
            .toList();
        return missing.isEmpty() ? null : ("缺少段落 " + String.join("、", missing));
    }

    /** 取第一个平衡的 {...}（模型常在 JSON 前后夹带说明文字） */
    private String extractJsonObject(String text) {
        String body = stripFence(text);
        int start = body.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < body.length(); i++) {
            char c = body.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                }
                else if (c == '\\') {
                    escaped = true;
                }
                else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            }
            else if (c == '{') {
                depth++;
            }
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return body.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    /** 剥掉 ```/```json 围栏 */
    private String stripFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstBreak = t.indexOf('\n');
            if (firstBreak > 0) {
                t = t.substring(firstBreak + 1);
            }
            if (t.trim().endsWith("```")) {
                t = t.trim().substring(0, t.trim().length() - 3);
            }
        }
        return t.trim();
    }

    /** 描述压成一行纯文本（去掉 markdown 标记与换行），超长不截断只告警 */
    private String oneLine(String text) {
        String flat = text.replaceAll("[#*`>_]+", "").replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
        if (flat.length() > AgentProfilePromptTemplates.DESCRIPTION_MAX_CHARS) {
            log.warn("AI 生成描述超过 {} 字（实际 {} 字），由模型未遵守约束导致，原样返回",
                AgentProfilePromptTemplates.DESCRIPTION_MAX_CHARS, flat.length());
        }
        return flat;
    }

    private String abbrev(String text) {
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }

    private ReturnVo<AgentProfileGenerateVO> fail(ProfileGenerationErrorCodeEnm code) {
        return ReturnVo.fail(code.getMsg(), code.getCode());
    }
}
