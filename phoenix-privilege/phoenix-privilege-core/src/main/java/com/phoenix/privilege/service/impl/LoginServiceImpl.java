package com.phoenix.privilege.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.crypto.SecureUtil;
import com.phoenix.privilege.constant.LoginConstant;
import com.phoenix.privilege.dto.LoginInfoDTO;
import com.phoenix.privilege.entity.*;
import com.phoenix.privilege.enums.AuthErrorCode;
import com.phoenix.privilege.service.*;
import com.phoenix.privilege.vo.LoginUserInfoVO;
import com.phoenix.privilege.vo.ModuleTreeVO;
import com.phoenix.privilege.vo.PrivilegePvalueVO;
import com.phoenix.privilege.vo.UserMenuVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

import static com.phoenix.privilege.constant.CommonConstant.LOGIN_USER_INFO;

@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

	private final IPrivilegeUserService privilegeUserService;

	private final IPrivilegeLoginLogService privilegeLoginLogService;

	private final IPrivilegeUserRoleService privilegeUserRoleService;

	private final IPrivilegeModuleService privilegeModuleService;

	private final IPrivilegeAclService privilegeAclService;

	private final IPrivilegePvalueService privilegePvalueService;

	private final IPrivilegeRoleService privilegeRoleService;

	private final ObjectMapper objectMapper;

	/**
	 * 超级管理员角色硬编码口径（沿用 AgentKnowledgeMapper.canReadKnowledgeSource 的既有约定）。 另同时接受
	 * sn=ROLE_ADMIN（忽略大小写）——两者满足其一即视为超管，避免单一口径失配导致 管理员反而看不到菜单。
	 */
	private static final String SUPER_ADMIN_ROLE_ID = "428007432736870400";

	@Override
	public ReturnVo<LoginUserInfoVO> login(LoginInfoDTO loginDTO, String ip) {
		/*
		 * CaptchaVerifyResult captchaResult =
		 * captchaService.verify(loginDTO.getCaptchaKey(), loginDTO.getCaptchaCode()); if
		 * (captchaResult == CaptchaVerifyResult.EXPIRED) { return
		 * ReturnVo.fail(AuthErrorCode.CAPTCHA_EXPIRED.getMessage(),
		 * AuthErrorCode.CAPTCHA_EXPIRED.getCode()); } if (captchaResult ==
		 * CaptchaVerifyResult.INVALID) { return
		 * ReturnVo.fail(AuthErrorCode.CAPTCHA_INVALID.getMessage(),
		 * AuthErrorCode.CAPTCHA_INVALID.getCode()); }
		 */
		if (loginDTO != null) {
			PrivilegeUser user = privilegeUserService.getByUsername(loginDTO.getUsername());
			if (user == null) {
				return ReturnVo.fail(AuthErrorCode.LOGIN_FAIL.getMessage(), AuthErrorCode.LOGIN_FAIL.getCode());
			}
			if (user.getStatus() == 1) {
				return ReturnVo.fail(AuthErrorCode.USER_DISABLED.getMessage(), AuthErrorCode.USER_DISABLED.getCode());
			}
			String hashedPassword = SecureUtil.md5(LoginConstant.PASSWORD_SALT + loginDTO.getPassword());
			if (!hashedPassword.equals(user.getPassword())) {
				// BUG-05(T-01)：登录错密码误用改密语义码 23007——文案对码值错的历史半修，纠正为 23009
				return ReturnVo.fail(AuthErrorCode.PASSWORD_ERROR.getMessage(), AuthErrorCode.PASSWORD_ERROR.getCode());
			}
			StpUtil.login(user.getId());
			// R-05（v2.0.0）：不再写 session ACL 快照 —— 菜单/权限位改为每次实时按角色查询，
			// 以免"改角色后必须重新登录才生效"（旧快照路径已删除）
			StpUtil.getSession().set(LOGIN_USER_INFO, user);
			String tokenValue = StpUtil.getTokenValue();
			// T-05：落地页判定依据——该账号是否持有后台角色
			// R-14（v2.3.0）：user_type（自建/IDM）已下线，不再出现在登录响应中
			List<PrivilegeUserRole> userRoles = privilegeUserRoleService.getByUserId(user.getId());
			boolean hasAdminRole = userRoles != null && !userRoles.isEmpty();
			LoginUserInfoVO loginVO = LoginUserInfoVO.builder()
				.token(tokenValue)
				.userId(user.getId())
				.username(user.getUsername())
				.realName(user.getRealName())
				.hasAdminRole(hasAdminRole)
				.build();
			PrivilegeLoginLog loginLog = PrivilegeLoginLog.builder()
				.operationId(user.getId())
				.operationUsername(user.getUsername())
				.operationPerson(user.getRealName())
				.ip(ip)
				.operationContent(LoginConstant.LOGIN_SUCCESS_OPERATION)
				.build();
			loginLog.setCreateTime(new Date());
			privilegeLoginLogService.save(loginLog);
			return ReturnVo.ok(loginVO);
		}
		return ReturnVo.fail("无效登录");
	}

	@Override
	public ReturnVo<Void> logout() {
		// BUG-54：登出幂等——token 失效/重复登出时 StpUtil.logout() 抛 NotLogin 不应冒 500，
		// 无登录态本就是登出想要的终态
		try {
			StpUtil.logout();
		}
		catch (Exception ignored) { // NOSONAR
		}
		return ReturnVo.ok(LoginConstant.LOGOUT_SUCCESS);
	}

	@Override
	@Transactional(readOnly = true)
	public ReturnVo<UserMenuVO> getUserMenus() {
		PrivilegeUser user = getPrivilegeUserFromSession();
		if (user == null) {
			return ReturnVo.fail("用户未登录");
		}
		// R-05（v2.0.0）：菜单与权限位**按角色 ACL 过滤**，不再"全放开"。
		// ① 超管：全部存活菜单 + 全权限位（豁免口径见 SUPER_ADMIN_ROLE_ID / ROLE_ADMIN）
		// ② 其他：按当前用户所有角色的 ACL 合并（buildUserAclMap 实时查库）
		// —— 刻意**不读 session 里的 ACL 快照**，使「改角色后无需重新登录即生效」
		List<PrivilegeModule> allModules = privilegeModuleService.list();
		List<PrivilegePvalue> allPvalues = privilegePvalueService.list();

		boolean superAdmin = isSuperAdmin(user.getId());
		Map<String, String> aclMap = superAdmin ? buildAdminAclMap(allModules, allPvalues)
				: buildUserAclMap(user.getId());

		// 仅保留被授权的菜单，并自动补全其祖先目录（否则子菜单会因父节点缺失而从树里消失）
		List<PrivilegeModule> visibleModules = filterGrantedModules(allModules, aclMap);
		List<ModuleTreeVO> menuTree = buildModuleTree(visibleModules, aclMap, allPvalues);
		// 按钮级权限位：只下发「角色实际授予的位」对应的 pvalue（超管=全部）
		List<PrivilegePvalueVO> pvalueVOs = resolveGrantedPvalues(allPvalues, aclMap, superAdmin);
		UserMenuVO vo = UserMenuVO.builder().menus(menuTree).pvalues(pvalueVOs).build();
		return ReturnVo.ok(vo);
	}

	private PrivilegeUser getPrivilegeUserFromSession() {
		Object value = StpUtil.getSession().get(LOGIN_USER_INFO);
		if (value instanceof PrivilegeUser pUser) {
			return pUser;
		}
		if (value instanceof Map<?, ?> map) {
			return objectMapper.convertValue(map, PrivilegeUser.class);
		}
		if (value instanceof String str) {
			try {
				return objectMapper.readValue(str, PrivilegeUser.class);
			}
			catch (Exception e) {
				log.warn("Failed to parse PrivilegeUser from session string", e);
			}
		}
		return null;
	}

	private Map<String, String> buildAdminAclMap(List<PrivilegeModule> modules, List<PrivilegePvalue> pvalues) {
		Map<String, String> map = new HashMap<>();
		for (PrivilegeModule module : modules) {
			int state = 0;
			for (PrivilegePvalue pv : pvalues) {
				state |= (1 << pv.getPosition());
			}
			map.put(module.getId(), String.valueOf(state));
		}
		return map;
	}

	/** 按当前用户所有角色的 ACL 合并出「模块 → 权限位」映射（实时查库，不使用 session 快照） */
	private Map<String, String> buildUserAclMap(String userId) {
		List<PrivilegeUserRole> userRoles = privilegeUserRoleService.getByUserId(userId);
		Map<String, Integer> merged = new HashMap<>();
		for (PrivilegeUserRole ur : userRoles) {
			List<PrivilegeAcl> acls = privilegeAclService.queryChain()
				.eq(PrivilegeAcl::getReleaseId, ur.getRoleId())
				.eq(PrivilegeAcl::getReleaseSn, "role")
				.list();
			for (PrivilegeAcl acl : acls) {
				// 位或合并多角色授权；aclState 为 NULL 时按 0 处理（避免拆箱 NPE）
				int state = acl.getAclState() == null ? 0 : acl.getAclState();
				merged.merge(acl.getModuleId(), state, (oldVal, newVal) -> oldVal | newVal);
			}
		}
		Map<String, String> result = new HashMap<>();
		merged.forEach((k, v) -> result.put(k, String.valueOf(v)));
		return result;
	}

	/**
	 * 是否超级管理员：持有硬编码超管角色 id（沿用 AgentKnowledgeMapper 既有口径）， 或持有
	 * sn=ROLE_ADMIN（忽略大小写）的角色。两者满足其一即豁免，避免单一口径失配 导致管理员自己反而看不到菜单。
	 */
	private boolean isSuperAdmin(String userId) {
		List<PrivilegeUserRole> userRoles = privilegeUserRoleService.getByUserId(userId);
		if (userRoles == null || userRoles.isEmpty()) {
			return false;
		}
		List<String> roleIds = userRoles.stream()
			.map(PrivilegeUserRole::getRoleId)
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		if (roleIds.isEmpty()) {
			return false;
		}
		if (roleIds.contains(SUPER_ADMIN_ROLE_ID)) {
			return true;
		}
		// 注意：库里 sn 实际存大写 'COMMON'/'ROLE_ADMIN'，比较一律忽略大小写（同类坑见 BUG-123）
		return privilegeRoleService.listByIds(roleIds)
			.stream()
			.anyMatch(r -> r.getSn() != null && "ROLE_ADMIN".equalsIgnoreCase(r.getSn()));
	}

	/**
	 * 只保留「已授权」的菜单，并向上补全其祖先目录。 必要性：前端按 pid 建树 —— 若某叶子被授权而其父目录未授权，该叶子既不会成为根节点、
	 * 也不会挂到任何父节点下，导致**静默消失**。补全祖先保证"授了子菜单就一定看得到"。
	 */
	private List<PrivilegeModule> filterGrantedModules(List<PrivilegeModule> allModules, Map<String, String> aclMap) {
		Map<String, PrivilegeModule> byId = new HashMap<>();
		for (PrivilegeModule m : allModules) {
			byId.put(m.getId(), m);
		}
		Set<String> keep = new LinkedHashSet<>();
		for (PrivilegeModule m : allModules) {
			if (!aclMap.containsKey(m.getId())) {
				continue;
			}
			keep.add(m.getId());
			String pid = m.getPid();
			int guard = 0;
			while (pid != null && !pid.isEmpty() && guard++ < 16) {
				if (!keep.add(pid)) {
					break;
				}
				PrivilegeModule parent = byId.get(pid);
				pid = (parent == null) ? null : parent.getPid();
			}
		}
		return allModules.stream().filter(m -> keep.contains(m.getId())).toList();
	}

	/**
	 * 按钮级权限位：只下发「角色实际授予的位」对应的 pvalue（超管=全部）。 与旧行为相反 —— 旧实现无条件下发全部 pvalue，使前端
	 * hasAccessByCodes 恒真。
	 */
	private List<PrivilegePvalueVO> resolveGrantedPvalues(List<PrivilegePvalue> allPvalues, Map<String, String> aclMap,
			boolean superAdmin) {
		int grantedBits = 0;
		if (!superAdmin) {
			for (String state : aclMap.values()) {
				try {
					grantedBits |= Integer.parseInt(state);
				}
				catch (NumberFormatException ignored) {
					// 脏数据（非数字 state）忽略，不影响其余权限位
				}
			}
		}
		final int bits = grantedBits;
		final boolean all = superAdmin;
		return allPvalues.stream().filter(p -> {
			if (all) {
				return true;
			}
			Integer pos = p.getPosition();
			return pos != null && pos >= 0 && pos < 31 && (bits & (1 << pos)) != 0;
		}).map(p -> BeanUtil.copyProperties(p, PrivilegePvalueVO.class)).toList();
	}

	private List<ModuleTreeVO> buildModuleTree(List<PrivilegeModule> allModules, Map<String, String> moduleAclMap,
			List<PrivilegePvalue> allPvalues) {
		List<ModuleTreeVO> nodeList = allModules.stream().map(m -> {
			ModuleTreeVO node = BeanUtil.copyProperties(m, ModuleTreeVO.class);
			int state = 0;
			String aclState = moduleAclMap.get(m.getId());
			if (aclState != null) {
				try {
					state = Integer.parseInt(aclState);
				}
				catch (NumberFormatException ignored) {
					state = 0;
				}
			}
			node.setState(state);
			// 每个菜单各自带一份权限位清单（enabled=该位是否授予），供前端按菜单渲染操作按钮
			final int moduleState = state;
			node.setPvalues(allPvalues.stream().map(pv -> {
				Integer pos = pv.getPosition();
				return ModuleTreeVO.PvalueInfo.builder()
					.pvalueId(pv.getId())
					.pvalueName(pv.getName())
					.position(pos)
					.orderNo(pv.getOrderNo())
					.enabled(pos != null && pos >= 0 && pos < 31 && (moduleState & (1 << pos)) != 0)
					.build();
			}).toList());
			return node;
		}).toList();

		Map<String, List<ModuleTreeVO>> parentMap = nodeList.stream()
			.collect(Collectors.groupingBy(n -> n.getPid() != null ? n.getPid() : "", Collectors.toList()));

		List<ModuleTreeVO> roots = new ArrayList<>();
		for (ModuleTreeVO node : nodeList) {
			List<ModuleTreeVO> children = parentMap.getOrDefault(node.getId(), List.of());
			node.setChildren(children);
			if (node.getPid() == null || node.getPid().isEmpty()) {
				roots.add(node);
			}
		}
		return roots;
	}

}
