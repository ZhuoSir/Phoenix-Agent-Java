package com.phoenix.data.component;

import com.mybatisflex.core.row.Db;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 管理员角色守卫（R-08）：判定当前登录账号是否持有后台超级管理员角色。
 *
 * <p>口径与 {@code LoginServiceImpl.isSuperAdmin} 保持一致（双条件豁免）：
 * 硬编码超管 role_id（沿用 {@code AgentKnowledgeMapper.canReadKnowledgeSource} 既有约定）
 * <b>或</b> 角色 {@code sn=ROLE_ADMIN}（忽略大小写，库中实存大写）。
 *
 * <p>跨域读 privilege 表用裸 SQL —— 与同仓 {@code AgentKnowledgeMapper} 的既有做法一致
 * （phoenix-data 不依赖 phoenix-privilege 的 service 层）。
 */
@Slf4j
@Component
public class AdminRoleGuard {

	/** 超级管理员角色 id（tbl_privilege_role.id，与 AgentKnowledgeMapper 同口径）。 */
	private static final String SUPER_ADMIN_ROLE_ID = "428007432736870400";

	private static final String SQL = """
		select count(*)
		  from tbl_privilege_user_role ur
		  left join tbl_privilege_role r on r.id = ur.role_id
		 where ur.user_id = ?
		   and coalesce(ur.del_flag, 0) = 0
		   and (ur.role_id = ? or upper(r.sn) = 'ROLE_ADMIN')
		""";

	/**
	 * 是否管理员。
	 * @param userId 登录账号 id（可为 null ⇒ 直接返回 false，不抛异常）
	 */
	public boolean isAdmin(String userId) {
		if (userId == null || userId.isBlank()) {
			return false;
		}
		try {
			Object count = Db.selectObject(SQL, userId, SUPER_ADMIN_ROLE_ID);
			return count != null && ((Number) count).longValue() > 0;
		}
		catch (Exception e) {
			// 判据不可用时应"拒绝"而非"放行"（fail-closed），并留日志便于排障
			log.error("管理员角色判定失败, userId={}, 按无权限处理", userId, e);
			return false;
		}
	}

}
