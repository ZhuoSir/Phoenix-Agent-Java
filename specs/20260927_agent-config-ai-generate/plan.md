> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-09-27 | 确认人: 陈卓 | 确认日期: 2026-09-27

# 技术方案：智能体配置 AI 生成 + Prompt Markdown 编辑 + 模型「启用集合 / 默认模型」

对应需求：`requirements.md` v1.0.0（R-01~R-21）。规范路由：api-design / database = global（已加载），code-frontend = none（跟随所在文件周边风格）。

## 一、总体方案

```
管理端（编辑抽屉 / 编辑页）
  ├─ MarkdownEditorField.vue（自研轻量：textarea + markdown-it 预览 + 工具栏）      ← R-05~R-09,R-21
  ├─ 「AI 生成」按钮 → POST /api/agent/generate-profile {name,targets,...}          ← R-01~R-04
  └─ 运行配置模型下拉：只列「该类型启用集合」，未选=默认                              ← R-15/R-16
模型管理页
  └─ 新增「默认」列 + 「设为默认」动作；启用改为多选（不再互斥）                      ← R-10~R-14
后端
  phoenix-agent-rest   AgentProfileGenerationController（新）
  phoenix-agent-core   AgentProfileGenerationService（新）→ AiModelRegistry.getChatClient()（默认 CHAT）
  phoenix-data-core    ModelConfigDataService.getDefaultByType(type)（新）
                       ModelConfigOpsService：setDefaultConfig / activateConfig(不互斥) / deactivateConfig(默认保护)
                       AiModelRegistry / HarnessModelRegistry：取模型由「该类型唯一启用」改判「该类型默认」
  tbl_data_model_config  新增 is_default 列 + 部分唯一索引（每类型至多一默认）+ 存量回填   ← R-19/R-20
```

关键约束：**存储与下发不变形态**（R-07）——`prompt` 列仍存 md 原文，运行时 `HarnessAgentFactory.sysPrompt(agent.getPrompt())` 原样作为系统提示词，不做转换；渲染只发生在管理端编辑/预览。

## 二、数据模型（升级件 05）

```sql
ALTER TABLE tbl_data_model_config
    ADD COLUMN IF NOT EXISTS is_default boolean NOT NULL DEFAULT false;
COMMENT ON COLUMN tbl_data_model_config.is_default IS '该类型的默认模型 0否 1是（每类型至多一条，未显式选择时加载）';

-- 业务防重的最终防线：部分唯一索引（PG 语法，仅约束"是默认且未删"的行）
CREATE UNIQUE INDEX IF NOT EXISTS uk_dmc_type_default
    ON tbl_data_model_config (model_type) WHERE is_default = true AND is_deleted = 0;

-- 存量回填：每类型「当前唯一启用」→ 默认；无启用则取最新一条；都没有则不回填
UPDATE tbl_data_model_config t SET is_default = true
 WHERE t.is_deleted = 0 AND t.is_active = true
   AND NOT EXISTS (SELECT 1 FROM tbl_data_model_config d
                    WHERE d.model_type = t.model_type AND d.is_default = true AND d.is_deleted = 0);
```
回填后若某类型出现多条默认（历史上"多条同时启用"的存量库），由 `uk_dmc_type_default` 建立失败暴露，脚本先按「最新启用条」收敛再建索引（实现时按顺序：去重 → 建索引），并在升级说明里写明。回滚件 `05_*_rollback.sql`：drop 索引 + drop 列（不动 `is_active`）。

**不做**：不把 `is_active` 改名、不动 `is_deleted`、不改主键与既有列类型（先加后切，R-20）。

## 三、接口契约

| 端点 | 方法 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| `/api/agent/generate-profile` | POST | `{name, description?, prompt?, targets:["DESCRIPTION","PROMPT"]}` | `{description?, prompt?, modelConfigId?, modelName?}` | 生成（R-01/R-03）。`name` 必填 ≤64；`targets` 非空且不重复；未选中的字段不出现在响应 |
| `/api/model-config/activate/{id}` | POST | 路径 id | `{message}` | **语义变更**：只启用本条，不再互斥（R-10） |
| `/api/model-config/deactivate/{id}` | POST | 路径 id | `{message}` | 新增：停用本条；若它是该类型默认 → 拒绝并提示（R-13） |
| `/api/model-config/default/{id}` | POST | 路径 id | `{message}` | 新增：设为该类型默认；同类型旧默认自动取消；本条自动置为启用（R-11/R-12） |
| `/api/model-config/list` | GET | — | DTO 增加 `isDefault` | 前端展示默认标记 |

错误码（新增枚举 `ProfileErrorCodeEnm`，沿用本模块 42xxx 段）：
`42010 名称不能为空`｜`42011 未设置默认对话模型，请到模型管理设置`｜`42012 模型调用失败或超时`｜`42013 生成结果解析失败，请重试`｜`42014 该模型是当前类型默认，请先改默认再停用`｜`42015 仅 CHAT 类型可设为该类型默认`（本期三类型都可，故此码保留给未来约束；实际按类型枚举校验）。
信封沿用各模块现状：agent 域 `ReturnVo`、data 域 `ApiResponse`（不新增第二套）。**响应不含 api_key**，日志也不打印 key（排查用 traceId + 配置ID）。

## 四、生成实现（meta-prompt 与解析）

- 取模型：`ModelConfigDataService.getDefaultByType(CHAT)` → 无默认即 `42011` 返回（R-18，不写半成品）；有默认则 `AiModelRegistry.getChatClient()`（改造后该缓存以默认为准，变更默认时 refresh）。
- **一次调用出两项**：`targets` 含两项时，system 话术要求严格 JSON 输出 `{"description":"...","prompt":"..."}`；单项时只要该字段文本。解析用现有 `JsonUtil/JsonParseUtil`，剥 ```json 围栏；解析失败 → `42013`（原文进日志，不进响应）。
- 超时：`block(Duration.ofSeconds(60))`（A-06），异常统一转 `42012`。
- 内置话术常量类 `AgentProfilePromptTemplates`（不散落字符串），产出硬约束：中文；描述 ≤120 字单段纯文本；提示词为 Markdown，**必须含 `## 角色`、`## 描述`、`## 能力`、`## 安全范围` 四个二级标题且各有实际内容**，可按名称追加 `## 输出要求` / `## 工具使用` 等；禁止 TODO、占位符、空段落；长度 300~600 字。
- 骨架模板（R-08）前后端共用同一份四段结构，由后端常量出（`GET /api/agent/generate-profile/skeleton`）——避免前端硬编码一份、后端另一份漂移（R-06 的段落集合唯一来源）。

## 五、前端

1. **`MarkdownEditorField.vue`（自研轻量，零新依赖）**
   - `textarea`（等宽字体、Tab=两空格、不劫持 Ctrl+S）+ `v-model` 纯文本进出
   - 工具栏：插入骨架 / 标题 / 加粗 / 斜体 / 无序·有序列表 / 行内代码 / 代码块 / 链接（都是"在光标处插入文本"，不引入编辑器框架）
   - 预览：复用 `markdown-it`（**`html: false`**，防 XSS），模式：编辑 / 分栏 / 预览
   - 字数与"含四段骨架"的轻量校验提示（不阻断保存）
   - 落在 `apps/admin-ui/src/components/markdown-editor/`，抽屉「基本信息」提示词框与 `/agent/:id` 编辑页同一处复用（R-05/R-09）；**描述字段仍用普通 textarea**
2. **AI 生成入口**：描述/提示词旁各一个「AI 生成」+ 一个「同时生成两项」；目标字段非空时成功后 `ElMessageBox.confirm` 再覆盖（R-04）；覆盖前快照入内存，提供「撤销」（R-02）；三项独立 loading（R-03）；失败仅提示，不清空（R-07/R-18）。
3. **模型管理页**：列表加「默认」标记列（`el-tag` 默认/—）与「设为默认」动作；「启用」文案改为不互斥（原确认文案"其他将被停用"要删）；停用默认项时展示后端 `42014` 提示（R-13）。
4. **运行配置面板（上一需求产物）模型下拉**：选项 = `list.filter(modelType==='CHAT' && isActive)`；空值占位显示「默认（模型名）」（R-15/R-16）。
5. 前端类型检查作为兜底：`npx vue-tsc --noEmit`。

## 六、决策与被拒方案

1. **md 编辑器自研轻量（Q4=B）**。拒绝 `md-editor-v3`/`vditor`：需求实质是"能写 md + 预览 + 产物是 md"，项目已有 `markdown-it`，引第三方=新增依赖+包体积+主题冲突。拒绝 tiptap/quill 类富文本：产物不是纯 md 源码，与 R-06/R-07（md 原文存储并直接下发）冲突。
2. **新增 `is_default` 列，不改 `is_active` 语义名**。拒绝"把启用改名成默认"：用户明确要求两者独立（启用=可选集合，可多个），且多启用下 `is_active` 无法表达"唯一默认"。
3. **每类型至多一默认由**「部分唯一索引 + 事务内先清后设」双保险。拒绝"仅应用层查再写"：并发设默认会穿（数据库规范第 10 条）。
4. **运行时取模型改判默认，并对"默认缺失"回落任一启用 + WARN**。拒绝"无默认即抛错中断对话"：对话是既有能力，存量库若未跑回填会让所有智能体瞬间不可用（R-18 的严格拒绝只用于管理动作「AI 生成」与依赖类型的显式操作）。
5. **生成用 `AiModelRegistry.getChatClient()`（改造后取默认）**。拒绝 `LlmServiceFactory`/`BlockLlmService`：它是 NL2SQL 图节点的取数装配（Mono/Flux 语义按节点场景设计），生成用例是管理端一次性调用，直连注册中心更贴合且少一层。拒绝 `DynamicModelFactory` 自建一次性 client：会绕开统一缓存与 refresh 语义，换默认后出现新旧并存。
6. **两项一次 JSON 调用**。拒绝两次调用：慢、贵、且"部分成功"状态复杂（R-03 的单项生成由 `targets` 覆盖，不需要两次调用）。拒绝 SSE 流式输出到表单：表单是"整体替换"语义，流式只会让撤销/覆盖确认复杂化。
7. **骨架段落集合由后端常量单一来源出（含 skeleton 端点）**。拒绝前端硬编码模板：会与 meta-prompt 里的段落要求漂移，导致"手工有骨架、生成没骨架"或反之。
8. **换 EMBEDDING 默认只影响新发起的向量化，不自动重算历史**。拒绝"换默认即后台重算全量向量"：不可控成本与长事务（R-17 的括号声明即此）。

## 七、风险与规避

| # | 风险 | 规避 |
|---|---|---|
| ① | **既有缺陷 B-20**：`ModelConfigMapper.deactivateOthers` 注释写"设为非启用"，SQL 实为 `SET is_active = true` → 点"启用"会把同类型其他条也置为启用 | 启用多值化后**删除该调用**（不再互斥），并登记 B-20；同时新增 `clearDefaultByType` 供设默认时清理 |
| ② | 存量库可能已有多条同时启用（因 ①），回填会出现同类型多默认 → 唯一索引建不起来 | 脚本顺序：先按"最新启用条"收敛为一条默认，再建索引；执行前先 `SELECT` 验证（数据库规范第 23 条），并把结果写进升级说明 |
| ③ | 部署顺序错（先发代码后跑升级件）→ 查询不存在的 `is_default` 列直接报错 | 升级件是**前置条件**，写进 `artifacts.md`/UPGRADE 顺序；实现上 `getDefaultByType` 的 SQL 与新列同批上线，不做跨版本兼容读取 |
| ④ | 换 EMBEDDING 默认后，新旧向量来自不同模型（不同维度/不可比），检索质量静默劣化 | 设为 EMBEDDING 默认时前端二次确认"历史向量需重新初始化才与新模型一致"；后端日志记录切换（谁/何时/旧新配置ID） |
| ⑤ | 模型不按 JSON 协议输出（描述里带 json 代码围栏或整段说明） | 解析器先剥围栏、截第一个 `{...}` 平衡段；仍失败 → `42013` 明确提示，原文进日志（不泄漏到响应） |
| ⑥ | md 预览 XSS | `markdown-it({html:false})`，不注入用户 HTML；链接 `linkify:false` 且 `href` 仅允许 http/https/mailto |
| ⑦ | 生成耗时把抽屉"卡住" | 独立 loading + 可取消（前端忽略迟到的响应），后端 60s 超时即返回 `42012`；不阻塞保存按钮 |
| ⑧ | 智能体已保存后再次生成覆盖，丢失线上生效提示词 | 覆盖必须确认（R-04），且提示"保存后立即生效于新会话" |

## 八、影响文件清单（实现期）

- 后端：`phoenix-data-api`（`ModelConfig`/`ModelConfigDTO` 加 `isDefault`、新增错误码枚举或复用）、`phoenix-data-core`（`ModelConfigMapper`、`ModelConfigDataService(+Impl)`、`ModelConfigOpsService`、`AiModelRegistry`）、`phoenix-data-rest`（`ModelConfigController` 新端点）、`phoenix-agent-api`（生成 DTO/VO + 错误码枚举）、`phoenix-agent-core`（`AgentProfileGenerationService` + `AgentProfilePromptTemplates` + `HarnessModelRegistry` 取默认）、`phoenix-agent-rest`（生成 Controller）
- 前端：`components/markdown-editor/`（新）、`views/agent/list/agent-create-drawer.vue`、`views/agent/edit/index.vue`、`views/agent/list/components/AgentRuntimeConfig.vue`、`views/modelconf/index.vue`、`api/core/modelConfig.ts`、`api/core/agent.ts`（或新增 `api/core/generate.ts`）
- SQL：`specs/20260927_agent-config-ai-generate/sql/05_model_default.sql` + `05_model_default_rollback.sql`
- 明确不改：`AgentRuntimeConfigService` 的字段校验语义（只换下拉选项来源）、技能绑定/可见性链路、图节点与 NL2SQL 流程、`is_active` 列本身
