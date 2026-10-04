# 待办清单（Backlog）
> 编号 `BL-xx` **永久不复用**（同 R/T/BUG 规则；作废标 `[作废]` 保留）
> 状态：`待立项` → `已立项(vX.Y.Z)` → `已交付(vX.Y.Z)`；旁路 `搁置(原因)`、`待确认`（需用户先定口径）
> 边界：本表只放**非需求、非缺陷**的功能与工程待办 —— 缺陷走 `bugs.md`；在手任务走对应 spec 的 `tasks.md`/`completion.md`
> 立项时移入 `specs/{日期}_{功能名}/`，本表条目**保留不删除**，状态列标注交付版本

## 一、功能待办
| # | 待办 | 来源 | 状态 | 备注（现状 / 建议 spec） |
|---|---|---|---|---|
| BL-01 | **MCP 支持**：对话智能体接入 MCP 工具（作为 MCP Client 消费外部 MCP Server），与现有「平台作为 MCP Server 对外暴露 nl2sql/agent 列表」形成双端能力 | 2026-09-27 用户列入待办 | **已立项(v1.6.0)** | 平台侧已有 `McpServerService`（暴露 `nl2SqlToolCallback`/`listAgentsToolCallback`）与 `McpServerConfig`；AgentScope Harness 侧具备 `McpServerRegistrar`/`McpServerConfig`（jar 内已见）但**未接线**。建议 spec：`mcp-client-tools` |
| BL-02 | 工作流智能体的**图定义与可视化编排** | 2026-09-27 用户明确"本期不做，先放起来" | 待立项 | 图仍由 Java 硬编码（`ParolCompiledGraph`），DB 无图定义存储、前端无画布。建议 spec：`workflow-orchestration` |
| BL-03 | 存量 5 个 Java 自注册智能体的**迁移与删除**（现仅从列表隐藏） | 2026-09-27 用户决定"代码保留，暂作实现参考" | 待立项 | **前置已具备**：`dynamic-agent-types` 16/16 完成、数据驱动链路与存量行为等价已实测（存量 5 个各一轮对话不回归）。建议 spec：`legacy-agent-migration` |
| BL-04 | 智能体↔组关联改为「**智能体发布时关联组**」（与技能发布时授权组同构） | 2026-09-27 用户明示（`agent-skill-management` Non-goals） | **已立项(v1.6.0)** | 现状：组授权在组管理侧维护（`tbl_platform_group_agent_info`）；目标是发布动作里多选组、发布后即时生效 |
|  BL-05  |  **统一账号中心**：消除 `tbl_privilege_user` / `tbl_platform_account_info` 双账号体系  |  2026-09-27 排障中发现（根因登记 `BUG-04`）  |  待立项  | 两表状态语义相反（1=禁用 vs 0=禁用）、密码互不相通、同名账号两条记录。最低成本方案=单表+角色字段；建议 spec：`unified-account`\|；承接 BUG-45 遗留：预设问题 add/delete 接口角色限制随体系统一一并做 |
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
| BL-17 | **汇总升级件（M3）**：把 spec-1/2/3 的 `01~05` 升级件重排为 Flyway 风格 `V<版本>_<序号>__<描述>.sql` + rollback 配对，产出 `UPGRADE.md` / `RELEASE-NOTES.md` / `config/changes.md` | 同上 | **已交付(v1.2.0)** | 2026-10-04 账面同步：M3 汇总自 v1.2.0 起成惯例，v1.3.0/v1.4.0/v1.5.0 三轮均执行（Flyway 重排/rollback 配对/四件套） |

## 四、在手未完成（指向 spec，不占 BL 编号）
- `specs/20260927_agent-config-ai-generate`：**〔已随 v1.2.0 发布，历史快照〕** 13 个任务已勾 8 个，未勾 `T-05 / T-10 / T-11 / T-12 / T-13` —— 只差**界面人工走查**（浏览器扩展未连接，无法自动走查）；代码层证据已齐（`vue-tsc` 189、8 个改动文件 Vite 转译 200）。
  分支 `feature/agent-config-ai-generate`（未合并，合并条件是 tasks 全勾 + 证据齐）；走查清单见该 spec `changelog.md` 末尾「界面走查待办」。
- ~~bugs.md 中 11 条「新建」缺陷待处置~~ **〔2026-10-04 注：已全部处置——fix-bug-batch 清仓（9 修复+BUG-04 批准不修复），随 v1.4.0 发布〕**
- 里程碑〔2026-10-04 注：**v1.2.0/v1.2.1/v1.3.0/v1.4.0/v1.5.0 全部已发布**；在途=无，下一需求起 v1.6.0〕

- **BL-18 前端 API 前缀治理**：**已交付(v1.3.0，原记 v1.2.2 已并入)**——baseURL 置空+路径归一+代理透传，nginx 折叠规则已删；BUG-33 转已修复。

- **BL-19 会话文件面板**：**已交付(v1.3.0)**——specs/20261001_agent-session-files 全链落地；v1.4.0 经 BUG-60 增强（复用文件跨会话可见）。

## 五、待办增补（2026-10-01 刷新）

| BL-20 | **多子智能体编排模式**：智能体编辑页新增「运行模式」选择——**单智能体**（现状：一个主智能体独立完成，默认）/**多智能体**（主智能体拆解任务→派生多个子智能体并行执行子任务→**页面上可实时查看各子智能体的子任务详情**→最终由主智能体汇总产出统一回复；子任务流需 SSE 透出子 agent 身份与状态）。实现落点预判：AgentScope harness 原生具备 subagent 原语（`HarnessAgent$Builder.buildSubagentEntries`、`middleware/SubagentEntry`、`IsolationScope`），核心工作=运行配置加 mode 字段（Flyway 件）+ 编辑页模式选择 UI + 子任务详情面板 + SSE 事件扩展（复用 BL-19 agentFiles 帧先例的通道） | 2026-10-01 用户口述立项（明确：**列入待办，暂不实施**；将来立项时走 spec 四阶段，建议挂下一 MINOR 或 v1.3.0 若冻结前挤入需明示） | 待立项 | 关联既有：BL-19 会话文件面板（子智能体产物同样进面板）；BL-05 账号体系（子任务归属人展示）；开放问题预置：子智能体可否独立配置模型/知识库？并发上限？失败子任务的重试语义？ |
| BL-21 | **对话页「深度思考」与「回答正文」分离展示**：智能体对话页当前把模型的思考增量（THINKING_BLOCK_DELTA）与最终回答（TEXT_BLOCK_DELTA）**混在同一条 content 流**里呈现（HarnessChatServiceImpl.toNodeOutput 两者同构造 StreamingOutput、mapper 同键透出）——用户实测多次看到英文思考独白混入答案（如「The user asks me to...」）。目标：思考内容经独立事件通道（如 eventMap.thinking）下发，前端渲染为**可折叠的「深度思考」区**（灰底/字号弱、默认收起或流式中展开结束后折叠），回答正文区只含最终输出；历史消息回显需持久区分（chat_message 增 thinking 列或 metadata 标记，M3 前定稿）；前台/后台两套聊天页同规范 | 2026-10-01 用户口述立项 → 同日 spec 实施完成 | 已交付(v1.3.0，已发版) | 关联：BL-19 agentFiles 帧先例（新增独立键向后兼容旧客户端）；BUG 面：现思考泄漏混排属既有行为，本单以功能形态收编不再单列缺陷 |
| BL-22 | **断线续传/流恢复（BUG-53 的 B 方案）**：SSE 执行与连接解耦——harness 流经 Sinks.replay 中继 + Redis Streams 轮次缓冲（db8 现成，XADD/XREAD 带 offset 重放+追live+TTL 清孤儿），刷新/断网后重进会话 rejoin 继续观看至完成落库；配套改造：助手消息落库所有权由前端 onComplete **移交服务端按 turnId upsert**（幂等）、新增显式「停止生成」取消接口（断连≠停止）、前端进会话先查服务端状态再决定是否用 A′ 本地快照回显（防双半截）。可行性已评：Reactor 取消传染有标准解法、HITL pending_confirm 天然兼容、帧协议零改动；预估 2-3 天，A′ 原型（stash: BUG-53 A′）可作灰度兜底一并取用 | 2026-10-02 用户拍板「列入待办」→ 同日升级为 v1.4.0 首需求立项 | **已交付(v1.4.0)** | 关联: BUG-53 / BL-20（多子智能体的实时子任务详情与本缓冲基建同源，宜同期或先行设计对齐） |
| BL-23 | package.sh mirror 竞速探活升级为吞吐型（测小 blob 下载速率而非 HEAD 响应） | 2026-10-04 演练四实证：1ms.run HEAD 快但传输 40KB/s 僵死，竞速选中慢源 | 待立项 | P2；临时方案=--mirror 手工指定 |
| BL-24 | install.ps1/bootstrap.ps1 共享 WSL 就绪段抽公共 ps1 库 | plan §1.6 被拒案留档（v1 薄复制+注释互指），两处改动需人工同步 | 待立项 | P3；下次改 ps1 时顺手做 |
| BL-25 | **API 插件**：插件市场「插件管理」下第二类插件——把 HTTP API 注册为智能体可调用工具（API 定义/鉴权/参数映射，组授权与绑定同 MCP 同构） | 2026-10-04 用户 BL-01 立项时明示「未来还会做 API 插件，列入待办」 | 待立项 | 依赖 mcp-client-tools（v1.6.0）落地的插件市场架构扩展位；立项时复用 R-02/R-03 组授权与绑定语义 |
