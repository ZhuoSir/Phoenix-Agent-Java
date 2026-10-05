# Plan: mcp-client-tools（插件市场 · MCP 统一管理）

> 版本: v1.1.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-04（v1.0.0 同日；v1.1.0 菜单层级修订重确认） | 更新: 2026-10-04
> v1.0.0→v1.1.0：用户指令去市场层——§1.1 菜单 DML 两件改单件顶级、§1.4 三层改两层；其余不变
> 规范路由（.specrc.yml）：api-design=global / database=global / code-backend=global / code-frontend=none（跟随周边风格）/ git-workflow=global（v1.6.0 版本分支制）

## 〇、坑核对（lessons.md 全 14 条 active 逐条过）

| L | 相交 | 规避 |
|---|---|---|
| L-01 补丁脱靶 | ✅ | 一切编辑带 assert 锚点；提交前验证声称内容在盘（60f21f2 教训刚发生） |
| L-03 部署三段证明 | ✅ | 每轮验证 inspect 镜像+标记串+功能探针三件套 |
| L-06 多入口枚举 | ✅✅ | **挂载链多入口**：admin 对话/前台对话/预览(preview)三链都要过交集判定——身份矩阵§三枚举；技能三件套的全部消费方（FrontSkillAccess/SkillAdmin/绑定 UI）作同构模板逐一对照 |
| L-07 假阳性验证 | ✅ | 交集语义断言双侧取证（DB 授权行 + 对话中工具可见性实测）；断言显式绑定目标会话/用户 |
| L-11 用户环境锚定 | ✅ | UI 验收明确 admin 页面 URL；前端复测带硬刷新 |
| L-14 表格闸 | ✅ | 本 plan 及后续全部 md 产物过 mdtable_check |
| L-02/L-04/L-05/L-08/L-09/L-10/L-12/L-13 | 弱相交 | 日志全量取证/磁盘预检/bash3.2 不适用(服务端 Java+Vue)/确定性轮询/curl -m≥10/不涉记忆/交付三查(本版无交付件变化)/git add 逐文件 |
| L-13 git add 面 | ✅ | 提交逐文件 add，禁整目录 |

**结论**：14 条全过，重相交 4 条（L-01/03/06/07）均有动作。

## 一、采用方案

### 1.1 数据模型（Flyway V1.6.0_01 DDL + V1.6.0_02 菜单/权限 DML，rollback 配对）
技能三件套同构三表（列形实施时以 tbl_harness_skills/tbl_platform_group_skill_info/tbl_data_agent_skill_info 真实 DDL 为准逐列对照）：

| 新表 | 同构模板 | 要点 |
|---|---|---|
| `tbl_mcp_server` | tbl_harness_skills | sn/name/transport(stdio·sse·streamable_http·http)/config jsonb(url·headers·command·args·env·enableTools·timeouts)/status(启停)/del_flag/审计列；敏感值(headers/env 中标记 secret 的)加密落库（复用 ModelConfigOpsService 的 api_key 加密与 sk-xxxxx 脱敏机制） |
| `tbl_platform_group_mcp_info` | tbl_platform_group_skill_info | group_id+mcp_id（注意 L：group_agent 的 agent_id 是 varchar 的历史坑——本表列型与模板逐一对齐，BUG-14 级联教训：删除时同步清理） |
| `tbl_data_agent_mcp_info` | tbl_data_agent_skill_info | agent_id+mcp_id 绑定 |

菜单 DML：「插件管理」顶级 +「MCP」页两层（镜像 V1.2.0_02 技能菜单种子写法，含 ACL；2026-10-04 用户裁决去市场层）。

### 1.2 后端（phoenix-agent 模块，与技能同模块同构）
- `McpAdminService(+Impl)`：CRUD/启停/组授权/绑定管理/删除防悬挂（被绑定则拒删并列出智能体，R-01）——镜像 `SkillAdminServiceImpl`
- `FrontMcpAccessService(+Impl)`：按用户组算可用 MCP 交集——镜像 `FrontSkillAccessServiceImpl`
- `McpTestService`：测试连接（按表单临时构建 client，列工具，不落库；超时上限 15s）
- REST：`McpAdminController`（/api/admin/mcp/**，镜像 SkillController 风格与鉴权）；前台**零新端点**
- **挂载接线（本 spec 技术核心）**：`HarnessAgentFactory` L141 `built` 之后不动；挂载点=对话轮次链（HarnessChatServiceImpl/确认续跑链）按「绑定∩组授权∩启用」构造 `Map<String,McpServerConfig>` → `McpServerRegistrar.register(agent.getToolkit(), map)`——**前提待 T-01 spike 定案**（见 §1.3）

### 1.3 T-01 技术 spike（首要任务，定案后才许写挂载代码）
**问题**：agent 实例被 `HarnessAgentRegistry` 缓存复用（跨用户共享），而挂载是 per-user 交集：
- spike 内容（反编译+仪表实测）：①Toolkit 注册是否幂等/可重复注册同名 server ②工具命名格式（来源标识 A-6 以实测定）③注册后能否按轮次卸载/替换 ④并发对话下 Toolkit 线程安全 ⑤`enableTools` 运行时语义
- **候选路线**（判据=spike 报告）：
  - **路线甲（优先尝试）**：共享实例+全量注册+调用面过滤——所有 MCP 一次注册，工具调用经我方过滤层按当轮用户交集放行/拒绝（若框架有 tool 调用拦截点）
  - **路线乙（兜底）**：per-轮次轻量实例——含 MCP 的 agent 按（agentId+用户组签名）维度缓存多实例（组数有限，缓存可控）；无 MCP 绑定的 agent 走现有单例零变化
- 产出：spike 报告入 changelog，选定路线+理由，若两路线皆不可行→回 requirements 层重议（铁律6）

### 1.4 前端（admin-ui，镜像技能管理页结构）
- 菜单路由两层：插件管理(顶级)/MCP（access.ts glob 自动收录组件；v1.1.0 用户裁决去市场层）
- MCP 列表页（搜索分页+状态+授权组数+绑定数）/详情页（只读配置+工具清单+授权组+绑定智能体）/编辑抽屉（表单+测试连接按钮+脱敏回显）
- 智能体编辑页：MCP 多选绑定块（镜像知识库/技能绑定组件同构）
- 对话端：零改动（Q3 决议=现有工具轨迹样式，来源标识随工具名带出）

## 二、涉及模块与数据流

```
admin UI(插件市场/绑定) → McpAdminController → McpAdminService → 三表
前台对话: FrontChat → HarnessChatServiceImpl → 交集计算(FrontMcpAccessService)
          → McpServerRegistrar.register(toolkit, map) → 模型调用 MCP 工具 → 结果回注
平台 MCP Server 端(@McpServerTool)：零触碰（R-07）
```
新增：phoenix-agent{api,core,rest} 各 1 组类 + admin-ui 1 组页面；修改：HarnessChatServiceImpl 挂载接线、智能体编辑页、AgentServiceImpl 删除级联（+mcp 绑定清理，BUG-14 同构）、菜单 DML。

## 三、共享面身份矩阵

| 共享对象 | 既有身份 | 变更后预期 |
|---|---|---|
| HarnessChatServiceImpl 对话链 | ①admin 对话 ②前台对话 ③confirm 续跑 ④join 追流 | 挂载只对①②③生效且交集为空=现行为零变化；④不涉工具 |
| HarnessAgentFactory/Registry | ①单例缓存构建 | 路线甲零变化；路线乙仅含 MCP 绑定的 agent 多实例化，无绑定者走原路 |
| tbl_privilege_module 菜单表 | ①既有全部菜单 | DML 纯新增行，既有菜单/ACL 零变化（回归断言） |
| 平台 MCP Server 端 | ①nl2sql/listAgents 暴露 | 逐字节不变（R-07 对面断言） |
| AgentServiceImpl.deleteById 级联 | ①五表级联（BUG-14 修复态） | +mcp 绑定表清理，原五表行为不变 |
| 智能体编辑页 | ①基础/运行配置/技能/知识库块 | +MCP 绑定块，既有块零变化 |

## 四、被拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| 智能体维度内嵌配置（无平台级注册） | 用户 Q1 裁决否决——无统一管理/无组授权/配置随智能体重复 |
| MCP 配置塞 runtime_config JSON | 组授权/绑定需可查询关系，JSON 内嵌无法 SQL  join，违背三件套同构 |
| 前台用户自助配置 | Non-goal（R-05 管理面仅 admin） |
| v1 做 API 插件 | 用户明示列待办（BL-25），本期只留架构位 |
| per-用户全新 agent 实例（无缓存） | 构建成本（sandbox/workspace）×并发不可控——降级为路线乙的「按组签名缓存」变体 |

## 五、风险与规避

| # | 风险 | 规避 |
|---|---|---|
| 1 | **Toolkit 共享实例挂载语义不明**（跨用户泄漏=安全事故） | T-01 spike 先行定案，两路线皆备；泄漏断言进测试矩阵（A 组用户对话后 B 组用户工具面不得含 A 专属 MCP） |
| 2 | 外部 MCP Server 生态参差（协议版本/慢/挂） | R-04 隔离+双超时+测试连接前置；挂载失败降级 WARN 不阻塞 |
| 3 | 敏感凭据泄漏面（落库/日志/回显） | 加密落库（复用既有机制）+脱敏回显+日志脱敏（spike 时验框架日志不打 headers/env） |
| 4 | 工具爆炸（多 Server×多工具撑爆模型上下文） | enableTools 过滤+绑定数软上限提示+列表页工具计数可见 |
| 5 | 菜单 DML 撞既有权限缓存 | 镜像 V1.2.0_02 写法（已验证过两版），升级后 JetCache 失效策略沿用既有 |

## 六、测试策略（真做）
- spike 报告（①~⑤逐项证据）
- curl 全链：CRUD/启停/组授权/绑定/删除防悬挂/测试连接（有效+坏鉴权+不可达三态）
- **回环 dogfood**：注册平台自家 MCP 端点→授权→绑定→前台对话调 nl2sql 工具出结果（R-03 验收场景实测）
- **跨用户泄漏断言**（风险1 对应）：G1/G2 两用户同智能体对话，工具面按交集隔离
- 故障隔离演练：坏 endpoint 对话照常+WARN 留痕
- 回归：既有技能链/菜单/admin 对话/front 对话四路不变；verify 13/13；前端 vue-tsc 基线
- 脱敏取证：落库密文+回显 sk 式+日志无明文三点核查

## 七、任务预告（Phase 3 细化）
T-01 框架 spike（Toolkit/Registrar 语义+路线定案）→ T-02 Flyway 三表+菜单 DML+rollback → T-03 admin 服务与端点（CRUD/测试连接/防悬挂）→ T-04 组授权+交集判定服务 → T-05 挂载接线（按 spike 定案路线+泄漏断言）→ T-06 admin 前端插件市场三层 → T-07 智能体编辑页绑定块+删除级联扩展 → T-08 回环 dogfood+隔离/泄漏演练+回归矩阵 → T-09 台账收尾。
