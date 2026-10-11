package com.phoenix.data.service.knowledge;

import cn.hutool.core.util.StrUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryChain;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseCreateDTO;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseQueryDTO;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseUpdateDTO;
import com.phoenix.data.entity.KnowledgeBase;
import com.phoenix.data.entity.AgentKnowledge;
import com.phoenix.data.mapper.AgentKnowledgeMapper;
import com.phoenix.data.mapper.KnowledgeBaseMapper;
import com.phoenix.data.enums.ModelType;
import com.phoenix.data.dto.ModelConfigDTO;
import com.phoenix.data.entity.ModelConfig;
import com.phoenix.data.service.aimodelconfig.ModelConfigDataService;
import com.phoenix.data.service.vectorstore.ModelVectorStoreFactory;
import com.mybatisflex.core.query.QueryWrapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final AgentKnowledgeMapper agentKnowledgeMapper;

    private final AgentKnowledgeResourceManager agentKnowledgeResourceManager;

    private final ModelConfigDataService modelConfigDataService;

    private final ModelVectorStoreFactory modelVectorStoreFactory;

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
          // R-01：创建时绑定向量模型——显式指定优先，否则取默认 embedding 模型
          kb.setEmbeddingModelId(resolveBindingModelId(dto.getEmbeddingModelId()));
        knowledgeBaseMapper.insert(kb);
        // R-02：首绑某模型即确保其专属向量表存在（幂等；默认模型复用既有表，不新建）
        if (kb.getEmbeddingModelId() != null) {
            java.util.Map<String, String> tables = modelVectorStoreFactory.ensureKnowledgeTables(kb.getEmbeddingModelId());
            log.info("知识库绑定模型专属表就绪: kbId={}, modelId={}, tables={}", kb.getId(), kb.getEmbeddingModelId(), tables);
        }
          log.info("知识库创建: id={}, name={}, by={}, embeddingModelId={}", kb.getId(), name, operator,
                  kb.getEmbeddingModelId());
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
          // R-01 附加：换模型必须走受控重建；未确认时仅提示、绑定不变
          if (dto.getEmbeddingModelId() != null && !dto.getEmbeddingModelId().equals(kb.getEmbeddingModelId())) {
              String hint = Boolean.TRUE.equals(dto.getConfirmRebuild())
                      ? "已确认换绑：该知识库的向量将按新模型重建（受控重建流程落地后执行；当前绑定未变更）"
                      : "该知识库的向量将按新模型重建，确认后执行受控重建流程（当前绑定未变更）";
              log.info("知识库换模型请求（未变更绑定）: kbId={}, from={}, to={}, confirmRebuild={}, by={}",
                      kb.getId(), kb.getEmbeddingModelId(), dto.getEmbeddingModelId(), dto.getConfirmRebuild(), operator);
              KnowledgeBaseVO vo = toVO(kb, false);
              vo.setRebuildHint(hint);
              return vo;
          }
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
    public PageResult<KnowledgeBaseVO> queryByConditionsWithPage(KnowledgeBaseQueryDTO dto, String ownerId) {
        var chain = QueryChain.of(knowledgeBaseMapper)
                .where("del_flag = 0");
        // R-18（CR-01/T-25）：普通用户仅见本人创建；ownerId=null（超管）不过滤。
        // 占位符必须用 ?：QueryChain.and 不做 {0} 替换（原 name/status 的 {0} 为既有缺陷 BUG-139，一并修正）
        if (StrUtil.isNotBlank(ownerId)) {
            chain.and("creator = ?", ownerId);
        }
        if (StrUtil.isNotBlank(dto.getName())) {
            chain.and("name LIKE ?", "%" + StrUtil.trim(dto.getName()) + "%");
        }
        if (dto.getStatus() != null) {
            chain.and("status = ?", dto.getStatus());
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
    public String getCreatorById(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(id);
        return kb == null ? null : kb.getCreator();
    }

    @Override
    public Map<String, Object> reEmbedKnowledgeBase(Long id) {
        KnowledgeBase kb = knowledgeBaseMapper.selectOneById(id);
        if (kb == null) {
            throw new IllegalArgumentException("知识库不存在: " + id);
        }
        List<AgentKnowledge> items = agentKnowledgeMapper.selectListByQuery(QueryWrapper.create()
            .where("knowledge_base_id = ?", id).and("type in ('QA','FAQ')").and("is_deleted = 0"));
        int ok = 0;
        List<String> failed = new ArrayList<>();
        for (AgentKnowledge item : items) {
            try {
                // doEmbedingToVectorStore = 先删该条目旧向量再按新文本嵌入（天然幂等原语）
                agentKnowledgeResourceManager.doEmbedingToVectorStore(item);
                ok++;
            }
            catch (Exception e) {
                failed.add(String.valueOf(item.getId()));
                log.warn("重刷向量失败 kb={} item={}: {}", id, item.getId(), e.toString());
            }
        }
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("total", items.size());
        r.put("success", ok);
        r.put("failed", failed);
        return r;
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

    /** 解析创建时的向量模型绑定：显式指定优先（须存在），否则取默认 embedding 模型、再退任一启用行（R-01） */
    private Integer resolveBindingModelId(Integer explicitModelId) {
        if (explicitModelId != null) {
            ModelConfig cfg = modelConfigDataService.getById(explicitModelId);
            if (cfg == null) {
                throw new IllegalArgumentException("指定的向量模型不存在：" + explicitModelId);
            }
            return explicitModelId;
        }
        ModelConfigDTO def = modelConfigDataService.getDefaultConfigByType(ModelType.EMBEDDING);
        if (def == null) {
            def = modelConfigDataService.getActiveConfigByType(ModelType.EMBEDDING);
        }
        return (def == null) ? null : def.getId();
    }

    private KnowledgeBaseVO toVO(KnowledgeBase kb, boolean withBoundAgents) {
        return KnowledgeBaseVO.builder()
                .id(kb.getId()).name(kb.getName()).description(kb.getDescription())
                .status(kb.getStatus()).creator(kb.getCreator())
                .createTime(kb.getCreateTime()).updateTime(kb.getUpdateTime())
                  .embeddingModelId(kb.getEmbeddingModelId()).modelSpaceKey(kb.getModelSpaceKey())
                .groupNames(knowledgeBaseMapper.selectGroupNamesByKb(kb.getId()))
                .itemCount(knowledgeBaseMapper.countItems(kb.getId()))
                .boundAgentNames(withBoundAgents ? knowledgeBaseMapper.selectBoundAgentNames(kb.getId()) : null)
                .build();
    }
}
