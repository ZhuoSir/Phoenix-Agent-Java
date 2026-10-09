# CR-03 会话空间隔离：admin 运行页 与 前台 chat（含 mobile）互不可见

> 档位: **major**（agent 提议、用户拍板 2026-10-09；判据：涉 SQL 新列 + 共享面 `tbl_data_chat_session` 多身份）
> 状态: **§S 已确认（陈卓 2026-10-09）｜§P 已确认（陈卓 2026-10-09，回填=A、拒绝=404）｜§T 待确认（3/3）**
> 父 spec: specs/20261008_chat-attachment-understanding v1.4.0（合入后 bump v1.5.0）
> 挂版: v2.0.0（在途，用户确认 2026-10-09）
> 提出人: 陈卓（2026-10-09，原话大意：admin 和 chat 页面会话是同步的，应该分开；admin 运行页只是测试管理，
> 在 chat 中不要显示，反之也一样）

## §S 变更意图

### 1. 三张清单

**新增**

- **R-13 会话空间隔离**：WHEN 会话在 admin 运行页（`/#/agent/{id}/run`）创建或列表，系统 SHALL 仅使其在
  运行页空间（`ADMIN_RUN`）可见；WHEN 会话在前台 chat（admin 前台页 `/front/chat` 或 mobile `/m/`）创建或列表，
  系统 SHALL 仅使其在聊天空间（`FRONT_CHAT`）可见；两空间的会话与消息 SHALL 互不显示
- **R-14 存量会话归属**：WHEN 本变更上线，系统 SHALL 按确认的回填口径为存量会话赋空间归属，
  且回填 SHALL 可回滚（rollback SQL 配对）

**修改**

- **R-10（持久化/历史回看）** 补充：历史回看的会话集合 SHALL 受空间隔离约束；同空间内的回看行为不变
- **R-11（归属鉴权）** 补充：空间归属 SHALL 与用户归属叠加校验；跨空间读取单个会话/消息/附件 SHALL 被拒绝
  （拒绝形态 404-as-不存在 或 403 于 §P 定）

**删除**：无

### 2. 受影响既有 R 核查（必答）

| 既有 R | 是否受影响 | 说明 |
|---|---|---|
| R-01~R-04 上传白名单/限制 | 否 | 上传与空间无关；sessionId 仅作为附件归属标签 |
| R-05~R-09 理解/降级/截断/解析原因 | 否 | 装配器按 attachmentIds 工作，与空间无关 |
| R-10 持久化/历史回看 | **是** | 回看的会话集合按空间过滤 ⇒ 修改条款如上 |
| R-11 归属鉴权 | **是** | 叠加空间维度 ⇒ 修改条款如上 |
| R-12 两端一致 | 否 | admin 前台页与 mobile 同属 FRONT_CHAT 空间，规则仍单一来源 |

### 3. Non-goals

- 不做跨空间迁移/合并工具；不做用户可自选/切换空间
- 不改 SSE 订阅、会话标题生成、附件抽取的既有语义（仅随空间过滤可见集合）
- 不做 admin 运行页会话「导出到 chat」；不自动清理存量测试会话（回填口径于 §P 拍板）
- 不改 agent 维度的可见性（R-08/R-17/R-18 口径不动）

### 4. 为何不另立 spec

- 用户 2026-10-09 拍板挂当前 spec 的 CR-03（载体三选一中选 C）
- 变更直接作用于本 spec 刚交付的两端会话 UI 与**同一张共享会话表**；另立 spec 会造成两个 spec 交叉改
  同一共享面，合并冲突与「矩阵没列的身份」漏评风险更高

### 5. 范围自检问句

- 「还有谁依赖 `tbl_data_chat_session` 与会话列表/创建端点？」→ 侦察已见 ≥8 身份
  （admin 运行页列表、admin 前台列表、mobile 列表、创建×3 端、clear-all、pin/rename/delete、SSE 订阅、
  标题生成、附件 listForSession）⇒ **全部进 §P 身份矩阵逐条评估后才施工**
- 「本 CR 是否改变可交付目标？」→ 不改变「附件上传与理解」目标；新增「会话空间隔离」交付面

## 施工停摆声明

开 CR 即停 T-09 施工；§S~§T 期间仅允许写 `changes/` 文档，禁改源码（铁律 3 照常生效）。

## §P 影响面与已实现处置表

### P-1 共享面判定

`tbl_data_chat_session` 与会话列表/创建端点 = **共享面**（自检问句「还有谁依赖」≥8 个"是"）。
空间维度拟以**新列 `source`** 承载（`ADMIN_RUN` / `FRONT_CHAT`），由**调用方显式声明**（query/body 参数 `scope`），
后端在列表/创建/按 id 操作三处统一校验。
**隔离定性：管理/体验隔离，非安全边界**——安全边界仍是 R-11 的用户归属；`scope` 参数可被同用户伪造不构成越权
（两空间同属一个登录主体的数据），此定性需确认②认可。

### P-2 共享面身份矩阵（method × 调用方 × 端点 → 变更后预期行为）

| # | 身份（调用方 → 端点/方法） | 现状 | 变更后预期行为 |
|---|---|---|---|
| 1 | admin 运行页 → `GET /api/agent/{id}/sessions`（api/core/chat.ts） | 全量 | 传 `scope=ADMIN_RUN`，仅见运行页空间 |
| 2 | admin 前台 chat → `GET /api/agent/{id}/sessions`（api/front/chat.ts, chat.vue:151） | 全量 | 传 `scope=FRONT_CHAT`，仅见聊天空间 |
| 3 | mobile → `GET /api/agent/{id}/sessions`（services/chat.ts） | 全量 | 传 `scope=FRONT_CHAT`（与 admin 前台同空间，R-12） |
| 4 | 三端创建 → `POST /api/agent/{id}/sessions` | 无来源标记 | 各传自身 scope，落库 `source` |
| 5 | 运行页/前台/ mobile → `GET /api/sessions/{sid}/messages` | 仅用户归属校验 | 叠加空间校验：声明 scope 与库内 source 不符 ⇒ **404-as-不存在**（形态待拍板） |
| 6 | 三端 → `POST /api/sessions/{sid}/messages`（saveMessage） | 同上 | 同 #5；标题生成（SessionTitleService）随会话既有 source，不改 |
| 7 | 三端侧边栏 → pin / rename / delete 单会话 | 仅用户归属 | 同 #5 叠加空间校验 |
| 8 | 运行页「清空」→ `DELETE /api/agent/{id}/sessions`（clearSessionsByAgentId） | 清该 agent 全部 | **仅清声明 scope 空间**（防运行页清空误删聊天历史） |
| 9 | SSE → `GET /api/agent/{id}/sessions/stream`（SessionEventController） | 按 sessionId 订阅 | 订阅属既有会话的续流，**不叠加空间校验**（非可见性面；声明见 P-4） |
| 10 | 附件面板 → `ChatAttachmentService.listForSession(sessionId, owner)` | 按会话+归属 | 仅从所属空间 UI 可达，**不叠加**（可见性已由 #5/#7 收敛） |
| 11 | HarnessAgentRegistry 缓存键 (agentId, sessionId) | 内部 | 不变（内部态，非可见性面） |
| 12 | 存量 168 条会话（无 source） | 全量可见 | 按拍板口径回填（P-3 选项） |

### P-3 存量回填口径（**待确认②拍板**）

| 选项 | 内容 | 后果 |
|---|---|---|
| A（推荐） | 存量全部 → `FRONT_CHAT` | 运行页从此只见新建测试会话（符合"运行页=测试管理"定位）；聊天侧保留全部历史（含旧测试会话，可用既有删除 UI 手工清） |
| B | 存量全部 → `ADMIN_RUN` | 聊天侧立刻干净；但存量真实对话会出现在运行页，与定位相悖 |
| C | 存量 → `LEGACY`（两空间都可见），隔离仅对新增生效 | 最保守、零迁移风险；但用户当前抱怨的"互见"对存量依旧存在 |

### P-4 关键日志点（涉共享面读写，遵循 logging 规范）

| 场景 | 级别 | 触发点 | 必带字段 |
|---|---|---|---|
| 列表按空间过滤 | INFO | 列表端点 | agentId, scope, 命中条数, userId |
| 跨空间访问被拒 | WARN | #5/#7/#8 校验点 | sessionId, 声明 scope, 库内 source, userId |
| 创建落 source | INFO | 创建端点 | sessionId, source, userId |
| 回填执行 | INFO | 迁移 SQL 执行期（migrator 日志） | 影响行数 |

### P-5 已实现处置表（既有代码逐条交代）

| 既有实现 | 处置 | 说明 |
|---|---|---|
| 附件表/上传/装配器（T-02~T-06） | **保留** | 与空间无关；sessionId 仅作归属标签 |
| ChatController 列表/创建/清空/pin/rename/delete | **改造** | 加 scope 入参 + source 落库/过滤/校验（T-10） |
| 三端前端会话列表/创建调用 | **改造** | 传各自 scope（T-11） |
| SessionEventController SSE 订阅 | **保留** | 非可见性面（P-2 #9），不改 |
| SessionTitleService / updateSessionTime | **保留** | 内部副作用，随既有 source |
| ChatAttachmentService.listForSession | **保留** | 可见性已由会话级校验收敛（P-2 #10） |
| 回退 | 无 | 不引入需要回退的既有行为 |
| 拆分 | 无 | 不拆表不拆端点 |

### P-6 被拒方案（含必选项）

| 方案 | 拒绝理由 |
|---|---|
| **另立新 spec** | 用户 2026-10-09 拍板走 CR-03；且与本 spec 交叉改同一共享面，拆开会漏评身份、合并冲突 |
| 用 token 类型推导空间（privilege=ADMIN_RUN） | 错：admin 前台页也用 privilege token 但属 FRONT_CHAT，推导必错分 |
| 用 agent 维度隔离（测试 agent 单独放） | 错：同一 agent 两空间都要用，agent 不是空间 |
| 前端过滤（列表全量返回、各端自行隐藏） | 错：向他空间泄露数据、多端易漏、移动端/未来端不可控 |
| 双表拆分（运行页会话另表） | 改动面远大于加列：SSE/标题/附件/registry 全要双写双读，风险不成比例 |

### P-7 SQL 与升级件（M3 时并入）

- `V2.0.0_16__chat_session_source_ddl.sql`：`ALTER TABLE tbl_data_chat_session ADD COLUMN source varchar(16) NOT NULL DEFAULT 'FRONT_CHAT'`
  + 按 P-3 拍板口径的回填语句 + 索引 `idx_chat_session_agent_source(agent_id, source)`；配 rollback（删列/删索引）
- 无配置变更

## §T 增/改/废任务

> 拍板记录（确认②，陈卓 2026-10-09）：存量回填 = **A（存量→FRONT_CHAT）**；跨空间拒绝 = **404-as-不存在**。
> 兼容决策：旧客户端不传 scope 时后端**默认 FRONT_CHAT**（保 mobile 旧包/admin 旧包可用），矩阵 #1~#3 注明。

- [ ] T-10 后端：`tbl_data_chat_session` 加 `source` 列 + 列表/创建/按 id 操作加 scope 入参与校验（404-as-不存在）+ 清空仅本空间 + P-4 日志点
    关联: R-13, R-14, R-10(改), R-11(改)　CR: CR-03　处置: 改造 ChatController 六处 + SQL V2.0.0_16（保留附件/装配器/SSE/标题不动）
    依赖: CR-03 §S/§P/§T 确认
    验证方式: ① SQL 三段证明（upgrade 于 drill 库执行 + 结构核对 + rollback 演练复原）；② 身份矩阵后端身份
      **#1~#8、#12 逐条 curl 断言**（含"不传 scope 默认 FRONT_CHAT"兼容断言、清空仅本空间断言、
      跨空间 404-as-不存在断言、存量 168 条回填后 source 全为 FRONT_CHAT 断言）；③ 日志断言：列表 INFO 含
      agentId/scope/命中条数/userId；跨空间 WARN 含 sessionId/声明 scope/库内 source/userId；创建 INFO 含
      sessionId/source/userId；④ 对面断言：SSE 订阅/标题生成/附件 listForSession 行为不变（矩阵 #9/#10/#11）
    验收标准: 矩阵 #1~#8、#12 全绿 + rollback 演练通过 + 四类日志行齐 + 对面断言无破坏
- [ ] T-11 前端三端传 scope（运行页=ADMIN_RUN；admin 前台与 mobile=FRONT_CHAT）+ 列表/创建/清空/pin/rename/delete 全调用点
    关联: R-13, R-12　CR: CR-03　处置: 改造三端会话 API 封装（保留 UI 结构不动）
    依赖: T-10
    验证方式: ① typecheck 增量 0（admin 203 / mobile 11 基线，L-32）；② 构建产物断言（scope 字面量入包）；
      ③ 浏览器三端实测：运行页列表只见 ADMIN_RUN、前台只见 FRONT_CHAT、mobile 与前台一致（R-12）；
      ④ 对面断言：附件上传/预览（BUG-157）、SSE、历史回看在隔离后仍正常
    验收标准: 三端互不可见实测通过 + 增量 0 + 对面断言无破坏
- [ ] T-12 隔离端到端汇总与证据落盘（含存量回填核对、隔离后附件/理解链路回归）
    关联: R-13, R-10, R-11　CR: CR-03　处置: 新增证据件 evidence/CR-03_session-isolation.txt
    依赖: T-10, T-11
    验证方式: ① e2e：同一用户分别在运行页与前台各建会话 → 两侧列表互不可见、跨空间直连 id 404；
      ② 附件回归：两空间各自上传/理解/预览不受隔离影响；③ DB 核对 source 分布与回填行数；④ 证据落盘
    验收标准: 隔离矩阵全绿 + 附件链路回归无破坏 + 证据入档

**作废任务**: 无（不删不取消任何既有 T）

## §I 执行与合入八动作

（待 §T 确认后执行）
