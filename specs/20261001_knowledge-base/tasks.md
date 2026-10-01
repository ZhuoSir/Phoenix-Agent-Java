> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-01 | 确认人: 陈卓 | 确认日期: 2026-10-01（沿用既定授权：tasks 生成即确认、直接执行）

# 任务清单：独立知识库模块

> 验证口径：无测试基建 → 临时库三拍 + A 交付栈真机 curl/psql/SSE 留证。

## 1. 数据与迁移

- [ ] T-01 V1.3.0_02 草案（spec/sql）：三新表+条目加列+索引+菜单种子+存量迁移段；回滚件
  关联: R-01,02,05,08,10,13 | 依赖: 无
  验证方式: 临时库三拍（正向/重跑/回滚 errors=0）；夹具迁移断言：造 2 agent×3 条目→跑迁移→自动库×绑定×kb_id 回填数量核对
  验收标准: 全部幂等可重跑；回滚无损条目数据

## 2. 库与绑定（后端）

- [ ] T-02 data 域知识库 CRUD（KnowledgeBase entity/mapper/service/controller：page/create/update/delete/detail；42040 查重、42041 删除保护附绑定清单）
  关联: R-01,03,04,12 | 依赖: T-01
  验证方式: curl 四端点全路径+负例（重名/删被绑库/无 token 401）
  验收标准: ReturnVo 信封、错误码入枚举集中、组标签与条目数在列表/详情可见

- [ ] T-03 绑定（data 表/服务 + platform 端点 kbase-bindable/kbase-bind，组交集校验与无组兜底，服务端复核）
  关联: R-08,14,15 | 依赖: T-01, T-02
  验证方式: curl：候选含 disabled_reason；跨组直绑被服务端拒绝；无组 agent 全量可选；绑定回显
  验收标准: 绑定唯一键防重；解绑即时生效（联动 T-06 召回）

- [ ] T-04 platform 组分配库（GroupKbaseInfo mapper/service + group-info 分配/清单端点，同构 assign-agent）
  关联: R-13 | 依赖: T-01
  验证方式: curl 分配两组×查询回显×撤组后 T-03 候选置灰联动
  验收标准: 与 tbl_platform_group_agent_info 同构；级联撤组时清绑定or提示（按实现最小语义：保留绑定仅提示孤儿——记 changelog）

## 3. 条目与召回

- [ ] T-05 条目端点作用域切换：/api/agent-knowledge 全端点支持 kbId（query/create/update/delete/recall/retry），旧 agentId 参数返回 42042 引导
  关联: R-05,06,07 | 依赖: T-01, T-02
  验证方式: curl 以 kbId 建 QA/FAQ/上传 DOCUMENT→列表/召回开关/retry-embedding 全链路；向量化状态流转实测
  验收标准: 类型/字段/校验与旧一致（对照 R-05"沿用"）；kb 停用状态不可新增

- [ ] T-06 召回切换：selectRecalledKnowledgeIdsByBindings + DynamicFilterService AGENT_KNOWLEDGE 分支改 in(knowledge_id)（去 eq(agent_id)）；向量写入 metadata 去 agent 语义；删 agent 不级联删向量（P6 调用点改造）
  关联: R-09,10,15 | 依赖: T-03, T-05
  验证方式: AC-04 对照脚本（克隆现库跑迁移→同问召回命中同条目）；绑定/解绑/停用库三态召回增减实测（SSE 或工具返回）
  验收标准: BUSINESS 分支零改动；RulesRagTool 风险注记落地（无绑定=空召回可观察）

## 4. 前端

- [ ] T-07 知识库页：views/knowledge-base/index.vue（列表/新建/编辑/删除保护提示/组标签）+ 条目面板复用改造（AgentKnowledgeConfig→kbId 参数化）+ 路由注册
  关联: R-02,03,04,05,06 | 依赖: T-02, T-05
  验证方式: 浏览器实操（用户）+ vue-tsc 改动零新增 + build；菜单种子在库后侧栏出现「知识库」
  验收标准: 与智能体管理同级；条目面板功能对齐旧 tab 体验

- [ ] T-08 智能体抽屉「知识库绑定」多选（置灰+原因 tooltip）+ 组管理页「分配知识库」dialog（抄 assign-agent 同构）
  关联: R-11,13,14 | 依赖: T-03, T-04
  验证方式: 浏览器实操：绑定候选/置灰/保存回显；组页分配联动；知识配置旧 tab 移除
  验收标准: 抽屉不再可编辑条目内容（R-11）

## 5. 交付与收口

- [ ] T-09 docker 联动：verify 增 [11]（菜单行+page 端点）；README 知识库迁移注记（V1.3.0_02 跑在升级序中）
  关联: R-02,10 | 依赖: T-02, T-06
  验证方式: 重建滚动后 verify 11 断言全绿；compose down/up 迁移台账一致
  验收标准: 无新对外端口；存量数据升级一遍到位（幂等）

- [ ] T-10 E2E 全链 + AC-01~06 留证（重点 AC-02 双 agent 复用、AC-04 迁移零感知对照、AC-05 解绑即停）
  关联: 全部 | 依赖: T-01~T-09
  验证方式: curl/psql/SSE 命令输出全录 artifacts；对照表逐 AC 红绿
  验收标准: 十项全绿可勾

- [ ] T-11 收口：artifacts.md、completion.md、changelog、backlog 销账、MILESTONE 推进、合并 main（发版仍等你指令，用户既定：v1.3.0 续收）
  关联: R-20~22 | 依赖: T-10
  验收标准: git diff main 无 spec 外越界改动；三重确认门全绿
