package com.phoenix.privilege.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 账号启用/禁用入参（R-15，v2.4.0）。
 *
 * <p>status 语义与库中一致：0 = 启用（可登录）、1 = 禁用（登录被拒，见 LoginServiceImpl）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrivilegeUserStatusDTO {

	/** 账号 id */
	private String id;

	/** 目标状态：0 启用 / 1 禁用 */
	private Integer status;

}
