package com.phoenix.privilege.service;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.service.IService;
import com.phoenix.privilege.dto.query.PrivilegeRoleQuery;
import com.phoenix.privilege.entity.PrivilegeRole;
import com.phoenix.privilege.vo.PrivilegeRoleVO;
import com.phoenix.privilege.vo.RoleAclVO;

import java.util.List;

public interface IPrivilegeRoleService extends IService<PrivilegeRole> {

	List<RoleAclVO> getRoleAcls(String roleId);

	Page<PrivilegeRoleVO> pageByQuery(PrivilegeRoleQuery query);

	/**
	 * 删除角色（R-17，v2.6.0）。
	 *
	 * <p>保护规则：① 内置管理员角色（`sn=ROLE_ADMIN`）**绝不可删**；② 仍有用户持有（有效绑定>0）的角色不可删。
	 *
	 * @return true=已删除；false=被保护规则拒绝（未做任何变更）
	 */
	boolean deleteRoleById(String roleId);

	/** 角色当前的有效持有者数量（R-17；用于删除保护与提示） */
	long countHolders(String roleId);

	/** 是否内置管理员角色（sn=ROLE_ADMIN，忽略大小写；R-17） */
	boolean isBuiltInAdminRole(String roleId);

}
