> 版本: v1.2.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-09 | 确认记录: 铁律6 回改后**重走确认②**，用户 2026-10-09 选定「确认通过（确认人：陈卓）」+ 接入范围裁定「两族都接」（harness 族 5 端点 + react 族 1 端点） | 变更源: 决策1 事实前提纠正（发送端点全是 POST，SSE GET 仅事件订阅）| 前次确认: v1.1.0（陈卓 2026-10-08）

# 技术方案：chat-attachment-understanding（对话附件上传与大模型理解）

> 对应 requirements **v1.0.0 已确认**（陈卓 2026-10-08）。本文件只写 how，不复述 what。

## 坑核对（必填，确认②请优先审这一节）

**已核对 `specs/_project/lessons.md` 全部 73 条 active 坑**。相交者逐条给出本方案的避开做法：

| 坑 | 相交点 | 本方案如何避开 |
|---|---|---|
| **L-65** WebFlux 下 Sa-Token 登录态只能在请求线程同步段取（BUG-140 原型） | 上传端点是 reactive multipart，**与踩过的坑同形** | 上传端点**不在 `Mono.fromCallable` 内取登录态**；用 `@RequestHeader("phoenix-token")` + `StpUtil.getLoginIdByToken(token)` **线程无关**反查（照抄 `SkillController.upload` 终修范式） |
| **L-64** `QueryChain.and` 只认 `?`，`{0}` 原样进 SQL 且错误被吞 | 新增附件查询条件 | 一律 `?` 占位；新增查询后用**非超管账号**实测非空路径（宽 catch 会吞语法错） |
| **L-58** 列表可见性必须与同族端点归属校验成对实施 | R-11 附件鉴权 | 附件**下载/预览端点**与列表**同批**实现归属校验（非本人且非超管 → 403），不允许只做列表过滤 |
| **L-26** 本项目业务失败也返回 HTTP 200 | 白名单拒绝、超限拒绝的验证 | 验证断言**业务码/业务字段**（`success=false` + msg），不以 HTTP 200 判"通过" |
| **L-06** 多入口枚举缺失——修一条链漏一条链 | 两端 + 共享包 | 入口清单写死在共享面矩阵：admin-ui 对话页、mobile-ui 对话页、`chat-shared`（类型/composable/mocks）、后端上传端点、后端 stream 端点 —— 每处一条断言 |
| **L-20** 视图态凡与"会话"绑定就必须按会话分片 | 待发送附件草稿态 | 附件草稿态**按 sessionId 分片**存储（切会话不串附件），不用单例 ref |
| **L-36** 外部服务"能力清单"接口不是权威 | 多模态模型可用性 | **不信 `/models` 清单**；Implement 首任务发一次**真实带 `image_url` 的请求**，拿到成功响应才算可用 |
| **L-11** 用户环境 ≠ 我以为的环境 | 模型名/密钥权限 | 模型名由用户裁定（CR-01 后为 `qwen3.8-max`）+ 探针实证；探针失败即停并回报，不改名硬试 |
| **L-19** 路径归属必须与写入方同源解析 | 附件存储路径 | 附件访问一律**以库中记录的 `storage_path`** 解析归属，不接受请求入参里的路径拼接 |
| **L-21** MyBatis-Flex 逻辑删列会让墓碑行隐形 | 新附件表若用 `del_flag` | 新表**不设 `del_flag`**，用 `status` 显式状态列 + 物理清理（决策 4） |
| **L-13** `git add` 面过宽吞大产物 | 测试用真实文件 | 测试附件走 `/tmp`，**不入库**；提交前 `git status` 核面 |
| **L-32** typecheck 必须看增量错误数 | 前端改动（两端 + 共享包） | 记录改动前基线错误数，改动后**只认增量=0**；错误数骤降视为可疑（先怀疑文件被改坏） |
| **L-49/L-51/L-53/L-55/L-66** 迁移件纪律（`IF EXISTS` 不护表不存在 / 自检禁硬编码环境绝对数 / 种子按迁移前 schema / 升级链可重执行 / 约束≠索引） | 本方案含 1 件 DDL | 新表 `CREATE TABLE IF NOT EXISTS`；自检只校**结构不变量**（不写环境相关绝对数）；正反件配对 drill（正向×2/反向/再正向） |
| **L-03/L-16/L-07/L-50/L-30** 部署三段证明 / 用户实测期禁静默部署 / 假阳性验证 / 断言匹配真实输出 / 管道吞退出码 | 验证与部署环节 | 部署后三段证明（build → up → 健康+接口实测）；断言用真实响应体字段；退出码不经管道判定；**编译 exit≠0 绝不部署**（BUG-140 二次教训） |
| **L-44/L-45** 能力命名≠业务形态 / 全称否定结论必须自己复核 | 命名与"零消费方"类结论 | 端点与类型命名以**调用方实测**为准；任何"没人用/死代码"结论先 grep 复核再写 |

其余 56 条（L-01/02/04/05/08/09/10/12/14/15/17/18/22~25/27~31/33~35/37~43/46~48/52/54/56~63/67~73）已逐条过，**与本方案无相交**（多为发布/迁移/PowerShell/前端弹窗/账号保护等已完成场景的纪律；其同源纪律已在上表覆盖）。

## 方案概述

**附件与消息解耦、上传先于发送**：新增独立上传端点（multipart）→ 落盘（复用 `FileStorageService`，子目录 `chat-attachments`）+ 落**新表** `tbl_data_chat_attachment`（归属/类型/大小/路径/状态）→ 前端拿到 `attachmentId` 挂到**该会话的待发送草稿**；发送时由**运行端点的 POST 请求体**携带 `attachmentIds`（两个 DTO：`HarnessRequest` / `ChatModelRequest`），在**两个收敛点**（`HarnessChatService` / `AgentManager.streamCall`）下游的同一装配器里解析、鉴权、注入。

**理解路径分两支**（对应 R-05/R-06）：
- **文档类** → 复用已在依赖中的 `spring-ai-tika-document-reader` 抽文本 → 受控模板注入提示词（含截断告知，R-08）→ 走既有 `CHAT` 模型。
- **图片类** → 装配 **OpenAI 兼容 multimodal 内容部件**（`type=image_url`，本地文件转 data URI）→ 走**新增 `MULTIMODAL` 模型**（**`qwen3.8-max`**，复用现有 qwen provider/base_url；api_key 经管理页配置实测可用的一把，CR-01）。
- **混合**（图 + 文档）→ 走 `MULTIMODAL`，文本部分同时含文档抽取内容。
- **无可用 MULTIMODAL 模型** → 按 R-07 **降级 + 用户可见告知**，标注写入消息 `metadata`，历史回看同样标注。

满足 R-01~R-05、R-07~R-12；R-06 由 MULTIMODAL 支路满足。

## 涉及模块与数据流

| 模块 | 改动 |
|---|---|
| `phoenix-data-api` | `ModelType` 枚举**新增 `MULTIMODAL`**（共享面 S2）；新增 `ChatAttachment` 实体 + DTO/VO |
| `phoenix-data-core` | 附件 service（存/查/鉴权/清理）、Tika 文本抽取、超长顺序截断、复用 `FileStorageService` |
| `phoenix-data-rest` | 新增 `POST /api/chat/attachment`、`GET /api/chat/attachment/{id}`（+可选 thumb）；`SessionEventController` 的 stream GET **增可选 `attachmentIds`** |
| `phoenix-agent-core` | 模型注册/选取增 `MULTIMODAL` 支路（既有 CHAT/EMBEDDING 消费点**语义不变**）；multimodal 消息装配 |
| `web-frontend/packages/chat-shared` | `ChatMessage`/草稿态类型加**可选** `attachments`；上传 API 封装；草稿态按会话分片 |
| `web-frontend/apps/admin-ui` | 对话页附件选择/展示/移除 + 失败提示（文案单一来源在 chat-shared） |
| `web-frontend/apps/mobile-ui` | vant 上传适配（相册/拍照/文件按平台能力），规则与提示语义与 admin 一致 |
| SQL | `V2.0.0_15`（**只**新表 + 2 索引）+ 配对 rollback；MULTIMODAL 模型行经管理页配置（CR-01） |

```
[前端] 选文件 → POST /api/chat/attachment (multipart, phoenix-token)
          ↓ 白名单双判定(R-01/02/03) + 大小数量(R-04) + 归属=token 反查(L-65)
[后端] FileStorageService.storeFile(file,"chat-attachments") → 落盘
       insert tbl_data_chat_attachment(uploader_id,kind,ext,mime,file_name,size_bytes,storage_path,status)
          ↓ 返回 {id,name,kind,ext,sizeBytes,thumbUrl?}
[前端] 挂到"该会话待发送草稿"(L-20 按会话分片) → 用户点发送
          ↓ POST 运行端点（harness 族 5 个 / react 族 1 个）body 内带 attachmentIds=[1,2]
[后端] 载入附件行 → 逐个鉴权(R-11/L-58/L-19) → 分派:
         文档 → Tika 抽文本 →(超长顺序截断+告知 R-08)→ 注入提示词 → CHAT 模型
         图片 → image_url 部件 → MULTIMODAL(qwen3.8-max)
         无 MULTIMODAL → 降级 + 显式告知(R-07) → 标注写 metadata
          ↓ SSE 流式返回；消息落 tbl_data_chat_message(content + metadata 标注)；附件回填 message_id
[历史] 打开会话 → 消息带 attachments 元信息 → 缩略图/文件名 → 点击经鉴权端点取原件
```

## 接口设计（遵守 standards/api-design.md）

| 方法 | 路径 | 入参 | 出参 | 说明 |
|---|---|---|---|---|
| POST | `/api/chat/attachment` | multipart：`file`、可选 `sessionId`；header `phoenix-token` | 信封 `{success,data:{id,fileName,kind,ext,sizeBytes,thumbUrl}}` | 白名单外/超限 ⇒ `success=false` + 明确 msg（**HTTP 可能仍 200**，验证断业务码 L-26） |
| GET | `/api/chat/attachment/{id}` | path id；header `phoenix-token` | 文件流（`Content-Disposition: attachment`）或 **403** | 归属校验：非本人且非超管 → 403（R-11，与列表成对 L-58） |
| GET | `/api/chat/attachment/{id}/thumb` | 同上 | 图片缩略图 / 403 / 404 | 仅 `kind=IMAGE`；文档类由前端按 kind 渲染图标，不发此请求 |
| POST | `/api/admin/harness/chat`、`/front/stream/chat`、`/platform/harness/chat`、`/api/front/harness/chat`、`/api/front/stream/chat` | `HarnessRequest` **新增可选** `attachmentIds`（数量上限同 R-04） | SSE（事件序列不变） | 收敛点 `HarnessChatService` 统一处理；**不传时行为完全不变**（矩阵 S1'） |
| POST | `/api/admin/agent/chat` | `ChatModelRequest` **新增可选** `attachmentIds` | SSE（不变） | 收敛点 `AgentManager.streamCall`；不传时行为不变 |
| GET | `/api/agent/{agentId}/sessions/stream` | **不改**（v1.1.0 曾计划改，已作废） | SSE | 纯事件订阅，与附件无关 |

错误语义：白名单外/超限/解析失败（加密、损坏、无文本层）= **业务失败 + 原因类别**（R-03/R-04/R-09，不是 500）；MULTIMODAL 不可用 = **正常流式返回但含显式降级告知**（R-07，不是错误）。

## 数据模型变更（遵守 standards/database-design.md）

**新增 1 表**（`V2.0.0_15`，配对 rollback）：

```sql
CREATE TABLE IF NOT EXISTS tbl_data_chat_attachment (
    id              bigserial    PRIMARY KEY,
    session_id      varchar(64),                      -- 可空：上传时未必已建会话
    message_id      bigint,                           -- 发送后回填（R-10 附件↔消息）
    uploader_id     varchar(64)  NOT NULL,            -- 归属（R-11）
    kind            varchar(16)  NOT NULL,            -- DOCUMENT | IMAGE
    ext             varchar(16)  NOT NULL,            -- 小写扩展名（白名单判定留痕）
    mime            varchar(128) NOT NULL,            -- 真实内容类型（双判定留痕 R-01）
    file_name       varchar(255) NOT NULL,            -- 原始文件名（展示用）
    size_bytes      bigint       NOT NULL,
    storage_path    varchar(512) NOT NULL,            -- 写入方生成；读取端不得拼接（L-19）
    status          varchar(16)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE|ORPHAN|EXTRACT_FAILED
    extracted_chars integer,                          -- 文档抽取字符数（截断告知与排障）
    create_time     timestamp    NOT NULL DEFAULT now(),
    update_time     timestamp    NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_chat_attachment_uploader ON tbl_data_chat_attachment (uploader_id);
CREATE INDEX IF NOT EXISTS idx_chat_attachment_message  ON tbl_data_chat_attachment (message_id);
```

- **不加外键**（沿用本项目既有风格：`tbl_data_chat_message` 亦无 FK）；`message_id` 为空 = 上传未发送（孤儿）。
- **不设 `del_flag`**（避开 L-21 墓碑行隐形）；清理走 `status` + 物理删（决策 4）。
- **不改** `tbl_data_chat_message` 结构：降级/截断标注写既有 `metadata`(jsonb→String)，**零 DDL**。
- **不改** `tbl_data_model_config` 结构：`model_type` 为 varchar，新增取值 `MULTIMODAL` 属数据层面，**由管理页配置写入**（CR-01：迁移件不插模型行、不搬运密钥）。
- **既有约束须遵守**：`uk_dmc_type_default` = 部分唯一索引 `(model_type) WHERE is_default=true AND is_deleted=0` ⇒ 每类型至多一个默认行；
  且 `ModelType.fromCode()` 对未知值**抛 IllegalArgumentException** ⇒ **必须先发代码（加枚举值）再写 MULTIMODAL 数据行**，否则管理页/转换链会炸。
- 回滚：`DROP TABLE IF EXISTS tbl_data_chat_attachment;` + 删除该 MULTIMODAL 行（按 `model_type='MULTIMODAL'` 定位）。

## 共享面身份矩阵（判据：还有谁依赖这个路径/列/配置？）

| 对象 | 既有身份（方法 × 调用方 × 端点） | 变更后预期行为 | 断言归属任务 |
|---|---|---|---|
| **S1'** `HarnessRequest` / `ChatModelRequest`（两个发送 DTO） | 既有调用方：admin-ui `streamHarnessChat`/`streamChat`、mobile-ui `stream.ts`、platform `AgentChatController`/`HarnessFrontController`；**现均不带 attachmentIds** | 新增字段为**可选**；不传时装配器短路 ⇒ **行为与事件序列不变**；两端旧调用零改动即可继续工作 | T-06 |
| **S1** `GET /api/agent/{agentId}/sessions/stream` | SSE **事件订阅**（`streamSessionUpdates`），非发送端点 | **本方案不改它**（v1.1.0 的改动计划作废） | — |
| **S2** `com.phoenix.data.enums.ModelType` | 取值 `CHAT`/`EMBEDDING`；消费方 ≥4：`HarnessConfig`、`AbstractCompiledGragph`、`MemoryPipelineServiceImpl`、`HarnessModelRegistry`（+ `AiModelConfigEpoch.bump(String,…)`） | 新增 `MULTIMODAL` **不改变** CHAT/EMBEDDING 选取结果；未配 MULTIMODAL 时 CHAT 路径不得抛错 | T-03 |
| **S3** `tbl_data_model_config.model_type` + 管理页「模型配置」 | 列表/新增/编辑/启用/停用/**设默认**（`is_default`）；现值仅 CHAT/EMBEDDING | 新类型可正常增删改查；**MULTIMODAL 行不得被误设为 CHAT 默认**；既有默认行不变 | T-03 |
| **S4** `FileStorageService.storeFile(file, subPath)` | 既有调用方 `/api/upload/avatar`（image-only，subPath=avatars）等 | 新 subPath `chat-attachments` **不影响** avatar 上传/读取；目录互不覆盖 | T-02 |
| **S5** `chat-shared` 的 `ChatMessage` 类型 + `useChatSession` + mocks | **两端共用**（admin-ui、mobile-ui），mocks 亦实现该类型 | `attachments` 为**可选字段**；两端 + mocks 的 typecheck **增量错误 0**（L-32）；旧路径不变 | T-07 |
| **S6** `tbl_data_chat_message.metadata` | 既有写入/读取方（jsonb→String 映射，先例见 `McpServerInfo` 注释） | 仅**新增键**（`attachmentNotice`/`truncated`），不改既有键语义；旧消息（无该键）渲染不报错 | T-06 |
| **S7** 路由命名 `/api/chat/attachment` | 与既有 `/api/upload/avatar`、`/api/skill/upload`、`AgentKnowledgeController` 上传**并存** | 不与既有路由冲突；`/api/**` 鉴权链覆盖；**不复用** avatar 端点（避免稀释其 image-only 语义） | T-02 |

## 关键决策

### 决策 1（**v1.2.0 重写**）：附件 id 随**发送请求体（POST）**传递，接入两个收敛点

- **事实纠正（v1.1.0 的前提是错的）**：`GET /api/agent/{agentId}/sessions/stream`（`SessionEventController.streamSessionUpdates`）
  **只是 SSE 事件订阅**，不是发送端点；真正的运行端点**全是 POST**：

  | 端 | 运行端点 | 后端 | 入参 DTO |
  |---|---|---|---|
  | admin-ui | `/api/admin/harness/chat` | HarnessController | `HarnessRequest` |
  | admin-ui | `/api/admin/agent/chat` | ReactAgentController | `ChatModelRequest` |
  | admin-ui | `/front/stream/chat`、`/platform/harness/chat` | AgentChatController / FrontHarnessController | `HarnessRequest` |
  | mobile-ui | `/api/front/harness/chat` | HarnessFrontController | `HarnessRequest` |
  | mobile-ui | `/api/front/stream/chat` | AgentChatController | `HarnessRequest` |
  且 admin-ui 对话页 `components/run/index.vue` **同时**调用 `streamChat` 与 `streamHarnessChat`（按智能体类型分流）。
- **采用**：给两个 DTO 各加**可选** `attachmentIds`，在**两个收敛点**各接入一次：
  ① `HarnessRequest` + `HarnessChatService`（覆盖上表 5 个 harness 族端点，两端共用）；
  ② `ChatModelRequest` + `AgentManager.streamCall`（覆盖 react/graph 族）。
  附件解析、鉴权（T-05）、文档注入与图片多模态装配统一放在收敛点下游的**同一个装配器**里，避免 6 处各写一遍（L-06）。
- **理由**：POST 天然有 body，无需查询参数 hack；**SSE 订阅端点完全不改** ⇒ 原 S1 风险归零；
  两条路径都接 ⇒ 不会出现"上传了附件却不生效"的静默失效。
- **被拒**：① **原 v1.1.0 方案**（GET 查询参数携带）——前提错误，且会把附件 id 写进访问日志、受 URL 长度限制；
  ② 只接 harness 族 —— admin-ui 的 react/graph 智能体将静默忽略附件（违 R-05/R-06 的可观察性）；
  ③ 前端把文件内容塞进 `content` 文本 —— 体积失控、绕过归属鉴权、无法历史回看。
- **重新评估条件**：若将来统一为单一运行端点（收敛为一个 DTO），则只需一处接入。

### 决策 2：图片走 OpenAI 兼容 `image_url` 部件 + 新增 `MULTIMODAL` 类型
- **采用**：`ModelType.MULTIMODAL`；模型行复用现有 qwen provider（DashScope compatible-mode，同 id=7 的 base_url/api_key），首行 **`qwen3.8-max`**（用户裁定 Q4-1 + CR-01 修正）。
- **理由**：配置体系已是 DB 驱动 + OpenAI 兼容路径，**零新依赖**即可支持 multimodal；类型独立于 CHAT，避免污染既有选取逻辑（S2）。
- **被拒**：① OCR 取文字 —— 用户明确要"真多模态"，图表/布局信息会丢；② 复用 `CHAT` 类型换模型名 —— 所有对话都走 VL（成本/延迟），且无法按"是否含图"分派；③ 类型名用 `VISION` —— 用户裁定用 `MULTIMODAL`（语义更宽）；④ 继续用 `qwen-vl-max` —— CR-01 实测其在 id=6 端点 **404 model_not_found**（跨环境不稳），而 `qwen3.8-max` 在两把可用 key 下均 200 并正确读图。
- **重新评估条件**：探针失败（provider 不支持 `image_url`）⇒ 回用户处重选模型/provider。

### 决策 3（CR-01 改造）：MULTIMODAL 模型行由**管理页配置**，迁移件**只做 DDL**
- **采用**：`V2.0.0_15` **只建表 + 2 索引**（不含任何模型配置行）；MULTIMODAL 行在 T-03 加完枚举值后，**经「模型配置」管理页**新增（模型名 `qwen3.8-max`，api_key 由配置人填实测可用的一把），并遵守部分唯一索引 `uk_dmc_type_default`（每类型至多一个默认行）。
- **理由**：**密钥不进版本库**（安全）；不依赖环境相关 id（L-51）；原方案"复用现有 qwen 行密钥"经实测指向**欠费死 key**（id=7 原 key → 400 Arrearage），照原样会把死 key 带进所有新环境。"未配置"本就是 R-07 定义的降级起点，语义自洽。
- **被拒**：① 迁移件幂等插行 + 子查询复制密钥 —— 复制到的可能是死 key（实测 id=7 原 key 欠费），且 SQL 间接搬运密钥；② 复制"EMBEDDING 默认行"的 key —— 把"embedding 行的 key 必可用于 chat"当假设，跨环境不成立；③ 写 `application.yml`/环境变量 —— 与"模型配置 DB 驱动 + 零 resource 配置文件"的既有事实相悖（profile 记载）。
- **重新评估条件**：若将来要求"全新环境零手工配置即可用多模态"，再评估由部署脚本（非 SQL 迁移件）注入配置。

### 决策 4：附件用**独立表 + status 物理清理**，不用 metadata-only、不用逻辑删
- **采用**：新表 `tbl_data_chat_attachment`；`status ∈ {ACTIVE, ORPHAN, EXTRACT_FAILED}`；发送后回填 `message_id`；查询以 `uploader_id` 强约束。
- **理由**：R-11 鉴权需要**可查询**的归属列（metadata-only 要解析 JSON 才能鉴权，性能与正确性都差）；R-10 需要 message↔attachment 关系；清理/统计需要索引；不设 `del_flag` 避开 L-21。
- **被拒**：metadata-only（零 DDL 但鉴权/清理/检索都难）；逻辑删 `del_flag`（L-21 墓碑行风险）。
- **重新评估条件**：若要做"附件级分享/授权"，再引入授权表。

### 决策 5：文档抽取用 Tika（既有依赖），超长**顺序截断 + 显式告知**
- **采用**：`TikaDocumentReader`（已在 `phoenix-rag-core`/`phoenix-agent-core` 依赖中）；截断保留**开头**；告知文案进回答 + `metadata.truncated`。
- **理由**：R-08 用户裁定"截断并告知"；不引新库（profile 技术债：依赖面已复杂）。
- **被拒**：分片多轮汇总（慢/贵/复杂，用户未选）；直接拒绝（用户未选）；扫描件自动转视觉模型（Q6 明确不选）。
- **重新评估条件**：若后续要"长文档问答准确率"，另立需求评估分片或走知识库 RAG。

## 风险与规避

| 风险 | 规避 |
|---|---|
| **探针失败**（key 未开通 `qwen3.8-max` / provider 不支持 image_url） | Implement **首任务**即探针（真实带图调用；L-36 不信清单接口）；失败**即停回报**，不带病施工（L-11） |
| **GET 查询参数改 id 越权读他人附件** | 后端**逐个**校验 `uploader_id`（非本人且非超管 → 403），不信任入参（L-19/L-58）；`attachmentIds` 数量上限同 R-04 |
| **reactive 上传取登录态**再踩 BUG-140 | 强制 token 反查范式（L-65）；验证必须**用非超管账号**实测上传成功 + 归属正确 |
| **两端行为漂移** | 白名单/上限/文案**集中在 chat-shared**（单一来源），两端只做渲染；矩阵 S5 各一条断言（L-06） |
| **共享面回归**（ModelType/metadata/stream/avatar） | 矩阵 S1~S7 每身份一条"对面断言"；提交 body 同时贴"改好了 + 没改坏"两类实测输出（skill §5 共享面提交纪律） |
| **大文件打爆内存/磁盘** | 流式落盘（`FilePart` → `storeFile`）；前置 size 校验（R-04）；抽取文本设字符上限；测试件走 `/tmp` 不入库（L-13） |
| **SVG/HTML 类 XSS**（若白名单被放宽） | `.svg` 明确排除（R-02）；下载端点强制 `Content-Disposition: attachment`，不内联回显 HTML |
| **前端 typecheck 基线噪声** | 记基线错误数，只认**增量 0**（L-32）；错误数骤降按"文件被改坏"处理 |
| **迁移件在活库首跑失败**（drill 脏库假通过） | 关键 DDL 在**从未应用过该件**的库上首跑验证（L-66 教训）；正/反/幂等三跑 |

## 依赖与前置

- **前置（已解除）**：多模态可用性探针 **已于 2026-10-08 通过** —— `qwen3.8-max` 带 `image_url` → HTTP 200 且正确读出图内编码（两把可用 key 各验一次），证据 `evidence/T-01_multimodal-probe.txt`。
- **前置（已解除）**：CHAT 链路死锁（deepseek 402 / qwen id=7 欠费）已由**运维配置动作**修复并端到端复验（SSE 21 事件含真实回答），证据 `evidence/OPS_chat-default-fix.txt`；衍生缺陷 **BUG-152** 已登记不顺手修。
- **依赖既有能力**：`FileStorageService`/`FileStorageProperties`、`spring-ai-tika-document-reader`、`tbl_data_model_config` + `HarnessModelRegistry`/`AiModelConfigEpoch`、`tbl_data_chat_message.metadata`、`chat-shared`。
- **无新增第三方依赖**；**无 nginx/网关新路径**（`/api/**` 已被既有鉴权链与代理覆盖）—— 若实测发现新路径需代理规则，**回改本 plan 并重走确认②**（铁律 6）。
- **版本归属**：v2.0.0（在途）；升级件序号 **V2.0.0_15**（现台账 14 件，续号；M3 汇总时统一复核编号）。
