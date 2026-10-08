package com.phoenix.agent.controller;

import com.mybatisflex.core.paginate.Page;
import com.phoenix.agent.dto.SkillBindingDTO;
import com.phoenix.agent.dto.SkillPublishDTO;
import com.phoenix.agent.service.SkillAdminService;
import com.phoenix.agent.vo.AgentSkillOptionVO;
import com.phoenix.agent.vo.SkillDetailVO;
import com.phoenix.agent.vo.SkillListVO;
import com.phoenix.agent.vo.SkillRefVO;
import com.phoenix.data.service.file.ByteArrayMultipartFile;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

/**
 * 技能管理后台接口（R-01/02/03/04/07/08）。
 * /api/** 由 Sa-Token 全局过滤器拦截，未登录返回未授权（R-01 场景3）。
 */
@RestController
@RequestMapping("/api/skill")
@RequiredArgsConstructor
public class SkillController {

    private final SkillAdminService skillAdminService;

    /** R-18（CR-01/T-25）：超管判定复用 R-08/R-17 守卫（单一口径） */
    private final com.phoenix.data.component.AdminRoleGuard adminRoleGuard;

    private String me() {
        return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
    }

    private boolean isSuperAdmin() {
        return adminRoleGuard.isAdmin(me());
    }

    /** R-18：列表 owner 过滤值（超管 null=全部） */
    private String ownerFilter() {
        return isSuperAdmin() ? null : me();
    }

    /** R-18：单对象归属校验（非本人且非超管 → 403） */
    private void assertOwner(Long id) {
        if (isSuperAdmin()) {
            return;
        }
        String creator = skillAdminService.getCreatorById(id);
        if (creator == null || !creator.equals(me())) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.FORBIDDEN, "无权访问他人创建的技能");
        }
    }

    /** 技能列表（keyword 匹配名称/描述，status 过滤） */
    @GetMapping
    public ReturnVo<Page<SkillListVO>> page(@RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String status,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize) {
        return skillAdminService.page(keyword, status, pageNum, pageSize, ownerFilter());
    }

    /** 技能详情（正文+资源清单） */
    @GetMapping("/{id}")
    public ReturnVo<SkillDetailVO> detail(@PathVariable Long id) {
        assertOwner(id);
        return skillAdminService.detail(id);
    }

    /**
     * 上传技能 ZIP（overwrite=true 时同名覆盖并存为草稿）。
     * 应用为 WebFlux（application.yml: web-application-type=reactive），
     * 故按项目既有模式（AgentKnowledgeController）用 FilePart + ByteArrayMultipartFile 承载。
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ReturnVo<Long>> upload(@RequestPart("file") FilePart filePart,
                                       @RequestPart(value = "overwrite", required = false) String overwrite,
                                       @RequestHeader(value = "phoenix-token", required = false) String tokenHeader) {
        boolean overwriteFlag = "true".equalsIgnoreCase(overwrite);
        String filename = filePart.filename();
        String contentType = filePart.headers().getContentType() != null
            ? filePart.headers().getContentType().toString() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        // BUG-140（二次修正）：multipart/reactive 端点的 handler 同步段也可能被派发到非请求线程
        // （实测 boundedElastic 上 SaTokenContext 未初始化 ⇒ me() 抛 SaTokenContextException）。
        // 故改用**线程无关**的 token→loginId 反查（StpUtil.getLoginIdByToken 不依赖 SaTokenContext）。
        String operator = loginIdFromToken(tokenHeader);
        if (operator == null) {
            return Mono.just(ReturnVo.fail("未登录或登录已失效"));
        }
        return DataBufferUtils.join(filePart.content()).flatMap(dataBuffer -> {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            DataBufferUtils.release(dataBuffer);
            // 阻塞式解析+落库放弹性线程池，避免占用事件循环
            return Mono.fromCallable(() -> skillAdminService
                .upload(new ByteArrayMultipartFile(bytes, filename, contentType), overwriteFlag, operator))
                .subscribeOn(Schedulers.boundedElastic());
        });
    }

    /** 线程无关地由 token 反查登录 id（不依赖 SaTokenContext）；无效/缺失返回 null */
    private String loginIdFromToken(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Object id = cn.dev33.satoken.stp.StpUtil.getLoginIdByToken(token);
        return id == null ? null : String.valueOf(id);
    }

    /** 发布（body.groupIds 为授权组，可为空=仅后台可见） */
    @PostMapping("/{id}/publish")
    public ReturnVo<Boolean> publish(@PathVariable Long id, @RequestBody(required = false) SkillPublishDTO dto) {
        assertOwner(id);
        return skillAdminService.publish(id, dto);
    }

    /** 下线 */
    @PostMapping("/{id}/offline")
    public ReturnVo<Boolean> offline(@PathVariable Long id) {
        assertOwner(id);
        return skillAdminService.offline(id);
    }

    /** 调整授权组（即时生效） */
    @PutMapping("/{id}/groups")
    public ReturnVo<Boolean> updateGroups(@PathVariable Long id,
                                          @RequestBody(required = false) SkillPublishDTO dto) {
        assertOwner(id);
        return skillAdminService.updateGroups(id, dto);
    }

    /** 删除前引用计数 */
    @GetMapping("/{id}/refs")
    public ReturnVo<SkillRefVO> refs(@PathVariable Long id) {
        assertOwner(id);
        return skillAdminService.refs(id);
    }

    /** 已授权组 id（发布/授权界面回显） */
    @GetMapping("/{id}/groups")
    public ReturnVo<List<String>> authorizedGroupIds(@PathVariable Long id) {
        assertOwner(id);
        return skillAdminService.authorizedGroupIds(id);
    }

    /** 删除（仅草稿） */
    @DeleteMapping("/{id}")
    public ReturnVo<Boolean> delete(@PathVariable Long id) {
        assertOwner(id);
        return skillAdminService.delete(id);
    }

    /** 智能体编辑页技能可选池 */
    @GetMapping("/options")
    public ReturnVo<List<AgentSkillOptionVO>> options(@RequestParam Long agentId) {
        return skillAdminService.options(agentId, me(), isSuperAdmin());
    }

    /** 智能体已绑定技能 id 回显 */
    @GetMapping("/binding/agent/{agentId}")
    public ReturnVo<List<Long>> boundSkillIds(@PathVariable Long agentId) {
        return skillAdminService.boundSkillIds(agentId);
    }

    /** 覆盖式保存智能体技能绑定 */
    @PutMapping("/binding/agent/{agentId}")
    public ReturnVo<Boolean> bindAgent(@PathVariable Long agentId, @RequestBody SkillBindingDTO dto) {
        return skillAdminService.bindAgent(agentId, dto);
    }
}
