package com.phoenix.privilege.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.phoenix.privilege.dto.PrivilegeUserDTO;
import com.phoenix.privilege.entity.PrivilegeUser;
import com.phoenix.privilege.vo.PrivilegeUserVO;

import java.util.List;

public interface IPrivilegeUserService extends IService<PrivilegeUser> {

	PrivilegeUser getByUsername(String username);

	boolean checkUsernameExist(String username);

	boolean checkMobileExist(String mobile);

	boolean updatePassword(String userId, String oldPassword, String newPassword);
	/** 管理员设置密码*/
	boolean setPassword(String userId, String newPassword);

	String resetPassword(String userId);

	boolean saveUser(PrivilegeUserDTO dto);

	/**
	 * 更新用户（R-03：roleIds/groupIds 非 null 时同步角色与组）。
	 */
	boolean updateUser(PrivilegeUserDTO dto);

	boolean deleteUser(String id);

	Page<PrivilegeUserVO> pageByQuery(Page<PrivilegeUserVO> page, PrivilegeUserDTO dto);

	/**
	 * 是否超管（R-15 抽出统一口径）：持超管角色 id 或角色 sn=ROLE_ADMIN（忽略大小写）。
	 * 登录期判定与启停保护共用同一实现，避免两套口径（同类坑见 BUG-123）。
	 */
	boolean isSuperAdmin(String userId);

	/**
	 * 是否**内置超管账号**（R-16）：`username = 'admin'`（忽略大小写）。
	 * 该账号不可禁用、不可删除、不可改名（服务端强制）。
	 */
	boolean isProtectedAdmin(String userId);

	/**
	 * 启用/禁用单个账号（R-15）。status：0 启用 / 1 禁用。
	 */
	boolean updateStatus(String id, Integer status);

	/**
	 * 批量启用/禁用（R-15）。返回实际更新行数。
	 */
	int updateStatusBatch(List<String> ids, Integer status);

	/**
	 * 停用保护（R-15）：禁用给定账号后，系统是否**仍有**至少一个启用的超管账号。
	 * true = 允许禁用；false = 会失去最后一个启用的超管（应拒绝）。
	 */
	boolean canDisable(List<String> ids);

}
