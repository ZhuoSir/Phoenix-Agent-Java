> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-09-27

# 需求规格：智能体类型可选与 Harness 统一化（dynamic-agent-types）

## 背景与目标
现状（2026-09-27 实测，bugs.md B-10 / profile）：
- 库中 7 个智能体里 **5 个由 Java 类启动自注册**并在运行时按 **sn** 取内存实例：`BpmReactAgent`、`ZhiduReactAgent`（agent）、`ParolCompiledGraph`（workflow）、`RulesHarnessAgent`、`HumanInTheLoop`（harness）
- 后台「新建智能体」不传 `type` → 落库 NULL、**无任何运行时**；`sql` 按 agentId 走 NL2SQL 状态图（唯一后台可建可用者）
- **Harness 是目前唯一支持技能（Skill）的运行时**；`workflow` 无编排能力（图硬编码、无图定义存储、无画布）
- Harness 现有工具能力零散：知识库检索工具（`RulesRagTool.getRagInfo`）仅挂在硬编码的制度专家上；**数据库查询未接任何工具**（平台侧有 `Nl2SqlService` 与自带 MCP 工具 `nl2SqlToolCallback` 可复用）

**方向（2026-09-27 用户拍板）**：以 **Harness 统一承载**「对话 + 知识库 + 数据库查询」；`agent`/`sql` 存量保留、代码留作实现参考、本期不迁移不删除；`workflow` 本期不做。目标是让后台**能创建 harness 智能体并配置其工具/技能**，不再依赖 Java 自注册。

## 需求条款

### R-01 类型选择
WHEN 用户在后台新建或编辑智能体, 系统 SHALL 提供类型选择并明确标注 **Harness 为推荐类型**；可选项含 `harness` / `sql` / `agent`（`workflow` 本期标注"暂不可创建"）。
IF 用户未选择类型, THEN 系统 SHALL 以明确默认值落库并显示（默认 `harness`），SHALL NOT 出现 type 为空的智能体。

### R-02 新智能体不依赖 Java 自注册
新建的智能体 SHALL 完全由库配置驱动其在运行时的构建与寻址；SHALL NOT 依赖 `SmartInitializingSingleton` 自注册或按 sn 的静态加载器。
存量 5 个 Java 自注册智能体 SHALL **保留原样**（作为实现参考与兜底，本期不删除、不迁移），但其存在 SHALL NOT 成为新能力的必要前提。

### R-03 Harness 智能体数据驱动创建
WHEN 创建或编辑 `type=harness` 的智能体, 系统 SHALL 以库配置构建 HarnessAgent：系统提示词、模型（取自模型配置）、技能仓库、工具集、记忆、计划模式开关、文件系统策略。
WHEN 发起对话（后台运行页 / 前台对话）, 系统 SHALL 以 **agentId** 即时构建或取得该 agent 的 Harness 实例。

### R-04 Harness 工具：知识库
WHEN 创建或编辑 harness 智能体, 系统 SHALL 允许开启「知识库检索」能力并选择其作用范围（至少支持复用现有向量/知识检索工具）。
WHEN 对话中需要知识检索, 系统 SHALL 由该工具返回知识库内容（沿用现有 `getRagInfo` 语义）。

### R-05 Harness 工具：数据库查询（Q5 裁决：**两级都要**）
WHEN 创建或编辑 harness 智能体, 系统 SHALL 允许开启「数据库查询」能力并选择目标数据源，并提供两个独立工具开关：
- **取数工具（默认开）**：`自然语言 → SQL → 结果`，只读、限定所选数据源，用于简单问数
- **深度分析工具**：内部复用既有 NL2SQL 状态图能力（多步规划 / SQL 生成与执行 / Python 深度分析 / 报告生成 / 人工反馈），用于复杂分析
WHEN 智能体两种工具都未开启, 系统 SHALL 明确提示「该智能体不具备数据库查询能力」而不是静默无工具。
IF 目标数据源未配置或不可达, THEN 工具调用 SHALL 返回明确错误，SHALL NOT 静默失败。

### R-06 存量类型保持可用
`type=sql` 与 `type=agent` 的存量智能体 SHALL 保持现有行为可用（SHALL NOT 因本需求失效）；UI SHALL 对其标注「存量类型，建议迁移至 Harness」（不强制、不自动迁移）。

### R-07 工作流类型（本期不做）
`workflow` SHALL 在本期仅保留展示与存量行为；UI SHALL 标注「暂不支持创建/编排」。
工作流定义载体与可视化编排 SHALL 作为独立需求另行立项（见 Non-goals）。

### R-08 寻址统一
所有对话通道 SHALL 以 **agentId** 寻址智能体；`sn` 仅保留兼容与展示用途（移除 `agent.sn || agent.id` 之类的兜底写法）。
IF agentId 不存在或其类型无可用运行时, THEN 系统 SHALL 返回明确错误码与提示，SHALL NOT 泄漏 `NoSuchElementException` 等内部异常。

### R-09 前端类型化配置面板
后台编辑抽屉 SHALL 按类型展示对应面板：
- `harness`：系统提示词 / 模型 / 技能配置（复用既有 AgentSkillConfig）/ 知识库开关 / 数据库查询开关+数据源 / 计划模式
- `sql`、`agent`：沿用现有面板并显示「存量类型」标注
- `workflow`：仅展示说明（不可创建）

### R-10 权限与可见性
组授权可见性（`getMyAgents`）、后台菜单 ACL、前台技能区（R-09 of skills spec）语义 SHALL 保持与本需求前一致；本需求不引入新权限模型。

### R-11 兼容与回滚
本需求产生的库表/配置变更 SHALL 提供升级与回滚脚本并登记 artifacts.md（先加后删原则）；存量智能体的数据源、知识、技能绑定、会话 SHALL NOT 丢失。

## Non-goals（范围外）
- **工作流定义与可视化编排画布**（含节点拖拽编辑器）——独立立项
- 存量 Java 自注册类的删除或迁移（本期保留作参考）
- 工具集的自定义脚本/DSL 注册（本期仅内置工具开关）
- 多智能体协作/子智能体编排（AgentScope Subagent）
- 前台创建智能体（仍限后台）
- Python 深度分析链路的工具化（若 Q5 选"单次取数"则明确不覆盖）

## 我正在做的假设
1. 模型统一取自既有「模型配置」（modelconf），不在智能体上冗余存密钥。
2. Harness 的动态构建复用现有 `HarnessAgent.builder()` 能力面（sysPrompt/model/toolkit/skillRepository/filesystem/memory/PlanMode），去掉"必须由 Java @Component 提供"这一层。
3. 知识库工具直接复用 `RulesRagTool` 语义（可泛化为按 agent 配置的数据源/知识范围）。
4. 数据库查询工具以「数据源配置 + 只读约束」为前提；写操作不在本期。
5. 存量 5 个自注册智能体继续按原路径工作，不影响本期新增能力（双轨并存，但**新增一律走数据驱动**）。
6. 前台对话继续走 `specs/20260927_agent-skill-management` 交付的 `/platform/harness/chat`（含技能显式执行与三重校验）。

现在纠正，否则按此执行。

## 已决问题（2026-09-27 用户拍板）
- **Q1 workflow**：本期不做，先放起来 → 见 R-07 + Non-goals
- **Q2 存量硬编码代码**：**保留**，暂作实现参考（不删除、不迁移）→ 见 R-02 / R-06
- **Q3 方向收敛**：`agent` 与 harness 功能重叠、harness 基本替代之；`sql` 亦可能无用 → 本期**以 harness 统一承载**（对话+知识库+数据库查询），存量类型保留不动
- **Q5 数据库查询层级**：**两级都要** → 取数工具（默认开）+ 深度分析工具（内部走 NL2SQL 状态图）→ 见 R-05
- 技能管理 spec `specs/20260927_agent-skill-management` v1.1.0 已合并 main（`ed2e3f9`），本需求 R-03/R-09 的复用前提已具备

## 待确认问题（阻塞）
- **Q6 新建智能体时类型下拉里还出现「数据智能体(sql)」「对话智能体(agent)」吗？**（这两类已被 harness 取代、但存量还在用）
  - **(A)（推荐）新建只给 Harness**：新建下拉只列 `harness`；`workflow` 灰显并标注"暂不可创建"；打开**存量**智能体编辑时仍显示其原类型（只读，标注「存量类型」）
  - **(B) 新建仍列四类**：`sql`/`agent` 保留在新建下拉中，标注「存量类型，建议使用 Harness」
