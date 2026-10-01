> 版本: v0.1.0 | 状态: 待确认 | 更新: 2026-10-01 | 确认人: | 确认日期:

# 技术方案：独立知识库模块（对齐 requirements v1.0.0）

## 0. 一图流

```
┌ 管理面（admin）────────────────────────────────────────────┐
│ 知识库页(/knowledge-base)          组管理页                │
│  建库/编辑/删除/组标签展示          「分配知识库」dialog     │
│  行内→条目面板(复用现组件,参数化kbId)  (同构 assign-agent)   │
│ 智能体抽屉 tab→「知识库绑定」多选(无共同组=置灰)             │
└──────────────────────────────────────────────────────────┘
数据域(data): tbl_data_knowledge_base · 条目表+kb_id · tbl_data_agent_kbase_bind
平台域(platform): tbl_platform_group_kbase_info · 绑定/分配 API(组校验在平台)
召回链路(data/DynamicFilterService):
   agentId ──绑定表──> kb 列表(启用) ──> 条目 id 集合(is_recall=1)
   → 向量过滤改为 agent_knowledge_id IN(...)，不再按 payload.agent_id 过滤
```

**最关键的取巧（实证于现状代码）**：向量过滤的 validIds **本来就来自条目表实时查询**（`selectRecalledKnowledgeIds(agentId)`），payload 里的 agent_id 只是冗余键。所以迁移**不动一行向量数据、不重嵌入**——只改 validIds 的解析路径（agent→自有条目 变为 agent→绑定库→条目），向量侧过滤条件把 `eq(agent_id)` 换成 `in(agent_knowledge_id,...)` 承载全部语义（条目 id 全局唯一，无泄漏面）。

## 1. 数据模型（DDL：V1.3.0_02，spec/sql 起草，M3 归位）

| 表 | 关键列 | 说明 |
|---|---|---|
| `tbl_data_knowledge_base` **新** | id(bigserial pk), name(64, 未删重名查), description, status(1启用/0停用), creator, del_flag, create/update_time | 库实体（A-03 状态字段生效） |
| `tbl_data_agent_knowledge` **改** | +`knowledge_base_id`(bigint, null) | agent_id 列保留为溯源冗余（查询不再使用）；索引 (kb_id, del_flag) |
| `tbl_data_agent_kbase_bind` **新** | id, agent_id(bigint), knowledge_base_id, creator, create_time | 多对多绑定（Q2）；唯一键 (agent_id, kb_id) |
| `tbl_platform_group_kbase_info` **新** | id(varchar), group_id, kbase_id | 组授权，与 tbl_platform_group_agent_info 严格同构（Q3） |
| `tbl_privilege_module` **种子** | 「知识库」/knowledge-base/sn=KnowledgeBase，同智能体管理同根 | V 件 INSERT…WHERE NOT EXISTS |

**迁移段（同件内，幂等）**：每个有未删条目的存量 agent → 建「{agent名}的知识库」（A-06）→ 建绑定 → 条目回填 kb_id。约束：全程 WHERE NOT EXISTS 守卫可重跑；回滚件 drop 三表+列+菜单行（条目数据无损）。

## 2. 域划分与接口（api 规范：ReturnVo 信封/42xxx 段位续号）

**data 域**（`/api/knowledge-base`，新 KnowledgeBaseController/Service）：
- POST 创建（名称必填≤64、查重→KB_NAME_DUPLICATE 42040）｜PUT 编辑｜DELETE 逻辑删（有绑定→拒绝 42041 并附绑定 agent 清单 R-04）
- GET `/page`（名称模糊+组 id 过滤+分页）、GET `/{id}`（详情含组标签与条目数）
- 条目端点**复用现 `/api/agent-knowledge`**：query/page、create、update、delete、recall/{id}、retry-embedding/{id}——全部把作用域参数从 agentId 换 kbId（旧参数兼容废弃，见 §6 灰度）

**platform 域**（组/绑定的写侧——组数据在平台域，校验必须在这层）：
- PUT `/platform/group-info/{groupId}/kbase-assign`（组分配库，同构现有 assign-agent）
- GET `/platform/agent/{agentId}/kbase-bindable`（候选列表：全库+共同组标记 disabled_reason，R-14）
- PUT `/platform/agent/{agentId}/kbase-bind`（全量提交绑定集合；服务端**复核**组交集，防绕过前端）

## 3. 召回改造（R-09/R-10/R-15 的落点，全在 data 域）

- `AgentKnowledgeMapper` 新增 `selectRecalledKnowledgeIdsByBindings(agentId)`：join bind×kb(status=1,未删)×条目(is_recall=1,未删)
- `DynamicFilterService.buildDynamicFilter` 的 AGENT_KNOWLEDGE 分支：eq(agent_id) 条件**去除**，改为上述新查询的 `in(agent_knowledge_id, ids)`；BUSINESS 分支一字不动（Q4）
- `KnowledgeRetrievalToolContributor` 无需改（经由上述服务）；`RulesRagTool` 硬编码 agentId=18 属遗留样本，不在本需求动它（记入风险）
- **向量写入路径**：新条目上传嵌向量的 metadata 构造去掉 agent_id 语义（写 kb_id 可选），因查询已不依赖该键

## 4. 前端（admin-ui，views 新目录 + 复用重构）

- `views/knowledge-base/index.vue`：列表（名称/描述/组标签/条目数/状态/时间，操作：编辑/删除/条目管理）+ 新建/编辑 dialog + 删除保护提示（展示后端 42041 的绑定清单）
- **条目面板复用**：现 `AgentKnowledgeConfig.vue` 参数化改造（agentId→kbId prop，API 同端点换参），智能体抽屉不再引用它
- 智能体抽屉「知识配置」tab → **绑定多选**：调 kbase-bindable，置灰项带原因 tooltip；保存调 kbase-bind
- 组管理页：新增「分配知识库」dialog（抄 assign-agent-form 同构）
- 菜单/路由：module 种子（V 件）+ 前端路由 `/knowledge-base` 注册

## 5. 交付包联动

- compose/nginx 零新增（全在 /api 前缀内）；verify 增补：[11] 知识库菜单行存在 + page 端点信封可达（登录态）
- 迁移在 migrator 既有通道内跑 V1.3.0_02（幂等重跑安全）；AC-04 对照脚本（迁移前后同问同命中）

## 6. 决策与已否方案

| # | 决策 | 被否替代 & 理由 |
|---|---|---|
| P1 | 向量**零迁移**：改 validIds 解析+过滤键切 knowledge_id | ①按绑定扇出向量（每绑一 agent 复制一份）——否：写放大+解绑一致性地狱；②payload 回填 kb_id——否：动大表 jsonb 且查询已不依赖它 |
| P2 | 条目表加列复用，不建新表 | 新建 item 表双写迁移——否：代码迁移面翻倍，现表 id 即向量关联 doc_id 源，动表伤召回 |
| P3 | 绑定/分配 API 放 platform，绑定表放 data | 全塞 data——否：data 不能反向依赖 platform（组数据在平台域），校验会跨不过去；绑定表在 data 保证召回同域读（platform→data 单向依赖现成） |
| P4 | 组校验时机=绑定期+服务端复核；运行时只认绑定表（R-15/A-05） | 运行时二次组过滤——否：每次对话多一跳平台域查询，且撤组的语义应是重绑而非隐身 |
| P5 | 现 /api/agent-knowledge 端点换作用域参数复用 | 新起 /api/knowledge-item 全套端点——否：五端点重复建设；旧 agentId 参数保留一个版本的兼容报错引导（42042 提示改用 kbId） |
| P6 | agent 删除不再删知识向量（只清绑定） | 沿袭 deleteDocumentsByVectorType(agentId)——否：知识资产归库后，agent 生死与库解耦，误删他人共享库条目是事故 |

## 7. 风险

1. **RulesRagTool 硬编码 agentId=18**：遗留演示代码，迁移后该工具行为随新过滤语义变化（18 无绑定则空召回）——如实观察，不动
2. 迁移幂等性：kb 名称查重与 NOT EXISTS 守卫必须严丝合缝，重跑 V 件不得产生双库（T 件用临时库三拍实测）
3. 组授权种子数据缺失：存量 agent 的组关系存在与否影响「候选置灰」——bindable 接口对无组 agent 返回全量可选（宽松兜底），写入 changelog 注记
4. P6 语义变化：删 agent 不再级联删向量——若库是迁移自动建的，删 agent 后其自动库成孤儿（保留，管理员在知识库页可见可删，可接受）
