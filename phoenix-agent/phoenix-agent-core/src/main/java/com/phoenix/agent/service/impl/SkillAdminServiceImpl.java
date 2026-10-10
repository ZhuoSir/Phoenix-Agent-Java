package com.phoenix.agent.service.impl;

import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.row.Db;
import com.phoenix.agent.constant.SkillConstant;
import com.phoenix.agent.dto.SkillBindingDTO;
import com.phoenix.agent.dto.SkillPublishDTO;
import com.phoenix.agent.enums.SkillErrorCodeEnm;
import com.phoenix.agent.enums.SkillStatusEnm;
import com.phoenix.agent.mapper.AgentSkillInfoMapper;
import com.phoenix.agent.mapper.GroupSkillInfoMapper;
import com.phoenix.agent.mapper.HarnessSkillMapper;
import com.phoenix.agent.mapper.HarnessSkillResourceMapper;
import com.phoenix.agent.model.AgentSkillInfo;
import com.phoenix.agent.model.GroupSkillInfo;
import com.phoenix.agent.model.HarnessSkill;
import com.phoenix.agent.model.HarnessSkillResource;
import com.phoenix.agent.service.SkillAdminService;
import com.phoenix.agent.util.SkillZipSanitizer;
import com.phoenix.agent.vo.AgentSkillOptionVO;
import com.phoenix.agent.vo.SkillDetailVO;
import com.phoenix.agent.vo.SkillListVO;
import com.phoenix.agent.vo.SkillRefVO;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

    private final GroupSkillInfoMapper groupSkillInfoMapper;

    private final AgentSkillInfoMapper agentSkillInfoMapper;

    @Override
    public ReturnVo<Page<SkillListVO>> page(String keyword, String status, int pageNum, int pageSize, String ownerId) {
        int size = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        // 周边模块无 APT TableDef 先例，采用字符串列 + ? 参数绑定（等价 #{}，无注入面）
        QueryWrapper query = QueryWrapper.create();
        // R-18（CR-01/T-25）：普通用户仅见本人创建；ownerId=null（超管）不过滤
        if (StringUtils.hasText(ownerId)) {
            query.and("creator = ?", ownerId);
        }
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
    public ReturnVo<Long> upload(MultipartFile file, boolean overwrite, String operator) {
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
            // 先清洗系统垃圾并锁定 SKILL.md 所在根，再交上游做格式权威校验（plan 决策3 + 兼容层）
            byte[] sanitized = SkillZipSanitizer.sanitize(file.getBytes());
            parsed = SkillUtil.createFromZip(sanitized);
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
        // CR-02/R-19：判重按「创建人 + 名称」—— 不同用户可上传同名技能；
        // 仅当**本人**已有同名时才进入覆盖/冲突分支（他人同名 ⇒ existing=null ⇒ 走新建）
        HarnessSkill existing = harnessSkillMapper.selectOneByQuery(
            QueryWrapper.create().where("name = ?", parsed.getName()).and("creator = ?", operator));
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
            // R-18：覆盖上传不改变归属（creator 保持原创建人）
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
        // R-18（CR-01/T-24）：创建人 = 当前登录用户（服务端注入，不信任入参）
        skill.setCreator(operator);
        skill.setCreatedAt(now);
        skill.setUpdatedAt(now);
        try {
            harnessSkillMapper.insert(skill);
        }
        catch (org.springframework.dao.DuplicateKeyException dup) {
            // CR-02：并发下同用户同名撞唯一索引 (name, creator) → 友好失败而非 500
            log.warn("技能同名并发冲突: name={}, creator={}", parsed.getName(), operator);
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_NAME_CONFLICT.getMsg(),
                SkillErrorCodeEnm.SKILL_NAME_CONFLICT.getCode());
        }
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnVo<Boolean> publish(Long id, SkillPublishDTO dto) {
        HarnessSkill skill = harnessSkillMapper.selectOneById(id);
        if (skill == null) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_NOT_FOUND.getMsg(),
                SkillErrorCodeEnm.SKILL_NOT_FOUND.getCode());
        }
        ReturnVo<Boolean> grantResult = replaceGroupGrants(id, dto == null ? null : dto.getGroupIds());
        if (grantResult != null) {
            return grantResult;
        }
        skill.setStatus(SkillStatusEnm.PUBLISHED.getCode());
        skill.setUpdatedAt(new Date());
        harnessSkillMapper.update(skill);
        log.info("技能发布完成, skillId={}, groupIds={}", id, dto == null ? null : dto.getGroupIds());
        return ReturnVo.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnVo<Boolean> offline(Long id) {
        HarnessSkill skill = harnessSkillMapper.selectOneById(id);
        if (skill == null) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_NOT_FOUND.getMsg(),
                SkillErrorCodeEnm.SKILL_NOT_FOUND.getCode());
        }
        // 幂等：已下线再调无副作用
        skill.setStatus(SkillStatusEnm.DRAFT.getCode());
        skill.setUpdatedAt(new Date());
        harnessSkillMapper.update(skill);
        return ReturnVo.ok(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnVo<Boolean> updateGroups(Long id, SkillPublishDTO dto) {
        HarnessSkill skill = harnessSkillMapper.selectOneById(id);
        if (skill == null) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_NOT_FOUND.getMsg(),
                SkillErrorCodeEnm.SKILL_NOT_FOUND.getCode());
        }
        ReturnVo<Boolean> grantResult = replaceGroupGrants(id, dto == null ? null : dto.getGroupIds());
        if (grantResult != null) {
            return grantResult;
        }
        return ReturnVo.ok(true);
    }

    /**
     * 覆盖式重写组授权。校验通过返回 null；任一目标组不存在返回失败。
     */
    private ReturnVo<Boolean> replaceGroupGrants(Long skillId, List<String> groupIds) {
        List<String> distinct = groupIds == null ? List.of()
            : groupIds.stream().filter(StringUtils::hasText).distinct().toList();
        for (String gid : distinct) {
            Object cnt = Db.selectObject("select count(*) from tbl_platform_group_info where id = ? and del_flag = 0",
                gid);
            if (cnt == null || ((Number) cnt).longValue() == 0) {
                return ReturnVo.fail(SkillErrorCodeEnm.SKILL_GROUP_NOT_FOUND.getMsg() + ": " + gid,
                    SkillErrorCodeEnm.SKILL_GROUP_NOT_FOUND.getCode());
            }
        }
        // 覆盖式：物理删后重建（授权关系无审计留存要求）
        groupSkillInfoMapper.deleteByQuery(QueryWrapper.create().where("skill_id = ?", skillId));
        if (!distinct.isEmpty()) {
            Date now = new Date();
            List<GroupSkillInfo> rows = distinct.stream().map(gid -> {
                GroupSkillInfo gsi = new GroupSkillInfo();
                gsi.setGroupId(gid);
                gsi.setSkillId(skillId);
                gsi.setCreateTime(now);
                gsi.setUpdateTime(now);
                return gsi;
            }).toList();
            groupSkillInfoMapper.insertBatch(rows);
        }
        return null;
    }

    @Override
    public ReturnVo<List<AgentSkillOptionVO>> options(Long agentId, String viewerId, boolean superAdmin) {
        List<Long> boundIds = boundIds(agentId);
        List<AgentSkillOptionVO> result = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        // R-18（CR-01/T-26）：可见集合 = 超管全部已发布；否则 自己的 ∪ 我所在组关联的 ∪ 公共（无组授权行）
        List<HarnessSkill> published;
        if (superAdmin) {
            published = harnessSkillMapper.selectListByQuery(
                QueryWrapper.create().where("status = ?", SkillStatusEnm.PUBLISHED.getCode()));
        }
        else {
            published = harnessSkillMapper.selectListByQuery(visibleSkillQuery(viewerId));
        }
        for (HarnessSkill s : published) {
            result.add(toOptionVo(s, boundIds.contains(s.getId())));
            seen.add(s.getId());
        }
        // 已绑定但当前不可见/已下线：仍返回供编辑页灰显（R-04 场景2，不丢已绑关系）
        List<Long> offlineBound = boundIds.stream().filter(id -> !seen.contains(id)).toList();
        if (!offlineBound.isEmpty()) {
            harnessSkillMapper.selectListByQuery(QueryWrapper.create().in("id", offlineBound))
                .forEach(s -> result.add(toOptionVo(s, true)));
        }
        return ReturnVo.ok(result);
    }

    /** R-18：技能可见集合查询（own ∪ myGroups ∪ public），仅已发布 */
    private QueryWrapper visibleSkillQuery(String viewerId) {
        QueryWrapper q = QueryWrapper.create().where("status = ?", SkillStatusEnm.PUBLISHED.getCode());
        List<String> myGroups = myGroupIds(viewerId);
        StringBuilder cond = new StringBuilder("(creator = ?");
        List<Object> params = new ArrayList<>();
        params.add(viewerId);
        if (!myGroups.isEmpty()) {
            cond.append(" or id in (select skill_id from tbl_platform_group_skill_info where del_flag = 0 and group_id in (")
                .append(placeholders(myGroups.size())).append("))");
            params.addAll(myGroups);
        }
        // 公共 = 无任何组授权行（Q-P4 / R-04 口径）
        cond.append(" or id not in (select skill_id from tbl_platform_group_skill_info where del_flag = 0))");
        q.and(cond.toString(), params.toArray());
        return q;
    }

    /** 当前用户所在组 id 集（tbl_platform_account_group_info.account_id = 用户 id） */
    private List<String> myGroupIds(String userId) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        return Db.selectListBySql(
                "select group_id from tbl_platform_account_group_info where account_id = ? and del_flag = 0", userId)
            .stream()
            .map(r -> r.getString("group_id"))
            .filter(Objects::nonNull)
            .distinct()
            .toList();
    }

    private String placeholders(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    @Override
    public String getCreatorById(Long id) {
        HarnessSkill s = harnessSkillMapper.selectOneById(id);
        return s == null ? null : s.getCreator();
    }

    @Override
    public ReturnVo<List<Long>> boundSkillIds(Long agentId) {
        return ReturnVo.ok(boundIds(agentId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnVo<Boolean> bindAgent(Long agentId, SkillBindingDTO dto) {
        if (agentId == null) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_AGENT_NOT_FOUND.getMsg(),
                SkillErrorCodeEnm.SKILL_AGENT_NOT_FOUND.getCode());
        }
        Object agentCnt = Db.selectObject("select count(*) from tbl_data_agent where id = ?", agentId);
        if (agentCnt == null || ((Number) agentCnt).longValue() == 0) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_AGENT_NOT_FOUND.getMsg(),
                SkillErrorCodeEnm.SKILL_AGENT_NOT_FOUND.getCode());
        }
        List<Long> targetIds = dto == null || dto.getSkillIds() == null ? List.of()
            : dto.getSkillIds().stream().filter(Objects::nonNull).distinct().toList();
        List<Long> alreadyBound = boundIds(agentId);
        // 仅约束「本次新增的绑定」必须已发布；已绑定但后被下线的技能允许保留（R-04 不级联删绑定）
        List<Long> newlyAdded = targetIds.stream().filter(id -> !alreadyBound.contains(id)).toList();
        if (!newlyAdded.isEmpty()) {
            long publishedCnt = harnessSkillMapper.selectCountByQuery(QueryWrapper.create()
                .where("status = ?", SkillStatusEnm.PUBLISHED.getCode())
                .in("id", newlyAdded));
            if (publishedCnt != newlyAdded.size()) {
                return ReturnVo.fail(SkillErrorCodeEnm.SKILL_NOT_PUBLISHED.getMsg(),
                    SkillErrorCodeEnm.SKILL_NOT_PUBLISHED.getCode());
            }
        }
        // 覆盖式：物理删后重建（绑定关系无审计留存要求）
        agentSkillInfoMapper.deleteByQuery(QueryWrapper.create().where("agent_id = ?", agentId));
        if (!targetIds.isEmpty()) {
            Date now = new Date();
            List<AgentSkillInfo> rows = targetIds.stream().map(sid -> {
                AgentSkillInfo info = new AgentSkillInfo();
                info.setAgentId(agentId);
                info.setSkillId(sid);
                info.setCreateTime(now);
                info.setUpdateTime(now);
                return info;
            }).toList();
            agentSkillInfoMapper.insertBatch(rows);
        }
        log.info("智能体技能绑定更新, agentId={}, skillIds={}", agentId, targetIds);
        return ReturnVo.ok(true);
    }

    /** 该智能体已绑定技能 id（未删除） */
    private List<Long> boundIds(Long agentId) {
        if (agentId == null) {
            return List.of();
        }
        return agentSkillInfoMapper
            .selectListByQuery(QueryWrapper.create().where("agent_id = ?", agentId))
            .stream()
            .map(AgentSkillInfo::getSkillId)
            .toList();
    }

    private AgentSkillOptionVO toOptionVo(HarnessSkill s, boolean bound) {
        AgentSkillOptionVO vo = new AgentSkillOptionVO();
        vo.setSkillId(s.getId());
        vo.setName(s.getName());
        vo.setDescription(s.getDescription());
        vo.setStatus(s.getStatus());
        vo.setBound(bound);
        return vo;
    }

    @Override
    public ReturnVo<SkillRefVO> refs(Long id) {
        SkillRefVO vo = new SkillRefVO();
        vo.setBoundAgentCount(countBy("select count(*) from tbl_data_agent_skill_info where skill_id = ? and del_flag = 0",
            id));
        vo.setAuthorizedGroupCount(countBy("select count(*) from tbl_platform_group_skill_info where skill_id = ? and del_flag = 0",
            id));
        return ReturnVo.ok(vo);
    }

    @Override
    public ReturnVo<List<String>> authorizedGroupIds(Long id) {
        List<String> groupIds = groupSkillInfoMapper
            .selectListByQuery(QueryWrapper.create().where("skill_id = ?", id))
            .stream()
            .map(GroupSkillInfo::getGroupId)
            .toList();
        return ReturnVo.ok(groupIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReturnVo<Boolean> delete(Long id) {
        HarnessSkill skill = harnessSkillMapper.selectOneById(id);
        if (skill == null) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_NOT_FOUND.getMsg(),
                SkillErrorCodeEnm.SKILL_NOT_FOUND.getCode());
        }
        if (SkillStatusEnm.PUBLISHED.getCode().equals(skill.getStatus())) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_MUST_OFFLINE.getMsg(),
                SkillErrorCodeEnm.SKILL_MUST_OFFLINE.getCode());
        }
        // 级联清理（同事务）：绑定、授权、资源，最后删技能本体
        agentSkillInfoMapper.deleteByQuery(QueryWrapper.create().where("skill_id = ?", id));
        groupSkillInfoMapper.deleteByQuery(QueryWrapper.create().where("skill_id = ?", id));
        harnessSkillResourceMapper.deleteByQuery(QueryWrapper.create().where("id = ?", id));
        harnessSkillMapper.deleteById(id);
        log.info("技能删除完成, skillId={}, name={}", id, skill.getName());
        return ReturnVo.ok(true);
    }

    private long countBy(String sql, Object... args) {
        Object cnt = Db.selectObject(sql, args);
        return cnt == null ? 0L : ((Number) cnt).longValue();
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
