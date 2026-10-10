package com.phoenix.platform.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.platform.dto.front.KbaseIdsDTO;
import com.phoenix.platform.service.front.AgentKbaseService;
import com.phoenix.platform.vo.BindableKbaseVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 智能体↔知识库绑定端点（R-08/R-11/R-14；智能体抽屉「知识库绑定」多选的数据源）。
 * 候选含 bound/selectable/disabledReason；提交绑定服务端复核组交集，越权项整单拒绝。
 */
@RestController
@RequestMapping("/platform/agent-kbase")
@RequiredArgsConstructor
public class AgentKbaseController {

    private final AgentKbaseService agentKbaseService;

    /** R-18（CR-01/T-26）：超管判定复用 R-08/R-17 守卫 */
    private final com.phoenix.data.component.AdminRoleGuard adminRoleGuard;

    @GetMapping("/agent/{agentId}/bindable")
    public ReturnVo<List<BindableKbaseVO>> bindable(@PathVariable Long agentId) {
        // R-18（CR-01/T-26）：可见集合按当前登录用户；超管看全部
        String me = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        boolean superAdmin = adminRoleGuard.isAdmin(me);
        return ReturnVo.ok(agentKbaseService.bindable(agentId, me, superAdmin));
    }

    @PutMapping("/agent/{agentId}/bind")
    public ReturnVo<Boolean> bind(@PathVariable Long agentId, @RequestBody KbaseIdsDTO dto) {
        try {
            agentKbaseService.bind(agentId, dto.getKbaseIds(), StpUtil.getLoginIdAsString());
            return ReturnVo.ok(Boolean.TRUE);
        }
        catch (IllegalArgumentException e) {
            return ReturnVo.fail(e.getMessage());
        }
    }
}
