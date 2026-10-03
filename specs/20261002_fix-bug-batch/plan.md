> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-02 | 确认人: 陈卓 | 确认日期: 2026-10-02 | 更新: 2026-10-02 | 确认人: | 确认日期:

# 技术方案：开放缺陷清仓批次（对齐 requirements v1.0.0）

## 1. 逐单落点

| R/单 | 落点（现场核对后允许微调） | 改法 |
|---|---|---|
| R-01/BUG-02 | `HumanFeedbackNode`（workflow/node/）+ AgentService 技能仓装配处 | 构造注入 skillRepository 并在加载技能处使用；缺失即 WARN |
| R-02/BUG-03 | platform 账号创建 DTO/服务（现场定位） | 密码字段 `@NotBlank` + 服务端二次校验（保存侧兜底） |
| R-03/BUG-05 | privilege `AuthErrorCode` + 登录失败分支 | 新增 `LOGIN_PASSWORD_ERROR(23009,"用户名或密码错误")`，登录路径改用新码；改密路径留 23007 |
| R-04/BUG-08 | `application-test.yml` | 对齐本地 docker 交付值（口令/库名/路径），删机主私有路径；入库终结脏文件 |
| R-05/BUG-11 | 验证单：前台 HITL 全链（confirm 帧→/api/front/harness/confirm→并轮续跑） | 实测通过→台账关账注「随 BL-22 治愈」；不通→现场修 |
| R-06/BUG-14 | admin 智能体删除服务 | 删除时级联软删 runtime_config/skill 绑定/组授权三表；附一次性孤儿清理脚本（干跑清单→执行） |
| R-07/BUG-21 | modelconf/index.vue:457 | 三元改 map：CHAT/EMBEDDING/AUDIO→对话/嵌入/音频 |
| R-08/BUG-25 | admin-ui dev 脚本/配置 | vite 启动读 VITE_PORT（package.json dev 加 `--port` 由 env 注入，或 vite.config 读 env） |
| R-10/BUG-18 | 向量文本构成点（DocumentConverterUtil/知识入库链，现场定位）+ 按库重刷入口 | QA/FAQ 嵌入文本=`问题 + "\n" + 答案`；重刷=按库遍历条目重嵌（幂等，可反复执行）；回表返回机制（BUG-49）不动 |
| R-11/BUG-24 | 主图 SSE 装配处（data 域 GraphController 实际路径现场定位）+ 各节点 | 执行上下文 `AtomicBoolean cancelled`；flux `doOnCancel` 置位；AbstractNodeAction 入口检查抛早停 |

## 2. 共享面身份矩阵（skill §3 新规首次执行）

**矩阵 A · AuthErrorCode 23007**（谁依赖这个码）
| 身份 | 变更后预期 |
|---|---|
| 登录失败（前端提示/用户理解） | 新码 23009 文案「用户名或密码错误」，前端不识别新码时走通用错误兜底 |
| 改密失败 | **仍 23007「原密码错误」零变化** |
| 已落库/日志中的旧 23007 记录 | 不迁移不回溯 |

**矩阵 B · QA/FAQ 向量文本构成**（谁消费这批向量）
| 身份 | 变更后预期 |
|---|---|
| 新保存/更新的条目 | 问+答联合文本入库 |
| 存量向量（不重刷） | 维持旧「仅问」向量，检索照常（回表取答案不变） |
| 重刷接口按库执行 | 幂等：重嵌覆盖同 vectorId；中途失败可重跑 |
| 召回过滤（agent_knowledge_id/agentId 语义，BUG-49 系） | 零变化 |
| DOCUMENT 类型条目 | 不受影响（仅 QA/FAQ 分支改构成） |

**矩阵 C · 主图 flux 取消语义**
| 身份 | 变更后预期 |
|---|---|
| 正常完成流 | 行为零变化（cancelled 恒 false 路径） |
| 超时/断开流 | 后续节点早停、无新模型调用（AC-05 日志断言） |
| HITL HumanFeedback 等待态 | 早停异常不得吞掉 pending 状态落库（沿用现路径） |
| Python 子进程/JDBC（③未做） | 行为照旧（技术债注记，已批准延期） |

**矩阵 D · 智能体删除级联**
| 身份 | 变更后预期 |
|---|---|
| 前台/后台可见性（未删的 agent） | 零变化 |
| 删除后 runtime_config/skill_bind/group_agent 行 | 软删（del_flag），查询端天然过滤 |
| 已删 agent 的历史会话/消息/文件 | **不级联**（审计留痕，Non-goals） |

## 3. 被否方案
- 23007 语义就地改文案（否：污染改密场景既有语义——矩阵 A）
- 全量强制重刷向量随发布执行（否：嵌入调用成本 + 存量库可能无 key 配置，交管理入口按需触发）
- 取消令牌用 Thread.interrupt（否：JDBC/子进程不响应且行为不可控，走协作式标志）
- 孤儿清理用物理删除（否：全链路软删一致性，回收交归档策略）

## 4. 风险
1. 重刷接口执行时长随条目数线性——单库同步执行（交付规模可接受），量大再异步化（记债）
2. 早停异常沿图框架传播路径需现场验证（alibaba graph 框架对节点抛错的封装行为以实测为准，不行则改返回值标记——completion 如实记）
3. R-04 改 test.yml 可能影响在用的本机 mvn test 习惯——口令对齐 docker 环境即事实标准
