package com.phoenix.agent.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.phoenix.agent.constant.SkillConstant;
import com.phoenix.agent.enums.SkillErrorCodeEnm;
import com.phoenix.agent.enums.SkillStatusEnm;
import com.phoenix.agent.mapper.HarnessSkillMapper;
import com.phoenix.agent.mapper.HarnessSkillResourceMapper;
import com.phoenix.agent.model.HarnessSkill;
import com.phoenix.agent.model.HarnessSkillResource;
import com.phoenix.agent.service.SkillAdminService;
import com.phoenix.agent.vo.SkillDetailVO;
import com.phoenix.agent.vo.SkillListVO;
import com.phoenix.agent.vo.SkillResourceVO;
import com.phoenix.tools.vo.ReturnVo;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.SkillUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 技能管理后台服务实现。
 */
@Slf4j
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnVo<Long> upload(MultipartFile file, boolean overwrite) {
        if (file == null || file.isEmpty()) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_ZIP_INVALID.getMsg(),
                SkillErrorCodeEnm.SKILL_ZIP_INVALID.getCode());
        }
        if (file.getSize() > SkillConstant.MAX_ZIP_BYTES) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_FILE_TOO_LARGE.getMsg(),
                SkillErrorCodeEnm.SKILL_FILE_TOO_LARGE.getCode());
        }
        AgentSkill parsed;
        try {
            // 复用上游解析：SKILL.md 定位/ frontmatter 必填校验为格式权威（plan 决策3）
            parsed = SkillUtil.createFromZip(file.getBytes());
        }
        catch (Exception e) {
            // 对外不泄漏内部信息（backend 规范15），细节进日志
            log.warn("技能包解析失败, fileName={}, reason={}", file.getOriginalFilename(), e.getMessage());
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_ZIP_INVALID.getMsg(),
                SkillErrorCodeEnm.SKILL_ZIP_INVALID.getCode());
        }
        if (!StringUtils.hasText(parsed.getName()) || !StringUtils.hasText(parsed.getDescription())) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_ZIP_INVALID.getMsg(),
                SkillErrorCodeEnm.SKILL_ZIP_INVALID.getCode());
        }
        List<HarnessSkillResource> resources = new ArrayList<>();
        for (Map.Entry<String, String> entry : parsed.getResources().entrySet()) {
            String path = normalizePath(entry.getKey());
            String ext = StringUtils.getFilenameExtension(path);
            if (ext == null || !SkillConstant.TEXT_EXTENSIONS.contains(ext.toLowerCase(Locale.ROOT))) {
                return ReturnVo.fail("技能包含不支持的非文本资源: " + path,
                    SkillErrorCodeEnm.SKILL_ZIP_INVALID.getCode());
            }
            HarnessSkillResource res = new HarnessSkillResource();
            res.setResourcePath(path);
            res.setResourceContent(entry.getValue());
            resources.add(res);
        }
        HarnessSkill existing = harnessSkillMapper.selectOneByQuery(
            QueryWrapper.create().where("name = ?", parsed.getName()));
        Date now = new Date();
        if (existing != null) {
            if (!overwrite) {
                return ReturnVo.fail(SkillErrorCodeEnm.SKILL_NAME_CONFLICT.getMsg(),
                    SkillErrorCodeEnm.SKILL_NAME_CONFLICT.getCode());
            }
            // 覆盖更新并回草稿（R-02）；资源整体替换（物理删，随覆盖语义）
            harnessSkillResourceMapper.deleteByQuery(
                QueryWrapper.create().where("id = ?", existing.getId()));
            existing.setDescription(parsed.getDescription());
            existing.setSkillContent(parsed.getSkillContent());
            existing.setSource(SkillConstant.SOURCE_UPLOAD);
            existing.setStatus(SkillStatusEnm.DRAFT.getCode());
            existing.setUpdatedAt(now);
            harnessSkillMapper.update(existing);
            insertResources(existing.getId(), resources, now);
            return ReturnVo.ok(existing.getId());
        }
        HarnessSkill skill = new HarnessSkill();
        skill.setName(parsed.getName());
        skill.setDescription(parsed.getDescription());
        skill.setSkillContent(parsed.getSkillContent());
        skill.setSource(SkillConstant.SOURCE_UPLOAD);
        skill.setStatus(SkillStatusEnm.DRAFT.getCode());
        skill.setCreatedAt(now);
        skill.setUpdatedAt(now);
        harnessSkillMapper.insert(skill);
        insertResources(skill.getId(), resources, now);
        return ReturnVo.ok(skill.getId());
    }

    /** 归一化资源路径：去 ./ 前缀并拒绝越界路径 */
    private String normalizePath(String raw) {
        String p = raw == null ? "" : raw.trim().replace('\\', '/');
        if (p.startsWith("./")) {
            p = p.substring(2);
        }
        if (p.startsWith("/") || p.contains("..")) {
            throw new IllegalArgumentException("illegal resource path: " + raw);
        }
        return p;
    }

    private void insertResources(Long skillId, List<HarnessSkillResource> resources, Date now) {
        if (resources.isEmpty()) {
            return;
        }
        resources.forEach(r -> {
            r.setId(skillId);
            r.setCreatedAt(now);
            r.setUpdatedAt(now);
        });
        // 批量插入（backend 规范20：禁循环单条）
        harnessSkillResourceMapper.insertBatch(resources);
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
