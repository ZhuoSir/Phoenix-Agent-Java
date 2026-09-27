package com.phoenix.agent.service.harness;

import com.phoenix.agent.enums.AgentRuntimeErrorCodeEnm;
import com.phoenix.agent.harness.factory.HarnessAgentFactory;
import com.phoenix.agent.harness.factory.HarnessAgentRegistry;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.agent.service.AgentRuntimeConfigService;
import com.phoenix.agent.vo.AgentRuntimePreviewVO;
import com.phoenix.data.entity.Agent;
import com.phoenix.data.service.agent.AgentService;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * 对话智能体构建预演（T-05 验证入口 / T-06 缓存可观测入口）。
 *
 * <p>真实走一遍对话入口的实例解析链路（{@link HarnessAgentRegistry#acquire}），
 * 证明「能力完全由库配置决定、不依赖 Java 自注册」，并把生效工具清单/模型/技能池/实例来源回显给管理端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HarnessAgentPreviewService {

    private final AgentService agentService;

    private final AgentRuntimeConfigService agentRuntimeConfigService;

    private final HarnessAgentFactory harnessAgentFactory;

    private final HarnessAgentRegistry harnessAgentRegistry;

    public ReturnVo<AgentRuntimePreviewVO> preview(Long agentId) {
        Agent agent = agentId == null ? null : agentService.findById(agentId);
        if (agent == null) {
            return ReturnVo.fail(AgentRuntimeErrorCodeEnm.AGENT_NOT_FOUND.getMsg(),
                AgentRuntimeErrorCodeEnm.AGENT_NOT_FOUND.getCode());
        }
        AgentRuntimeConfig config = agentRuntimeConfigService.resolve(agentId);
        AgentRuntimePreviewVO vo = new AgentRuntimePreviewVO();
        vo.setAgentId(agentId);
        vo.setSn(agent.getSn());
        vo.setRuntimeKey(harnessAgentFactory.runtimeKey(agent));
        vo.setModelConfigId(config.getModelConfigId());
        vo.setPlanMode(isOn(config.getPlanMode()));
        vo.setMemoryEnabled(isOn(config.getMemoryEnabled()));
        vo.setKnowledgeEnabled(isOn(config.getKnowledgeEnabled()));
        vo.setDbQueryEnabled(isOn(config.getDbQueryEnabled()));
        vo.setDbDeepAnalysisEnabled(isOn(config.getDbDeepAnalysisEnabled()));
        vo.setDatasourceId(config.getDatasourceId());
        vo.setFilesystemPolicy(config.getFilesystemPolicy());
        vo.setSkillNames(harnessAgentFactory.skillPoolNames(agent));
        vo.setSkillPoolSize(vo.getSkillNames().size());
        try {
            HarnessAgentRegistry.RuntimeHandle handle = harnessAgentRegistry.acquire(agentId);
            vo.setBuildOk(true);
            vo.setInstanceSource(handle.source());
            vo.setSummary(handle.summary());
            vo.setToolNames(handle.toolNames());
            vo.setRegistryStats(harnessAgentRegistry.stats());
        }
        catch (NoSuchElementException e) {
            return ReturnVo.fail(AgentRuntimeErrorCodeEnm.AGENT_NOT_FOUND.getMsg(),
                AgentRuntimeErrorCodeEnm.AGENT_NOT_FOUND.getCode());
        }
        catch (RuntimeException e) {
            vo.setBuildOk(false);
            vo.setErrorMessage(e.getMessage());
            log.error("对话智能体构建预演失败: agentId={}, err={}", agentId, e.toString());
        }
        return ReturnVo.ok(vo);
    }

    private Boolean isOn(Integer value) {
        return value != null && value == 1;
    }
}
