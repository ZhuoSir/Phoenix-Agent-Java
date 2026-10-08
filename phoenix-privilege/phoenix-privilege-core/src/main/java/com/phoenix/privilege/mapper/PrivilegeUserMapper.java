package com.phoenix.privilege.mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.privilege.dto.PrivilegeUserDTO;
import com.phoenix.privilege.entity.PrivilegeUser;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PrivilegeUserMapper extends BaseMapper<PrivilegeUser> {

	@Delete("delete from tbl_privilege_user where id = #{id}")
	int deletePhysicallyById(@Param("id") String id);

}
