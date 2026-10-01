package com.phoenix.data.service.knowledge;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseCreateDTO;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseQueryDTO;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseUpdateDTO;
import com.phoenix.data.entity.KnowledgeBase;
import com.phoenix.data.mapper.KnowledgeBaseMapper;
import com.phoenix.data.vo.KnowledgeBaseVO;
import com.phoenix.data.vo.PageResult;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识库门面实现。creator 由 controller 从登录态注入；删除保护附绑定清单（R-04）。
 */
@Slf4j
@Service
@AllArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    public KnowledgeBaseVO create(KnowledgeBaseCreateDTO dto, String operator) {
        String name = StrUtil.trim(dto.getName());
        if (knowledgeBaseMapper.countByName(name, 0) > 0) {
            throw new IllegalArgumentException("已存在同名知识库：" + name);
        }
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(name);
        kb.setDescription(dto.getDescription());
        kb.setStatus(1);
        kb.setCreator(operator);
        kb.setDelFlag(0);
        kb.setCreateTime(LocalDateTime.now());
        kb.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.insert(kb);
        log.info("知识库创建: id={}, name={}, by={}", kb.getId(), name, operator);
        return toVO(kb, false);
    }

    @Override
    public KnowledgeBaseVO update(KnowledgeBaseUpdateDTO dto, String operator) {
        KnowledgeBase kb = requireAlive(dto.getId());
        if (StrUtil.isNotBlank(dto.getName())) {
            String name = StrUtil.trim(dto.getName());
            if (knowledgeBaseMapper.countByName(name, kb.getId()) > 0) {
                throw new IllegalArgumentException("已存在同名知识库：" + name);
            }
            kb.setName(name);
        }
        if (dto.getDescription() != null) {
            kb.setDescription(dto.getDescription());
        }
        if (dto.getStatus() != null && (dto.getStatus() == 0 || dto.getStatus() == 1)) {
            kb.setStatus(dto.getStatus());
        }
        kb.setUpdator(operator);
        kb.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.update(kb);
        return toVO(kb, false);
    }

    @Override
    public void delete(Long id, String operator) {
        KnowledgeBase kb = requireAlive(id);
        List<String> bound = knowledgeBaseMapper.selectBoundAgentNames(id);
        if (!bound.isEmpty()) {
            throw new IllegalStateException("知识库仍被智能体绑定，无法删除：" + String.join("、", bound));
        }
        kb.setDelFlag(1);
        kb.setUpdator(operator);
        kb.setUpdateTime(LocalDateTime.now());
        knowledgeBaseMapper.update(kb);
        log.info("知识库逻辑删除: id={}, name={}, by={}", id, kb.getName(), operator);
    }

    @Override
    public PageResult<KnowledgeBaseVO> queryByConditionsWithPage(KnowledgeBaseQueryDTO dto) {
        var chain = QueryChain.of(knowledgeBaseMapper)
                .where("del_flag = 0");
        if (StrUtil.isNotBlank(dto.getName())) {
            chain.and("name LIKE {0}", "%" + StrUtil.trim(dto.getName()) + "%");
        }
        if (dto.getStatus() != null) {
            chain.and("status = {0}", dto.getStatus());
        }
        chain.orderBy("update_time DESC");
        Page<KnowledgeBase> page = chain.page(new Page<>(dto.getPageNum(), dto.getPageSize()));
        List<KnowledgeBaseVO> vos = page.getRecords().stream().map(kb -> toVO(kb, false)).toList();
        PageResult<KnowledgeBaseVO> result = new PageResult<>();
        result.setData(vos);
        result.setTotal(page.getTotalRow());
        result.setPageNum((int) page.getPageNumber());
        result.setPageSize((int) page.getPageSize());
        result.setTotalPages((int) page.getTotalPage());
        return result;
    }

    @Override
    public KnowledgeBaseVO detail(Long id) {
        return toVO(requireAlive(id), true);
    }

    private KnowledgeBase requireAlive(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(id);
        if (kb == null || Integer.valueOf(1).equals(kb.getDelFlag())) {
            throw new IllegalArgumentException("知识库不存在或已删除");
        }
        return kb;
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb, boolean withBoundAgents) {
        return KnowledgeBaseVO.builder()
                .id(kb.getId()).name(kb.getName()).description(kb.getDescription())
                .status(kb.getStatus()).creator(kb.getCreator())
                .createTime(kb.getCreateTime()).updateTime(kb.getUpdateTime())
                .groupNames(knowledgeBaseMapper.selectGroupNamesByKb(kb.getId()))
                .itemCount(knowledgeBaseMapper.countItems(kb.getId()))
                .boundAgentNames(withBoundAgents ? knowledgeBaseMapper.selectBoundAgentNames(kb.getId()) : null)
                .build();
    }
}
