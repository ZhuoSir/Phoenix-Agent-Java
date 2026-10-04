# Requirements: MCP 客户端工具接入（mcp-client-tools）

> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-04
> 挂载: v1.6.0（2026-10-04，用户确认「挂 v1.6.0」；BL-01 立项）

## 背景与目标

**勘察事实（2026-10-04，代码为准）**：
- 平台已具 **MCP Server 端**：`phoenix-data` 的 `@McpServerTool` 注解 + `McpServerToolUtil` + spring `McpServerConfig`（对外暴露 nl2sql / agent 列表工具）
- AgentScope 框架已具**完整 MCP Client 栈**：core `McpClientBuilder` 四传输（stdio/SSE/StreamableHTTP/HTTP）+ Sync/Async wrapper + `McpTool`；harness `McpServerRegistrar.register(Toolkit, Map<String,McpServerConfig>)` 现成注册器（含 `enableTools` 工具过滤、连接/初始化双超时）；`HarnessAgent.getToolkit()` 公开可挂
- 即：**框架零改造，本需求是接线+配置管理+UI**

**目标**：对话智能体可按智能体维度配置 N 个外部 MCP Server，其工具挂入对话工具集供模型与内置工具同席调用；与平台 Server 端形成双端能力（并可回环消费自家端点）。

**成功画面**：管理员在智能体编辑页添加一个 MCP Server（如自家 nl2sql 端点或第三方天气服务）→ 连接测试显示工具清单 → 对话中模型自主调用该工具 → 结果进入回复；某 Server 宕机时对话照常、日志留 WARN。

## 需求条款（EARS）

### R-01 MCP Server 配置管理（智能体维度）
WHEN 管理员在智能体编辑页保存 MCP Server 配置（每条含：名称、传输类型、endpoint URL 或 command+args、鉴权 headers/env、enableTools 工具过滤、超时、启用开关）, 系统 SHALL 按智能体维度持久化，且新配置 SHALL 至迟于该智能体**下一轮对话**生效（无需重启服务）。

**验收场景**
- GIVEN 智能体 A 配置了 MCP-S1（启用）与 MCP-S2（停用）WHEN 发起新对话 THEN 仅 S1 工具可被模型看到/调用。
- GIVEN 修改 S1 配置后保存 WHEN 立即发起新对话 THEN 新配置生效（不需重启后端）。

### R-02 工具挂载与调用
WHEN 智能体发起对话且存在启用的 MCP Server, 系统 SHALL 将其工具挂载进本轮工具集（与内置工具同席、模型自主选用），工具呈现 SHALL 带来源标识（server 名）避免同名冲突；WHEN 模型调用 MCP 工具, 系统 SHALL 将结果回注推理循环并在对话过程展示中可辨识（工具名含来源）。

**验收场景**
- GIVEN 智能体挂载平台自家 MCP 端点（nl2sql 工具）WHEN 用户问数据问题 THEN 模型可调用该 MCP 工具完成回答（回环 dogfood 用例）。
- GIVEN 两个 Server 各有一个同名工具 WHEN 挂载 THEN 两者以来源前缀区分共存，模型可分别调用。

### R-03 故障隔离
IF 某 MCP Server 连接失败/初始化超时/调用异常, THEN 系统 SHALL 隔离该故障（跳过该 Server 或该次调用返回可读错误给模型），主对话循环与其他 Server SHALL 不受影响，并记 WARN 日志（含 server 名与原因）。

**验收场景**
- GIVEN 配置了一个不存在的 endpoint WHEN 发起对话 THEN 对话正常进行（其余工具可用），日志含该 Server 的 WARN。
- GIVEN 工具调用中途 Server 断开 WHEN 模型调用该工具 THEN 模型收到可读错误结果并可自行改道，轮次不崩。

### R-04 安全边界
WHERE 配置 stdio 类型（服务端拉起本地进程）, 系统 SHALL 仅允许管理员操作并在保存时明示风险提示与留痕（操作人/时间/命令）；鉴权 env/headers 中的敏感值 SHALL 脱敏存储与回显（沿用平台 api_key `sk-xxxxx` 先例），明文仅在连接时使用。

**验收场景**
- GIVEN 保存 stdio 配置含 env 密钥 WHEN 重新打开编辑页 THEN 密钥显示为脱敏形态；库表落盘不含明文（或加密，plan 定）。
- GIVEN 普通前台用户 THEN 无任何 MCP 配置入口（仅管理端）。

### R-05 连接测试
WHEN 管理员点击「测试连接」, 系统 SHALL 按当前表单配置即时试连目标 Server，并回显结果：成功=协议版本+工具数+工具名列表；失败=可读原因（不可达/鉴权失败/超时）。

**验收场景**
- GIVEN 有效 SSE endpoint WHEN 测试 THEN 数秒内回显工具清单。
- GIVEN 错误鉴权头 WHEN 测试 THEN 回显鉴权失败而非笼统超时。

### R-06 平台 Server 端零变化（身份矩阵对面）
既有平台 MCP Server 端（@McpServerTool 暴露面）行为 SHALL 保持逐字节不变；本需求全部改动 SHALL 不触碰其注册/暴露/协议路径。

**验收场景**
- GIVEN 部署新版本 WHEN 外部 MCP Client 调用平台既有端点 THEN 工具列表与调用行为与旧版一致（plan 期以身份矩阵枚举断言）。

## Non-goals（范围外）
- MCP 市场/配置模板库/跨智能体共享配置（每智能体独立配置）
- OAuth2 等复杂鉴权流（v1 = headers/env 静态凭据）
- 平台 MCP Server 端功能扩展（R-06 只保不变）
- stdio 进程沙箱隔离（v1 = 管理员信任模型 + 留痕，容器内运行天然有一层隔离）
- 对话中途热加载工具（生效粒度 = 轮次）
- 前台用户自助配置 MCP

## 我正在做的假设
- **A-1** 生效时机 = 下一轮对话（agent 构建/取用时挂载）；不做运行中轮次热替换
- **A-2** 配置存储走智能体维度既有运行配置体系（具体表/列/JSON 结构 plan 定，允许 Flyway 件）
- **A-3** 四种传输全部开放（框架已支持）；stdio 在容器内执行 = 服务端进程，受 R-04 约束
- **A-4** 仅管理端（admin 智能体编辑页）可配置；前台对话只消费
- **A-5** `enableTools` 留空 = 挂载该 Server 全部工具
- **A-6** 工具来源标识格式（如 `serverName__toolName`）plan 定，以框架 McpTool 实际命名为准不硬造

**现在纠正，否则按此执行。**

## 待确认问题（阻塞项，确认①前请裁决）
- **Q1 UI 落点**：MCP 配置区**内嵌智能体编辑页**（推荐——与运行配置/技能同页，按智能体隔离语义直观）还是独立「MCP 管理」菜单页（可跨智能体复用配置，但引入共享语义）？
- **Q2 stdio 开放度**：v1 全开（管理员+留痕+风险提示，推荐——框架现成、内网场景常用）/ 白名单命令 / v1 仅远程（SSE+HTTP）stdio 后置？
- **Q3 对话端工具展示**：MCP 工具调用在思考/工具轨迹中按现有工具调用样式展示即可（推荐，零前端新概念），还是需要专门的「外部工具」视觉标识？
