package com.phoenix.privilege.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUserInfoVO {

	private String token;

	private String userId;

	private String username;

	private String realName;

	/** T-05（统一账号中心）：是否持有后台角色——前端落地页按此判定 */
	private Boolean hasAdminRole;

}
