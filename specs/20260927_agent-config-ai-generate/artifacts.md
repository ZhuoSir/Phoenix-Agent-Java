# 升级件登记: agent-config-ai-generate

| 类型 | 内容摘要 | 来源任务 | 草案位置 | 已汇总至里程碑 |
|---|---|---|---|---|
| DDL | `tbl_data_model_config` 增 `is_default boolean NOT NULL DEFAULT false`（含 COMMENT：每类型至多一条，未显式选择模型时加载） | T-01 | `sql/05_model_default.sql` | **v1.2.0** |
| INDEX | 部分唯一索引 `uk_dmc_type_default ON (model_type) WHERE is_default = true AND is_deleted = 0`（每类型唯一默认的 DB 级防线） | T-01 | `sql/05_model_default.sql` | **v1.2.0** |
| DML | 存量回填：为"尚无默认"的每个类型，按「启用优先 → updated/created/id 最新」置一条 `is_default=true`（幂等，重复执行 no-op；先收敛后建索引） | T-01 | `sql/05_model_default.sql` | **v1.2.0** |
| DDL(基线) | `sql/all_schema.sql` 补 5 个缺失的 `CREATE SEQUENCE`（categories/order_items/orders/products/users 的 id 序列）+ 5 条 `ALTER SEQUENCE ... OWNED BY` | T-13（修 B-01） | 基线文件内（已提交 `34877da`） | **v1.2.0** |
| 代码 | 启用互斥调用 `ModelConfigMapper.deactivateOthers` 删除（B-20 消解）；取模型由「该类型唯一启用」改判「该类型默认」（无默认回落启用+WARN） | T-02 / T-03 / T-06 | 无 DDL（行为变更） | **v1.2.0** |
| 配置 | `phoenix.agent.profile-generate-timeout-seconds`（生成调用超时，默认 **90**；实测两项一次出 45~70s，原 60s 会切断成功调用） | T-09 / T-11（BUG-23 修正） | `AgentProfileController` @Value 默认值 | **v1.2.0** |
| 常量 | 名称≤64 字、描述≤120 字、提示词 300~600 字、骨架四段（`AgentProfilePromptTemplates`，代码常量无外部配置源） | T-07~T-09 | plan §接口设计 | **v1.2.0** |
| 依赖 | **前端零新增依赖**：md 编辑器自研（复用已装 `markdown-it`）；补 `src/types/markdown-it.d.ts` 消除既有 TS7016 | T-10 | - | **v1.2.0** |
| 回滚 | `05_model_default_rollback.sql`：drop `uk_dmc_type_default` + drop `is_default` 列（不动 `is_active`/数据）；`all_schema.sql` 的序列补齐为纯增量，回滚=不再需要（旧库本就有这些序列） | T-01 | `sql/` | **v1.2.0** |

## 重放验证记录（T-13）

**环境**：容器 `phoenix-pg`；`drop database → create database phoenix_r13`（真·空库）。

| 步骤 | 命令 | 结果 |
|---|---|---|
| 基线 | `psql -v ON_ERROR_STOP=0 -f sql/all_schema.sql` | **0 报错**（修复 B-01 前为 64 处） |
| 01 技能管理 | `psql -v ON_ERROR_STOP=1 -f specs/20260927_agent-skill-management/sql/01_agent_skill_upgrade.sql` | exit=0 errors=0 |
| 02 技能菜单 | 同上 `02_skill_menu.sql` | exit=0 errors=0 |
| 03 运行配置 | 同上 `dynamic-agent-types/sql/03_agent_runtime_config.sql` | exit=0 errors=0 |
| 04 知识库参数 | 同上 `04_knowledge_tool_params.sql` | exit=0 errors=0 |
| 05 模型默认 | 同上 `05_model_default.sql` | exit=0 errors=0 |
| 幂等复跑 | 再执行 03 与 05 | 均 exit=0 errors=0 |
| 对象校验 | information_schema | `tbl_data_agent_runtime_config` 17 列、`is_default` 列存在、3 张新表建成 |
| 回滚 | `05_model_default_rollback.sql` | exit=0 errors=0，`is_default` 列消失（=0） |

**执行顺序**：`all_schema.sql` → 01 → 02 → 03 → 04 → 05（05 依赖 `tbl_data_model_config` 及其 `is_active` 列；`is_default` 为纯增量新列，旧代码不读，可先升级代码或先跑 SQL 均不报错，但**运行时取默认模型需先执行 05**）。

## 存量库实测（非空库，现网 phoenix）

- 首次执行 05：exit=0；CHAT 因 B-20 历史遗留有 2 条同时启用 → 回填只选中最新一条（`is_default` 计数=1），EMBEDDING 同样=1；索引建成
- 重复执行 05：exit=0 errors=0（幂等）
- 回滚 05：`is_active` 快照 md5 与执行前完全一致（`c55fe87b…`）→ 证未动启用数据
- 回滚后再执行 05：成功

## 回滚操作说明

1. 数据：`05_model_default_rollback.sql`（丢默认标记，回滚后按「唯一启用」取模型；重新升级会重新回填，可能与回滚前不同一条）。
2. 代码：回退到本 spec 之前提交；由于运行时取模型改判默认，**回退代码前必须先回滚 SQL 或保留该列**（旧代码不读 `is_default`，保留列无害）。
3. 基线序列补齐（B-01）为纯增量，不需要回滚动作。

## 界面走查证据（T-05/T-10/T-11/T-12，2026-09-30，验证人=用户）
- 模型管理页（T-05）：用户确认无问题；**用户实际操作把 CHAT 默认从 qwen3.8-flash 切到 deepseek-v4-flash 且两条保持同时启用**——同时实证 AC-06（多启用集合）与 AC-07（设为默认迁移）→ BUG-20 转已验证
- md 编辑器（T-10）：用户确认无问题
- AI 生成（T-11）：首轮复现「30s 超时/转圈」→ 定位前端全局超时（BUG-30，commit c916cb4 修复）+ 当日 macOS 代理残留致后端出站被拒（清代理重启后端解决，`api.deepseek.com` 直连 0.27s）；复测通过 → BUG-22/23/30 转已验证
- 运行配置下拉（T-12）：用户按指引（编辑页底部卡片/抽屉「对话智能体配置」菜单）确认后通过
- 剩余：T-13 端到端（对话一轮走默认模型与技能共存 + 存量纯文本保存不变 + AC 汇总）

## T-13 端到端回归记录（AC-01~AC-13）
| AC | 结论 | 证据（本 spec 各节汇总） |
|---|---|---|
| AC-01 | ✅ | 早前 curl 三组合实测（描述/提示词/双项 45.7s）+ 09-30 UI 复测；启动日志 `默认对话模型: configId=5, provider=deepseek, modelName=deepseek-v4-flash` |
| AC-02 | ✅ | 生成产物 `## 角色/描述/能力/安全范围` 四段（agent 30 现库 `left(prompt,80)` 实证），编辑器预览用户确认 |
| AC-03 | ✅ | 走查清单第 3 项含覆盖确认/取消保留/撤销，用户确认通过 |
| AC-04 | ⚠️ 前提变更 | 用户 09-27 决议把两个生成按钮**合并为单按钮同时生成**，「分字段独立 loading/单独重试」不再成立；API 层 `targets` 单项能力保留（早前 DESCRIPTION-only / PROMPT-only curl 实测）。按用户决议记录，不视为缺陷 |
| AC-05 | ✅ | T-10 走查（骨架插入、`**加粗**` 手写保存原文、预览渲染）用户确认 |
| AC-06 | ✅ | 用户实操：id=5/7 两条 CHAT 同时 `is_active=t` 互不影响（09-30 psql 复核） |
| AC-07 | ✅ | 用户实操默认迁移 qwen→deepseek：`default` 唯一落 5，7 仍启用，EMBEDDING 默认 6 不受影响 |
| AC-08 | ✅ | 早前接口实测：设默认自动启用；停用默认被拒（400 + 后端文案） |
| AC-09 | ✅ | 早前 HarnessModelRegistry 实测：所选缺失/停用 → 回退默认并 WARN（日志留档） |
| AC-10 | ✅ | T-06 证据：设 EMBEDDING 默认后 schema 初始化按新默认（日志含 configId + 重建提示） |
| AC-11 | ✅ | 早前实测 42011 引导文案、表单不变 |
| AC-12 | ✅ | 见「重放验证记录」「存量库实测」两节：空库全序零报错/幂等/回滚干净 |
| AC-13 | ✅ | agent 25 纯文本提示词经编辑器保存后 `length=29`、md5 `5c585afaf147688f5b8b7232bd5ea3f5` 逐字未变 |

**对话共存回归（09-30 17:58）**：agent 30（提示词=生成的 md 原文、绑定 weather 技能、运行配置不选模型）→ 日志 `Registered tool 'load_skill_through_path'` + `tool_call/PRE_ACTING/POST_ACTING` 实际执行，模型 = 默认 `deepseek-v4-flash`，多轮消息 2→18 正常。
