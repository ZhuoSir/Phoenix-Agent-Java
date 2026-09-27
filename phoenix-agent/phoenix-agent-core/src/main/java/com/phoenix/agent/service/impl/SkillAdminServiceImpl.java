package com.phoenix.agent.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.phoenix.agent.enums.SkillErrorCodeEnm;
import com.phoenix.agent.mapper.HarnessSkillMapper;
import com.phoenix.agent.mapper.HarnessSkillResourceMapper;
import com.phoenix.agent.model.HarnessSkill;
import com.phoenix.agent.model.HarnessSkillResource;
import com.phoenix.agent.service.SkillAdminService;
import com.phoenix.agent.vo.SkillDetailVO;
import com.phoenix.agent.vo.SkillListVO;
import com.phoenix.agent.vo.SkillResourceVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 技能管理后台服务实现。
 */
@Service
@RequiredArgsConstructor
public class SkillAdminServiceImpl implements SkillAdminService {

    /** 分页上限防拖库（api 规范） */
    private static final int MAX_PAGE_SIZE = 500;

    private final HarnessSkillMapper harnessSkillMapper;

    private final HarnessSkillResourceMapper harnessSkillResourceMapper;

    @Override
    public ReturnVo<Page<SkillListVO>> page(String keyword, String status, int pageNum, int pageSize) {
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        // 周边模块无 APT TableDef 先例，采用字符串列 + ? 参数绑定（等价 #{}，无注入面）
        QueryWrapper query = QueryWrapper.create();
        if (StringUtils.hasText(keyword)) {
            String like = "%" + keyword.trim() + "%";
            query.and("(name like ? or description like ?)", like, like);
        }
        if (StringUtils.hasText(status)) {
            query.and("status = ?", status);
        }
        query.orderBy("updated_at desc");
        Page<HarnessSkill> entityPage = harnessSkillMapper.paginate(Math.max(pageNum, 1), size, query);
        Page<SkillListVO> voPage = new Page<>(entityPage.getPageNumber(), entityPage.getPageSize());
        voPage.setTotalRow(entityPage.getTotalRow());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toListVo).toList());
        return ReturnVo.ok(voPage);
    }

    @Override
    public ReturnVo<SkillDetailVO> detail(Long id) {
        HarnessSkill skill = harnessSkillMapper.selectOneById(id);
        if (skill == null) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_NOT_FOUND.getMsg(),
                SkillErrorCodeEnm.SKILL_NOT_FOUND.getCode());
        }
        SkillDetailVO vo = new SkillDetailVO();
        vo.setId(skill.getId());
        vo.setName(skill.getName());
        vo.setDescription(skill.getDescription());
        vo.setStatus(skill.getStatus());
        vo.setSource(skill.getSource());
        vo.setUpdatedAt(skill.getUpdatedAt());
        vo.setSkillContent(skill.getSkillContent());
        List<HarnessSkillResource> resources = harnessSkillResourceMapper
            .selectListByQuery(QueryWrapper.create().where("id = ?", id));
        vo.setResources(resources.stream().map(r -> {
            SkillResourceVO rv = new SkillResourceVO();
            rv.setPath(r.getResourcePath());
            rv.setContent(r.getResourceContent());
            return rv;
        }).toList());
        return ReturnVo.ok(vo);
    }

    private SkillListVO toListVo(HarnessSkill s) {
        SkillListVO vo = new SkillListVO();
        vo.setId(s.getId());
        vo.setName(s.getName());
        vo.setDescription(s.getDescription());
        vo.setStatus(s.getStatus());
        vo.setSource(s.getSource());
        vo.setUpdatedAt(s.getUpdatedAt());
        return vo;
    }
}
