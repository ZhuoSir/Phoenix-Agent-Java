package com.phoenix.agent.service;

import com.mybatisflex.core.paginate.Page;
import com.phoenix.tools.vo.ReturnVo;
import com.phoenix.agent.vo.SkillDetailVO;
import com.phoenix.agent.vo.SkillListVO;

/**
 * 技能管理后台服务（R-01/02/03/04/07/08）。
 * 返回值风格跟随模块周边（LoginServiceImpl 等直接 ReturnVo）。
 */
public interface SkillAdminService {

    /**
     * 技能分页列表（管理视角全量，keyword 匹配名称/描述）。
     */
    ReturnVo<Page<SkillListVO>> page(String keyword, String status, int pageNum, int pageSize);

    /**
     * 技能详情：SKILL.md 正文 + 资源清单。
     */
    ReturnVo<SkillDetailVO> detail(Long id);
}
