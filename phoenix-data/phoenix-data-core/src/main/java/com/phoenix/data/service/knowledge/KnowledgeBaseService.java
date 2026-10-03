package com.phoenix.data.service.knowledge;

import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseCreateDTO;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseQueryDTO;
import com.phoenix.data.dto.knowledge.kb.KnowledgeBaseUpdateDTO;
import com.phoenix.data.vo.KnowledgeBaseVO;
import com.phoenix.data.vo.PageResult;

/**
 * 知识库门面（knowledge-base R-01/03/04）。
 * 名称冲突与删除保护抛 IllegalArgumentException/IllegalStateException，controller 转 error message
 * （data 域 ApiResponse 无 code 位——plan「42040/42041」措辞在此域以文案落地，changelog 已注记）。
 */
public interface KnowledgeBaseService {

    KnowledgeBaseVO create(KnowledgeBaseCreateDTO dto, String operator);

    KnowledgeBaseVO update(KnowledgeBaseUpdateDTO dto, String operator);

    /** 逻辑删；仍被绑定则 IllegalStateException 携带绑定清单（R-04）。 */
    void delete(Long id, String operator);

    PageResult<KnowledgeBaseVO> queryByConditionsWithPage(KnowledgeBaseQueryDTO query);

    /** BUG-18(T-04)：按库重刷 QA/FAQ 联合向量（幂等，DOCUMENT 不在范围） */
    java.util.Map<String, Object> reEmbedKnowledgeBase(Long id);

    KnowledgeBaseVO detail(Long id);
}
