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

    /** 当前账号在某智能体下可见可用的技能（前台技能区数据源） */
    @GetMapping("/account-info/getMySkills")
    public ReturnVo<List<SkillListVO>> getMySkills(@RequestParam Long agentId) {
        String accountId = StpUtil.getLoginIdAsString();
        return frontSkillAccessService.mySkills(accountId, agentId);
    }

    /** 前台 harness 对话（SSE） */
    @PostMapping(value = "/harness/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> chat(@RequestBody HarnessRequest body) {
        String accountId = StpUtil.getLoginIdAsString();
        Long agentId = body.getAgentId();
        // 1) 智能体对本账号可见（组-智能体授权）——只判可见性，不要求 sn：
        //    库配置驱动的对话智能体 sn 为空，寻址一律用 agentId（R-08）
        ReturnVo<Boolean> visible = frontSkillAccessService.validateVisible(accountId, agentId);
        if (visible.getData() == null) {
            Map<String, Object> err = new java.util.LinkedHashMap<>();
            err.put("content", visible.getMsg());
            err.put("end", true);
            return Flux.just(err);
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
            return Flux.just(err);
        }
        // 3) 组装请求：前台身份 + 技能范围约束提示（自主模式缝隙缓解）
        //    R-08：统一以 agentId 寻址（内部走运行时注册表）；harnessSn 仅作存量兼容兜底字段
        HarnessRequest request = HarnessRequest.builder()
            .userId(accountId)
            .sessionId(body.getSessionId())
            .message(body.getMessage())
            .agentId(agentId)
            .harnessSn(agentSn)
            .enabledSkillIds(body.getEnabledSkillIds())
            .skillScopeHint(frontSkillAccessService.buildScopeHint(accountId, agentId))
            .build();
        return harnessChatService.stream(request).map(HarnessEventMapper::toEventMap);
    }
}
