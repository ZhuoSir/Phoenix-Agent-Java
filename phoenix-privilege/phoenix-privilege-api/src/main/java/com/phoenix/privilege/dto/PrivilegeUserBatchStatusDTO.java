package com.phoenix.privilege.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 账号批量启用/禁用入参（R-15，v2.4.0）。
 *
 * <p>status 语义与库中一致：0 = 启用、1 = 禁用。
 * ids 为空时不执行任何更新（返回 0）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrivilegeUserBatchStatusDTO {

	/** 账号 id 集合 */
	private List<String> ids;

	/** 目标状态：0 启用 / 1 禁用 */
	private Integer status;

}
