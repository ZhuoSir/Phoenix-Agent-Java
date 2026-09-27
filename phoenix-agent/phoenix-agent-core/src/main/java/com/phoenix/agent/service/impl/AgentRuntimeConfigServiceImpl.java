package com.phoenix.agent.service.impl;

import com.mybatisflex.core.query.QueryWrapper;
import com.phoenix.agent.constant.AgentRuntimeConstant;
import com.phoenix.agent.dto.AgentRuntimeConfigDTO;
import com.phoenix.agent.enums.AgentRuntimeErrorCodeEnm;
import com.phoenix.agent.enums.FilesystemPolicyEnm;
import com.phoenix.agent.mapper.AgentRuntimeConfigMapper;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.agent.service.AgentRuntimeConfigService;
import com.phoenix.agent.vo.AgentRuntimeConfigVO;
import com.phoenix.data.service.agent.AgentService;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.Objects;

/**
 * 对话智能体运行配置实现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentRuntimeConfigServiceImpl implements AgentRuntimeConfigService {

    private final AgentRuntimeConfigMapper agentRuntimeConfigMapper;

    private final AgentService agentService;

    @Override
    public ReturnVo<AgentRuntimeConfigVO> get(Long agentId) {
        AgentRuntimeConfig config = find(agentId);
        return ReturnVo.ok(config == null ? defaults(agentId) : toVo(config));
    }

    @Override
    public AgentRuntimeConfig resolve(Long agentId) {
        AgentRuntimeConfig config = find(agentId);
        if (config != null) {
            return config;
        }
        AgentRuntimeConfig defaulted = new AgentRuntimeConfig();
        defaulted.setAgentId(agentId);
        defaulted.setPlanMode(0);
        defaulted.setMemoryEnabled(1);
        defaulted.setKnowledgeEnabled(0);
        defaulted.setDbQueryEnabled(0);
        defaulted.setDbDeepAnalysisEnabled(0);
        defaulted.setFilesystemPolicy(FilesystemPolicyEnm.LOCAL.getCode());
        return defaulted;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnVo<Boolean> save(Long agentId, AgentRuntimeConfigDTO dto) {
        if (agentId == null || agentService.findById(agentId) == null) {
            return ReturnVo.fail(AgentRuntimeErrorCodeEnm.AGENT_NOT_FOUND.getMsg(),
                AgentRuntimeErrorCodeEnm.AGENT_NOT_FOUND.getCode());
        }
        AgentRuntimeConfigDTO target = dto == null ? new AgentRuntimeConfigDTO() : dto;
        boolean dbToolOn = Boolean.TRUE.equals(target.getDbQueryEnabled())
            || Boolean.TRUE.equals(target.getDbDeepAnalysisEnabled());
        if (dbToolOn && target.getDatasourceId() == null) {
            return ReturnVo.fail(AgentRuntimeErrorCodeEnm.CONFIG_DATASOURCE_REQUIRED.getMsg(),
                AgentRuntimeErrorCodeEnm.CONFIG_DATASOURCE_REQUIRED.getCode());
        }
        int toolCount = (Boolean.TRUE.equals(target.getKnowledgeEnabled()) ? 1 : 0)
            + (Boolean.TRUE.equals(target.getDbQueryEnabled()) ? 1 : 0)
            + (Boolean.TRUE.equals(target.getDbDeepAnalysisEnabled()) ? 1 : 0);
        if (toolCount > AgentRuntimeConstant.MAX_TOOL_COUNT) {
            return ReturnVo.fail(AgentRuntimeErrorCodeEnm.CONFIG_TOOL_LIMIT.getMsg()
                + ": 上限 " + AgentRuntimeConstant.MAX_TOOL_COUNT,
                AgentRuntimeErrorCodeEnm.CONFIG_TOOL_LIMIT.getCode());
        }
        if (StringUtils.hasText(target.getFilesystemPolicy())
            && !isValidPolicy(target.getFilesystemPolicy())) {
            return ReturnVo.fail(AgentRuntimeErrorCodeEnm.CONFIG_POLICY_INVALID.getMsg(),
                AgentRuntimeErrorCodeEnm.CONFIG_POLICY_INVALID.getCode());
        }
        AgentRuntimeConfig existing = find(agentId);
        Date now = new Date();
        if (existing == null) {
            AgentRuntimeConfig config = new AgentRuntimeConfig();
            config.setAgentId(agentId);
            apply(config, target);
            config.setCreateTime(now);
            config.setUpdateTime(now);
            agentRuntimeConfigMapper.insert(config);
        }
        else {
            apply(existing, target);
            existing.setUpdateTime(now);
            agentRuntimeConfigMapper.update(existing);
        }
        log.info("对话智能体运行配置已保存, agentId={}, 工具数={}, datasourceId={}", agentId, toolCount,
            target.getDatasourceId());
        return ReturnVo.ok(true);
    }

    private void apply(AgentRuntimeConfig config, AgentRuntimeConfigDTO dto) {
        if (dto.getModelConfigId() != null) {
            config.setModelConfigId(dto.getModelConfigId());
        }
        if (dto.getPlanMode() != null) {
            config.setPlanMode(toSmallint(dto.getPlanMode()));
        }
        if (dto.getMemoryEnabled() != null) {
            config.setMemoryEnabled(toSmallint(dto.getMemoryEnabled()));
        }
        if (dto.getKnowledgeEnabled() != null) {
            config.setKnowledgeEnabled(toSmallint(dto.getKnowledgeEnabled()));
        }
        if (dto.getDbQueryEnabled() != null) {
            config.setDbQueryEnabled(toSmallint(dto.getDbQueryEnabled()));
        }
        if (dto.getDbDeepAnalysisEnabled() != null) {
            config.setDbDeepAnalysisEnabled(toSmallint(dto.getDbDeepAnalysisEnabled()));
        }
        if (dto.getDatasourceId() != null) {
            config.setDatasourceId(dto.getDatasourceId());
        }
        if (StringUtils.hasText(dto.getFilesystemPolicy())) {
            config.setFilesystemPolicy(dto.getFilesystemPolicy());
        }
    }

    private AgentRuntimeConfig find(Long agentId) {
        if (agentId == null) {
            return null;
        }
        return agentRuntimeConfigMapper
            .selectOneByQuery(QueryWrapper.create().where("agent_id = ?", agentId));
    }

    private AgentRuntimeConfigVO defaults(Long agentId) {
        AgentRuntimeConfigVO vo = new AgentRuntimeConfigVO();
        vo.setAgentId(agentId);
        vo.setPlanMode(false);
        vo.setMemoryEnabled(true);
        vo.setKnowledgeEnabled(false);
        vo.setDbQueryEnabled(false);
        vo.setDbDeepAnalysisEnabled(false);
        vo.setFilesystemPolicy(FilesystemPolicyEnm.LOCAL.getCode());
        return vo;
    }

    private AgentRuntimeConfigVO toVo(AgentRuntimeConfig config) {
        AgentRuntimeConfigVO vo = new AgentRuntimeConfigVO();
        vo.setAgentId(config.getAgentId());
        vo.setModelConfigId(config.getModelConfigId());
        vo.setPlanMode(isOn(config.getPlanMode()));
        vo.setMemoryEnabled(isOn(config.getMemoryEnabled()));
        vo.setKnowledgeEnabled(isOn(config.getKnowledgeEnabled()));
        vo.setDbQueryEnabled(isOn(config.getDbQueryEnabled()));
        vo.setDbDeepAnalysisEnabled(isOn(config.getDbDeepAnalysisEnabled()));
        vo.setDatasourceId(config.getDatasourceId());
        vo.setFilesystemPolicy(config.getFilesystemPolicy());
        return vo;
    }

    private boolean isValidPolicy(String policy) {
        for (FilesystemPolicyEnm enm : FilesystemPolicyEnm.values()) {
            if (Objects.equals(enm.getCode(), policy)) {
                return true;
            }
        }
        return false;
    }

    private Integer toSmallint(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private Boolean isOn(Integer value) {
        return value != null && value == 1;
    }
}
