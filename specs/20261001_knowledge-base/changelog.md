# Changelog: knowledge-base

## Implement 收口 + 验收期补充（2026-10-01）
- T-01~T-11 全勾；六 AC 全绿（artifacts/completion 全录）；关键实证：双 agent 复用一库、解绑即停、迁移 [19]=[19]
- 用户实测三缺全修：BUG-50 弹窗裸渲染(ElDialog 漏 import) / BUG-51 切库抽屉残留(:key 重建) / 
  「分配组无入口」→ 知识库行新增「分配组」dialog（预选+增撤合并），组管理侧入口保留成双向；
  后端补 GET /platform/group-kbase/kbase/{id}/groups 反查端点（5534c0f）
- 环境插曲：宿主磁盘写满→Redis MISCONF→登录 500 假象，清 10GB 自愈（非产品缺陷）
- 发现存量缺口 BUG-49（QA 召回不回表答案）登记待拍板
## v1.0.0 方案确认 + tasks（2026-10-01）确认人: 陈卓
- **第②关通过**（用户「确认方案」，P1~P6 与 4 风险整体接受）→ plan 转 v1.0.0 已确认
- Phase 3：tasks T-01~T-11 生成即确认（沿用既定授权）；开分支 feature/knowledge-base，进入 Implement

## T-06 完成注记（2026-10-01）——召回切换绑定语义，三态实证
- Mapper selectRecalledKnowledgeIdsByBindings（绑定→启用库→召回条目）；DynamicFilterService AGENT_KNOWLEDGE
  分支去 eq(agent_id) 改纯 in(knowledge_id)（BUSINESS/其余维持旧口径一字未动）
- AC-04 数据面：agent20 旧口径=[19]=新口径绑定路（psql 实跑）
- AC-05 功能面：agent33 绑定态原文引用知识特征词"7+1"、解绑态明确"检索不到"（特征词零命中+空召回WARN）
- **发现（非缺陷）**：解绑后 agent25 仍能"引用"——AgentScope 长期记忆固化了历史对话（记忆体系范畴，
  与召回过滤无关）；测试改用无记忆 agent33 做判定，特征词法区分命中/未命中
- P6 落实为自然成立：删 agent 现流程本就不级联删知识行/向量（grep 无调用点），绑定行遗留不影响语义

## T-05 完成注记（2026-10-01）——条目端点 kbId 化 + 空 agent 全链 null 安全
- 端点改造：create/query 双维（kbId 新主流；agentId 写侧停用返回引导文案、读侧兼容）；kb 存在性/启用态校验；
  测试九项全对（建 QA/DOCUMENT 落 kb、停用库拒增、双空报错、旧维度引导）
- **修正 4 处**：①page SQL 替换时丢 FROM 子句（自查重验发现）②原 SELECT * 顺带规范为显式列（动到即治理）
  ③QueryDTO 遗留 @NotNull(agentId) 挡死 kbId-only 查询→移除改 service 守门 ④嵌入/删除路径 NPE：
  kb 条目 agent_id 为空——**哨兵 "0" 方案**统一（util/manager/listener 四处；agent_id 列放 NOT NULL 入 V 件）
- **事实纠正**：AGENT_KNOWLEDGE 向量实存 **tbl_vector_store_simple_data**（631+行，metadata 驼峰键
  agentId/agentKnowledgeId）；plan 写的 tbl_harness_vector_store_knowledge 是 harness SimpleKnowledge 空表——
  T-06 改造与召回验证一律以 simple_data 为准（risk 注记）
- retry-embedding 为 **POST**（先前 curl 用 PUT 撞 500 属测试姿势，非产品缺陷）；哨兵行重试后 4/4 COMPLETED 实证
- 环境插曲：Docker VM 磁盘写满致 Redis MISCONF/登录 500/容器退——清理旧镜像 tag+构建缓存回收 ~10GB 后全栈恢复

## T-03/T-04 实测修正两处类型地雷（2026-10-01）
1. 组×agent 表 agent_id 列是 varchar（BL-07 同族遗留）而实体 Long——QueryChain 传 Long 触发 PG「varchar=bigint」无算子 500；改显式 String.valueOf 并注释钉死
2. platform BaseModel 的 create/update_time 是 java.util.Date（非 LocalDateTime），insert 未显式填 update_time 撞 NOT NULL 500；两处补齐
教训：跨域借用实体/mapper 前先核对列物理类型与基类字段类型，不靠命名惯例推断

## Implement 注记（2026-10-01）
- **措辞落地**：data 域 ApiResponse 无 code 位——plan §2 的 42040/42041/42042 在本域以**可读 message** 承载（查重/删除保护/引导文案），行为等价；错误码枚举仅用于 agent 域（files 4203x）
- T-01 实测修正一处：条目表软删列实为 is_deleted（DDL 已改，四拍重验通过）

## Plan v0.1.0（2026-10-01）初稿
- 实证取巧点：向量过滤 validIds 本为条目表实时查询（DynamicFilterService:48/56），故**向量零迁移**（P1）
- 域划分：写侧校验在 platform（组数据域），召回读侧在 data（同域读绑定表）（P3）
- 端点复用策略 P5（agent-knowledge 换作用域参数），删 agent 不再级联删向量 P6（风险注记 2 条）
- 待第②关确认

## v1.0.0（2026-10-01）确认人: 陈卓
- **三重确认第①关通过**（用户「确认需求」；A-01~A-06 默认生效，尤其 A-05 绑定期校验语义、A-06 迁移命名）
- 进入 Phase 2 Plan

## v0.2.0（2026-10-01）五问决议入册
- Q1~Q5 全部落条款：R-11 定稿（绑定多选）；新增 **R-13~R-15 组授权**（用户主动扩大范围，对齐 tbl_platform_group_agent_info 同构；组校验时机=绑定期，运行时只认绑定表——A-05 待纠正窗口保留）
- Non-goals 复核：业务知识不搬（Q4 拍板维持）；里程碑 v1.3.0（Q5）已 M1 挂接
- 迁移命名假设 A-06 新增

## v0.1.0（2026-10-01）创建
- Specify 起稿：12 条 EARS / 6 AC / Non-goals / 假设 A-01~05
- 现状实证：tbl_data_agent_knowledge(agent_id 强绑, 类型 DOCUMENT/QA/FAQ, isRecall/embeddingStatus/retry-embedding, 上传链路)；前端在 agent-create-drawer 知识 tab；向量表 tbl_harness_vector_store_knowledge
- 待用户回答 Q1~Q6 后进 v1.0.0 确认门
