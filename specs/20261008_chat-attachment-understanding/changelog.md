# Changelog: chat-attachment-understanding

## v1.4.0（2026-10-09）**待重确认** —— 铁律 6：消息由前端落库 ⇒ metadata/回填改经既有 saveMessage 副作用

- **停编码**：T-06 仍未写任何生产代码
- **查实事实**：agent 侧**不写消息表**；消息由前端 `saveMessageApi` 落库
  （`components/run/index.vue:637` 用户消息、`:698/:713` 助手消息）⇒ 后端拿不到 message id，
  原 T-06 验收里「`metadata.attachmentNotice` 落库」「附件回填 `message_id`」两条**无法只靠后端达成**
- **方案（不新增端点）**：扩展既有 `POST /api/sessions/{sessionId}/messages` —— 前端把 `attachmentIds` 与告知写入
  `metadata`，后端 `saveMessage` 见该键即回填附件 `message_id`；**不含该键时行为逐字节不变**
- **新增共享面 S9**：`ChatController.saveMessage` 的四个既有身份（保存用户消息 / 保存助手消息 /
  `titleNeeded` 触发标题生成 / 每次更新会话时间）逐一列出变更后预期；metadata 畸形不得影响消息保存（降级 WARN，不 500）
- **新增 plan 小节「T-06 / T-07 责任边界」**：R-07/R-08 告知的「当次用户可见」由 T-06 写进回答内容；
  「历史回看仍可见」由前端写 metadata + 后端回填共同达成；缩略图必须 fetch+blob（不支持 `?token=`）
- **T-06 可独立验证**：直接以带 `attachmentIds` 的 metadata 调既有端点即可断言回填，**不依赖 T-07 完成**
- 影响文档：plan（接口表 / S9 / 数据流 / 分工小节）、tasks（T-06 ⑤b·⑦、T-07 ④⑤⑥）；**requirements 不变**
- 状态：plan/tasks **v1.4.0 待重确认**（确认② 重走）；三重门暂不齐 ⇒ 继续禁止写生产代码


## v1.3.0 确认② 重走通过（2026-10-09）⇒ 三重门恢复齐全

- 确认人 **陈卓**（用户选定「确认通过（确认人：陈卓）」）；plan **v1.3.0 已确认** / tasks **v1.3.0 已确认**
- 决策2 定案：**两阶段（方案 B）**——阶段1 `qwen3.8-max` 结构化理解图片产出描述；
  阶段2 描述 + 文档抽取文本 + 提示词进正常 agent 循环（技能/工具/记忆/工作区全保留）
- 三重门：requirements v1.1.0 / plan v1.3.0 / tasks v1.3.0 均 已确认 ⇒ **允许继续 Implement（T-06 起）**
- T-06 施工纪律：触碰共享面 S1'（两个发送 DTO）与 S8（buildUserMessage）⇒ 提交 body 必须同贴
  "改好了 + 没改坏"两类实测输出；SSE 订阅端点保持 diff 为空


## v1.3.0（2026-10-09）**待重确认** —— 铁律 6：决策2 改为两阶段（AgentScope 无 per-call 模型覆盖）

- **停编码**：T-06 未写任何生产代码；查实设计前提后回改文档
- **硬事实（javap 实证）**：`HarnessAgent` 只有 `getModel()` 无 setter；`call(...)` **8 个重载**均不接受 model/options；
  delegate `ReActAgent` 同为只读；模型仅能在 `Builder.model(...)` 建实例时绑定。
  实例由 `harnessAgentRegistry.get(agentId, sessionId)` 按会话缓存，工作区/记忆/turn 态挂其上
  ⇒ **"含图就换模型"在不重建实例前提下不可行**，v1.1.0/v1.2.0 决策2 的"image_url 部件直接进对话模型"前提错误
- **用户裁定（2026-10-09）：方案 B 两阶段** —— 阶段1 `qwen3.8-max` 对图片做结构化理解产出描述；
  阶段2 描述 + 文档抽取文本 + 提示词进**正常 agent 循环**（技能/工具/记忆/工作区全保留）
- **被拒**：A 直连多模态（绕过 agent ⇒ 能力全丢）；C 按请求另建 MULTIMODAL 实例（同会话双实例致
  Compaction/SkillUsageStore/turn 态分叉 + AutoCloseable 生命周期自管，**其可行性未验证**，不做未验证的高风险改动）；
  OCR（用户明确要真多模态）
- **矩阵新增 S8**：`HarnessChatServiceImpl.buildUserMessage`（既有两身份：无附件纯文本 / `skillScopeHint` 末尾拼接）
- **风险新增 3 条**：描述瓶颈（模板化 + 允许重跑阶段1）、双次调用成本（仅含图触发）、描述过长（同受 R-08 截断）
- **tasks T-06 重写**：验证方式含 S1'（两族各一条）、S8、阶段1 描述含图中独特事实、R-06 端到端、
  T-04 遗留的"文档独特事实命中"在此完成、降级与历史标注、S6 旧消息兼容、`message_id` 回填
- **requirements 不变**（R-06 原文"提交给多模态模型理解、非 OCR"在方案 B 下依然成立）⇒ 无需重走确认①
- 状态：plan/tasks **v1.3.0 待重确认**（确认② 重走）；三重门暂不齐 ⇒ 继续禁止写生产代码


## v1.2.0 确认② 重走通过（2026-10-09）⇒ 三重门恢复齐全

- 确认人 **陈卓**（铁律 6 回改文档后重走确认②，用户选定「确认通过（确认人：陈卓）」）
- **接入范围裁定：两族都接** —— harness 族 5 端点（`HarnessRequest` + `HarnessChatService`）
  + react 族 1 端点（`/api/admin/agent/chat`，`ChatModelRequest` + `AgentManager.streamCall`）；
  理由：admin-ui 对话页 `components/run/index.vue` 同时调用两族，只接一族会造成"上传了附件却静默不生效"
- 三重门现状：requirements **v1.1.0 已确认** / plan **v1.2.0 已确认** / tasks **v1.2.0 已确认** ⇒ 允许继续 Implement
- 施工纪律不变：T-06 触碰共享面 S1'（两个发送 DTO）⇒ 提交 body 必须同贴"改好了 + 没改坏"两类实测输出


## v1.2.0（2026-10-09）**待重确认** —— 铁律 6：T-06 施工前侦察推翻 plan 决策1 的事实前提

- **停编码**：T-06 尚未写任何生产代码；发现设计缺陷即回改文档（未"先写了再说"）
- **事实纠正**：`GET /api/agent/{agentId}/sessions/stream` 只是 **SSE 事件订阅**（`streamSessionUpdates`），
  **不是发送端点**；真正的运行端点**全是 POST**，共 **6 个**、分两族：
  harness 族 5 个（`/api/admin/harness/chat`、`/front/stream/chat`、`/platform/harness/chat`、
  `/api/front/harness/chat`、`/api/front/stream/chat`，共用 `HarnessChatService` + `HarnessRequest`）、
  react 族 1 个（`/api/admin/agent/chat`，`ChatModelRequest` → `AgentManager.streamCall`）
- **关键约束**：admin-ui 对话页 `components/run/index.vue` **同时**调用两族 ⇒ 只接一族会造成
  "上传了附件却不生效"的**静默失效**（违 R-05/R-06 可观察性）
- **决策1 重写**：附件 id 随 **POST 请求体**传递（两个 DTO 各加可选 `attachmentIds`），
  在**两个收敛点**下游用**同一个装配器**解析/鉴权/注入（避免 6 处各写一遍，L-06）；
  **SSE 订阅端点不改** ⇒ 原 S1 风险归零；矩阵新增 **S1'**（两个发送 DTO 的既有调用方）
- 被拒方案补：原 GET 查询参数方案（前提错误 + id 进访问日志 + URL 长度限制）、只接 harness 族（静默失效）、
  前端把文件内容塞 content（体积失控 + 绕鉴权 + 无法回看）
- 影响文档：plan（决策1/接口表/矩阵/数据流/方案概述）、tasks（T-06 标题与验证方式）；**requirements 不变**（R-01~R-12 语义未动）
- 状态：plan/tasks 均 **待重确认**（确认② 重走）；三重门暂时不齐 ⇒ 继续禁止写生产代码


## v1.1.0 = CR-01 合入（2026-10-08，micro，1 次确认 陈卓）

- **变更**：多模态模型 `qwen-vl-max` → **`qwen3.8-max`**（实测原生支持 `image_url`，两把可用 key 均 200 并正确读图；
  原 `qwen-vl-max` 在 id=6 端点 404 model_not_found、在原 CHAT 行 key 下 400 Arrearage）
- **变更**：plan 决策 3 改造 —— `V2.0.0_15` **只做 DDL**（建表+2 索引），MULTIMODAL 模型行**改由管理页配置**；
  理由：原方案"子查询复用现有 qwen 行密钥"实测指向**欠费死 key**，会把死 key 带进所有新环境；密钥不入版本库
- **新增纪律**（写入 plan/tasks）：`ModelType.fromCode()` 对未知值抛异常 ⇒ **先发代码加枚举值、再写 MULTIMODAL 数据行**；
  遵守部分唯一索引 `uk_dmc_type_default`（每类型至多一个默认行）
- **T-01 达成并勾选**：探针 HTTP 200 且读出图内编码 `PROBE-4821-KQ`（证据 `evidence/T-01_multimodal-probe.txt`）
- **三文档 bump**：requirements v1.1.0 / plan v1.1.0 / tasks v1.1.0（本 CR 的 1 次确认即其重确认）
- **关联账**：BUG-152（CHAT 选取两路径语义不一致，新建·不顺手修）、L-74（改配置前先读选取代码+确认运行时缓存）、
  运维证据 `evidence/OPS_chat-default-fix.txt`（CHAT 死链修复 + 端到端复验 SSE 21 事件含真实回答）
- 八动作：①②③④⑤⑥⑦⑧ 全勾（⑥ 本 CR 不涉新 SQL 件：`V2.0.0_15` 仍 1 件、范围收窄，待 T-02 落地登记 artifacts）


## Implement 阻塞（2026-10-08）：T-01 探针未通过 ⇒ 按 plan 停工回报

- **T-01 未通过**：`qwen-vl-max` 真实带 `image_url` 调用 → **HTTP 400 `Arrearage`**（阿里云百炼账号欠费/状态异常）。
  失败原因**不是**模型名、不是 provider 不支持多模态、不是本需求代码（尚未写任何生产代码）。
- **影响面诊断**：既有 **默认 CHAT（deepseek）HTTP 402 Insufficient Balance**、qwen CHAT 400 Arrearage
  ⇒ **当前环境所有 CHAT/多模态链路不可用**；embedding 链路**正常**（/v1/embeddings → 200，1024 维）。
- **自我纠错留痕**：首次用 `/v1/chat/completions` 探 embedding 模型得到 404 `model_not_supported`，
  属**探错端点**的假信号；按 L-45/L-37 复核后撤销，**未登记假 BUG**。
- **按 tasks 依赖图判定**：T-01/T-03/T-06/T-09 阻塞，T-04 验证阻塞（代码可写但**不得声称验证通过**，铁律 5）；
  **T-02/T-05/T-07/T-08 不依赖模型**（若用户授权可先做，属对 plan「探针为阻塞前置」的偏离，需明确同意并留账）。
- **未勾选任何任务**；证据 `evidence/T-01_multimodal-probe.txt`（含 request_id、三条链路诊断、解除条件 A/B/C）。
- 密钥全程仅脚本内读取，**未落盘未打印**。


## v1.0.0 tasks 确认③ 通过 ⇒ **三重确认门达成**（2026-10-08）

- **确认③**：tasks **v1.0.0 已确认**，确认人 **陈卓**
- 三重确认自检：requirements **v1.0.0 已确认（陈卓）** / plan **v1.0.0 已确认（陈卓）** /
  tasks **v1.0.0 已确认（陈卓）** ⇒ 三份齐、确认人非空 ⇒ **允许进入 Implement（铁律 1 解除）**
- 施工分支：`git checkout -b feature/chat-attachment-understanding v2.0.0`（基点 = 在途版本分支）
- 开工顺序：T-01 多模态探针为**阻塞前置**——探针不过即停并回报，不带病施工


## v1.0.0 plan 确认② 通过 + tasks v0.1.0 草稿（2026-10-08）

- **确认②**：plan **v1.0.0 已确认**，确认人 **陈卓**（用户选定「确认通过（确认人：陈卓）」）；
  同轮裁定**施工范围 = 全量做**（两端 + 文档 + 图片多模态 + 历史回看 + 鉴权）
- **tasks v0.1.0 草稿**成文：9 个任务（T-01~T-09），分 5 组（前置探针 / 上传存储库表 / 理解与鉴权 / 前端两端 / 收口）
  - T-01 多模态探针（**阻塞前置**，失败即停回报）
  - T-02 上传端点+存储+`V2.0.0_15`（含 S4/S7 对面断言：avatar 与既有上传零回归）
  - T-03 `ModelType.MULTIMODAL` + 选取支路（含 S2 四消费点 + S3 配置页对面断言）
  - T-04 Tika 抽取 + 顺序截断告知 + 三类解析失败提示（扫描件不转视觉）
  - T-05 附件归属鉴权 + 下载/缩略图（三身份断言；L-58 成对实施核对）
  - T-06 stream 集成 + 图片多模态 + 降级告知 + metadata（含 S1/S6 对面断言）
  - T-07 chat-shared + admin-ui（S5 typecheck 增量 0；L-20 草稿按会话分片）
  - T-08 mobile-ui（R-12 两场景断言；平台能力差异如实记录）
  - T-09 端到端 + 部署三段证明 + 证据落盘（编译 exit=0 才部署）
- 覆盖矩阵：R-01~R-12 全覆盖；S1~S7 每身份指派到具体 T；依赖均指向更小编号、无循环；无占位任务
- 状态：**确认③未过** ⇒ 三重确认门未齐 ⇒ 仍禁止写生产代码（铁律 1/3）


## v0.1.0 plan（2026-10-08）Phase 2 技术方案成文（待确认②）

- 坑核对：**已核对全部 73 条 active 坑**，列出 17 组相交项与避开做法（L-65 reactive 登录态、L-64 占位符、
  L-58 归属成对、L-26 业务码断言、L-06 多入口、L-20 会话分片、L-36 不信能力清单、L-11 环境、L-19 路径同源、
  L-21 墓碑行、L-13 add 面、L-32 typecheck 增量、L-49/51/53/55/66 迁移纪律、L-03/16/07/50/30 验证与部署、L-44/45 命名与全称否定）
- 方案：附件先上传拿 id（对话流是 **GET SSE 不能带 body**）→ stream GET 携带 `attachmentIds`；
  文档走 Tika 抽文本 + CHAT 模型；图片走 OpenAI 兼容 `image_url` + 新增 **MULTIMODAL** 模型（qwen-vl-max）
- 共享面身份矩阵 **S1~S7**（stream GET / ModelType 枚举 / model_config+管理页 / FileStorageService+avatar /
  chat-shared 类型 / message.metadata / 路由命名），每身份一条对面断言并指派到 T-02/T-03/T-06/T-07
- 数据模型：新表 `tbl_data_chat_attachment`（**不设 del_flag**，用 status；无 FK）+ 2 索引；
  `V2.0.0_15` 幂等插入 MULTIMODAL 行（密钥子查询复用现有 qwen 行，不落明文）；message/config 表**零 DDL**
- 5 条关键决策各带被拒方案与重新评估条件；9 条风险各带规避
- 前置阻塞：Implement 首任务 = 多模态可用性探针（真实 image_url 调用），不过即停
- 状态：**确认②未过**，未进入 Tasks；未触碰源码（铁律 3）


## v1.0.0（2026-10-08）确认① 通过

- 确认人: **陈卓**（用户 2026-10-08 选定「确认通过（确认人：陈卓）」）；三重确认门第 **①** 重达成
- 同轮裁定 **Q4-1**：模型类型枚举值 = **`MULTIMODAL`**（非 VISION；语义为多模态大模型，qwen3.8 类原生图文模型同归此类型）；
  首行 `model_name = qwen-vl-max`，provider/base_url/api_key 复用现有 qwen（DashScope compatible-mode，id=7）
- 修改：假设 4 重写（MULTIMODAL + qwen-vl-max + OpenAI 兼容 `image_url` 部件 + 不新增 SDK）；
  裁定表 Q4 行去掉「模型名待定」；Q4-1 段落翻为「已裁定」；待确认问题节标题改「已全部裁定」
- 保留兜底纪律：Implement 首任务 = 多模态可用性探针（真实带 `image_url` 调用），探针不过即停
- 下一步：Phase 2 Plan（须先加载 lessons 全部 active 坑 + api-design/database 规范源，坑核对节置顶）


## v0.2.0（2026-10-08）用户裁定 Q1~Q6（草稿期，待确认①）

- 裁定：Q1 仅会话级 / Q2 按草案白名单（.svg 排除）/ Q3 20MB·5 个 / Q4 允许加 `model_type=VISION` 配置行 /
  **Q5 降级但显式提示（非硬失败）** / Q6 超长顺序截断并告知 + 扫描件报错不转视觉
- 修改：**R-07 整条改写**（硬失败 → 降级 + 用户可见告知 + metadata 留标注 + 只问图时不编造）；
  **R-08 口径固化**（顺序截断、保留开头、告知可见、记 metadata）；**R-09 扫描件场景改为明确报错**
- 修改：假设区 8 条标 ✅ 并写入裁定值；新增「裁定记录」表（Q1~Q6 逐条落点）
- 新增：**Q4-1 唯一遗留阻塞项** = 视觉模型确切模型名（agent 不代猜；Implement 首任务为可用性探针，探针不过即停）
- 侦察事实：现有 `tbl_data_model_config` 有 qwen / DashScope compatible-mode 行（id=7，api_key 在库）⇒ 视觉模型可复用同 provider
- 状态：**确认①仍未通过**，未进入 Plan；未触碰源码


## v0.1.0（2026-10-08）草稿创建

- 创建：requirements / plan / tasks / changelog 四件套（Phase 0）
- 挂载: **v2.0.0**（2026-10-08，用户确认「挂 v2.0.0（在途）」；台账在途唯一，未新开版本）
- 功能名: `chat-attachment-understanding`（用户从两个候选中选定）
- 范围拍板（用户 2026-10-08）：入口 = **admin-ui + mobile-ui 两端**；图片理解 = **真多模态（新增视觉模型）**
- requirements v0.1.0 草稿成文，含 8 条待确认问题（阻塞项 6 条）——**未确认，禁止进入 Plan**
