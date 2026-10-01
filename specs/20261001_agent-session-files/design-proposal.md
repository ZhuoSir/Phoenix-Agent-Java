> 版本: v0.1.0 | 状态: 草案（方案征询中，未进三重确认门） | 更新: 2026-10-01

# 方案：智能体会话文件面板（生成文件可见 + 可下载）

## 一、现状事实（实测，方案立论基础）

1. 智能体产出文件有**两个去向，都不成体系**：
   - AgentScope workspace：`HumanInTheLoop.java:90` `.workspace(Path.of(".agentscope/workspace"))` → 容器内 `/app/.agentscope/workspace`（**没挂卷，容器重建即丢**；目录已确认存在）
   - 文件上传存储：`FileStorageProperties`（path=./uploads → `/app/uploads`，已挂卷）但那是**头像/上传**用的，不是智能体产物
   - 大量场景文件根本没落盘：如 diagram 技能把整篇 HTML 塞进**聊天气泡**（4.9 万字符那条），用户无法保存
2. 下载通道现状：`/uploads/**` 静态直出（WebConfig 资源映射）——**无鉴权、无会话归属概念**，且只服务上传目录
3. 会话消息表 `tbl_data_chat_message`（session_id/role/content），**没有文件维度的表和事件**

## 二、目标与非目标

**目标**：会话页右侧「文件」面板，列出该会话（及该智能体）产出的全部文件，支持下载/预览；产物持久化（跨容器重建不丢）；下载有鉴权。
**非目标**：文件在线编辑器、跨会话全文检索、OSS/对象存储接入（留钩子）、配额计费。

## 三、方案总览（三层）

```
[落盘层] AgentFileService（统一写入口）
   工具/技能/报告生成 产出文件 → 一律经它：写 {fileRoot}/{agentId}/{sessionId}/{uuid}_{name}
   + 登记 DB（新表 tbl_data_agent_file）+ SSE 广播 file_created 事件
   fileRoot 默认 /app/artifacts（docker 挂卷 artifacts:；非容器部署默认 ./artifacts）
[服务层] 两个端点（agent 域信封）
   GET  /api/agent/files?sessionId=     → 列表（名/大小/mime/时间/来源工具）
   GET  /api/agent/files/{id}/download  → Sa-Token 鉴权 + 会话归属校验 + Content-Disposition
   （预览 HTML/图片：同端点 ?inline=1 + CSP sandbox 头，防存储型 XSS）
[展示层] 会话页右侧可折叠「文件」抽屉
   进入会话拉列表；SSE 收到 file_created 实时插入；下载=a 标签带 token 端点
   HTML 报告类气泡下加「另存为文件」按钮（把消息内容物化成文件走同一入库口，解决 diagram 塞气泡问题）
```

## 四、关键设计点

| # | 决策 | 理由/代价 |
|---|---|---|
| D1 | 新表 `tbl_data_agent_file`（id/agent_id/session_id/file_name/store_key/size/mime/source/creator/del_flag/create_time），**DDL 走 releases 升级件序号**（v1.3.0_06 起排，不与 1.x 冲突） | 文件即数据资产，必须可迁移可回滚（rollback=drop 表，无损） |
| D2 | 存储=本地卷 + store_key 相对路径抽象；接口预留 `AgentFileStorage` SPI（local/oss 两实现位） | 不上 OSS 但随时可上；docker  compose 加 `artifacts` 卷（顺带把 `/app/.agentscope/workspace` 也挂卷——现状裸容器内丢文件，不管做不做面板都该修） |
| D3 | 路径安全：文件名清洗（去 `../`、控制字符）、canonical 前缀校验、扩展名黑名单（.sh/.exe 等只下不预览） | 防目录穿越；预览 mime 白名单 text/html、image/*、text/* |
| D4 | 鉴权=登录 + **会话属主或同组授权**（复用现有 group ACL 语义），下载走端点不走静态直出 | /uploads 静态直给是历史遗留（头像场景可接受），产物必须过鉴权 |
| D5 | 生命周期：默认永久保留 + 逻辑删除；清理策略放后台任务（30 天未访问 + 容量水位）——参数进 config，**首期只做手动删** | 避免首期背上 GC 复杂度 |
| D6 | 气泡内容物化（diagram HTML→文件）提供按钮但**不改自动行为**（模型爱输出 HTML 是提示词问题，另治理） | 侵入最小 |

## 五、待办清单（预估 3~4 人天，走 spec 流程则按 R/AC 重排）

- [ ] T1 `tbl_data_agent_file` DDL + 升级件（V 序号 + rollback 配对）
- [ ] T2 `AgentFileService`：落盘 + 登记 + 列表/下载/软删；路径清洗与安全校验（单测口径：canonical 断言）
- [ ] T3 两个 REST 端点 + 鉴权/归属校验 + inline 预览头
- [ ] T4 接入产出方：① python 分析产物 ② 报告/HTML（现有 report 落盘点）③ 技能执行若写 workspace 的文件扫描登记
- [ ] T5 SSE `file_created` 事件（HarnessEventMapper 加类型，前端已按 messageType 分发，兼容旧客户端）
- [ ] T6 前端会话页「文件」抽屉：列表/下载/预览/删除 + 实时插入 + 空态
- [ ] T7 气泡「另存为文件」按钮（D6）
- [ ] T8 compose 挂 `artifacts` + `workspace` 卷；docker 栈验证跨重建不丢
- [ ] T9 端到端：生成→列表→下载→down/up 存活；越权下载 403 断言
- [ ] T10 台账：spec 目录三件套走确认门；BL-19 销账

## 六、开放问题（需你定）

- Q1 文件可见范围：仅本会话？还是「该智能体全部产物」也列（面板加个 tab）？
- Q2 保留策略首期就要自动清理吗（D5 默认：不要）？
- Q3 大文件上限（单文件默认建议 50MB，与上传一致）？
- Q4 归属里程碑：v1.3.0（建议，MINOR 新功能）还是等不进版？
