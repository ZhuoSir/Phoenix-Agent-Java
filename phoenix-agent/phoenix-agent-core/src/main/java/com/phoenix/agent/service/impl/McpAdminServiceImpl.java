package com.phoenix.agent.service.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.phoenix.agent.enums.McpErrorCodeEnm;
import com.phoenix.agent.mapper.AgentMcpInfoMapper;
import com.phoenix.agent.mapper.GroupMcpInfoMapper;
import com.phoenix.agent.mapper.McpServerInfoMapper;
import com.phoenix.agent.model.AgentMcpInfo;
import com.phoenix.agent.model.GroupMcpInfo;
import com.phoenix.agent.model.McpDetailVO;
import com.phoenix.agent.model.McpListVO;
import com.phoenix.agent.model.McpSaveDTO;
import com.phoenix.agent.model.McpServerInfo;
import com.phoenix.agent.model.McpTestDTO;
import com.phoenix.agent.model.McpTestResultVO;
import com.phoenix.agent.service.McpAdminService;
import com.phoenix.agent.util.McpSecretCipher;
import com.phoenix.tools.vo.ReturnVo;

import io.agentscope.core.tool.mcp.McpClientBuilder;
import io.agentscope.core.tool.mcp.McpClientWrapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * MCP 插件管理服务实现（镜像 SkillAdminServiceImpl 范式；mcp-client-tools T-03）。
 * 敏感值：headers/env 加密落库（McpSecretCipher），回显掩码；掩码回传=未修改保留原值。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpAdminServiceImpl implements McpAdminService {

    /** 分页上限防拖库（api 规范，同技能） */
    private static final int MAX_PAGE_SIZE = 500;
    private static final Set<String> TRANSPORTS = Set.of("stdio", "sse", "streamable_http", "http");
    private static final long DEFAULT_TIMEOUT_MS = 30000L;
    private static final long DEFAULT_INIT_TIMEOUT_MS = 15000L;

    private final McpServerInfoMapper mcpServerInfoMapper;
    private final GroupMcpInfoMapper groupMcpInfoMapper;
    private final AgentMcpInfoMapper agentMcpInfoMapper;
    /** WebFlux 上下文无自动装配 ObjectMapper bean（启动崩溃实证）——自持实例，同 HitlCacheService 惯例 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ReturnVo<Page<McpListVO>> page(String keyword, int pageNum, int pageSize, String ownerId) {
        int pn = Math.max(pageNum, 1);
        int ps = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        QueryWrapper q = QueryWrapper.create().where("del_flag = 0");
        // R-18（CR-01/T-25）：普通用户仅见本人创建；ownerId=null（超管）不过滤
        if (StringUtils.hasText(ownerId)) {
            q.and("creator = ?", ownerId);
        }
        if (StringUtils.hasText(keyword)) {
            q.and("name like ?", "%" + keyword.trim() + "%");
        }
        q.orderBy("update_time", false);
        Page<McpServerInfo> page = mcpServerInfoMapper.paginate(pn, ps, q);
        List<McpServerInfo> rows = page.getRecords();
        Map<String, Long> groupCounts = countByMcp(rows.stream().map(McpServerInfo::getId).toList(), true);
        Map<String, Long> boundCounts = countByMcp(rows.stream().map(McpServerInfo::getId).toList(), false);
        List<McpListVO> vos = new ArrayList<>();
        for (McpServerInfo e : rows) {
            McpListVO vo = new McpListVO();
            vo.setId(e.getId());
            vo.setName(e.getName());
            vo.setTransport(e.getTransport());
            vo.setStatus(e.getStatus());
            vo.setDescription(e.getDescription());
            vo.setGroupCount(groupCounts.getOrDefault(e.getId(), 0L));
            vo.setBoundCount(boundCounts.getOrDefault(e.getId(), 0L));
            vo.setUpdateTime(e.getUpdateTime());
            vos.add(vo);
        }
        return ReturnVo.ok(new Page<>(vos, page.getPageNumber(), page.getPageSize(), page.getTotalRow()));
    }

    @Override
    public ReturnVo<McpDetailVO> detail(String id) {
        McpServerInfo e = load(id);
        if (e == null) {
            return ReturnVo.fail(McpErrorCodeEnm.MCP_NOT_FOUND.getMsg(), McpErrorCodeEnm.MCP_NOT_FOUND.getCode());
        }
        Map<String, Object> cfg = parseConfig(e.getConfig());
        McpDetailVO vo = new McpDetailVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setTransport(e.getTransport());
        vo.setDescription(e.getDescription());
        vo.setStatus(e.getStatus());
        vo.setUrl(str(cfg.get("url")));
        vo.setHeaders(maskMap(asMap(cfg.get("headers"))));
        vo.setCommand(str(cfg.get("command")));
        vo.setArgs(asList(cfg.get("args")));
        vo.setEnv(maskMap(asMap(cfg.get("env"))));
        vo.setEnableTools(asList(cfg.get("enableTools")));
        vo.setTimeoutMs(lng(cfg.get("timeoutMs")));
        vo.setInitTimeoutMs(lng(cfg.get("initTimeoutMs")));
        vo.setGroupIds(groupMcpInfoMapper
            .selectListByQuery(QueryWrapper.create().where("mcp_id = ?", id).and("del_flag = 0"))
            .stream().map(GroupMcpInfo::getGroupId).toList());
        vo.setBoundAgentIds(agentMcpInfoMapper
            .selectListByQuery(QueryWrapper.create().where("mcp_id = ?", id).and("del_flag = 0"))
            .stream().map(AgentMcpInfo::getAgentId).distinct().toList());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return ReturnVo.ok(vo);
    }

    @Override
    public ReturnVo<String> save(McpSaveDTO dto, String operator) {
        ReturnVo<String> invalid = validate(dto);
        if (invalid != null) {
            return invalid;
        }
        boolean create = !StringUtils.hasText(dto.getId());
        McpServerInfo existing = null;
        Map<String, Object> oldCfg = Map.of();
        if (!create) {
            existing = load(dto.getId());
            if (existing == null) {
                return ReturnVo.fail(McpErrorCodeEnm.MCP_NOT_FOUND.getMsg(), McpErrorCodeEnm.MCP_NOT_FOUND.getCode());
            }
            oldCfg = parseConfig(existing.getConfig());
        }
        QueryWrapper dup = QueryWrapper.create().where("name = ?", dto.getName().trim()).and("del_flag = 0");
        if (!create) {
            dup.and("id != ?", dto.getId());
        }
        if (mcpServerInfoMapper.selectCountByQuery(dup) > 0) {
            return ReturnVo.fail(McpErrorCodeEnm.MCP_NAME_CONFLICT.getMsg(),
                McpErrorCodeEnm.MCP_NAME_CONFLICT.getCode());
        }
        // config 组装：敏感值三分支（掩码=保留原密文 / 空=移除 / 新值=加密）
        Map<String, Object> cfg = new LinkedHashMap<>();
        if (StringUtils.hasText(dto.getUrl())) {
            cfg.put("url", dto.getUrl().trim());
        }
        putSecrets(cfg, "headers", dto.getHeaders(), asMap(oldCfg.get("headers")));
        if (StringUtils.hasText(dto.getCommand())) {
            cfg.put("command", dto.getCommand().trim());
        }
        if (dto.getArgs() != null && !dto.getArgs().isEmpty()) {
            cfg.put("args", dto.getArgs());
        }
        putSecrets(cfg, "env", dto.getEnv(), asMap(oldCfg.get("env")));
        if (dto.getEnableTools() != null && !dto.getEnableTools().isEmpty()) {
            cfg.put("enableTools", dto.getEnableTools());
        }
        cfg.put("timeoutMs", dto.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : dto.getTimeoutMs());
        cfg.put("initTimeoutMs", dto.getInitTimeoutMs() == null ? DEFAULT_INIT_TIMEOUT_MS : dto.getInitTimeoutMs());

        McpServerInfo entity = create ? new McpServerInfo() : existing;
        entity.setName(dto.getName().trim());
        entity.setTransport(dto.getTransport());
        entity.setDescription(dto.getDescription());
        entity.setStatus(StringUtils.hasText(dto.getStatus()) ? dto.getStatus() : "enabled");
        entity.setConfig(writeConfig(cfg));
        Date now = new Date();
        if (create) {
            entity.setCreator(operator);
            entity.setCreateTime(now);
            entity.setUpdator(operator);
            entity.setUpdateTime(now);
            mcpServerInfoMapper.insert(entity);
            log.info("MCP 配置创建: id={}, name={}, transport={}, by={}", entity.getId(), entity.getName(),
                entity.getTransport(), operator);
        }
        else {
            entity.setUpdator(operator);
            entity.setUpdateTime(now);
            mcpServerInfoMapper.update(entity);
            log.info("MCP 配置更新: id={}, name={}, by={}", entity.getId(), entity.getName(), operator);
        }
        // 双参重载防歧义：ok(String) 会命中 ok(msg) 而非 ok(data)——String 型 data 必须走 ok(msg,data)
        return ReturnVo.ok("保存成功", entity.getId());
    }

    @Override
    public ReturnVo<Boolean> toggleStatus(String id, String status, String operator) {
        if (!"enabled".equals(status) && !"disabled".equals(status)) {
            return ReturnVo.fail(McpErrorCodeEnm.MCP_INVALID_PARAM.getMsg(), McpErrorCodeEnm.MCP_INVALID_PARAM.getCode());
        }
        McpServerInfo e = load(id);
        if (e == null) {
            return ReturnVo.fail(McpErrorCodeEnm.MCP_NOT_FOUND.getMsg(), McpErrorCodeEnm.MCP_NOT_FOUND.getCode());
        }
        e.setStatus(status);
        e.setUpdator(operator);
        e.setUpdateTime(new Date());
        mcpServerInfoMapper.update(e);
        log.info("MCP 启停: id={}, status={}, by={}", id, status, operator);
        return ReturnVo.ok(Boolean.TRUE);
    }

    @Override
    public ReturnVo<Boolean> delete(String id, String operator) {
        McpServerInfo e = load(id);
        if (e == null) {
            return ReturnVo.fail(McpErrorCodeEnm.MCP_NOT_FOUND.getMsg(), McpErrorCodeEnm.MCP_NOT_FOUND.getCode());
        }
        // R-01 删除防悬挂：被绑定则拒删并列名智能体
        List<AgentMcpInfo> bound = agentMcpInfoMapper
            .selectListByQuery(QueryWrapper.create().where("mcp_id = ?", id).and("del_flag = 0"));
        if (!bound.isEmpty()) {
            List<Long> agentIds = bound.stream().map(AgentMcpInfo::getAgentId).distinct().toList();
            return ReturnVo.fail(McpErrorCodeEnm.MCP_IN_USE.getMsg() + "（agentIds=" + agentIds + "）",
                McpErrorCodeEnm.MCP_IN_USE.getCode());
        }
        Date now = new Date();
        e.setDelFlag(1);
        e.setUpdator(operator);
        e.setUpdateTime(now);
        mcpServerInfoMapper.update(e);
        // 授权关系随删（软删留痕）
        List<GroupMcpInfo> grants = groupMcpInfoMapper
            .selectListByQuery(QueryWrapper.create().where("mcp_id = ?", id).and("del_flag = 0"));
        for (GroupMcpInfo g : grants) {
            g.setDelFlag(1);
            g.setUpdator(operator);
            g.setUpdateTime(now);
            groupMcpInfoMapper.update(g);
        }
        log.info("MCP 删除(软): id={}, name={}, by={}", id, e.getName(), operator);
        return ReturnVo.ok(Boolean.TRUE);
    }

    @Override
    public ReturnVo<Boolean> grantGroups(String mcpId, List<String> groupIds) {
        McpServerInfo e = load(mcpId);
        if (e == null) {
            return ReturnVo.fail(McpErrorCodeEnm.MCP_NOT_FOUND.getMsg(), McpErrorCodeEnm.MCP_NOT_FOUND.getCode());
        }
        List<String> distinct = groupIds == null ? List.of()
            : groupIds.stream().filter(StringUtils::hasText).distinct().toList();
        // 组存在性校验（镜像技能 replaceGroupGrants：Db 直查跨域表）
        for (String gid : distinct) {
            Object cnt = Db.selectObject("select count(*) from tbl_platform_group_info where id = ? and del_flag = 0",
                gid);
            if (cnt == null || ((Number) cnt).longValue() == 0) {
                return ReturnVo.fail(McpErrorCodeEnm.MCP_GROUP_NOT_FOUND.getMsg() + ": " + gid,
                    McpErrorCodeEnm.MCP_GROUP_NOT_FOUND.getCode());
            }
        }
        // 覆盖式：物理删后重建（授权关系无审计留存要求，同技能先例）
        groupMcpInfoMapper.deleteByQuery(QueryWrapper.create().where("mcp_id = ?", mcpId));
        if (!distinct.isEmpty()) {
            Date now = new Date();
            List<GroupMcpInfo> rows = distinct.stream().map(gid -> {
                GroupMcpInfo g = new GroupMcpInfo();
                g.setGroupId(gid);
                g.setMcpId(mcpId);
                g.setCreateTime(now);
                g.setUpdateTime(now);
                return g;
            }).toList();
            groupMcpInfoMapper.insertBatch(rows);
        }
        log.info("MCP 组授权覆盖: mcpId={}, groupIds={}", mcpId, distinct);
        return ReturnVo.ok(Boolean.TRUE);
    }

    @Override
    public ReturnVo<McpTestResultVO> testConnection(McpTestDTO dto) {
        McpTestResultVO vo = new McpTestResultVO();
        long t0 = System.currentTimeMillis();
        if (dto == null || !StringUtils.hasText(dto.getTransport()) || !TRANSPORTS.contains(dto.getTransport())) {
            vo.setSuccess(false);
            vo.setError("传输类型无效（stdio/sse/streamable_http/http）");
            return ReturnVo.ok(vo);
        }
        // 编辑场景：掩码值取库中原明文合并（仅内存，不落盘）
        Map<String, String> headers = dto.getHeaders() == null ? Map.of() : new HashMap<>(dto.getHeaders());
        Map<String, String> env = dto.getEnv() == null ? Map.of() : new HashMap<>(dto.getEnv());
        if (StringUtils.hasText(dto.getId())) {
            McpServerInfo e = load(dto.getId());
            if (e != null) {
                Map<String, Object> cfg = parseConfig(e.getConfig());
                mergeMasked(headers, asMap(cfg.get("headers")));
                mergeMasked(env, asMap(cfg.get("env")));
            }
        }
        long timeoutMs = dto.getTimeoutMs() == null ? DEFAULT_TIMEOUT_MS : dto.getTimeoutMs();
        long initMs = dto.getInitTimeoutMs() == null ? DEFAULT_INIT_TIMEOUT_MS : dto.getInitTimeoutMs();
        // HTTP 层预探（R-06：MCP SDK 把 401/拒连统一包成"failed to initialize"，鉴权失败与不可达无法区分）——
        // 带配置 headers 直连读状态码：401/403→鉴权失败；拒连/未知主机→不可达；其余状态放行给完整试连
        if (!"stdio".equals(dto.getTransport()) && StringUtils.hasText(dto.getUrl())) {
            String pre = httpPrecheck(dto.getUrl().trim(), headers);
            if (pre != null) {
                vo.setSuccess(false);
                vo.setError(pre);
                vo.setElapsedMs(System.currentTimeMillis() - t0);
                return ReturnVo.ok(vo);
            }
        }
        McpClientWrapper wrapper = null;
        try {
            McpClientBuilder b = McpClientBuilder
                .create(StringUtils.hasText(dto.getName()) ? dto.getName() : "conn-test");
            switch (dto.getTransport()) {
                case "stdio" -> {
                    if (!StringUtils.hasText(dto.getCommand())) {
                        vo.setSuccess(false);
                        vo.setError("stdio 传输必须提供 command");
                        return ReturnVo.ok(vo);
                    }
                    b = b.stdioTransport(dto.getCommand().trim(),
                        dto.getArgs() == null ? List.of() : dto.getArgs(), env);
                }
                case "sse" -> {
                    if (!StringUtils.hasText(dto.getUrl())) {
                        vo.setSuccess(false);
                        vo.setError("sse 传输必须提供 url");
                        return ReturnVo.ok(vo);
                    }
                    b = b.sseTransport(dto.getUrl().trim());
                    if (!headers.isEmpty()) {
                        b = b.headers(headers);
                    }
                }
                default -> {
                    if (!StringUtils.hasText(dto.getUrl())) {
                        vo.setSuccess(false);
                        vo.setError(dto.getTransport() + " 传输必须提供 url");
                        return ReturnVo.ok(vo);
                    }
                    b = b.streamableHttpTransport(dto.getUrl().trim());
                    if (!headers.isEmpty()) {
                        b = b.headers(headers);
                    }
                }
            }
            b = b.timeout(Duration.ofMillis(timeoutMs)).initializationTimeout(Duration.ofMillis(initMs));
            wrapper = b.buildAsync().block(Duration.ofMillis(initMs + 5000));
            if (wrapper == null) {
                vo.setSuccess(false);
                vo.setError("客户端构建失败");
                return ReturnVo.ok(vo);
            }
            wrapper.initialize().block(Duration.ofMillis(initMs + 5000));
            List<McpSchema.Tool> tools = wrapper.listTools().block(Duration.ofMillis(timeoutMs + 5000));
            List<String> names = tools == null ? List.of() : tools.stream().map(McpSchema.Tool::name).toList();
            vo.setSuccess(true);
            vo.setToolCount(names.size());
            vo.setToolNames(names.size() > 200 ? names.subList(0, 200) : names);
            log.info("MCP 测试连接成功: transport={}, tools={}", dto.getTransport(), names.size());
        }
        catch (Exception ex) {
            vo.setSuccess(false);
            vo.setError(classifyError(ex));
            log.warn("MCP 测试连接失败: transport={}, err={}", dto.getTransport(), vo.getError());
        }
        finally {
            if (wrapper != null) {
                try {
                    wrapper.close();
                }
                catch (Exception ignored) {
                }
            }
        }
        vo.setElapsedMs(System.currentTimeMillis() - t0);
        return ReturnVo.ok(vo);
    }

    @Override
    public ReturnVo<List<com.phoenix.agent.model.McpOptionVO>> options(Long agentId, String viewerId, boolean superAdmin) {
        Set<String> bound = new HashSet<>(boundIdSet(agentId));
        List<com.phoenix.agent.model.McpOptionVO> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // R-18（CR-01/T-26）：可见集合 = 超管全部启用；否则 自己的 ∪ 我所在组关联的 ∪ 公共（无组授权行）
        QueryWrapper pool = QueryWrapper.create().where("status = ?", "enabled").and("del_flag = 0");
        if (!superAdmin) {
            List<String> myGroups = myGroupIds(viewerId);
            StringBuilder cond = new StringBuilder("(creator = ?");
            List<Object> params = new ArrayList<>();
            params.add(viewerId);
            if (!myGroups.isEmpty()) {
                cond.append(" or id in (select mcp_id from tbl_platform_group_mcp_info where del_flag = 0 and group_id in (")
                    .append(placeholders(myGroups.size())).append("))");
                params.addAll(myGroups);
            }
            cond.append(" or id not in (select mcp_id from tbl_platform_group_mcp_info where del_flag = 0))");
            pool.and(cond.toString(), params.toArray());
        }
        // 启用池
        for (McpServerInfo e : mcpServerInfoMapper.selectListByQuery(pool)) {
            result.add(toOption(e, bound.contains(e.getId())));
            seen.add(e.getId());
        }
        // 已绑定但停用/下线/不可见的保留展示（镜像技能：可手动解绑，R-04 场景2）
        for (String id : bound) {
            if (seen.contains(id)) {
                continue;
            }
            McpServerInfo e = load(id);
            if (e != null) {
                result.add(toOption(e, true));
            }
        }
        return ReturnVo.ok(result);
    }

    /** 当前用户所在组 id 集（R-18 可见集合用） */
    private List<String> myGroupIds(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return com.mybatisflex.core.row.Db
            .selectListBySql("select group_id from tbl_platform_account_group_info where account_id = ? and del_flag = 0", userId)
            .stream()
            .map(r -> r.getString("group_id"))
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
    }

    private String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    @Override
    public String getCreatorById(String id) {
        McpServerInfo e = load(id);
        return e == null ? null : e.getCreator();
    }

    @Override
    public ReturnVo<List<String>> boundIds(Long agentId) {
        return ReturnVo.ok(boundIdSet(agentId));
    }

    @Override
    public ReturnVo<Boolean> bind(Long agentId, List<String> mcpIds, String operator) {
        if (agentId == null) {
            return ReturnVo.fail(McpErrorCodeEnm.MCP_INVALID_PARAM.getMsg(), McpErrorCodeEnm.MCP_INVALID_PARAM.getCode());
        }
        List<String> distinct = mcpIds == null ? List.of()
            : mcpIds.stream().filter(StringUtils::hasText).distinct().toList();
        Set<String> currentlyBound = new HashSet<>(boundIdSet(agentId));
        // 仅启用可新绑（已绑定的停用项允许保留——镜像技能 optionDisabled 语义）
        for (String id : distinct) {
            if (currentlyBound.contains(id)) {
                continue;
            }
            McpServerInfo e = load(id);
            if (e == null) {
                return ReturnVo.fail(McpErrorCodeEnm.MCP_NOT_FOUND.getMsg() + ": " + id,
                    McpErrorCodeEnm.MCP_NOT_FOUND.getCode());
            }
            if (!"enabled".equals(e.getStatus())) {
                return ReturnVo.fail("仅启用的 MCP 可新绑定: " + e.getName(),
                    McpErrorCodeEnm.MCP_INVALID_PARAM.getCode());
            }
        }
        // 覆盖式：物理删后重建（绑定关系无审计留存要求，同组授权先例）
        agentMcpInfoMapper.deleteByQuery(QueryWrapper.create().where("agent_id = ?", agentId));
        if (!distinct.isEmpty()) {
            Date now = new Date();
            List<AgentMcpInfo> rows = distinct.stream().map(mid -> {
                AgentMcpInfo a = new AgentMcpInfo();
                a.setAgentId(agentId);
                a.setMcpId(mid);
                a.setCreator(operator);
                a.setCreateTime(now);
                a.setUpdateTime(now);
                return a;
            }).toList();
            agentMcpInfoMapper.insertBatch(rows);
        }
        log.info("智能体 MCP 绑定覆盖: agentId={}, mcpIds={}, by={}", agentId, distinct, operator);
        return ReturnVo.ok(Boolean.TRUE);
    }

    private List<String> boundIdSet(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        return agentMcpInfoMapper
            .selectListByQuery(QueryWrapper.create().where("agent_id = ?", agentId).and("del_flag = 0"))
            .stream().map(AgentMcpInfo::getMcpId).distinct().toList();
    }

    private com.phoenix.agent.model.McpOptionVO toOption(McpServerInfo e, boolean bound) {
        com.phoenix.agent.model.McpOptionVO vo = new com.phoenix.agent.model.McpOptionVO();
        vo.setId(e.getId());
        vo.setName(e.getName());
        vo.setTransport(e.getTransport());
        vo.setStatus(e.getStatus());
        vo.setBound(bound);
        return vo;
    }

    // ── 私有工具 ──

    private McpServerInfo load(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        List<McpServerInfo> list = mcpServerInfoMapper
            .selectListByQuery(QueryWrapper.create().where("id = ?", id).and("del_flag = 0"));
        return list.isEmpty() ? null : list.get(0);
    }

    private ReturnVo<String> validate(McpSaveDTO dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            return ReturnVo.fail("名称必填", McpErrorCodeEnm.MCP_INVALID_PARAM.getCode());
        }
        if (!StringUtils.hasText(dto.getTransport()) || !TRANSPORTS.contains(dto.getTransport())) {
            return ReturnVo.fail(McpErrorCodeEnm.MCP_TRANSPORT_UNSUPPORTED.getMsg(),
                McpErrorCodeEnm.MCP_TRANSPORT_UNSUPPORTED.getCode());
        }
        if ("stdio".equals(dto.getTransport()) && !StringUtils.hasText(dto.getCommand())) {
            return ReturnVo.fail("stdio 传输必须提供 command", McpErrorCodeEnm.MCP_INVALID_PARAM.getCode());
        }
        if (!"stdio".equals(dto.getTransport()) && !StringUtils.hasText(dto.getUrl())) {
            return ReturnVo.fail(dto.getTransport() + " 传输必须提供 url", McpErrorCodeEnm.MCP_INVALID_PARAM.getCode());
        }
        return null;
    }

    private Map<String, Long> countByMcp(List<String> mcpIds, boolean groups) {
        Map<String, Long> out = new HashMap<>();
        if (mcpIds.isEmpty()) {
            return out;
        }
        if (groups) {
            for (GroupMcpInfo g : groupMcpInfoMapper
                .selectListByQuery(QueryWrapper.create().in("mcp_id", new HashSet<>(mcpIds)).and("del_flag = 0"))) {
                out.merge(g.getMcpId(), 1L, Long::sum);
            }
        }
        else {
            Set<String> seen = new HashSet<>();
            for (AgentMcpInfo a : agentMcpInfoMapper
                .selectListByQuery(QueryWrapper.create().in("mcp_id", new HashSet<>(mcpIds)).and("del_flag = 0"))) {
                if (seen.add(a.getMcpId() + "#" + a.getAgentId())) {
                    out.merge(a.getMcpId(), 1L, Long::sum);
                }
            }
        }
        return out;
    }

    private void putSecrets(Map<String, Object> cfg, String key, Map<String, String> incoming,
            Map<String, String> old) {
        if (incoming == null) {
            return;
        }
        Map<String, String> merged = new LinkedHashMap<>();
        for (Map.Entry<String, String> en : incoming.entrySet()) {
            String k = en.getKey();
            String v = en.getValue();
            if (!StringUtils.hasText(k)) {
                continue;
            }
            if (v == null || v.isBlank()) {
                continue; // 空值=移除该项
            }
            if (McpSecretCipher.isMasked(v)) {
                String oldV = old.get(k); // 原密文原样保留
                if (oldV != null) {
                    merged.put(k, oldV);
                }
            }
            else {
                merged.put(k, McpSecretCipher.encrypt(v));
            }
        }
        if (!merged.isEmpty()) {
            cfg.put(key, merged);
        }
    }

    private void mergeMasked(Map<String, String> form, Map<String, String> stored) {
        for (Map.Entry<String, String> en : form.entrySet()) {
            if (McpSecretCipher.isMasked(en.getValue())) {
                String sv = stored.get(en.getKey());
                en.setValue(sv == null ? "" : McpSecretCipher.decrypt(sv));
            }
        }
    }

    private Map<String, String> maskMap(Map<String, String> stored) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> en : stored.entrySet()) {
            String plain;
            try {
                plain = McpSecretCipher.decrypt(en.getValue());
            }
            catch (Exception e) {
                plain = "****";
            }
            out.put(en.getKey(), McpSecretCipher.mask(plain));
        }
        return out;
    }

    /** 返回 null=放行（继续完整试连）；非 null=可读错误定案。 */
    private String httpPrecheck(String url, Map<String, String> headers) {
        try {
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) java.net.URI.create(url).toURL()
                .openConnection();
            conn.setConnectTimeout(4000);
            conn.setReadTimeout(4000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            headers.forEach(conn::setRequestProperty);
            conn.getOutputStream().write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int code = conn.getResponseCode();
            conn.disconnect();
            if (code == 401 || code == 403) {
                return "鉴权失败：HTTP " + code + "（请检查 Headers 凭据）";
            }
            return null; // 200/405/404 等交由完整 MCP 握手判定
        }
        catch (java.net.ConnectException | java.net.UnknownHostException e) {
            return "目标不可达：" + e.getClass().getSimpleName() + " " + e.getMessage();
        }
        catch (java.net.SocketTimeoutException e) {
            return "连接超时：预探 4s 无响应";
        }
        catch (Exception e) {
            return null; // 预探自身异常不拦截，交给完整试连
        }
    }

    private String classifyError(Exception ex) {
        StringBuilder sb = new StringBuilder();
        Throwable t = ex;
        while (t != null && sb.length() < 400) {
            if (t.getMessage() != null) {
                sb.append(t.getMessage()).append(" | ");
            }
            t = t.getCause();
        }
        String m = sb.toString().toLowerCase();
        String raw = sb.length() > 300 ? sb.substring(0, 300) : sb.toString();
        if (m.contains("401") || m.contains("403") || m.contains("unauthorized") || m.contains("forbidden")) {
            return "鉴权失败：" + raw;
        }
        if (m.contains("timeout") || m.contains("timed out")) {
            return "连接超时：" + raw;
        }
        if (m.contains("connect") || m.contains("refused") || m.contains("unknownhost") || m.contains("resolve")
                || m.contains("unreachable") || m.contains("failed to initialize")) {
            // "failed to initialize"=MCP SDK 对连接层失败的统一包装（T-03 演练实证分类词缺失）
            return "目标不可达或握手失败：" + raw;
        }
        return "连接失败：" + raw;
    }

    private Map<String, Object> parseConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        }
        catch (Exception e) {
            log.warn("MCP config 解析失败，按空处理: {}", e.getMessage());
            return Map.of();
        }
    }

    private String writeConfig(Map<String, Object> cfg) {
        try {
            return objectMapper.writeValueAsString(cfg);
        }
        catch (Exception e) {
            throw new IllegalStateException("MCP config 序列化失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> asMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, String> out = new LinkedHashMap<>();
            m.forEach((k, v) -> out.put(String.valueOf(k), v == null ? null : String.valueOf(v)));
            return out;
        }
        return Map.of();
    }

    @SuppressWarnings("unchecked")
    private List<String> asList(Object o) {
        if (o instanceof List<?> l) {
            return l.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private Long lng(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
