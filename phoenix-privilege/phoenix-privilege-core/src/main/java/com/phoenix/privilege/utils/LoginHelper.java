package com.phoenix.privilege.utils;

import cn.dev33.satoken.exception.SaTokenContextException;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.phoenix.common.enm.LoginTypeEnm;
import com.phoenix.common.vo.login.LoginInfo;
import com.phoenix.privilege.entity.PrivilegeUser;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import static com.phoenix.privilege.constant.CommonConstant.LOGIN_USER_INFO;

/**
 * @title:
 * @author: bruce.liu
 * @since: 2026/2/10 8:11
 */
@Slf4j
public class LoginHelper {

	public static LoginInfo getLoginUser(String type) {
		LoginInfo loginInfo = null;
		try {
			if (StpUtil.isLogin()) {
				Object loginId = StpUtil.getLoginId();
				SaSession session = StpUtil.getSessionByLoginId(loginId);
				if (type.equals(LoginTypeEnm.USER.getCode())) {
					// TODO 前台用户登录的构建
				}
				else {
					PrivilegeUser user = getPrivilegeUser(session);
					if (user != null) {
						loginInfo = LoginInfo.builder()
							.id(user.getId())
							.username(user.getUsername())
							.email(user.getEmail())
							.name(user.getRealName())
							.phone(user.getPhone())
							.type(type)
							.build();
					}
				}
			}
		}
		catch (SaTokenContextException e) {
			log.info("No login required to call the API");
		}
		return loginInfo;
	}

	private static PrivilegeUser getPrivilegeUser(SaSession session) {
		Object value = session.get(LOGIN_USER_INFO);
		if (value instanceof PrivilegeUser pUser) {
			return pUser;
		}
		// R-14（v2.3.0）：会话里可能是旧版 PrivilegeUser（含已下线字段）⇒ 必须忽略未知属性
		ObjectMapper mapper = new ObjectMapper()
			.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		if (value instanceof Map<?, ?> map) {
			return mapper.convertValue(map, PrivilegeUser.class);
		}
		if (value instanceof String str) {
			try {
				return mapper.readValue(str, PrivilegeUser.class);
			}
			catch (Exception e) {
				log.warn("Failed to parse PrivilegeUser from session string", e);
			}
		}
		return null;
	}

}
