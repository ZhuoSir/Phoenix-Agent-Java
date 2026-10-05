# Tasks: agent-publish-group-grant

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-04 | 更新: 2026-10-04

## 组 1：后端判定与端点

- [x] T-01 发布扩展+授权读写端点（phoenix-data）：AgentPublishDTO{groupIds}；publish 增 body（无 body=仅置状态不动授权，向后兼容）；GET/PUT /api/agent/{id}/groups（回显/覆盖式调整）；组存在性 Db 直查校验+假组拒绝（镜像技能 replaceGroupGrants）
  关联: R-01, R-02, R-04
  依赖: 无
  验证方式: curl 发布三态（无 body/空数组/带组）；覆盖式实证（重发布换组旧组行移除）；假组拒绝；GET 回显与 DB 双侧一致
  验收标准: 三态+覆盖式+假组+双侧取证全绿

- [x] T-02 validateVisible 双分支（FrontSkillAccessServiceImpl）：先数授权行（agent_id String 化），0 行→可见（全公开分支）；>0 行→现有交集 SQL 原样；同文件技能判定方法零改动
  关联: R-03
  依赖: 无
  验证方式: 三态 curl+DB 双侧取证（无行公开/有行命中/有行不命中）；**技能交集对面断言**（同文件他方法行为不变）
  验收标准: 三态全绿且技能域零变化

- [x] T-03 getMyAgents 公开合并（AccountInfoServiceImpl）：组交集结果 ∪ 无授权行 published 且 sn 空直查集（NOT EXISTS + id::text）；无组账号走公开集（现返回 null 死角修复）
  关联: R-03
  依赖: 无
  验证方式: 双账号实测（有组=交集∪公开 / 无组夹具=仅公开）；admin 列表（listCreatedInPlatform）零变化断言
  验收标准: 双账号语义正确+admin 面不变

## 组 2：前端

- [x] T-04 发布弹窗（list/index.vue）：handlePublish 从 ElMessageBox 升级为组多选弹窗（getGroupInfoPageApi 数据源+已发布回显现有授权+**空选警示 Alert「不授权所有前台用户均可见」**）→ publishAgentApi(id, groupIds)
  关联: R-01, R-05
  依赖: T-01
  验证方式: 界面走查三态（草稿发布带组/空选警示可见/重发布回显）；vue-tsc 基线不涨；列表页其余按钮零变化
  验收标准: 走查全通+基线守住

- [x] T-05 授权组区块 AgentGroupGrant.vue：多选+保存（PUT groups）；已发布可编辑、草稿提示先发布；**双入口挂载**（编辑抽屉左菜单「授权组」+ 独立页 /agent/:id——L-06 今日教训，技能/插件块双入口先例）
  关联: R-02, R-04
  依赖: T-01
  验证方式: 双入口走查（保存/回显/草稿态提示）；既有区块零变化；vue-tsc 基线
  验收标准: 双入口全通+既有零变化

## 组 3：收口

- [x] T-06 E2E 矩阵+对面断言+全量回归
  关联: 全部 R
  依赖: T-02, T-03, T-04, T-05
  验证方式: **E2E 主链**（发布空授权 → 无组夹具用户前台列表可见+对话成功 → 勾 G1 收敛 → 无组用户列表不可见+chat 拒绝）；**对面断言四连**（无授权技能仍不可见 / MCP 未授权语义不变 / 组管理侧 CRUD 行为不变 / admin 智能体列表不变）；回归（verify 13/13、vue-tsc 213、MCP 链抽查同版共存）；夹具全清+发布态还原
  验收标准: 主链+四对面+回归全绿，现场零残留

- [x] T-07 台账收尾：completion/artifacts/MILESTONE 第二需求行/BL-04 交付联动注记/A-5 存量翻转提醒移交 M3 RELEASE-NOTES
  关联: 流程
  依赖: T-06
  验证方式: completion 与勾选核对；artifacts 与实改动对账
  验收标准: 台账齐、偏差与去向写明

## 自检
- R 覆盖：R-01(T-01/04) R-02(T-01/05) R-03(T-02/03/06) R-04(T-01/05) R-05(T-04/06)——全覆盖无孤儿
- 依赖无循环；读点清单（plan §1.2）六项分布：#1→T-02、#2→T-03、#3~#6 零变化→T-06 对面断言
- 粒度：7 任务各一次会话可完成可独立验证
