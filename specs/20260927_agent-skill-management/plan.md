> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-09-27 | 确认人: 陈卓 | 确认日期: 2026-09-27

# 技术方案：智能体技能管理

规范依据：`.specrc.yml` → api-design/database = global（已加载），code-frontend = none（跟随仓库工具链）；信封沿用项目现状 `ReturnVo`（api 规范「全项目统一一种」按既成事实满足，不强制迁移 /api/v1）。

## 方案概述
技能数据仍以 AgentScope 的 `tbl_harness_skills`(+resources) 为唯一真源（运行时与 CRUD 共表，无双写）。在其上做四件事：①加 `status` 列实现草稿/发布两态（R-03）；②新增两张关联表实现「智能体绑定」与「组授权」（R-04/R-07）；③用**装饰器包一层 AgentSkillRepository**，把运行时技能池从"全表"过滤为「已发布 ∧ 绑定本智能体」（R-06）；④对话请求加 `enabledSkillIds`，在 `buildUserMessage` 处校验并**强制注入** `<active_skills>` 段（R-05/R-09），前台新增带组鉴权的对话通道。管理 CRUD 落在 phoenix-agent 域（与 skill 表同域），前端三块 UI 在 admin-ui。

## 涉及模块与数据流
```
phoenix-agent-api      + SkillStatusEnm / DTO(SkillUploadDTO,SkillPublishDTO,SkillBindingDTO) / VO(SkillListVO,SkillDetailVO,SkillRefVO)
phoenix-agent-core     + SkillMapper/SkillGroupMapper/SkillAgentMapper(MyBatis-Flex)
                        + SkillAdminService(CRUD/发布/授权/绑定) + SkillAccessService(三重交集判定)
                        + FilteredSkillRepository(装饰 AgentSkillRepository, 按 sn 过滤) ← R-06 关键
                        + HarnessChatServiceImpl.buildUserMessage 注入点改造 ← R-05
                        + AbstractHarnessAgent 两处 builder: skillRepository(装饰器)
phoenix-agent-rest     + /api/skill/** (后台管理)
                        + /platform/account-info/getMySkills + /platform/harness/chat (前台通道)
phoenix-data           不动接口；saveBySn 若覆盖 status 则随附修正(见风险②)
admin-ui               + views/skill/** (列表/详情/上传弹窗/发布弹窗)   ← R-01/02/03/07/08
                        + views/agent/edit 技能配置块                  ← R-04
                        + views/front/chat 技能区 + api-transport 分流  ← R-09
SQL 升级件              ALTER + 2 新表 + 回滚（Implement 时登记 artifacts.md）

数据流（显式执行）: 用户勾选 ids → 对话端点 → SkillAccessService 校验(前台=三重交集)
  → 逐个 getSkill(name).content 拼 <active_skills> 前置注入 → harnessAgent.streamEvents → 响应回带 loadedSkills[]
```

## 接口设计

**后台管理（Sa-Token 登录态，全量视角，前缀 `/api/skill`）**
| 方法/路径 | 语义 | 关键约束 |
|---|---|---|
| GET `/api/skill` | 列表（?keyword ?status，分页 pageNum/pageSize） | 只回 id/name/description/status/updateTime，无 SELECT * |
| GET `/api/skill/{id}` | 详情：正文+资源路径清单 | |
| POST `/api/skill/upload` | multipart `file` + `overwrite`(默认false) | ≤10MB、ZIP 内须 SKILL.md；解析失败→具体原因错误、零写入；同名未确认→冲突错误码 |
| POST `/api/skill/{id}/publish` | body `groupIds[]`（可为空=仅后台） | 置 published + 覆盖式授权 |
| POST `/api/skill/{id}/offline` | → draft | |
| PUT `/api/skill/{id}/groups` | 授权组覆盖式调整，任意状态可调、即时生效 | |
| DELETE `/api/skill/{id}` | 仅 draft 可删；级联清绑定+授权 | 已发布→拒绝；返回引用数供确认框（GET `/api/skill/{id}/refs`） |
| GET `/api/skill/published` | 编辑页可选池 | |
| GET/PUT `/api/skill/binding/agent/{agentId}` | 绑定查询/覆盖式保存 | 独立端点，不改 agent 保存链路 |

**前台（platform 会话身份）**
| 方法/路径 | 语义 |
|---|---|
| GET `/platform/account-info/getMySkills?agentId=` | 三重交集（组授权∧published∧绑定该agent）列表 |
| POST `/platform/harness/chat` | SSE；body 同 HarnessRequest + `enabledSkillIds[]`；校验 agent 对组可见；越权构造→权限错误 |

**存量增强**：`/api/admin/harness/chat` 的 HarnessRequest 加**可选** `enabledSkillIds`（不传行为不变，向后兼容）。错误码进 `SkillErrorCode` 枚举集中维护。

## 数据模型变更（DDL 草案，PG 语法）
1. `ALTER TABLE tbl_harness_skills ADD COLUMN status varchar(16) NOT NULL DEFAULT 'draft';` + COMMENT（`状态 draft草稿 published已发布`）。表由 AgentScope 建（createIfNotExist），上游 insert 不带该列→默认 draft，兼容成立；存量数据迁移后均为 draft（满足假设4）。
2. 新表 `tbl_data_agent_skill_info`：`id varchar(32) PK(雪花), agent_id bigint NOT NULL, skill_id bigint NOT NULL, create_time, update_time, del_flag smallint DEFAULT 0` + COMMENT 全套；`uk_data_agent_skill(agent_id,skill_id) WHERE del_flag=0`（PG 部分唯一索引）；`idx_das_skill(skill_id)`。
3. 新表 `tbl_platform_group_skill_info`：同上结构换 `group_id varchar(32)`；`uk_pgs_group_skill(group_id,skill_id) WHERE del_flag=0`。
4. 代码侧枚举 `SkillStatusEnm(draft/published)`，禁魔法值。
5. 回滚：`ALTER ... DROP COLUMN status;` + `DROP TABLE` ×2。
（命名/公共字段/uk 防线按 database 规范 §六自查通过；status 不建索引——行数量级小且低选择性。）

## 关键决策
1. **CRUD 落 phoenix-agent 域**。理由：与 PostgresSkillRepository/运行时装载同域同表；拒绝落 phoenix-data（那是 NL2SQL 报表域，tbl_data_* 命名虽像但语义不属）；拒绝新建顶层模块（模块数控制）。
2. **运行时隔离用装饰器实现 `AgentSkillRepository`**，agent 构建时传 `new FilteredSkillRepository(delegate, sn)`。理由：不动上游、天然满足 R-06（registry/prompt 注入都经 repository）。拒绝：靠 metadata_json 里 status 过滤（load 路径在上游不可控）；拒绝：按 agent 复制技能行（双写漂移）。
3. **ZIP 解析复用 agentscope-core `SkillUtil.createFromZip(byte[])`**。理由：SKILL.md 定位/frontmatter 必填校验/资源提取的格式权威在上游，自研解析必漂移。拒绝：手写 unzip+YAML 解析。
4. **显式执行=服务端把所选技能全文以 `<active_skills>` 注入本轮 user message**（buildUserMessage 一处改动）。理由：确定性满足"强制、不靠触发词"。拒绝：提示模型"请用技能X"让其自主 load_skill（不确定）；拒绝：改 HarnessAgent 框架（不可控上游）。
5. **绑定/授权走独立端点**，嵌进 agent 通用 save/update 的方案被否（触碰共用链路+register 覆写语义，风险面大）。
6. **前台通道新端点而非复用 admin 的**（权限模型与身份域不同：privilege id vs platform account id，混用即越权面）。

## 风险与规避（2026-09-27 陈卓已拍板）
- **① 自主匹配的授权缝隙【决议：接受为已知限制】**：运行时池按 agent 过滤，但前台用户在**未勾选**（自主模式）下，其组未授权却绑定在该 agent 上的技能，仍可能经模型 load_skill 被触发（name/description 已进提示）。缓解：前台通道在 buildUserMessage 追加"本轮可用技能仅：{用户交集集合}"约束提示；显式勾选路径服务端三重校验无漏洞；彻底封死需上游支持 per-session 技能池（留观察）。
- **② harness 启动 register 覆写【已消解，2026-09-27 实测】**：`AgentServiceImpl.saveBySn` 为 **insert-only**（sn 已存在时不做任何 update），重启不会重置管理端设置的 status；库中制度专家重启后保持 published 佐证。无需附带修复任务。
- **③ 注入体积【决议：可】**：单轮 ≤3 个技能（配置 `phoenix.agent.skill.max-explicit=3`），超限明确报错防 token 爆炸。
- **④ 上游 SkillManageTool**：允许 agent 把自创技能写进 workspace 仓（非 PG 仓），不经发布治理，但按决策 2 装饰器同样过滤 workspace 源——影响限于"agent 自用"，附注观察。
- **⑤ 前台 api-transport 分流**：现按 `type==='sql'` 分流，新增 harness 分支需与既有 react/sql 通道回归隔离；验证方式含三条通道各跑一轮对话。

## 依赖与前置
- agentscope-core 2.0.0 的 `SkillUtil.createFromZip`、`AgentSkillRepository` 接口（jar 已确认存在，具体方法签名 Implement 时以反编译/IDE 核对）
- phoenix-agent-core 需引用 platform 的组数据（读 account-group）——优先复用现模块依赖链，不足则加 pom 依赖（无环：platform 不依赖 agent）
- 菜单 SQL（privilege_module 注册「技能管理」到智能体管理下）
- 规则文件变更清单：无（不改 AgentScope 依赖版本）
