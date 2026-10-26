package com.phoenix.data.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.data.entity.KbRebuildJob;

/** 重建任务台账 Mapper（T-10） */
@Mapper
public interface KbRebuildJobMapper extends BaseMapper<KbRebuildJob> {

}
