> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-09

# 技术方案：ollama-model-support

> 挂载: v2.0.0（在途）｜对应 requirements **v1.0.0 已确认**（陈卓 2026-10-09）

## 0. 坑核对（置顶·确认②必审节）

已核对 `specs/_project/lessons.md` 全部 **L-01 ~ L-82（82 条）**，与本案相交 **10 条**，逐条说明避让：

| 坑 | 相交点 | 本方案如何避开 |
|---|---|---|
| L-09 curl 超时太小制造"链路不通"假象 | R-04 连接测试 / R-05 列表拉取 | 超时按语义给足：连接测试探针 ≥60s 上限、`/api/tags` 10s；**失败必须回传原始错误要点**，不允许把超时写成"离线/不可用"而无原因 |
| L-27 改身份源后须对迁移后状态回归 | 默认 embedding 模型可能被换 | 若切换默认 embedding（512→768/1024），回归对象是**既有知识库**而非新建数据：入库/检索双侧断言 |
| L-39 表驱动 + 双向测试才能发现旁路 | provider 分支 | 测试矩阵按「模型类型 × provider × 场景(测试/对话/工具/向量化)」表驱动；反向断言=既有 deepseek/qwen 路径行为不变 |
| L-40 手工执行 SQL 必须补登迁移台账 | 若涉向量表维度迁移 | 任何手工 DDL 一律补 `tbl_phoenix_release` 台账并配 rollback；本期若零 DDL 则在 artifacts 明确"零" |
| L-49 跨基线迁移件写 `ALTER TABLE IF EXISTS` | 向量表维度处置 | 迁移件一律 `IF EXISTS` 形式；不做无判据的破坏性变更 |
| L-51 升级件自检禁硬编码环境绝对数 | 交付自检 | 只校验结构不变量（列存在/维度与模型一致/守恒），不写死"必须 512" |
| L-63 类型判别列必须服务端写死，不信任前端 DTO | provider 字段 | `provider` 由服务端归一与校验（白名单常量），前端下拉仅是 UI；不因前端传入未知 provider 而静默走默认分支 |
| L-74 改配置类数据前先读选取代码、改后确认运行时缓存 | 模型配置变更→运行时生效 | 复用既有"发布变更"刷新链路（`publishChanged`）并在变更后确认缓存/实例已重建；新增 provider 走同一刷新路径 |
| L-76 空串是合法配置值（`\|\|` 会吞空串） | R-02 apiKey 可空 | Ollama 空 key **存空串**，判定用显式 `isBlank()`/`??` 语义，禁止 `\|\|` 兜默认把空串变回默认值 |
| L-58 列表可见性须与归属校验成对 | R-05 模型列表接口 | 新增的模型列表接口属管理域，鉴权与既有模型配置接口一致（不能只"看不见"而"能调用"） |

> 另：L-79（输出结束仍转圈）与本需求无相交，但验收 R-06 会复用其逐帧时间线手法。

## 1. 采用方案（总览）

**方案 A：双通道 provider 适配 + 模型类型上移至框架抽象。**

1. **依赖**：`phoenix-agent-core` 新增 `io.agentscope:agentscope-extensions-model-ollama:2.0.0`
   （已实勘：jar 在 `/Users/bryanchen/.m2/.../agentscope-extensions-model-ollama-2.0.0.jar`，**离线可编**，不新增外网依赖）。
2. **类型上移**（BL-29 备忘已勘明）：`HarnessModelRegistry` 内 `OpenAIChatModel` 具体类型 **8 处** →
   框架抽象（`io.agentscope.core.model.ChatModelBase` / `Model`）；`HarnessAgentFactory`、`memoryConfig(...)`
   等签名同步上移 —— 这是"能装下 Ollama 模型"的前置。
3. **provider 分派**：
   - `provider=ollama` → `OllamaChatModel.builder().modelName(...).baseUrl(...).formatter(new OllamaChatFormatter())`
     （多智能体场景用 `OllamaMultiAgentFormatter`；思考开关经 `ThinkOption`/`OllamaOptions`）
   - 其它 provider → 维持 `OpenAIChatModel` + 既有 formatter（`DeepSeekFormatter` 等），**行为零变化**
4. **API Key**：Ollama builder 无 apiKey 概念；`tbl_data_model_config.api_key` 为 **NOT NULL** ⇒ 服务端把空 key
   归一为**空串**存储（不改 DDL）；读取侧以"provider=ollama 时忽略 key"处理。
5. **模型列表（R-05）**：框架 `OllamaModelProvider` **只提供 create，不提供列模型** ⇒ 由**服务端**新增
   `GET /api/model-config/ollama/models?baseUrl=...`，服务端 HTTP 调 Ollama `/api/tags` 后返回模型名列表
   （服务端代理的原因：浏览器无法访问内网/宿主地址，且需统一鉴权与超时治理）。
6. **连接测试（R-04）**：`ModelConfigOpsService.testConnection` 增 `provider=ollama` 分支
   （CHAT/MULTIMODAL 复用轻量 chat 探针；EMBEDDING 走 embedding 探针），与既有 CHAT/EMBEDDING/MULTIMODAL 分支并列。
7. **向量维度（R-08/R-09）**：把 4 处硬编码 `.dimensions(512)`（`VectorConfig:32/49`、`HarnessConfig:109/127`，
   另有 `DataAgentConfiguration:313`）改为**取当前 embedding 模型的实际维度**（Spring AI `EmbeddingModel.dimensions()`）；
   入库/检索前比对目标表既有维度，不一致则**显式拒绝**并给出双维度与处置指引。
8. **前端（R-01/R-02/R-05）**：`providerBaseUrlMap` 增 `ollama`（默认 `http://host.docker.internal:11434`）；
   apiKey 校验对 `ollama` 放行；「模型名称」处增「获取模型列表」并渲染为可搜索下拉（失败退化为手工输入）。

## 2. 涉及模块与数据流

| 层 | 位置 | 变更要点 |
|---|---|---|
| 依赖 | `phoenix-agent/phoenix-agent-core/pom.xml` | 新增 ollama 扩展依赖（本机仓已有） |
| 运行时装配 | `.../harness/HarnessModelRegistry.java` | 类型上移 + provider 分派 + formatter 选择 |
| 运行时装配 | `.../harness/factory/HarnessAgentFactory.java` | 签名上移（model/memoryConfig 等） |
| 模型配置域 | `ModelConfigOpsService` / `DynamicModelFactory` | provider=ollama 分支；apiKey 归一；测试探针 |
| 模型配置接口 | `ModelConfigController` | 新增「Ollama 模型列表」端点；鉴权与既有接口一致 |
| 数据面 | `VectorConfig` / `HarnessConfig` / `DataAgentConfiguration` | 维度来源改为模型实际维度 + 不一致拒绝 |
| 前端 | `web-frontend/apps/admin-ui/src/views/modelconf/index.vue`（+ api 层） | provider 选项/默认地址/key 放行/模型列表下拉 |

**关键数据流**

1. 配置流：前端表单 → `ModelConfigController` → 服务端校验/归一（provider 白名单、apiKey 空串）→ `tbl_data_model_config`
2. 生效流：变更 → 既有发布变更链路 → `HarnessModelRegistry` 缓存重建 → 智能体下轮使用
3. 对话流：智能体 → 抽象 Model → `OllamaChatModel`（`/api/chat`，流式 + 工具）→ 事件流回前端
4. 知识流：上传 → 文本分块 → 当前 embedding 模型（可能为 Ollama，768/1024 维）→ 向量表（维度须一致）→ 检索

## 3. 共享面身份矩阵（触碰共享面，必备节）

**自检问句**「还有谁依赖这个路径/列/配置？」——以下四个对象均 >1 个身份，按身份逐一给出变更后预期行为。

### 对象 1：`tbl_data_model_config`（列语义 + provider 取值域）

| # | 既有身份 | 变更后预期行为 |
|---|---|---|
| 1 | 管理端列表查询 | 新增 `ollama` 行正常展示（provider 原样显示） |
| 2 | 管理端新增/编辑 | provider=ollama 时 apiKey 允许空（存空串）；baseUrl 必填 |
| 3 | 连接测试（三类分支） | 新增 ollama 分支；CHAT/EMBEDDING/MULTIMODAL 既有分支行为不变 |
| 4 | 启用/停用/设默认（同类型唯一 + 停用守卫） | 语义完全不变，Ollama 行同样受守卫 |
| 5 | 智能体运行配置绑定 `modelConfigId` | 绑到 ollama 行时按 provider 分派装配；绑到既有行行为不变 |
| 6 | 默认模型加载（CHAT/MULTIMODAL/EMBEDDING） | 若默认行被换成 ollama（无论同 provider 或跨 provider）→ 按新 provider 装配 |
| 7 | Embedding 默认（向量维度来源） | 换 embedding 默认行会改变维度 ⇒ 触发 R-09 校验与拒绝 |
| 8 | 交付/迁移脚本与种子数据 | 无新增列；种子不需变（L-53 无关） |

### 对象 2：向量表（pgvector，维度是表结构的一部分）

| # | 既有身份 | 变更后预期行为 |
|---|---|---|
| 1 | 知识入库写向量 | 维度须等于模型输出维度；不一致 → 拒绝并给处置（R-09） |
| 2 | 知识检索（`getRagInfo` / 相似度查询） | 同上；不一致 → 拒绝而非返回空结果 |
| 3 | 既有 512 维存量数据 | **本期不自动重建**（Non-goals）；给迁移指引，检索在维度不匹配时显式报错 |
| 4 | 交付升级件 | 若本期需新建/迁移向量表 → 出 `artifacts.md` + Flyway 风格件 + rollback；零 DDL 则明确标注 |

### 对象 3：前端模型配置表单（provider 下拉 / apiKey 校验 / 模型名）

| # | 既有身份 | 变更后预期行为 |
|---|---|---|
| 1 | 新增表单 | 出现 `ollama` 选项；选中预填默认地址；key 免填 |
| 2 | 编辑表单 | 回显既有 provider；选 `ollama` 时同样免 key；不代表历史数据被改写 |
| 3 | 列表页操作（启用/停用/测试） | 对 ollama 行同样可用，文案与既有 provider 一致 |
| 4 | 其它 provider 的新增/编辑 | 完全不变（含 apiKey 必填校验、baseUrl 预填） |

### 对象 4：`HarnessModelRegistry`（类型 + 缓存）

| # | 既有身份 | 变更后预期行为 |
|---|---|---|
| 1 | 默认 CHAT 模型获取 | 返回抽象类型；provider=openai/deepseek 行为不变 |
| 2 | 多模态默认获取 | 同上 |
| 3 | 按 `modelConfigId` 指定（智能体级） | 同上；ollama 行按 ollama 装配 |
| 4 | Embedding 获取 | 同类型；维度来源随之变化（见对象 2） |
| 5 | 变更发布/缓存刷新 | 新 provider 走同一刷新链路，无旁路缓存 |

## 4. 关键日志点清单（外部调用类，必备节）

| 场景 | 级别 | 触发点 | 必带字段 | traceId |
|---|---|---|---|---|
| 配置/模型装配（服务启停+配置加载类） | INFO | 装配完成时 | `provider`、`modelConfigId`、`modelName`、`endpoint`（**不含密钥**） | 是 |
| Ollama 对话调用 | DEBUG 出 / INFO 回 | `OllamaChatModel` 调用前后 | 目标端点、模型名、耗时 ms、结果码、是否流式 | 是 |
| Ollama 向量化调用 | DEBUG 出 / INFO 回 | embedding 调用前后 | 端点、模型名、**输出维度**、耗时、结果码 | 是 |
| Ollama 模型列表拉取 | INFO / 失败 WARN | 调 `/api/tags` 前后 | 端点、返回模型数、耗时；失败带原因要点 | 是 |
| 连接测试（三类） | INFO / 失败 WARN | 测试开始与结束 | 类型、provider、端点、耗时、结论 | 是 |
| 维度不一致拒绝 | WARN | 入库/检索前校验 | 期望维度、表实际维度、处置建议 | 是 |
| API Key 变更（安全事件类） | INFO | 新增/修改/清空 key | 主体、`modelConfigId`、动作（**不得落密钥明文**） | 是 |

## 5. 被拒绝的替代方案

| 方案 | 内容 | 拒绝理由 |
|---|---|---|
| B | **只走 OpenAI 兼容层**（baseUrl 指向 Ollama `/v1`），零代码改动 | 可行但被拒：① formatter 仍是 `DeepSeekFormatter`，会把思考/reasoning 语义套到不支持该语义的本地模型上（与 BUG-77 的 400 修复线冲突）② 拿不到 Ollama 原生能力（`ThinkOption`、媒体/多模态转换、工具格式差异）③ 无法满足 R-05 模型列表（与协议无关，需另做）④ 用户明确要求"tool calling + 三类都支持"。**保留为交付环境的应急兜底手段**（文档化，不进本期实现） |
| C | 自研 HTTP 客户端直连 Ollama `/api/chat`、`/api/embeddings` | 拒绝：重复造轮子；流式解析、工具调用、图片编码、错误映射全部自担，缺陷面大；框架已有经维护适配 |
| D | 引入 `spring-ai-ollama` 在 Spring AI 侧接入 | 拒绝：harness 侧模型是 AgentScope `Model` 抽象，Spring AI 模型不能直接喂给 `HarnessAgent.Builder.model(...)`；且会造成"两套模型接入体系"并存 |
| E | 本期仅支持 CHAT | 拒绝：用户明确"三类都支持"（CHAT/MULTIMODAL/EMBEDDING） |
| F | 存量 512 维向量**自动重建** | 拒绝：用户确认只做"拒绝 + 迁移指引"，避免与 BL-34（存量向量批量重建）重叠；自动重建属数据面破坏性操作，需独立 spec |
| G | 另立新 spec（脱离本 spec） | 拒绝：需求边界清晰（模型接入面），且与 v2.0.0 在途模型配置域同源，另立会重复评估共享面 |

## 6. 风险与规避

| 风险 | 影响 | 规避 |
|---|---|---|
| 本机 `gemma3:latest` **不支持工具调用** | R-07 正例验收无法在本机完成 | 待 Q3 授权 pull `qwen2.5:7b`；未授权时先验"可诊断提示"路径，正例留待模型就绪 |
| 无 embedding 模型 | R-08/R-09 无法验收 | 待 Q1 授权 pull `nomic-embed-text`(768) |
| Ollama 流式/工具报文格式差异 | 解析失败 → 空答 | 复用 `OllamaChatFormatter`/`OllamaResponseParser`；失败必须落日志并回显原因（R-07 禁静默失败） |
| 类型上移引入编译面扩散 | 影响面超预期 | 编译期即暴露；共享面矩阵逐身份回归（对象 4 五个身份） |
| 向量维度变更 + 存量数据 | 检索失效 | R-09 显式拒绝 + 迁移指引；不做静默降级 |
| `api_key` NOT NULL | 保存失败 | 空串归一（L-76：不得用 `\|\|` 兜默认） |
| `host.docker.internal` 仅 Docker Desktop 有效 | 生产/裸机不可达 | 文档写清两种写法（内网 IP 兜底）；R-01 允许改地址 |
| 离线交付漏带 ollama 扩展 jar | 离线环境编译/启动失败 | 依赖走本机仓；交付物清单同步（M3 汇总时核对） |

## 7. 收尾预告（本期升级件判断）

- **DDL**：本期**预期零 DDL**（仅存空串，无列变更）；若维度方案需新建向量表/加列，则出 `artifacts.md` + Flyway 件 + rollback。
- **配置**：如引入新配置项（如 Ollama 默认地址模板、`/api/tags` 超时），登记 config 变更行。
- **前置产品缺陷**：`agentscope` 依赖版本变化与离线包一致性需在 M3 核对。
