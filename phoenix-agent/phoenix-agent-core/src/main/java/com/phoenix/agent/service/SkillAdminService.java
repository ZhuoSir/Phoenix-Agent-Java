package com.phoenix.agent.service;

import com.mybatisflex.core.paginate.Page;
import com.phoenix.agent.dto.SkillBindingDTO;
import com.phoenix.agent.dto.SkillPublishDTO;
import com.phoenix.agent.vo.AgentSkillOptionVO;
import com.phoenix.agent.vo.SkillDetailVO;
import com.phoenix.agent.vo.SkillListVO;
import com.phoenix.tools.vo.ReturnVo;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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

    /**
     * 上传技能 ZIP（SKILL.md+文本资源），解析校验后落库为草稿。
     * 同名且未指定 overwrite → 冲突错误；overwrite=true 覆盖更新并回到草稿态。
     * @return 技能 id
     */
    ReturnVo<Long> upload(MultipartFile file, boolean overwrite);

    /**
     * 发布：置 published 并覆盖式设置授权组（groupIds 可为空=仅后台可见）。
     */
    ReturnVo<Boolean> publish(Long id, SkillPublishDTO dto);

    /**
     * 下线：published → draft。
     */
    ReturnVo<Boolean> offline(Long id);

    /**
     * 覆盖式调整授权组（任意状态可调用，即时生效，无需下线重发）。
     */
    ReturnVo<Boolean> updateGroups(Long id, SkillPublishDTO dto);

    /**
     * 智能体编辑页技能可选池：已发布技能 + 该智能体已绑定但已下线的技能（bound=true、status=draft 供灰显）。
     */
    ReturnVo<List<AgentSkillOptionVO>> options(Long agentId);

    /**
     * 智能体已绑定技能 id 列表（编辑页回显）。
     */
    ReturnVo<List<Long>> boundSkillIds(Long agentId);

    /**
     * 覆盖式保存智能体绑定；target 必须全部为已发布技能（R-04）。
     */
    ReturnVo<Boolean> bindAgent(Long agentId, SkillBindingDTO dto);
}
