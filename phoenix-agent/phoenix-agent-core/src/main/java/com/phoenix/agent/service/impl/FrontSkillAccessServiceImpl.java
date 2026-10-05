package com.phoenix.agent.service.impl;

import com.mybatisflex.core.row.Db;
import com.mybatisflex.core.row.Row;
import com.phoenix.agent.enums.SkillErrorCodeEnm;
import com.phoenix.agent.service.FrontSkillAccessService;
import com.phoenix.agent.vo.SkillListVO;
import com.phoenix.tools.vo.ReturnVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 前台技能可见性判定实现。
 *
 * <p>组相关数据（账号-组、组-智能体、组-技能）与平台模块同库同 schema，
 * 采用只读 SQL 直查（项目单部署单元既有做法，见 plan §依赖与前置）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FrontSkillAccessServiceImpl implements FrontSkillAccessService {

    /** 三重交集查询：已发布 ∧ 绑定该智能体 ∧ 授权给账号所属任一可见组 */
    private static final String MY_SKILLS_SQL = """
            select distinct s.id, s.name, s.description, s.status, s.source, s.updated_at
              from tbl_harness_skills s
              join tbl_data_agent_skill_info b on b.skill_id = s.id and b.del_flag = 0
              join tbl_platform_group_skill_info gs on gs.skill_id = s.id and gs.del_flag = 0
              join tbl_platform_account_group_info ag on ag.group_id = gs.group_id and ag.del_flag = 0
             where ag.account_id = ?
               and b.agent_id = ?
               and s.status = 'published'
             order by s.updated_at desc
            """;

    @Override
    public ReturnVo<List<SkillListVO>> mySkills(String accountId, Long agentId) {
        if (agentId == null) {
            return ReturnVo.ok(List.of());
        }
        List<Row> rows = Db.selectListBySql(MY_SKILLS_SQL, accountId, agentId);
        List<SkillListVO> result = rows.stream().map(row -> {
            SkillListVO vo = new SkillListVO();
            vo.setId(row.getLong("id"));
            vo.setName(row.getString("name"));
            vo.setDescription(row.getString("description"));
            vo.setStatus(row.getString("status"));
            vo.setSource(row.getString("source"));
            vo.setUpdatedAt(row.getDate("updated_at"));
            return vo;
        }).toList();
        return ReturnVo.ok(result);
    }

    @Override
    public ReturnVo<String> resolveVisibleAgentSn(String accountId, Long agentId) {
        ReturnVo<Boolean> visible = validateVisible(accountId, agentId);
        if (visible.getData() == null) {
            return ReturnVo.fail(visible.getMsg(), SkillErrorCodeEnm.SKILL_ACCESS_DENIED.getCode());
        }
        Object sn = Db.selectObject("select sn from tbl_data_agent where id = ?", agentId);
        // sn 可为空（库配置驱动的对话智能体以 agentId 寻址），空 sn 属正常而非"不存在"
        return ReturnVo.ok("操作成功!", sn == null ? null : (String) sn);
    }

    @Override
    public ReturnVo<Boolean> validateVisible(String accountId, Long agentId) {
        if (agentId == null) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_AGENT_NOT_FOUND.getMsg(),
                SkillErrorCodeEnm.SKILL_AGENT_NOT_FOUND.getCode());
        }
        // agent-publish-group-grant R-03 双分支：无任何授权行 = 全公开（仅 published；草稿不享公开待遇，防反向漏洞）
        Object grants = Db.selectObject(
            "select count(*) from tbl_platform_group_agent_info where agent_id = ? and del_flag = 0",
            String.valueOf(agentId));
        if (grants != null && ((Number) grants).longValue() == 0) {
            Object pub = Db.selectObject(
                "select count(*) from tbl_data_agent where id = ? and status = 'published'", agentId);
            if (pub != null && ((Number) pub).longValue() > 0) {
                return ReturnVo.ok(true);
            }
            return ReturnVo.fail("该智能体未发布且未授权，前台不可见", SkillErrorCodeEnm.SKILL_ACCESS_DENIED.getCode());
        }
        // 有授权行 → 组交集判定（现状原样）
        // 注意：tbl_platform_group_agent_info.agent_id 为 varchar，按字符串比较避免 varchar=bigint 报错
        Object visible = Db.selectObject("""
                select count(*)
                  from tbl_platform_account_group_info ag
                  join tbl_platform_group_agent_info ga on ga.group_id = ag.group_id and ga.del_flag = 0
                 where ag.account_id = ? and ag.del_flag = 0 and ga.agent_id = ?
                """, accountId, String.valueOf(agentId));
        if (visible == null || ((Number) visible).longValue() == 0) {
            return ReturnVo.fail("该智能体未授权给当前账号所在组",
                SkillErrorCodeEnm.SKILL_ACCESS_DENIED.getCode());
        }
        return ReturnVo.ok(true);
    }

    @Override
    public ReturnVo<Boolean> validateExplicitSkills(String accountId, Long agentId, List<Long> skillIds) {
        if (skillIds == null || skillIds.isEmpty()) {
            return ReturnVo.ok(true);
        }
        List<Long> allowed = allowedSkillIds(accountId, agentId);
        List<Long> denied = skillIds.stream().filter(id -> !allowed.contains(id)).toList();
        if (!denied.isEmpty()) {
            return ReturnVo.fail(SkillErrorCodeEnm.SKILL_ACCESS_DENIED.getMsg() + ": " + denied,
                SkillErrorCodeEnm.SKILL_ACCESS_DENIED.getCode());
        }
        return ReturnVo.ok(true);
    }

    @Override
    public String buildScopeHint(String accountId, Long agentId) {
        List<Row> rows = Db.selectListBySql(MY_SKILLS_SQL, accountId, agentId);
        if (rows.isEmpty()) {
            return "本轮你没有任何可用技能，禁止调用技能加载类工具。";
        }
        String names = rows.stream().map(r -> r.getString("name")).reduce((a, b) -> a + "、" + b).orElse("");
        return "本轮你仅可使用以下技能：" + names + "；其他技能一律视为不存在，禁止加载。";
    }

    private List<Long> allowedSkillIds(String accountId, Long agentId) {
        List<Row> rows = Db.selectListBySql(MY_SKILLS_SQL, accountId, agentId);
        return rows.stream().map(r -> r.getLong("id")).toList();
    }
}
