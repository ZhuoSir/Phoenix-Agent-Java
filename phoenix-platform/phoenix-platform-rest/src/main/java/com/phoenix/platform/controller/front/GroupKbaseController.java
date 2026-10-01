package com.phoenix.platform.controller.front;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.platform.dto.front.KbaseIdsDTO;
import com.phoenix.platform.service.front.GroupKbaseInfoService;
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
 * 组×知识库授权端点（R-13；入口=组管理页「分配知识库」dialog，同构 assign-agent）。
 * 撤组不级联删既有绑定（plan T-04 最小语义）；绑定新增强制组交集校验（R-14）。
 */
@RestController
@RequestMapping("/platform/group-kbase")
@RequiredArgsConstructor
public class GroupKbaseController {

    private final GroupKbaseInfoService groupKbaseInfoService;

    @PutMapping("/{groupId}/assign")
    public ReturnVo<Boolean> assign(@PathVariable String groupId, @RequestBody KbaseIdsDTO dto) {
        groupKbaseInfoService.assignKbases(groupId, dto.getKbaseIds(), StpUtil.getLoginIdAsString());
        return ReturnVo.ok(Boolean.TRUE);
    }

    @GetMapping("/{groupId}/kbases")
    public ReturnVo<List<Long>> kbasesOfGroup(@PathVariable String groupId) {
        return ReturnVo.ok(groupKbaseInfoService.getKbaseIdsByGroup(groupId));
    }

    /** 该库当前被多少智能体绑定（撤组提示用）。 */
    @GetMapping("/kbase/{kbaseId}/bound-count")
    public ReturnVo<Long> boundCount(@PathVariable Long kbaseId) {
        return ReturnVo.ok(groupKbaseInfoService.countBoundAgentsOfKbase(kbaseId));
    }
}
