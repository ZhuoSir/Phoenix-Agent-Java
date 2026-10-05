# Plan: workspace-isolation（BUG-67+68 同刃双修）

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05
> 规范路由：code-backend=global / database=global（零 DDL、零 DB 重写）/ git-workflow=global（v1.6.0 第三 spec）

## 〇、坑核对（lessons 全 15 条 active 逐条过）

| L | 相交 | 规避 |
|---|---|---|
| L-06 多入口枚举 | ✅✅✅ | workspace 消费方**已全量枚举定性**：真实消费=3（factory 构建/scanner 扫描/fileService 下载）；十文件复核 8 个零引用；legacy 2 处自带默认根=对面。**下载走 tee 副本（filesRoot 另一根）→迁移零 DB 影响**——枚举避免了一次多余的 rel_path 重写手术 |
| L-01 补丁脱靶 | ✅ | 编辑带 assert 锚点；提交前验证在盘 |
| L-03 部署三段证明 | ✅ | 迁移演练看容器内真实目录树；前端不动 |
| L-07 假阳性验证 | ✅ | 暗号双智能体实测（A 记得/B 答不出/A 复认）；归属断言双侧（DB 行+面板 API） |
| L-14 表格闸 | ✅ | md 产物全过检 |
| 其余 10 条 | 弱相交 | 照常（日志不吞/确定性轮询/git add 逐文件…） |

**结论**：15 条全过，L-06 已在勘察期兑现（消费方定性完毕）。

## 一、采用方案

### 1.1 共享路径工具（防规则漂移）
新增 `WorkspacePaths`（agent-core util）：`runtimeKey(agentId, sn)`（=factory 现行规则：sn 有值用 sn，否则 agent-{id}）+ `agentRoot(root, key)`。factory 与 scanner **同源引用**，杜绝两处规则各写各的。

### 1.2 factory 换根（BUG-68 主刀，一行级）
`HarnessAgentFactory` `.workspace(Path.of(workspaceRoot))` → `.workspace(WorkspacePaths.agentRoot(workspaceRoot, runtimeKey(agent)))`。框架内部布局（{userId}/MEMORY.md、agents/*/sessions、根级散文件）原样下沉一层 → 记忆/产物/索引全隔离。MCP 变体（buildUncached 走同 factory）自动继承（A-4 兑现）。

### 1.3 scanner 适配 + 归属全局首占（BUG-67 主刀）
- `candidateAgentDirs` 根改走 `WorkspacePaths.agentRoot(root, runtimeKey(agentId, sn))`，内部候选布局不变（{userId} + agents/{keys}）
- **登记去重从"按会话"改"全局首占"**：同 storeKey 已被任何会话登记（del_flag=0）→ 跳过不再收编（BUG-60 的会话维度去重正是跨会话收编的机制性漏洞；轮末扫描是首占主通道，时序上天然归属正确轮次）
- **抽屉补扫窗口收窄**（AgentFileController）：windowStart 从 session.createTime 改为**该会话最近一条 assistant 消息的 create_time**（无消息→跳过补扫）；轮末扫描兜底不变——补扫只兜"最后一轮尾写"，不再兜全史

### 1.4 存量迁移器（R-04）
`WorkspaceMigrationRunner`（ApplicationRunner，agent-core）：
- 允许集 = `_legacy_shared` ∪ DB 全量智能体 runtimeKey（select id,sn from tbl_data_agent）∪ 不存在则跳过的框架缓存目录名（.cache/.fonts/.skills-cache/tmp_home/fonts/.agentscope——一并归档）
- 根下其余全部条目 **move**（非删除）入 `_legacy_shared/`；幂等（二次启动允许集全命中=零动作）；单条失败 WARN 继续，整体失败不阻塞启动（R-04）
- **零 DB 重写**：下载走 filesRoot tee 副本（勘察实证），workspace 搬家不碰 tbl_data_agent_file

### 1.5 对面（零改动清单）
legacy Java 自注册智能体（自带默认根 .agentscope/workspace）/ REMOTE 策略 Redis 存储 / PostgresAgentStateStore / tee 副本下载链 / 技能加载（Postgres 驱动）/ MCP 全链 / 断线续传。

## 二、涉及模块与数据流
```
构建: factory → workspace=root/{runtimeKey} → 框架在其内自建 {userId}/… 全隔离
扫描: artifactsTail(轮末,turnStart) + 抽屉补扫(窄窗) → scanner(root/{runtimeKey}/…) → 全局首占登记 → tee 副本 → 面板
迁移: 启动 runner → 非允许集条目 → _legacy_shared/（一次性，幂等）
```
修改：factory 1 行级 + scanner 2 处 + AgentFileController 补扫窗口 + 新增 WorkspacePaths/WorkspaceMigrationRunner 两件。前端零改动。

## 三、共享面身份矩阵
| 共享对象 | 既有身份 | 变更后预期 |
|---|---|---|
| HarnessAgentFactory 构建链 | ①全部库驱动智能体+MCP 变体 | 根下沉一层；构建其余参数零变化 |
| WorkspaceArtifactScanner | ①轮末扫描 ②抽屉补扫 | 根同步下沉；登记语义=全局首占（原按会话去重） |
| AgentFileController 补扫 | ①scan=true 全史窗 | 窄窗（最近 assistant 消息起）；无消息不补扫 |
| 下载链 | ①tee 副本 relPath | 零变化（filesRoot 不动，对面断言） |
| legacy 智能体 | ①默认根 workspace | 零变化（对面断言） |
| 存量数据 | ①88MB 共享根 | 整迁 _legacy_shared，只 move 不删，幂等 |

## 四、被拒绝的替代方案
| 方案 | 拒绝理由 |
|---|---|
| 会话级子目录隔离 | 框架布局非我控，会话维已在路径内（agents/agent-{id}/sessions），串扰源是用户级记忆不是会话 |
| 迁移+rel_path DB 重写 | tee 副本实证下载不依赖 workspace 原位——重写是多余手术（L-06 枚举的直接收益） |
| 存量 mtime 分拣归属 | Q1 用户已裁决归档重置；混合记忆文件不可拆 |
| stateStore 键加 agentId | 已实证跨 agent 碰撞=0，无病不动刀 |

## 五、风险与规避
| # | 风险 | 规避 |
|---|---|---|
| 1 | 迁移误搬运行中目录 | 允许集来自 DB 实时查询+启动时机在任何对话前；只 move 不删，误搬可人工搬回 |
| 2 | 全局首占改变补捞语义（尾写文件被别的会话先占） | 首占主通道=轮末扫描（时序归属正确）；补扫窄窗只兜本会话最后一轮 |
| 3 | sn 变更致目录漂移 | 库驱动智能体 sn 空→agent-{id} 永久稳定；有 sn 的 legacy 不走 factory |
| 4 | 磁盘峰值（归档+新树并存） | 88MB 量级无压力；归档只读不繁殖 |
| 5 | 记忆重置用户感知 | Q2 已裁决接受；RELEASE-NOTES/UPGRADE 显著提示（M3 件） |

## 六、测试策略（真做）
- **R-02 暗号双智能体实测**：A 会话"记住暗号 alpha"→ B 新会话问暗号（应答不出）→ A 复认（应答得出）——双向断言
- **R-03 归属**：同智能体 S1 产文件 → S2 面板不见（DB 行+API 双侧）；S1 面板仍在（首占不回溯）
- **R-04 迁移演练**：造 legacy 假数据（含 MEMORY.md/散文件）→ 重启 → 全量入 _legacy_shared、新根干净、二次重启幂等、下载老文件仍通（tee 副本）
- **对面五连**：legacy 智能体对话 / 技能加载调用 / 文件下载 / MCP 链抽查（麦当劳绑定+回环测试连接）/ verify 13/13
- 回归：typecheck 基线（前端零改动，引用值）、admin/front 对话各一轮
- 演练夹具全清

## 七、任务预告（Phase 3 细化）
T-01 WorkspacePaths 工具+factory 换根 → T-02 scanner 新根适配+全局首占去重 → T-03 补扫窗口收窄 → T-04 WorkspaceMigrationRunner+迁移演练 → T-05 E2E 矩阵（暗号/归属/对面五连/回归）→ T-06 台账收尾（含 BUG-67/68 翻已修复→已验证）。


---

## v1.1.0 增量方案：每会话独立目录（读写统一，R-06）

> 版本: v1.1.0 | 状态: 草稿 | 更新: 2026-10-05

### 一、坑核对（16 条 active 全过）
L-06 多入口：本次**必须枚举四面**——写（file 工具 / shell-exec / 脚本 / 下载）、读（file 工具 / 面板 API / 产物扫描 / 清理）；L-07：验收绑用户可见面（面板能看到+shell pwd 实测）；L-16：部署前查活跃轮；L-17：动组件前先证可达；L-18（新）：改工作区根布局必须枚举全部消费者。

### 二、采用方案
1. **会话级工作区根**：`{PHOENIX_AGENT_WORKSPACE_ROOT}/{agentKey}/{sessionId}`（agentKey=sn 非空否则 agent-{id}）——由 registry 键加入 sessionId、factory 接收会话工作区路径实现；**框架会在其下再拼一层 `{userId}`**（实测 file 工具落 `{root}/{uid}/`），故实际为 `{agentKey}/{sessionId}/{uid}/...`；面板/扫描**递归**扫会话目录即可满足"同处一个文件夹、全可见"
2. **shell cwd 统一**（BUG-79 主刀）：查框架 shell/exec 规格是否支持 cwd；支持→显式设置；不支持→**包装 shell 工具**（复用 PrefixedAgentTool 包装先例）注入 `cd {sessionDir} && `；`/app` 历史散落文件一次性清理
3. **读路径统一**（BUG-78 主刀）：产物扫描与面板 API 以会话目录为唯一根递归扫描；旧会话保留 legacy 只读兼容（多根扫描，新根优先）
4. **实例缓存**：registry 键 (agentId, fingerprint) → (agentId, sessionId, fingerprint)；LRU 容量维持
### 三、被否替代方案
| 方案 | 拒绝理由 |
|---|---|
| 只注入 shell cwd，file 工具维持 `{agent}/{uid}` | 不满足用户"读写统一"，会话间仍互见 |
| 进程级 chdir | 多会话并发下全局副作用，线程不安全 |
| 前端按文件名过滤伪装隔离 | 掩耳盗铃，服务端仍串（用户要的是真隔离） |
### 四、风险与规避
| # | 风险 | 规避 |
|---|---|---|
| 1 | 按会话构建 → 首轮延迟 +0.2~2s（含 MCP） | 已获用户确认接受；LRU 控内存；构建异步 boundedElastic |
| 2 | 框架再拼 `{uid}` 层与"会话文件夹"预期不符 | 面板递归扫描；文档写明实际层级；如需去掉 `{uid}` 层另立 BL |
| 3 | 存量文件"消失"于面板 | 多根扫描 legacy 只读兼容，实测旧会话仍可见 |
| 4 | shell 包装漏掉 pwsh/其他执行入口 | L-06 四面枚举：shell-local/pwsh/后台 job 三入口逐一核 |
### 五、测试策略
四项验收实测（同会话三工具三文件同处且面板全可见 / 跨会话互不可见 / shell pwd=会话目录 / 旧会话仍可见）+ 现有 verify 13/13 + 活跃轮门禁（L-16）
