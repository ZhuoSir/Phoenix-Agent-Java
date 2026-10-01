> 版本: v1.1.0 | 状态: 待重确认 | 更新: 2026-10-01 | 确认人: 陈卓 | 确认日期: 2026-10-01

# 技术方案：会话文件面板（对齐 requirements v1.0.0）

## 0. 核心机制（一张图）

```
                    ┌─ LOCAL 策略（workspace root → /app/uploads/agent-workspace，复用 uploads 卷）
AbstractFilesystem ─┤
 (AgentScope 接口)   └─ REMOTE 策略（Redis BaseStore，AOF 已持久）
        ▲ 装饰器 FilesystemTeeDecorator（write/uploadFiles/delete 钩子）
        │   tee 到 /app/uploads/{agentId}/{sessionId}/{uuid}_{name} + 登记 DB + SSE file_created
        ▼
AgentFileService（唯一登记/检索/删除/下载门面）── tbl_data_agent_file（Flyway 件 V1.3.0_01）
        ▲
REST /api/agent/files*（ReturnVo 信封，Sa-Token + 会话属主）  ←→  ChatMessages.vue「文件」抽屉
```

**关键取巧**：不改 AgentScope 一行——`AbstractFilesystem` 是接口（`ls/read/write/uploadFiles/downloadFiles/delete` 齐备，javap 实证），构造侧 `HarnessAgentFactory` 本就自建 filesystem 对象，装饰器在**我们这侧**包一层即可同时覆盖 LOCAL 与 REMOTE 两种策略（R-02 无需降级）。下载统一走装饰器持有的原后端（local 读盘 / remote 从 Redis store 取 bytes），tee 副本落 uploads 保证持久与备份口径单一。

## 1. 存储与登记

- **表**（`releases/v1.3.0/sql/V1.3.0_01__agent_file_table.sql`，回滚 `R1.3.0_01`）：
  `tbl_data_agent_file(id bigint pk, agent_id bigint, session_id varchar(64), file_name varchar(255), rel_path varchar(512), size_bytes bigint, mime varchar(128), source varchar(32), backend varchar(16), store_key varchar(512), del_flag smallint default 0, creator varchar(64), create_time)`；索引 `(session_id, del_flag)`。rel_path=tee 副本相对 uploads 根的路径；store_key=原后端定位串（LOCAL: 相对 workspace 路径；REMOTE: Redis key）
- **workspace root 配置化**（R-01）：`HarnessAgentFactory.WORKSPACE` 改读 `phoenix.agent.workspace-root`（默认 `.agentscope/workspace` 保持现行为）；交付包 env 置 `/app/uploads/agent-workspace` → 复用 `uploads` 卷，无 compose 破坏性变更
- **50MB 闸口**（R-18）：装饰器 tee 前检查字节数（含报告物化入口同闸）

## 2. AgentFileService（phoenix-agent-core，新包 service/file）

- `register(agentId, sessionId, name, bytes, source, backend, storeKey)`：路径清洗（`../`/控制字符/分隔符→安全名）、uuid 前缀落盘、mime 探测（Files.probeContentType + 白名单表）、INSERT
- `listBySession(sessionId)`（del_flag=0）、`logicalDelete(id, requester)`、`load(file)`：backend=LOCAL 读 tee 副本；REMOTE 优先 tee、缺失时经 store 回源（Redis 被清后 tee 仍是权威）
- 首期不物理删（R-10/R-19）

## 3. REST 接口（`/api/agent/files`，agent 域 ReturnVo，R-07~R-11）

| 方法 | 路径 | 鉴权/行为 |
|---|---|---|
| GET | `/api/agent/files?sessionId=` | 登录 + 会话属主（`tbl_data_chat_session.user_id`）→ VO 列表（不回 rel_path 原文，防探测） |
| GET | `/api/agent/files/{id}/download` | 属主校验；`Content-Disposition: attachment; filename*=UTF-8''…`；正确 MIME |
| 同上 `?inline=1` | — | 仅 text/html、image/*、text/* 白名单；HTML 附 `Content-Security-Policy: sandbox`（R-09）；其余 400 |
| DELETE | `/api/agent/files/{id}` | 属主逻辑删（R-10） |
- Sa-Token 放行路径不变（`/api/agent/*` 本就需登录域）；错误复用 42xxx 段位新增 `AGENT_FILE_*` 码

## 4. SSE file_created（R-14）

- 装饰器落库成功后发 Spring 事件 → `HarnessChatService` 的事件流 merge 一条 `{type:"file_created", file:{id,name,size,mime}}`；`HarnessEventMapper` 增加映射；未知 type 的旧客户端天然忽略（向后兼容）
- 会话外产出（无活动流）只落库，面板拉取时自然可见

## 5. 产出方接入清单（R-12，逐源在 T-05 实测勾选，不做纸面归因）

| 产出源 | 现状 | 接入方式 |
|---|---|---|
| Agent 工具/skill 写文件（write/uploadFiles） | 双后端 | 装饰器 tee（本方案主通路） |
| 报告 HTML（现塞消息正文） | 无文件 | ChatMessages 气泡「另存为文件」→ POST `/api/agent/files/materialize`（body=sessionId+内容/mime，属主校验+50MB 闸） |
| 深度分析/python 产物 | 临时目录即焚 | 本期只接「结果文本另存」（同上 materialize 通道）；运行时产物收集列后续 |
| 头像/上传（既有 uploads） | 已落盘 | 不登记（非"产物"），Non-goal 保持 |

## 6. 前端（R-15~R-17，零新增依赖）

- `views/front/components/ChatFilesPanel.vue`：右侧可折叠抽屉（宽度 280，绝对定位覆盖，不破坏现有布局）；进入会话/切换会话拉列表；`file_created` 事件插入行；每行：名+size+时间+来源 tag；操作：下载（带 token 的 fetch→blob，不走裸 `<a href>` 免 Referer 泄漏）、预览（白名单新窗 inline）、删除（confirm）；空态文案
- `api/core/agentFiles.ts` 新模块；`api-transport.ts` 事件分发补 `file_created` case
- 图片消息正文内联（R-13）：marked 渲染后 DOMPurify 白名单已放行 `<img>`，产物 URL 用 `/api/agent/files/{id}/download?inline=1` 由消息渲染约定（生成侧提示词引导，非本期强制）

## 7. 交付包联动

- compose backend 增 `PHOENIX_AGENT_WORKSPACE_ROOT=/app/uploads/agent-workspace`（属性 relaxed-binding 名实施期以日志验证）
- 备份口径：`uploads` 卷范围含产物（README §5 更新一句）
- nginx：无新前缀（全走 `/api`）；下载大文件沿用现有 300s 超时

## 8. 决策与已否方案

| # | 决策 | 被否替代 & 理由 |
|---|---|---|
| P1 | **v1.1.0 修订：轮末 workspace 扫描为主通路**（实测 LOCAL write_file 直落 {root}/{uid}/，扫描全覆盖且天然含 shell 直写；装饰器需 AgentScope 私有 NamespaceFactory，会破坏 USER 隔离语义） | 原「装饰器 tee」——实施否决（同上）；改 AgentScope 落点——否（外部 jar 不可改）。**代价：REMOTE 策略(Redis store)产物扫描不可见，本版本由 materialize 兜底**——requirements R-02 随之修订待追认 |
| P2 | workspace root 走配置默认不变 | 直接改常量到 uploads——否（裸机开发路径被污染） |
| P3 | tee 副本为下载权威，store_key 仅溯源 | 下载回源 Redis——否（AOF 清理/迁移即断供；双份存储代价可接受，50MB×量级小） |
| P4 | 报告「另存为文件」手动化 | 自动物化每条报告——否（消息正文已是可用呈现，自动落盘产生噪音文件；D6 决策沿袭） |
| P5 | 属主=会话 user_id 单点校验 | 引入组授权体系——否（会话本属个人，组维度属 Non-goals） |

## 9. 风险

1. **装饰器覆盖面**：AgentScope 内部若有旁路写（不经 AbstractFilesystem 直接落 workspace 的索引/marker 文件），面板会漏——以实测为准列「已知不采集」（`.index/`、marker 类显式黑名单）
2. **tee 与 shell 写盘不一致**：LOCAL 策略 shell 命令产物可能绕过 write 钩子（直落文件系统）→ T-05 用 shell `echo >file` 实测；漏网则补「会话结束扫 workspace 增量」兜底（黑名单过滤后登记，source=scan）
3. **SSE 断流时事件丢失**：面板以拉列表为准，事件只做增强（幂等：同 id 去重）
4. **大 tee 拖慢对话**：tee 在 boundedElastic 异步 + 失败仅 WARN 不阻断会话（下载一致性靠 DB+副本原子写：先写盘后插库，孤儿文件可接受）
5. **多实例 REMOTE 语义**：单实例交付成立；若未来多实例+LOCAL 并存，workspace 卷需共享存储——备注进 README，不在本期
