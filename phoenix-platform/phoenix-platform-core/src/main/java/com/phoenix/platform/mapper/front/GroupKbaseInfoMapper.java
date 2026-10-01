package com.phoenix.platform.mapper.front;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.platform.model.front.GroupKbaseInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 组×知识库 Mapper（显式列，禁 SELECT *）。
 */
@Mapper
public interface GroupKbaseInfoMapper extends BaseMapper<GroupKbaseInfo> {

	@Select("SELECT kbase_id FROM tbl_platform_group_kbase_info WHERE group_id = #{groupId} AND del_flag = 0")
	List<Long> selectKbaseIdsByGroup(@Param("groupId") String groupId);

	@Select("SELECT group_id FROM tbl_platform_group_kbase_info WHERE kbase_id = #{kbaseId} AND del_flag = 0")
	List<String> selectGroupIdsByKbase(@Param("kbaseId") Long kbaseId);
}
