package com.phoenix.agent.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.phoenix.agent.dto.AgentFileMaterializeDTO;
import com.phoenix.agent.enums.AgentFileBackendEnm;
import com.phoenix.agent.enums.AgentFileSourceEnm;
import com.phoenix.agent.model.AgentFile;
import com.phoenix.agent.service.file.AgentFileException;
import com.phoenix.agent.service.file.AgentFileService;
import com.phoenix.agent.vo.AgentFileVO;
import com.phoenix.data.entity.ChatSession;
import com.phoenix.data.mapper.ChatSessionMapper;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 会话文件面板接口（BL-19，T-04/T-08）：列表/下载(inline)/逻辑删/消息物化。
 *
 * <p>/api/** 由 Sa-Token 全局过滤器保证登录态；属主=会话 user_id 在本层+门面双重校验（R-07）。
 * 下载返回原始字节（非 ReturnVo 信封）；业务错误回 ReturnVo JSON，前端以 content-type 区分。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent/files")
@RequiredArgsConstructor
public class AgentFileController {

    private final AgentFileService agentFileService;
    private final ChatSessionMapper chatSessionMapper;

    @GetMapping
    public Mono<ReturnVo<List<AgentFileVO>>> list(@RequestParam String sessionId) {
        String userId = StpUtil.getLoginIdAsString();
        return Mono.fromCallable(() -> ReturnVo.ok("操作成功!", agentFileService.listBySession(sessionId, userId)))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(AgentFileException.class, e -> Mono.just(ReturnVo.fail(e.getMessage(), e.getCode())));
    }

    @GetMapping("/{id}/download")
    public Mono<ResponseEntity<byte[]>> download(@PathVariable String id,
            @RequestParam(name = "inline", required = false, defaultValue = "false") boolean inline) {
        String userId = StpUtil.getLoginIdAsString();
        return Mono.fromCallable(() -> {
            AgentFileService.Download d = agentFileService.download(id, userId, inline);
            String encoded = URLEncoder.encode(d.fileName(), StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(
                            d.mime() != null ? d.mime() : MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            (inline ? "inline" : "attachment") + "; filename*=UTF-8''" + encoded)
                    // R-09：HTML 预览沙箱，防存储型 XSS
                    .header("Content-Security-Policy", "sandbox; default-src 'none'; img-src data:; style-src 'unsafe-inline'")
                    .body(d.content());
        }).onErrorResume(AgentFileException.class, e -> Mono.just(
                ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(("{\"code\":\"" + e.getCode() + "\",\"msg\":\"" + e.getMessage()
                                + "\",\"data\":null,\"success\":false}").getBytes(StandardCharsets.UTF_8))))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/{id}")
    public Mono<ReturnVo<Boolean>> delete(@PathVariable String id) {
        String userId = StpUtil.getLoginIdAsString();
        return Mono.fromCallable(() -> {
            agentFileService.logicalDelete(id, userId);
            return ReturnVo.ok("删除成功!", Boolean.TRUE);
        }).subscribeOn(Schedulers.boundedElastic())
          .onErrorResume(AgentFileException.class, e -> Mono.just(ReturnVo.fail(e.getMessage(), e.getCode())));
    }

    /**
     * 报告 HTML 等消息内容手动物化（R-12/P4）。agentId 从会话反查，物化后走 file_created 同款登记。
     */
    @PostMapping("/materialize")
    public Mono<ReturnVo<AgentFileVO>> materialize(@RequestBody AgentFileMaterializeDTO dto) {
        String userId = StpUtil.getLoginIdAsString();
        return Mono.fromCallable(() -> {
            ChatSession session = chatSessionMapper.selectOneById(dto.getSessionId());
            if (session == null || !userId.equals(session.getUserId())) {
                throw new AgentFileException(com.phoenix.agent.enums.AgentFileErrorCodeEnm.FILE_FORBIDDEN);
            }
            byte[] bytes = dto.getContent() == null ? new byte[0]
                    : dto.getContent().getBytes(StandardCharsets.UTF_8);
            AgentFile f = agentFileService.register(AgentFileService.RegisterCmd.builder()
                    .agentId(session.getAgentId() == null ? null : session.getAgentId().longValue())
                    .sessionId(dto.getSessionId())
                    .fileName(dto.getFileName())
                    .content(bytes)
                    .source(AgentFileSourceEnm.MATERIALIZE)
                    .backend(AgentFileBackendEnm.LOCAL)
                    .creator(userId)
                    .build());
            return ReturnVo.ok("操作成功!", AgentFileVO.builder()
                    .id(f.getId()).fileName(f.getFileName()).sizeBytes(f.getSizeBytes())
                    .mime(f.getMime()).source(f.getSource()).createTime(f.getCreateTime())
                    .build());
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
