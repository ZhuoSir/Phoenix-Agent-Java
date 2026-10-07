package com.phoenix.privilege.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.mybatisflex.core.paginate.Page;
import com.phoenix.privilege.dto.PrivilegeRoleDTO;
import com.phoenix.privilege.dto.query.PrivilegeRoleQuery;
import com.phoenix.privilege.service.IPrivilegeRoleService;
import com.phoenix.privilege.vo.PrivilegeRoleVO;
import com.phoenix.privilege.vo.RoleAclVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/privilege/role")
@RequiredArgsConstructor
public class PrivilegeRoleController {

	private final IPrivilegeRoleService privilegeRoleService;

	@PostMapping("/page")
	public ReturnVo<Page<PrivilegeRoleVO>> page(@RequestBody PrivilegeRoleQuery query) {
		return ReturnVo.ok(privilegeRoleService.pageByQuery(query));
	}

	@GetMapping("/{id}")
	public ReturnVo<PrivilegeRoleVO> getById(@PathVariable String id) {
		return ReturnVo.ok(BeanUtil.copyProperties(privilegeRoleService.getById(id), PrivilegeRoleVO.class));
	}

	@PostMapping
	public ReturnVo<Boolean> save(@RequestBody PrivilegeRoleDTO dto) {
		dto.setCreateBy((String) StpUtil.getLoginId());
		return ReturnVo.ok(privilegeRoleService.save(dto.toEntity()));
	}

	@PutMapping
	public ReturnVo<Boolean> update(@RequestBody PrivilegeRoleDTO dto) {
		return ReturnVo.ok(privilegeRoleService.updateById(dto.toEntity()));
	}

	/**
	 * 删除角色（R-17，v2.6.0 加保护）。
	 *
	 * <p>规则（用户口径：「role_admin 不可以删除，除此之外都可以删，但是有用户不能删」）：
	 * ① 内置管理员角色（`sn=ROLE_ADMIN`）**不可删除**；② 角色下**仍有用户**时不可删除（提示持有者数量）。
	 */
	@DeleteMapping("/{id}")
	public ReturnVo<Boolean> delete(@PathVariable String id) {
		if (privilegeRoleService.isBuiltInAdminRole(id)) {
			return ReturnVo.fail("系统管理员角色不可删除");
		}
		long holders = privilegeRoleService.countHolders(id);
		if (holders > 0) {
			return ReturnVo.fail("该角色下仍有 " + holders + " 个用户，请先解除绑定后再删除");
		}
		return ReturnVo.ok(privilegeRoleService.deleteRoleById(id));
	}

	/**
	 * 获取当前角色下的权限制，与选中情况
	 * @param roleId
	 * @return
	 */
	@GetMapping("/{roleId}/acls")
	public ReturnVo<List<RoleAclVO>> getRoleAcls(@PathVariable String roleId) {
		return ReturnVo.ok(privilegeRoleService.getRoleAcls(roleId));
	}

}
