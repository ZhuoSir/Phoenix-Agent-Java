package com.phoenix.privilege.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.phoenix.privilege.constant.LoginConstant;
import com.phoenix.privilege.dto.PrivilegeUserDTO;
import com.phoenix.privilege.entity.PrivilegeRole;
import com.phoenix.privilege.entity.PrivilegeUser;
import com.phoenix.privilege.entity.PrivilegeUserRole;
import com.phoenix.privilege.mapper.PrivilegeUserMapper;
import com.phoenix.privilege.service.IPrivilegeRoleService;
import com.phoenix.privilege.service.IPrivilegeUserRoleService;
import com.phoenix.privilege.service.IPrivilegeUserService;
import com.phoenix.privilege.vo.PrivilegeRoleVO;
import com.phoenix.privilege.vo.PrivilegeUserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class PrivilegeUserServiceImpl extends ServiceImpl<PrivilegeUserMapper, PrivilegeUser>
		implements IPrivilegeUserService {

	private static final String RESET_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

	private final IPrivilegeRoleService privilegeRoleService;

	private final IPrivilegeUserRoleService privilegeUserRoleService;

	@Override
	public PrivilegeUser getByUsername(String username) {
		return QueryChain.of(getMapper()).eq(PrivilegeUser::getUsername, username).one();
	}

	@Override
	public PrivilegeUser getByCode(String code) {
		return QueryChain.of(getMapper()).eq(PrivilegeUser::getCode, code).one();
	}

	@Override
	public boolean checkUsernameExist(String username) {
		return QueryChain.of(getMapper()).eq(PrivilegeUser::getUsername, username).exists();
	}

	@Override
	public boolean checkMobileExist(String mobile) {
		return QueryChain.of(getMapper()).eq(PrivilegeUser::getMobile, mobile).exists();
	}

	@Override
	public boolean updatePassword(String userId, String oldPassword, String newPassword) {
		PrivilegeUser user = getById(userId);
		if (user == null) {
			return false;
		}
		String hashedOld = SecureUtil.md5(LoginConstant.PASSWORD_SALT + oldPassword);
		if (!hashedOld.equals(user.getPassword())) {
			return false;
		}
		user.setPassword(SecureUtil.md5(LoginConstant.PASSWORD_SALT + newPassword));
		user.setPwdInit(1);
		return updateById(user);
	}

	@Override
	public boolean setPassword(String userId, String newPassword) {
		PrivilegeUser user = getById(userId);
		if (user == null) {
			return false;
		}
		user.setPassword(SecureUtil.md5(LoginConstant.PASSWORD_SALT + newPassword));
		user.setPwdInit(1);
		return updateById(user);
	}

	@Override
	public String resetPassword(String userId) {
		PrivilegeUser user = getById(userId);
		if (user == null) {
			return null;
		}
		String plainPassword = generateRandomPassword(8);
		user.setPassword(SecureUtil.md5(LoginConstant.PASSWORD_SALT + plainPassword));
		user.setPwdInit(0);
		updateById(user);
		return plainPassword;
	}

	@Override
	public boolean saveUser(PrivilegeUserDTO dto) {
		PrivilegeUser entity = dto.toEntity();
		entity.setPassword(SecureUtil.md5(LoginConstant.PASSWORD_SALT + entity.getPassword()));
		entity.setStatus(0);
		entity.setPwdInit(0);
		boolean result = save(entity);
		if (result) {
			// R-03（v2.0.0）：表单显式选了角色就按所选落库（三维度化）；未选才走默认角色兜底
			List<String> roleIds = dto.getRoleIds();
			if (roleIds == null || roleIds.isEmpty()) {
				PrivilegeRole role = findDefaultRole();
				if (role == null) {
					log.warn("默认角色(sn=common)不存在，用户 {} 创建后无任何角色", entity.getUsername());
				}
				else {
					saveUserRole(entity.getId(), entity.getCode(), role.getId());
				}
			}
			else {
				roleIds.stream()
					.filter(StrUtil::isNotBlank)
					.distinct()
					.forEach(roleId -> saveUserRole(entity.getId(), entity.getCode(), roleId));
			}
			// R-03：创建时可直接入组
			addUserGroups(entity.getId(), entity.getCode(), dto.getGroupIds());
			log.info("用户创建完成: id={}, username={}, roleIds={}, groupIds={}", entity.getId(), entity.getUsername(),
					roleIds, dto.getGroupIds());
		}
		return result;
	}

	@Override
	public boolean updateUser(PrivilegeUserDTO dto) {
		boolean result = updateById(dto.toEntity());
		if (result) {
			// null = 不改动（兼容既有「分配角色/分配组」弹窗路径）；空列表 = 清空
			if (dto.getRoleIds() != null) {
				privilegeUserRoleService.removeUserRoleByUserId(dto.getId());
				dto.getRoleIds()
					.stream()
					.filter(StrUtil::isNotBlank)
					.distinct()
					.forEach(roleId -> saveUserRole(dto.getId(), dto.getCode(), roleId));
			}
			if (dto.getGroupIds() != null) {
				Db.updateBySql("delete from tbl_platform_account_group_info where account_id = ?", dto.getId());
				addUserGroups(dto.getId(), dto.getCode(), dto.getGroupIds());
			}
		}
		return result;
	}

	/**
	 * 默认角色（BUG-123 修复）：库中 sn 实存**大写** 'COMMON'，旧实现按小写 'common' 等值匹配
	 * ⇒ 永远查不到 ⇒ 「自动补默认角色」从未生效（API 建的用户全部无角色）。
	 * 改为 Java 侧忽略大小写匹配，与 LoginServiceImpl.isSuperAdmin 同思路，避免再被大小写坑。
	 */
	private PrivilegeRole findDefaultRole() {
		return privilegeRoleService.list()
			.stream()
			.filter(r -> r.getSn() != null && "common".equalsIgnoreCase(r.getSn()))
			.findFirst()
			.orElse(null);
	}

	private void saveUserRole(String userId, String userNo, String roleId) {
		privilegeUserRoleService.save(PrivilegeUserRole.builder()
			.userId(userId)
			.userNo(userNo)
			.roleId(roleId)
			.build());
	}

	/**
	 * 入组（R-03）。account_group_info 属平台域，phoenix-privilege 不依赖平台模块
	 * ⇒ 沿用同仓跨域裸 SQL 先例（AgentServiceImpl 写 tbl_platform_group_agent_info）。
	 *
	 * <p>用 insert...select 一次取到 group_name，并顺带跳过不存在/已删除的组；
	 * **del_flag 必须显式置 0** —— 该列 DDL 默认值是 1（"已删除"），漏写会插入"出生即删除"的行
	 * （同一坑见 T-01 实勘记录）。
	 */
	private void addUserGroups(String userId, String accountName, List<String> groupIds) {
		if (groupIds == null || groupIds.isEmpty()) {
			return;
		}
		for (String groupId : groupIds.stream().filter(StrUtil::isNotBlank).distinct().toList()) {
			int inserted = Db.insertBySql(
					"insert into tbl_platform_account_group_info (id, group_id, account_id, group_name, account_name, creator, create_time, update_time, del_flag) "
							+ "select ?, g.id, ?, g.name, ?, ?, now(), now(), 0 from tbl_platform_group_info g "
							+ "where g.id = ? and coalesce(g.del_flag, 0) = 0",
					cn.hutool.core.util.IdUtil.getSnowflakeNextIdStr(), userId, accountName, userId, groupId);
			if (inserted == 0) {
				log.warn("组不存在或已删除，跳过入组: userId={}, groupId={}", userId, groupId);
			}
		}
	}

	@Override
	public boolean deleteUser(String id) {
		privilegeUserRoleService.removeUserRoleByUserId(id);
		return this.removeById(id);
	}

	@Override
	public Page<PrivilegeUserVO> pageByQuery(Page<PrivilegeUserVO> page, PrivilegeUserDTO dto) {
		// 组织维度已下线（v2.0.0）：不再 leftJoin 公司/部门，也不再按 companyId/deptId 过滤
		QueryWrapper qw = QueryWrapper.create()
			.select("tbl_privilege_user.*")
			.eq(PrivilegeUser::getUserType, dto.getUserType(), dto.getUserType() != null);
		if (StrUtil.isNotBlank(dto.getKeyword())) {
			qw.and((Consumer<QueryWrapper>) w -> w.like(PrivilegeUser::getCode, dto.getKeyword())
				.or(PrivilegeUser::getUsername)
				.like(dto.getKeyword())
				.or(PrivilegeUser::getRealName)
				.like(dto.getKeyword())
				.or(PrivilegeUser::getPhone)
				.like(dto.getKeyword()));
		}
		qw.orderBy(PrivilegeUser::getCreateTime, false);
		Page<PrivilegeUser> entityPage = getMapper().paginate(page.getPageNumber(), page.getPageSize(), qw);
		Page<PrivilegeUserVO> voPage = new Page<>(entityPage.getPageNumber(), entityPage.getPageSize(),
				entityPage.getTotalRow());
		List<PrivilegeUser> records = entityPage.getRecords();
		if (records.isEmpty()) {
			voPage.setRecords(List.of());
			return voPage;
		}
		List<String> userIds = records.stream().map(PrivilegeUser::getId).toList();
		List<PrivilegeUserRole> userRoles = privilegeUserRoleService
			.list(QueryWrapper.create().in(PrivilegeUserRole::getUserId, userIds));
		List<String> roleIds = userRoles.stream().map(PrivilegeUserRole::getRoleId).distinct().toList();
		Map<String, String> roleNameMap = new HashMap<>();
		if (!roleIds.isEmpty()) {
			roleNameMap = privilegeRoleService.listByIds(roleIds)
				.stream()
				.collect(HashMap::new, (m, r) -> m.put(r.getId(), r.getName()), HashMap::putAll);
		}
		Map<String, List<PrivilegeRoleVO>> userRolesMap = new HashMap<>();
		for (PrivilegeUserRole ur : userRoles) {
			userRolesMap.computeIfAbsent(ur.getUserId(), k -> new ArrayList<>())
				.add(PrivilegeRoleVO.builder()
					.id(ur.getRoleId())
					.name(roleNameMap.getOrDefault(ur.getRoleId(), ""))
					.build());
		}
		List<PrivilegeUserVO> voList = records.stream().map(e -> {
			PrivilegeUserVO vo = BeanUtil.copyProperties(e, PrivilegeUserVO.class);
			vo.setPassword(null);
			vo.setRoles(userRolesMap.getOrDefault(e.getId(), List.of()));
			return vo;
		}).toList();
		voPage.setRecords(voList);
		return voPage;
	}

	private String generateRandomPassword(int length) {
		Random random = new Random();
		StringBuilder sb = new StringBuilder(length);
		for (int i = 0; i < length; i++) {
			sb.append(RESET_CHARS.charAt(random.nextInt(RESET_CHARS.length())));
		}
		return sb.toString();
	}

}
