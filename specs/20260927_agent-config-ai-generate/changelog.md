# Changelog: agent-config-ai-generate

## v1.0.0（2026-09-27）确认人: 陈卓
- 三重确认第①关通过：requirements.md（R-01~R-21 + AC-01~13 + Non-goals + 假设 A-01~A-10）
- **Q4 决议 = B**：自研轻量 md 编辑器（textarea + 复用项目已有 `markdown-it` 实时预览 + 少量工具栏按钮），**不引入新依赖**；requirements 正文未改（与 A 分支未选、与「不回则按 B」一致），故不触发重确认
- 进入 Phase 2 Plan


## v0.3.0（2026-09-27）草稿期修订（新增 Prompt Markdown 编辑需求）
- 用户追加：「智能体 Prompt 支持 H5 的 md 编辑器方式编辑，可直接写 md 语法；生成出来的也要是 md 语法，包括常见的角色、描述、能力、安全范围等」
- 新增第二节条款 R-05~R-09（编辑器可写 md + 实时预览、生成产物为 md 且含**角色/描述/能力/安全范围**骨架、存储与下发仍为 md 原文不改形态、空值时可一键插入骨架、**描述字段不做 md**）；连带新增 R-21（存量纯文本 prompt 正常加载不强改）与 AC-02/03/05/13
- 既有条款重编号（草稿期允许）：生成能力 R-01~R-04 不变；模型管理→R-10~R-14；运行时取模型→R-15~R-18；兼容升级→R-19~R-21
- 假设调整：A-04 记录"md 化已确认"；A-06 生成超时由 30s 放宽到 **60s**（md 提示词更长）并给出提示词 300~600 字目标；A-09 依据实测：`HarnessAgentFactory.sysPrompt()` 直接取 `agent.getPrompt()` 原文，显式技能注入是**前置追加**不改写原文，故"md 原文即系统提示词"成立
- **实测事实**：`web-frontend` 仅有 `markdown-it` / `marked`（渲染侧），**无任何 md 编辑器组件**（无 codemirror/monaco/md-editor-v3/vditor/quill/tiptap）→ 编辑器需"引入依赖"或"自研轻编辑器复用 markdown-it"，列为 Q4（非阻塞，默认按 B 自研轻量执行）
- Non-goals 补充：不做所见即所得富文本、图片插入、md→PDF/Word 导出、版本对比；骨架段落集合固定不做管理界面
- 范围未变的部分（模型启用/默认、存量回填、回滚）沿用 v0.2.0 结论
## v0.2.0（2026-09-27）草稿期修订
- 依用户答复重写需求，范围从「新增默认标记字段」扩为「**启用=可多选集合 / 默认=未选时加载项**」两个独立概念：
  - Q1「启用和默认独立；启用可多个，用户在启用集合中选择，未选则加载默认」→ 新增 R-05（取消同类型唯一启用的互斥）、R-10/R-11/R-12（取模型规则改判默认）、R-13（无默认可用时的引导）
  - Q2「三种类型都要默认标记」→ R-09（CHAT/EMBEDDING/AUDIO 各自独立默认）
  - Q3「覆盖确认」→ R-04
- 新增 R-14（存量按"当前启用项"回填该类型默认）/R-15（回滚只删新列，不动启用与数据）
- **明确风险来源**：现有 `ModelConfigDataServiceImpl.switchActiveStatus` 会 `deactivateOthers(...)`，且 `AiModelRegistry`/`HarnessModelRegistry` 按"该类型唯一启用"取模型——R-05/R-12 落地时这些取模型路径必须改判默认，否则多启用会出现取到哪条不确定的问题（留给 plan 决策）

## v0.1.0（2026-09-27）
- 初始化创建（requirements.md 草稿全文；plan.md / tasks.md 占位，待 Phase 2/3）
- 需求来源：用户口述——智能体配置中支持大模型按名称生成「描述 + 提示词」；生成使用「默认的对话模型」，故模型管理需新增「默认模型」字段
- 立论依据（实测）：编辑抽屉基本信息区同屏含 名称/描述/Prompt（`agent-create-drawer.vue` 783/811/824 行）；`tbl_data_model_config` 现仅有 `is_active`（CHAT 与 EMBEDDING 各自单启用，`switchActiveStatus`），**无默认标记字段**；`LlmService` 已有 `callUser/callSystem` 可复用；项目已有同类范式（`AgentRuntimeConfigService` 配置驱动 + 42xxx 错误码段）
- 待用户回答 Q1（默认 vs 启用：新增字段 or 改名）/ Q2（默认是否覆盖 EMBEDDING 类型）/ Q3（覆盖策略：确认/撤销/仅填空）→ 已在 v0.2.0 全部回答并落条款
