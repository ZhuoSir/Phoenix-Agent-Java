package com.phoenix.agent.mapper;

import com.mybatisflex.core.BaseMapper;
import com.phoenix.agent.model.AgentFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话产物文件登记 Mapper（BL-19）。查询一律走 QueryChain 显式列，禁 SELECT *。
 */
@Mapper
public interface AgentFileMapper extends BaseMapper<AgentFile> {
}
