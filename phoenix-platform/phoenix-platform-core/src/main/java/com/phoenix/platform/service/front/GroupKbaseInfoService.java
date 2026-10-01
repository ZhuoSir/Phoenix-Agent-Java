package com.phoenix.platform.service.front;

import java.util.List;

/**
 * 组×知识库授权服务（R-13）。全量替换语义：以提交集合为准增删该组的授权。
 */
public interface GroupKbaseInfoService {

	void assignKbases(String groupId, List<Long> kbaseIds, String operator);

	List<Long> getKbaseIdsByGroup(String groupId);

	List<String> getGroupIdsByKbase(Long kbaseId);

	/** 组撤授权时**不级联删绑定**（plan T-04 最小语义），仅返回受影响的绑定 agent 数供提示。 */
	long countBoundAgentsOfKbase(Long kbaseId);
}
