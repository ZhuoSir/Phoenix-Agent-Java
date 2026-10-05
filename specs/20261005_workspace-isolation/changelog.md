# Changelog: workspace-isolation

## T-04 完成（2026-10-05）迁移演练三段全绿
- 交付：WorkspaceMigrationRunner（ApplicationRunner；允许集=_legacy_shared ∪ DB 实时 runtimeKey；只 move 不删；名字冲突加时间戳绝不覆盖；允许集查询失败=宁可不迁；整体失败 WARN 不阻塞启动）
- 演练：①布假存量（888-fake-user/MEMORY.md+memory/note.md+legacy-probe.txt）→ 部署 → 根级只剩 _legacy_shared+agent-36，**16 项归档**（含真存量 88MB 用户树/散文件/框架缓存），假存量三件在档 ②**二次重启幂等**：日志"无存量条目（幂等零动作）"、_legacy_shared 零嵌套、agent-36 树完好 ③**下载对面证**：迁移前登记的老文件走 tee 副本下载 HTTP 200/3859 字节（workspace 搬家零影响实证）④verify=0

## T-02+T-03 完成（2026-10-05）全局首占+窄窗四证全绿
- 交付：existsByStoreKeyAnySession 全局首占方法；scanner 扫描根同源下沉（WorkspacePaths）+ storeKey 加 runtimeKey 前缀（防跨智能体相对路径误撞）；AgentFileController 补扫窗=最近 assistant 消息起（无消息不补扫）
- 夹具实测（免模型）：①S1 补扫登记 w02-new ✓ ②S2 补扫**空**（不收编 S1 已占——BUG-67 主症状毙）✓ ③窄窗证：mtime 窗外文件零登记 ✓ ④归属 DB 双侧=w02-s1 ✓；调用方枚举复核：仅轮末主通道+本抽屉入口两处，前后台共用单端点无漏
- 工装自记：清场核验 SQL 三犯 bigint||bigint 拼接错（删除本身已执行，::text 修正复核全零）

## T-01 完成（2026-10-05）factory 换根生效实证
- 交付：WorkspacePaths（runtimeKey/agentRoot 单一实现）+ factory .workspace 下沉 {root}/{runtimeKey} + runtimeKey 方法同源化委托；build 绿
- 目录实证：部署后 agent-36 对话轮 → 框架自建 `agent-36/agents/36/tasks/_sweep.marker` 落**专属子树**，根级零新增散文件 ✓；存量旧树（461671…/agents/fonts/散文件）原样未动（待 T-04 迁移归档）
- 环境注记：探针轮模型调用报 OpenAIException(HTTP transport error)=容器→模型 API 网络故障（既有 VPN 拐杖问题，非本改动）——R-02 暗号等模型依赖验证待外联恢复（T-05 批次）

## tasks v1.0.0（2026-10-05）—— 确认③通过，三重门全绿
- 用户「确认」；tasks v0.1.0→v1.0.0 已确认（陈卓）；三重门自检后切 feature/workspace-isolation（基点 v1.6.0），进入 Phase 4，首任务=T-01

## tasks v0.1.0（2026-10-05）
- Phase 3 拆解：T-01~T-06 三组（隔离主刀/迁移/收口）；BUG-67 双主刀分置 T-02(全局首占)+T-03(窄窗)；迁移演练含幂等二次重启+tee 副本对面证；暗号双向断言=R-02 一锤定音；改动面后端 5 文件、前端/DB 零改动

## plan v1.0.0（2026-10-05）—— 确认②通过
- 用户「确认」；plan v0.1.0→v1.0.0 已确认（陈卓）；进入 Phase 3 Tasks

## plan v0.1.0（2026-10-05）
- Phase 2 落盘：L-06 消费方勘察闭环（真实消费仅 3 处，8/10 文件零引用；**下载走 tee 副本 filesRoot 另一根→迁移零 DB 重写**——枚举省掉一台多余手术）；方案=factory 换根一行级+scanner 全局首占+补扫窄窗（最近 assistant 消息起）+迁移 runner（允许集=DB runtimeKey 实时查询，只 move 不删，幂等）；身份矩阵 6/被否案 4/风险 5；坑核对 15 条全过

## v1.0.0（2026-10-05）—— 确认①通过
- 用户「确认，按推荐」（Q1 归档重置/Q2 接受记忆重置）；存储布局图已向用户展示（卷不动、根下加 agent 层、_legacy_shared 归档）；进 Phase 2

## v0.1.0（2026-10-05）
- 初始化创建（用户口令「新建一个spec，解决bug 67-68」；BUG-67+BUG-68 同刃双修立项）
- 挂载: v1.6.0（用户现行指令「1.6.0 不冻结续收」，第三 spec；如需另起版本随时改判）
- 勘察终版入档：真通道={userId}/MEMORY.md 用户级记忆无 agent 维+根级散文件；已排除=会话转录/任务区/Postgres状态键/Redis(仅legacy)；BUG-68 根因栏精化回写 bugs.md
- requirements v0.1.0 草稿：R-01~R-05 + Q1/Q2（存量归档策略+记忆重置副作用确认）
