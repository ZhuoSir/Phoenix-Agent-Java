# Artifacts: agent-publish-group-grant

> 版本: v1.0.0 | 更新: 2026-10-04

## SQL
零 DDL/零 DML（tbl_platform_group_agent_info 现成表，仅写入口变化）。

## 后端（6 文件）
- phoenix-data-api：dto/AgentPublishDTO.java（新增）
- phoenix-data-rest：AgentController（publish 可选 body + GET/PUT /{id}/groups + currentOperator）
- phoenix-data-core：AgentService/AgentServiceImpl（getGrantGroupIds/replaceGroupGrants，覆盖式物理删重建+假组校验+del_flag 默认1陷阱显式置0）
- phoenix-agent-core：FrontSkillAccessServiceImpl（validateVisible 双分支：无授权行且 published→公开；有行→交集原样）
- phoenix-platform-core：AccountInfoServiceImpl（getMyAgents 交集∪公开合并，NOT EXISTS+id::text+sn空过滤，无组账号 null 死角修复）

## 前端（5 文件，admin-ui）
- api/core/agent.ts：publishAgentApi(+groupIds)/getAgentGroupsApi/updateAgentGroupsApi
- views/agent/list/index.vue：发布弹窗（组多选+重发布回显+空选全公开警示）
- views/agent/edit/components/AgentGroupGrant.vue（新增）：授权组区块（全公开语义提示/非 published 禁用引导）
- views/agent/list/agent-create-drawer.vue：授权组菜单+区块（双入口其一）
- views/agent/edit/index.vue：授权组区块（双入口其二）

## 文档/台账
- requirements/plan/tasks 均 v1.0.0 已确认；changelog 全程；completion/artifacts 本件
- MILESTONE v1.6.0 第二需求行；backlog BL-04 已立项(v1.6.0)（发版翻已交付）；bugs 无新增（BUG-64 定点绕行注记）
