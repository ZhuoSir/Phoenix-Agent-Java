package com.phoenix.platform.service.front;

import com.phoenix.platform.vo.BindableKbaseVO;

import java.util.List;

/**
 * 智能体↔知识库绑定（R-08/R-14/R-15）。绑定表写侧在此（platform 可见两张组表），
 * 召回读侧在 data 域绑定表——同库不同层。
 */
public interface AgentKbaseService {

	List<BindableKbaseVO> bindable(Long agentId);

	/** 全量替换绑定；逐项服务端复核组交集（防绕过前端置灰）。 */
	void bind(Long agentId, List<Long> kbaseIds, String operator);
}
