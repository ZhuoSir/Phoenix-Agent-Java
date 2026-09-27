package com.phoenix.agent.service.harness;

import com.phoenix.agent.enums.AgentRuntimeErrorCodeEnm;
import com.phoenix.agent.harness.factory.HarnessAgentFactory;
import com.phoenix.agent.model.AgentRuntimeConfig;
import com.phoenix.agent.service.AgentRuntimeConfigService;
import com.phoenix.agent.vo.AgentRuntimePreviewVO;
import com.phoenix.data.entity.Agent;
import com.phoenix.data.service.agent.AgentService;
import com.phoenix.tools.vo.ReturnVo;
import io.agentscope.harness.agent.HarnessAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 对话智能体构建预演（T-05 验证入口）。
 *
 * <p>真实调用 {@link HarnessAgentFactory} 构建一次，证明「能力完全由库配置决定、不依赖 Java 自注册」，
 * 并把生效工具清单/模型/技能池回显给管理端。构建成功即关闭实例（{@code close()} 只释放本实例自有资源，
 * 共享的 stateStore/distributedStore/skillRepository 不受影响，见 AgentScopedSkillRepository.close 语义）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HarnessAgentPreviewService {

    private final AgentService agentService;

    private final AgentRuntimeConfigService agentRuntimeConfigService;

    private final HarnessAgentFactory harnessAgentFactory;

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
        vo.setModelConfigId(config.getModelConfigId());
        vo.setPlanMode(isOn(config.getPlanMode()));
        vo.setMemoryEnabled(isOn(config.getMemoryEnabled()));
        vo.setKnowledgeEnabled(isOn(config.getKnowledgeEnabled()));
        vo.setDbQueryEnabled(isOn(config.getDbQueryEnabled()));
        vo.setDbDeepAnalysisEnabled(isOn(config.getDbDeepAnalysisEnabled()));
        vo.setDatasourceId(config.getDatasourceId());
        vo.setFilesystemPolicy(config.getFilesystemPolicy());
        vo.setSkillPoolSize(harnessAgentFactory.skillPoolSize(harnessAgentFactory.runtimeKey(agent)));
        HarnessAgentFactory.BuildResult result;
        try {
            result = harnessAgentFactory.buildWithSummary(agent);
        }
        catch (RuntimeException e) {
            vo.setBuildOk(false);
            vo.setErrorMessage(e.getMessage());
            log.error("对话智能体构建预演失败: agentId={}, err={}", agentId, e.toString());
            return ReturnVo.ok(vo);
        }
        try (HarnessAgent built = result.agent()) {
            vo.setBuildOk(true);
            vo.setSummary(result.summary());
            vo.setToolNames(result.toolNames());
        }
        return ReturnVo.ok(vo);
    }

    private Boolean isOn(Integer value) {
        return value != null && value == 1;
    }
}
