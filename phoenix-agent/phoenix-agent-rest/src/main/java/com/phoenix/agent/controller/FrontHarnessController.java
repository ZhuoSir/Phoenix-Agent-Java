package com.phoenix.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.agent.controller.support.HarnessEventMapper;
import com.phoenix.agent.harness.request.HarnessRequest;
import com.phoenix.agent.harness.send.HarnessChatService;
import com.phoenix.agent.service.FrontSkillAccessService;
import com.phoenix.agent.vo.SkillListVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.phoenix.agent.harness.sse.SseSupport;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 前台（普通用户）技能与对话通道（R-09）。
 *
 * <p>与后台通道的区别：身份取当前 platform 账号，先做「组-智能体」可见性校验，
 * 再对显式勾选技能做三重交集校验，并在自主模式下注入"本轮可用技能仅…"约束提示（风险①缓解）。
 */
@Slf4j
@RestController
@RequestMapping("/platform")
@RequiredArgsConstructor
public class FrontHarnessController {

    private final FrontSkillAccessService frontSkillAccessService;

    private final HarnessChatService harnessChatService;

    private final com.phoenix.agent.harness.turn.HarnessTurnManager turnManager;

    private final com.phoenix.data.mapper.ChatSessionMapper chatSessionMapper;

    /** 当前账号在某智能体下可见可用的技能（前台技能区数据源） */
    @GetMapping("/account-info/getMySkills")
    public ReturnVo<List<SkillListVO>> getMySkills(@RequestParam Long agentId) {
        String accountId = StpUtil.getLoginIdAsString();
        return frontSkillAccessService.mySkills(accountId, agentId);
    }

    /** 前台 harness 对话（SSE） */
    @PostMapping(value = "/harness/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> chat(@RequestBody HarnessRequest body) {
        String accountId = StpUtil.getLoginIdAsString();
        Long agentId = body.getAgentId();
        // 1) 智能体对本账号可见（组-智能体授权）——只判可见性，不要求 sn：
        //    库配置驱动的对话智能体 sn 为空，寻址一律用 agentId（R-08）
        ReturnVo<Boolean> visible = frontSkillAccessService.validateVisible(accountId, agentId);
        if (visible.getData() == null) {
            Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("content", visible.getMsg());
            err.put("end", true);
            return Flux.just(org.springframework.http.codec.ServerSentEvent.builder(err).build());
        }
        // 存量自注册智能体才有 sn，仅作兼容字段
        String agentSn = frontSkillAccessService.resolveVisibleAgentSn(accountId, agentId).getData();
        // 2) 显式勾选技能三重交集校验
        ReturnVo<Boolean> access = frontSkillAccessService
            .validateExplicitSkills(accountId, agentId, body.getEnabledSkillIds());
        if (access.getData() == null) {
            Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("content", access.getMsg());
            err.put("end", true);
            return Flux.just(org.springframework.http.codec.ServerSentEvent.builder(err).build());
        }
        // 3) 组装请求：前台身份 + 技能范围约束提示（自主模式缝隙缓解）
        //    R-08：统一以 agentId 寻址（内部走运行时注册表）；harnessSn 仅作存量兼容兜底字段
        HarnessRequest request = HarnessRequest.builder()
            .channel("front")
            .userId(accountId)
            .sessionId(body.getSessionId())
            .message(body.getMessage())
            .agentId(agentId)
            .harnessSn(agentSn)
            .enabledSkillIds(body.getEnabledSkillIds())
            .skillScopeHint(frontSkillAccessService.buildScopeHint(accountId, agentId))
            
            // T-06：转抄附件 id —— 不转抄则该入口静默丢附件（L-06 多入口枚举）
            .attachmentIds(body.getAttachmentIds()).build();
        return SseSupport.withHeartbeat(turnManager.openOrReject(body.getSessionId(),
                () -> harnessChatService.stream(request).map(HarnessEventMapper::toEventMap)));
    }

    /** 断线续传 T-03：前台追流（属主校验） */
    @GetMapping(value = "/harness/turn/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<org.springframework.http.codec.ServerSentEvent<Map<String, Object>>> turnJoin(@RequestParam String sessionId) {
        if (!ownsSession(StpUtil.getLoginIdAsString(), sessionId)) {
            Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("content", "");
            err.put("end", true);
            return Flux.just(org.springframework.http.codec.ServerSentEvent.builder(err).build());
        }
        return com.phoenix.agent.harness.sse.SseSupport.withHeartbeat(turnManager.join(sessionId));
    }

    @GetMapping("/harness/turn/status")
    public ReturnVo<Boolean> turnStatus(@RequestParam String sessionId) {
        return ReturnVo.ok(ownsSession(StpUtil.getLoginIdAsString(), sessionId) && turnManager.hasActive(sessionId));
    }

    @PostMapping("/harness/turn/cancel")
    public ReturnVo<Boolean> turnCancel(@RequestParam String sessionId) {
        boolean mine = ownsSession(StpUtil.getLoginIdAsString(), sessionId);
        return ReturnVo.ok(mine && turnManager.cancel(sessionId));
    }

    private boolean ownsSession(String accountId, String sessionId) {
        com.phoenix.data.entity.ChatSession s = chatSessionMapper.selectOneById(sessionId);
        return s != null && accountId.equals(s.getUserId());
    }
}