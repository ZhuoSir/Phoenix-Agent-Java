package com.phoenix.data.service.agent;

import com.mybatisflex.core.service.IService;
import com.phoenix.data.entity.Agent;

import java.util.List;

/**
 * Agent 服务接口，提供 Agent 的增删改查及 API Key 管理功能。
 */
public interface AgentService extends IService<Agent> {

	List<Agent> findAll();

	Agent findById(Long id);

	List<Agent> findByIds(List<Long> ids, String status);

	/**
	 * 根据唯一标识 sn 查找 Agent
	 */
	Agent findBySn(String sn);

	/**
	 * 根据状态查找 Agent 列表
	 */
	List<Agent> findByStatus(String status);

	/**
	 * 根据关键字搜索 Agent
	 */
	List<Agent> search(String keyword);

	/**
	 * 保存 Agent（新增或更新）
	 */
	Agent saveAgent(Agent agent);

	/**
	 * 根据 sn 保存 Agent，若已存在则跳过
	 */
	void saveBySn(Agent agent);

	/**
	 * 生成 API Key
	 */
	Agent generateApiKey(Long id);

	/**
	 * 重置 API Key
	 */
	Agent resetApiKey(Long id);

	/**
	 * 管理端智能体列表：**只返回平台内创建的智能体**（`sn` 为空）。
	 *
	 * <p>Java 自注册类（BpmReactAgent/ZhiduReactAgent/ParolCompiledGraph/HumanInTheLoop/RulesHarnessAgent）
	 * 每次启动都会 `saveBySn` 写库，它们的 `sn` 非空；这些存量智能体不再出现在列表里，
	 * 但保留在库中且运行能力不变（可被 agentId 直接调用），属可逆的产品取舍。
	 *
	 * @param status 可选状态过滤（draft/published/offline）
	 * @param keyword 可选关键字
	 */
	List<Agent> listCreatedInPlatform(String status, String keyword);

	/**
	 * 删除 Agent
	 */
	void deleteById(Long id);

	/**
	 * 智能体已授权组 id 集（agent-publish-group-grant T-01）。
	 */
	java.util.List<String> getGrantGroupIds(Long agentId);

	/**
	 * 覆盖式重写组授权（物理删后重建，镜像技能 replaceGroupGrants）。
	 * 目标组不存在抛 ResponseStatusException(BAD_REQUEST)。空列表=清空（新语义即全公开）。
	 */
	void replaceGroupGrants(Long agentId, java.util.List<String> groupIds, String operator);

	/**
	 * 删除 API Key
	 */
	Agent deleteApiKey(Long id);

	/**
	 * 切换 API Key 的启用状态
	 */
	Agent toggleApiKey(Long id, boolean enabled);

	/**
	 * 获取脱敏后的 API Key
	 */
	String getApiKeyMasked(Long id);

}
