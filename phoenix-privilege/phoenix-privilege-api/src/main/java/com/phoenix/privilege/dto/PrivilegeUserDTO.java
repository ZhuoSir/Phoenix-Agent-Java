package com.phoenix.privilege.dto;

import com.mybatisflex.annotation.Table;
import com.phoenix.privilege.entity.PrivilegeUser;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.BeanUtils;

import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("tbl_privilege_user")
public class PrivilegeUserDTO {

	public PrivilegeUser toEntity() {
		PrivilegeUser entity = new PrivilegeUser();
		BeanUtils.copyProperties(this, entity);
		return entity;
	}

	private String id;

	private String code;

	private String realName;

	private String username;

	private String password;

	private String tel;

	private String phone;

	private String mobile;

	private String email;

	private byte[] image;

	private Integer sex;

	private String address;

	private String fax;

	private Integer failMonth;

	private Date failureTime;

	private Integer aclTimestamp;

	private Date pwdFtime;

	private Integer pwdInit;

	/**
	 * R-03（v2.0.0）：创建/更新时一并授予的角色 id 集合。
	 * null = 不改动（更新场景）；空列表 = 清空；创建时为空则走默认角色兜底。
	 */
	private List<String> roleIds;

	/** R-03（v2.0.0）：创建/更新时一并加入的组 id 集合。null = 不改动；空列表 = 清空。 */
	private List<String> groupIds;

	private Integer userType;

	private String keyword;

	private String createBy;

}
