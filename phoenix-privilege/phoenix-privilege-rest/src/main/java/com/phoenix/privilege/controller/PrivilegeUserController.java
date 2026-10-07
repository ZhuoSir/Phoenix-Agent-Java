package com.phoenix.privilege.controller;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.phoenix.privilege.dto.PasswordUpdateDTO;
import com.phoenix.privilege.dto.PrivilegeUserBatchStatusDTO;
import com.phoenix.privilege.dto.PrivilegeUserDTO;
import com.phoenix.privilege.dto.PrivilegeUserStatusDTO;
import com.phoenix.privilege.entity.PrivilegeUser;
import com.phoenix.privilege.enums.AuthErrorCode;
import com.phoenix.privilege.service.IPrivilegeUserService;
import com.phoenix.privilege.vo.PrivilegeUserVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/privilege/user")
@RequiredArgsConstructor
public class PrivilegeUserController {

	private final IPrivilegeUserService privilegeUserService;

	/**
	 * 分页查询用户列表
	 */
	@GetMapping("/page")
	public ReturnVo<Page<PrivilegeUserVO>> page(@RequestParam(defaultValue = "1") long pageNumber,
			@RequestParam(defaultValue = "10") long pageSize, PrivilegeUserDTO dto) {
		return ReturnVo.ok(privilegeUserService.pageByQuery(new Page<>(pageNumber, pageSize), dto));
	}

	@GetMapping("/{id}")
	public ReturnVo<PrivilegeUserVO> getById(@PathVariable Long id) {
		return ReturnVo.ok(toVo(privilegeUserService.getById(id)));
	}

	@GetMapping("/username/{username}")
	public ReturnVo<PrivilegeUserVO> getByUsername(@PathVariable String username) {
		return ReturnVo.ok(toVo(privilegeUserService.getByUsername(username)));
	}

	/**
	 * 启用/禁用单个账号（R-15，v2.4.0）。
	 *
	 * <p>安全保护：① 禁止操作**当前登录账号本人**（防自锁）；② 禁止停用**最后一个启用的超管**（防失管）。
	 */
	@PutMapping("/status")
	public ReturnVo<Boolean> updateStatus(@RequestBody PrivilegeUserStatusDTO dto) {
		String currentUserId = StpUtil.getLoginIdAsString();
		if (StrUtil.equals(currentUserId, dto.getId())) {
			return ReturnVo.fail("不能对当前登录账号执行启用/禁用操作");
		}
		if (!privilegeUserService.canDisable(List.of(dto.getId())) && isDisable(dto.getStatus())) {
			return ReturnVo.fail("系统必须保留至少一个启用的超管账号");
		}
		return ReturnVo.ok(privilegeUserService.updateStatus(dto.getId(), dto.getStatus()));
	}

	/**
	 * 批量启用/禁用（R-15）。保护规则同单个：所选若含当前登录账号则整体拒绝。
	 */
	@PutMapping("/status/batch")
	public ReturnVo<Integer> updateStatusBatch(@RequestBody PrivilegeUserBatchStatusDTO dto) {
		List<String> ids = dto.getIds() == null ? List.of() : dto.getIds().stream().filter(StrUtil::isNotBlank).toList();
		if (ids.isEmpty()) {
			return ReturnVo.fail("请先勾选账号");
		}
		String currentUserId = StpUtil.getLoginIdAsString();
		if (ids.contains(currentUserId)) {
			return ReturnVo.fail("所选账号包含当前登录账号，请取消后再操作");
		}
		if (isDisable(dto.getStatus()) && !privilegeUserService.canDisable(ids)) {
			return ReturnVo.fail("系统必须保留至少一个启用的超管账号");
		}
		return ReturnVo.ok(privilegeUserService.updateStatusBatch(ids, dto.getStatus()));
	}

	private boolean isDisable(Integer status) {
		return status != null && status == 1;
	}

	private PrivilegeUserVO toVo(PrivilegeUser entity) {
		if (entity == null) {
			return null;
		}
		PrivilegeUserVO vo = BeanUtil.copyProperties(entity, PrivilegeUserVO.class);
		// 组织维度已下线（v2.0.0）：不再回填 companyName / deptName
		return vo;
	}

	/**
	 * 新增用户（校验用户名和手机号唯一性）
	 */
	@PostMapping
	public ReturnVo<Boolean> save(@RequestBody PrivilegeUserDTO dto) {
		if (privilegeUserService.checkUsernameExist(dto.getUsername())) {
			return ReturnVo.fail(AuthErrorCode.USERNAME_EXIST.getMessage(), AuthErrorCode.USERNAME_EXIST.getCode());
		}
		if (StrUtil.isNotBlank(dto.getMobile()) && privilegeUserService.checkMobileExist(dto.getMobile())) {
			return ReturnVo.fail(AuthErrorCode.MOBILE_EXIST.getMessage(), AuthErrorCode.MOBILE_EXIST.getCode());
		}
		return ReturnVo.ok(privilegeUserService.saveUser(dto));
	}

	/**
	 * 更新用户信息
	 */
	@PutMapping
	public ReturnVo<Boolean> update(@RequestBody PrivilegeUserDTO dto) {
		// R-03（v2.0.0）：改走 updateUser —— 除基本信息外，roleIds/groupIds 非 null 时同步角色与组
		return ReturnVo.ok(privilegeUserService.updateUser(dto));
	}

	/**
	 * 删除用户
	 */
	@DeleteMapping("/{id}")
	public ReturnVo<Boolean> delete(@PathVariable String id) {
		return ReturnVo.ok(privilegeUserService.deleteUser(id));
	}

	/**
	 * 修改密码（需校验原密码）
	 */
	@PutMapping("/password")
	public ReturnVo<Void> updatePassword(@RequestBody PasswordUpdateDTO dto) {
		if (!privilegeUserService.updatePassword(dto.getUserId(), dto.getOldPassword(), dto.getNewPassword())) {
			return ReturnVo.fail(AuthErrorCode.OLD_PASSWORD_ERROR.getMessage(),
					AuthErrorCode.OLD_PASSWORD_ERROR.getCode());
		}
		return ReturnVo.ok("密码修改成功");
	}
	@PutMapping("/setPassword")
	public ReturnVo<Void> setPassword(@RequestBody PasswordUpdateDTO dto) {
		if (!privilegeUserService.setPassword(dto.getUserId(), dto.getNewPassword())) {
			return ReturnVo.fail("设置密码失败！");
		}
		return ReturnVo.ok("密码修改成功");
	}

	/**
	 * 重置密码（返回随机生成的明文密码）
	 */
	@PutMapping("/reset-password/{id}")
	public ReturnVo<String> resetPassword(@PathVariable String id) {
		String newPassword = privilegeUserService.resetPassword(id);
		if (newPassword == null) {
			return ReturnVo.fail("用户不存在");
		}
		return ReturnVo.ok("重置成功", newPassword);
	}
}
