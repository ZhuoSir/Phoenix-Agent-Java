> 版本: v1.1.0 | 状态: 待重确认 | 更新: 2026-10-01 | 确认人: 陈卓 | 确认日期: 2026-10-01

# 需求：会话文件面板（agent-session-files）

## 背景与现状事实（2026-10-01 实测，方案立论基础）

用户诉求：**智能体生成文件后，能在会话页看到文件列表并下载**。现状：
- 智能体运行配置 `filesystemPolicy` 决定文件落点（`HarnessAgentFactory:115`）：
  - `LOCAL`（默认）→ `LocalFilesystemSpec` 隔离到 `/app/.agentscope/workspace`（`WORKSPACE` 常量，`HarnessAgentFactory:51`），USER 隔离
  - `REMOTE` → `RemoteFilesystemSpec` 走 **Redis**（`HarnessConfig:34`），且 shell 被禁用
- 头像/生成图另走 `FileStorageProperties`：path=`./uploads`→`/app/uploads`（已挂卷 `uploads`），url 前缀 `/uploads`（`WebConfig` 资源映射，**无鉴权静态直出**）
- **workspace 目录当前没挂卷**（compose 只挂了 applogs/uploads）→ 容器重建产物即丢
- 会话表 `tbl_data_chat_session(id,agent_id,title,status,is_pinned,user_id,…)`、`tbl_data_chat_message`；**无文件维度表、无文件产出事件**
- 前端聊天组件 `ChatMessages.vue`：`marked`+`DOMPurify`+`v-html`，`:deep(img)` 已具备（消息正文里放图片 URL 已能显示，实测）
- 交付包 nginx：v1.2.2 起按 `/api`、`/platform`、`/auth`、`/uploads` 前缀透传

**关键设计约束（plan 期必须解决）**：`LOCAL` 与 `REMOTE` 两套后端产物分散在不同介质。用户 2026-10-01 已决策产物**统一收敛到 `/app/uploads`**（Q2）——但 REMOTE 策略文件现落 Redis，收敛需重定向该策略落点或改其 spec，此为最大技术不确定点，plan 期先做可行性验证，若 AgentScope `RemoteFilesystemSpec` 无法改落点则回退为「面板统一读、写侧暂双介质」方案并回报。

## 决议（2026-10-01 用户拍板，作为需求基线）
- Q1 可见范围：**仅当前会话**
- Q2 存储：**统一收敛到 `/app/uploads`**（含 workspace 挂卷；REMOTE 落点见上方约束）
- Q3 限制：**50MB/文件 + 会话属主可下载 + 首期不自动清理**（手动删）
- Q4 归属：**v1.3.0**（MINOR 新功能，workspace 挂卷为其前置任务并入）

## 需求条款（EARS）

### 一、持久化前置
- **R-01** THE 交付包 compose SHALL 为 workspace 目录（`/app/.agentscope/workspace`）配置 named volume，使 LOCAL 策略产物跨容器重建持久（当前缺失，是面板可用性的硬前提）
- **R-02（v1.1.0 修订，待追认）** 实测证实 REMOTE 策略产物落 Redis store（无公开结构契约，反向工程不采用），轮末扫描不可见。THE 平台 SHALL 为 REMOTE 策略提供 **materialize 物化通道**（消息内容另存为文件）作为本版本兜底；store 级适配器列 v1.3.x 增强。本条由"不可降级"调整为"兜底降级"，**需需求方追认**

### 二、文件登记与存储
- **R-03** THE 平台 SHALL 新增文件登记能力：智能体一次会话中产出的文件（含工具/技能/报告写出的文件）统一经一个写入口落 `/app/uploads/{agentId}/{sessionId}/` 并在数据库登记（新表 `tbl_data_agent_file`：id/agent_id/session_id/file_name/store_key/size/mime/source/creator/del_flag/create_time）
- **R-04** THE 文件登记（`tbl_data_agent_file` 建表 + 必要索引）SHALL 以 Flyway 风格升级件交付（`releases/v1.3.0/sql/V1.3.0_NN__*.sql` + 配对回滚 `R1.3.0_NN__*.sql`），DDL 可重入、回滚无损既有数据
- **R-05** THE 文件存储 SHALL 抽象为可替换接口（默认本地 uploads 实现），为未来接入对象存储预留扩展点，但本版本不引入 OSS
- **R-06** THE 文件写入口 SHALL 对文件名做清洗（去 `../`、控制字符、非法分隔符），落盘路径做 canonical 前缀校验，杜绝目录穿越；扩展名黑名单（如 .sh/.exe）允许下载但禁止 inline 预览

### 三、服务接口
- **R-07** THE 平台 SHALL 提供文件列表接口 `GET /api/agent/files?sessionId=`，返回该会话产物文件（名称/大小/mime/产出来源/时间），**按当前登录用户+会话归属过滤**（未登录 401，跨用户会话 403）
- **R-08** THE 平台 SHALL 提供下载接口 `GET /api/agent/files/{id}/download`，经 Sa-Token 鉴权 + 会话属主校验后以 `Content-Disposition: attachment` 回传，正确设置 MIME 与中文文件名编码
- **R-09** WHERE 请求预览（`?inline=1`）且文件为 HTML/图片/文本类 THE 下载接口 SHALL 以 inline + CSP sandbox 响应头回传（防存储型 XSS）；非白名单类型拒绝 inline
- **R-10** THE 平台 SHALL 提供逻辑删除接口 `DELETE /api/agent/files/{id}`（会话属主或管理员），标 `del_flag` 而非物理删，首期不自动物理清理
- **R-11** THE 文件相关接口 SHALL 走 agent 域统一信封（`ReturnVo`：code/msg/data/success），与既有 `/api/agent/*` 一致

### 四、产出接入（让文件真的进面板）
- **R-12** THE 平台 SHALL 将现有「产物落 workspace / 生成文件」的产出方（数据库深度分析结果文件、报告 HTML、技能执行产物、图片生成如可用）接入统一写入口，使其文件出现在会话面板（接入清单以实测为准，plan 期逐产出方确认，不得遗漏已存在的产出路径）
- **R-13** THE 消息渲染 SHALL 支持会话内引用产物（`![](/api/agent/files/{id}/download)` 或图片内联），配合既有 `:deep(img)` 展示；非图片类文件以可点击下载链接呈现
- **R-14** WHEN 一次对话产出新文件 THE 后端 SHALL 通过既有 SSE 通道下发 `file_created` 事件（含文件 id/名称/大小），使前端文件面板实时更新；事件格式向后兼容（旧客户端忽略不报错）

### 五、前端界面
- **R-15** THE 会话页 SHALL 新增可折叠「文件」面板（右侧抽屉或等价），进入会话即拉 `GET /api/agent/files?sessionId=`，列表含名称/大小/时间/来源，每行提供下载、预览（受 R-09 约束）、删除操作，空态有引导文案
- **R-16** WHEN 面板收到 `file_created` 事件 THE 前端 SHALL 实时插入该文件行，无需刷新
- **R-17** THE 面板 SHALL 对超限文件（>50MB，R-18 上限）给出可读提示；下载走带 token 的接口而非裸 `/uploads` 静态路径（产物鉴权，不破坏 R-08）

### 六、限制与生命周期
- **R-18** THE 平台 SHALL 限制单文件 ≤50MB（超限拒写/告警），与技能上传上限一致；总存储不做首期配额（记 Non-goals）
- **R-19** THE 平台 SHALL 首期仅提供手动逻辑删除（R-10），不引入自动清理/GC；清理策略与容量水位留待后续需求
- **R-20** THE 交付包 README/UPGRADE SHALL 记录：workspace 与 uploads 两个卷的备份范围（产物属业务数据），以及升级需执行 `tbl_data_agent_file` 建表件

### 七、约束
- **R-21** WHILE 保持既有对话/技能/工具行为不回归 THE 本功能 SHALL 为增量（新表、新接口、面板；写入口改造不得改变现有产物的既有消费方式，若必须回改则回本 spec 重确认）
- **R-22** THE 本功能 SHALL 不引入新的外部重依赖（前端零新增依赖优先复用；后端如需库须在 plan 说明理由）

## 验收标准（AC）
- **AC-01**（R-01）交付栈重建（`compose down`+`up`，非 -v）后，升级前会话的产物文件仍在面板可下载
- **AC-02**（R-03/R-04）新环境跑 `V1.3.0_NN` 建表件零报错、可重入；回滚 `R1.3.0_NN` 后表消失、既有数据无损
- **AC-03**（R-07/R-08）用户 A 登录只能看到并下载自己会话的文件；用户 B 访问 A 的文件 id → 403；未登录 → 401
- **AC-04**（R-08）下载中文文件名不乱码、MIME 正确、`Content-Disposition` 存在
- **AC-05**（R-09）HTML/图片产物可 inline 预览且带 CSP sandbox；.sh 类拒绝 inline 只给下载
- **AC-06**（R-12/R-13）实际触发一次产出文件的对话（如报告生成/深度分析/技能），产物出现在当前会话面板并能下载
- **AC-07**（R-14/R-16）对话中产出文件时，面板无需刷新即收到 `file_created` 并插入
- **AC-08**（R-18）超 50MB 文件被拒并提示（构造一个超限用例）
- **AC-09**（R-10）删除后文件从列表消失（del_flag），DB 记录仍在，物理文件首期可保留
- **AC-10**（R-02/R-21）LOCAL 与 REMOTE 两种 filesystemPolicy 的会话产物均可在面板呈现下载；且既有对话链路不回归（老会话正常）
- **AC-11**（R-21/R-22）前端零新增依赖或 plan 已说明；全链走交付包（9080）真机验证留证

## Non-goals（明确不做）
- 不做文件在线编辑/富文本改写
- 不做跨会话/全局文件中心、跨用户共享、协作
- 不做对象存储（OSS）接入（仅留 R-05 扩展点）
- 不做自动清理/配额/生命周期 GC（首期手动删，R-19）
- 不做文件全文检索/版本历史
- 不在本版本引入 workspace 之外的新存储介质

## 假设
- A-01 单文件 50MB 上限、会话属主可下载 —— 用户 2026-10-01 确认（Q3）
- A-02 产物统一 `/app/uploads` —— 用户确认（Q2）；REMOTE→uploads 落点可行性为 plan 首任务（R-02 约束）
- A-03 归属 v1.3.0（MINOR）—— 用户确认（Q4）；workspace 挂卷作为前置并入
- A-04 首期数据量为演示/内网规模，无性能配额要求

## 待确认开放点（不阻塞草稿，但影响 plan/实现）
- 无（Q1~Q4 已决）。plan 期若 REMOTE 落点不可收敛将回报并可能触发本 spec 修订重确认
