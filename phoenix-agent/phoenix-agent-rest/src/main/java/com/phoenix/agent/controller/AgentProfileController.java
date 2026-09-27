package com.phoenix.agent.controller;

import com.phoenix.agent.dto.AgentProfileGenerateDTO;
import com.phoenix.agent.enums.ProfileGenerationErrorCodeEnm;
import com.phoenix.agent.service.profile.AgentProfileGenerationService;
import com.phoenix.agent.service.profile.AgentProfilePromptTemplates;
import com.phoenix.agent.vo.AgentProfileGenerateVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * 智能体配置 AI 生成（T-07 骨架端点 / T-09 生成端点，R-01~R-08）。
 *
 * <p>/api/** 由 Sa-Token 全局过滤器拦截（未登录返回未授权）。
 * 生成是"读模型、写表单"的辅助动作：**不写库**，结果由管理员确认后自行保存。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentProfileController {

    /** 生成调用响应式超时上限（模型出 md 提示词比纯文本慢，A-06） */
    private static final Duration GENERATE_TIMEOUT = Duration.ofSeconds(60);

    private final AgentProfileGenerationService agentProfileGenerationService;

    /**
     * Markdown 骨架（R-08）：与 meta-prompt 的必含段落同源，供编辑器「插入骨架」按钮使用。
     */
    @GetMapping("/generate-profile/skeleton")
    public ReturnVo<String> skeleton() {
        // 注意：ReturnVo.ok(String) 命中 msg 重载，必须用两参形式显式传 data
        return ReturnVo.ok("操作成功!", AgentProfilePromptTemplates.skeleton());
    }

    /**
     * AI 生成描述/提示词（R-01/R-03）：targets 决定生成项，未请求的字段不出现在响应里。
     */
    @PostMapping("/generate-profile")
    public Mono<ReturnVo<AgentProfileGenerateVO>> generate(@RequestBody AgentProfileGenerateDTO dto) {
        return Mono.fromCallable(() -> agentProfileGenerationService.generate(dto))
            .subscribeOn(Schedulers.boundedElastic())
            .timeout(GENERATE_TIMEOUT)
            .onErrorResume(TimeoutException.class, e -> {
                log.error("AI 生成超时（>{}s）", GENERATE_TIMEOUT.toSeconds());
                return Mono.just(ReturnVo.fail(ProfileGenerationErrorCodeEnm.MODEL_CALL_FAILED.getMsg(),
                    ProfileGenerationErrorCodeEnm.MODEL_CALL_FAILED.getCode()));
            })
            .onErrorResume(e -> {
                log.error("AI 生成异常: {}", e.toString());
                return Mono.just(ReturnVo.fail(ProfileGenerationErrorCodeEnm.MODEL_CALL_FAILED.getMsg(),
                    ProfileGenerationErrorCodeEnm.MODEL_CALL_FAILED.getCode()));
            });
    }
}
