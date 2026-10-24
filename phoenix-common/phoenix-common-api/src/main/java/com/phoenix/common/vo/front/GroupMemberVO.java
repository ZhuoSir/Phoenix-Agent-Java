package com.phoenix.common.vo.front;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 组成员（分页）视图对象。
 *
 * <p>用于组详情页「人员」页签：关联表提供加入时间，账号表提供账号/姓名/状态。
 * 说明：当前数据模型无"角色"列（`tbl_platform_account_info` 无 role 字段），故以账号状态 `accountStatus` 呈现，
 * 角色能力待账号体系补齐后再加。
 */
@Data
public class GroupMemberVO implements Serializable {

	/** 关联记录 id（account_group_info.id） */
	private String relationId;

	private String accountId;

	/** 账号（account_info.username） */
	private String username;

	/** 姓名（account_info.real_name） */
	private String realName;

	/** 账号状态（account_info.status，沿用账号域语义） */
	private String accountStatus;

	/** 加入时间（关联记录 create_time） */
	@JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
	private Date joinTime;

}
