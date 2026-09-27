package com.phoenix.agent.service;

import com.phoenix.agent.dto.AgentRuntimeConfigDTO;
import com.phoenix.agent.vo.AgentRuntimeConfigVO;
import com.phoenix.tools.vo.ReturnVo;

/**
 * 对话智能体运行配置服务（R-03/R-05/R-09）。
 *
 * <p>配置驱动 HarnessAgent 构建：模型、工具开关（知识库/取数/深度分析）、数据源、
 * 计划模式、记忆、文件系统策略。无配置时读取返回默认值（不报错）。
 */
public interface AgentRuntimeConfigService {

    /**
     * 读取运行配置；未配置时返回默认值（planMode=false, memory=true, 工具全关, policy=local）。
     */
    ReturnVo<AgentRuntimeConfigVO> get(Long agentId);

    /**
     * 保存运行配置（不存在则创建）。校验：智能体存在、开数据库工具须有数据源、
     * 工具数不超上限、文件系统策略合法。
     */
    ReturnVo<Boolean> save(Long agentId, AgentRuntimeConfigDTO dto);
}
