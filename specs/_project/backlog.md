# 待办清单（Backlog）
> 编号 `BL-xx` **永久不复用**（同 R/T/BUG 规则；作废标 `[作废]` 保留）
> 状态：`待立项` → `已立项(vX.Y.Z)` → `已交付(vX.Y.Z)`；旁路 `搁置(原因)`、`待确认`（需用户先定口径）
> 边界：本表只放**非需求、非缺陷**的功能与工程待办 —— 缺陷走 `bugs.md`；在手任务走对应 spec 的 `tasks.md`/`completion.md`
> 立项时移入 `specs/{日期}_{功能名}/`，本表条目**保留不删除**，状态列标注交付版本

## 一、功能待办
| # | 待办 | 来源 | 状态 | 备注（现状 / 建议 spec） |
|---|---|---|---|---|
| BL-01 | **MCP 支持**：对话智能体接入 MCP 工具（作为 MCP Client 消费外部 MCP Server），与现有「平台作为 MCP Server 对外暴露 nl2sql/agent 列表」形成双端能力 | 2026-09-27 用户列入待办 | 待立项 | 平台侧已有 `McpServerService`（暴露 `nl2SqlToolCallback`/`listAgentsToolCallback`）与 `McpServerConfig`；AgentScope Harness 侧具备 `McpServerRegistrar`/`McpServerConfig`（jar 内已见）但**未接线**。建议 spec：`mcp-client-tools` |
| BL-02 | 工作流智能体的**图定义与可视化编排** | 2026-09-27 用户明确"本期不做，先放起来" | 待立项 | 图仍由 Java 硬编码（`ParolCompiledGraph`），DB 无图定义存储、前端无画布。建议 spec：`workflow-orchestration` |
| BL-03 | 存量 5 个 Java 自注册智能体的**迁移与删除**（现仅从列表隐藏） | 2026-09-27 用户决定"代码保留，暂作实现参考" | 待立项 | **前置已具备**：`dynamic-agent-types` 16/16 完成、数据驱动链路与存量行为等价已实测（存量 5 个各一轮对话不回归）。建议 spec：`legacy-agent-migration` |
| BL-04 | 智能体↔组关联改为「**智能体发布时关联组**」（与技能发布时授权组同构） | 2026-09-27 用户明示（`agent-skill-management` Non-goals） | 待立项 | 现状：组授权在组管理侧维护（`tbl_platform_group_agent_info`）；目标是发布动作里多选组、发布后即时生效 |
| BL-05 | **统一账号中心**：消除 `tbl_privilege_user` / `tbl_platform_account_info` 双账号体系 | 2026-09-27 排障中发现（根因登记 `BUG-04`） | 待立项 | 两表状态语义相反（1=禁用 vs 0=禁用）、密码互不相通、同名账号两条记录。最低成本方案=单表+角色字段；建议 spec：`unified-account` |
| BL-06 | 清理**早期 demo 遗留表**：`tbl_tmp_orders/products/users`、`tbl_data_orders/products/users/order_items/categories`（应用代码零引用） | `BUG-01` 附带建议 | 待立项 | 这批表源自早期演示数据，占种子体积且易误导（本次曾被它们触发级联报错）。需带迁移+回滚脚本 |
| BL-07 | 关联表 `agent_id` **列类型统一为 bigint** | `BUG-29` 根因（代码侧已绕过） | 待立项 | `tbl_platform_group_agent_info.agent_id` 是 varchar，`tbl_data_agent_skill_info.agent_id` 是 bigint，口径不一致；涉及存量数据迁移与索引重建 |
| BL-08 | 深度分析 **schema 初始化异步化 + 进度查询** | `agent-config-ai-generate` Implement 发现 | 待立项 | 现状：首次调用**同步**初始化该数据源全部表（本库 55 张）→ 大概率顶穿 180s 工具超时（本次只初始化 2 张表规避）；应改为后台任务 + 状态接口 |
| BL-09 | **模型配置变更审计**（操作人/时间/旧新配置ID 落库） | `agent-config-ai-generate` plan 风险④ 的实现降级 | 待立项 | 现状只落 WARN 日志（含 configId/modelName），无操作人与持久审计；换 EMBEDDING 默认会静默改变检索语义，值得留痕 |
| BL-10 | **语音（AUDIO）能力接入或下线** | 本次模型「启用/默认」改造暴露 | 待确认 | 现状：AUDIO 是三种模型类型之一、可设默认，`AiModelRegistry.getTranscriptionModel()` 与 `DynamicModelFactory.createTranscriptionModel()` 均已实现，但**全仓无业务调用方** → 要么接 ASR 场景（如语音问数），要么在模型管理隐藏 AUDIO |
| BL-11 | 清理**死配置** `phoenix.agent.skillPath`（`PhoenixAgentProperties:12` 定义、全仓无引用） | `BUG-06` 遗留 | 待立项 | 技能资源路径实际由 `PostgresSkillRepository` + `SkillZipSanitizer` 决定；该属性留着会误导配置者 |

## 二、工程卫生（非功能，随手可做）
| # | 待办 | 来源 | 状态 | 备注 |
|---|---|---|---|---|
| BL-12 | `.gitignore` 补 `.mvn-home/`、`.pnpm-store/`、`diagrams/`、`scripts/` | 长期未跟踪目录 | 待立项 | 目前 `git status` 一直挂着 4 个未跟踪目录，噪音干扰提交审阅 |
| BL-13 | 前端 dev 端口固化（`.env.development` 的 `VITE_PORT=5777` 未生效） | `BUG-25` | 待立项 | `pnpm vite --mode development` 会起在 5173；建议 dev 脚本显式 `--port` 或改 vite config 读取方式 |
| BL-14 | `application-test.yml` 与 AGENTS.md 描述对齐 | `BUG-08` | 待确认 | 二选一：改文档（承认有资源文件）或把配置外置；另含 maven settings 路径指向他人机器的问题 |
| BL-15 | 清理工作区残留：`AGENTS.md.bak.*` 备份、`RulesHarnessAgent.java` 未提交实验改动 | 会话遗留 | 待立项 | 实验改动还原命令见 `bugs.md` §工作区遗留状态 |

## 三、流程与发版（spec 机制）
| # | 待办 | 来源 | 状态 | 备注 |
|---|---|---|---|---|
| BL-16 | **里程碑挂接（M0+M1）**：建 `releases/vX.Y.Z/`、把三个 spec 与已修复缺陷挂入、回填版本号 | skill 里程碑流程；项目原先无 `releases/` | **已交付(v1.2.0)** | 2026-09-27 完成：`releases/v1.2.0/MILESTONE.md`（3 spec + 18 缺陷）；版本号取 MINOR=v1.2.0（历史分支线已到 1.1.x，用户确认）。**M2 冻结前置未满足**：① `agent-config-ai-generate` 未合并；② P1（BUG-01/20）未达「已验证」 |
| BL-17 | **汇总升级件（M3）**：把 spec-1/2/3 的 `01~05` 升级件重排为 Flyway 风格 `V<版本>_<序号>__<描述>.sql` + rollback 配对，产出 `UPGRADE.md` / `RELEASE-NOTES.md` / `config/changes.md` | 同上 | 待立项 | 铁律：M3 未汇总完不得进 M4；升级件禁止在单需求完成时私自塞进 `releases/` |

## 四、在手未完成（指向 spec，不占 BL 编号）
- `specs/20260927_agent-config-ai-generate`：**13 个任务已勾 8 个**，未勾 `T-05 / T-10 / T-11 / T-12 / T-13` —— 只差**界面人工走查**（浏览器扩展未连接，无法自动走查）；代码层证据已齐（`vue-tsc` 189、8 个改动文件 Vite 转译 200）。
  分支 `feature/agent-config-ai-generate`（未合并，合并条件是 tasks 全勾 + 证据齐）；走查清单见该 spec `changelog.md` 末尾「界面走查待办」。
- `bugs.md` 中 11 条「新建」缺陷待处置（`BUG-02/03/04/05/08/11/14/18/21/24/25`）；其中 `BUG-04` 的「不修复」**需用户批准**、`BUG-18` 的修复口径**需产品决策**。
- 里程碑 `v1.2.0` 已立项并挂接（M0+M1 完成，BL-16 已交付）；下一步触发词：「**冻结 v1.2.0**」（M2，会先列差距）或「**汇总 v1.2.0**」（M3，需先满足冻结前置）。

- **BL-18 前端 API 前缀治理**：**已交付(v1.3.0，原记 v1.2.2 已并入)**——baseURL 置空+路径归一+代理透传，nginx 折叠规则已删；BUG-33 转已修复。

- **BL-19 会话文件面板（生成文件可见+可下载）**：方案已成稿 `specs/20261001_agent-session-files/design-proposal.md`（v0.1.0 草案，含 T1~T10 与 Q1~Q4）；待用户回答开放问题后按 spec 流程立项，建议挂 v1.3.0。
