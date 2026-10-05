# Tasks: workspace-isolation

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05

## 组 1：隔离主刀

- [x] T-01 WorkspacePaths 共享工具 + factory 换根：runtimeKey(agentId,sn)/agentRoot(root,key) 单一实现；HarnessAgentFactory .workspace 下沉 {root}/{runtimeKey}（BUG-68 主刀；MCP 变体经 buildUncached 自动继承）
  关联: R-01, R-02
  依赖: 无
  验证方式: 编译绿；容器内目录实证（新对话后 root 下出现 agent-{id}/ 树，根级零新增散文件）；runtimeKey 规则与 factory 原 name() 逐字一致（同源引用断言）
  验收标准: 目录树实证 + 同源引用（scanner 复用在 T-02 验证）

- [x] T-02 scanner 新根适配 + 全局首占去重：candidateAgentDirs 根走 WorkspacePaths.agentRoot；登记 storeKey 去重从按会话改全局（任何会话已登记→跳过）（BUG-67 主刀一）
  关联: R-01, R-03
  依赖: T-01
  验证方式: 同智能体双会话实测——S1 轮末产物登记 S1；S2 打开面板/轮末扫描**不收编** S1 文件（DB 行归属+面板 API 双侧取证）；S1 面板自身文件仍在（首占不回溯）
  验收标准: 归属双侧取证全绿

- [x] T-03 抽屉补扫窄窗：AgentFileController windowStart 从 session.createTime 改最近 assistant 消息 create_time（无消息→跳过补扫，轮末兜底）（BUG-67 主刀二）
  关联: R-03
  依赖: T-02
  验证方式: 构造 S1 老会话（创建时间早于 S2 产物）→ S1 抽屉 scan=true → 不收编 S2 之后产生的他会话文件；本会话最后一轮尾写文件仍可补捞（窄窗有效性正向断言）
  验收标准: 反向不收编+正向可补捞双证

## 组 2：迁移

- [x] T-04 WorkspaceMigrationRunner：启动迁移（允许集=_legacy_shared ∪ DB 全量 runtimeKey；其余条目 move 入 _legacy_shared/；幂等；单条失败 WARN 继续、整体失败不阻塞启动）
  关联: R-04
  依赖: T-01
  验证方式: **迁移演练**——容器内造 legacy 假数据（MEMORY.md/散文件/假 userId 树）→ 重启 → 假数据全量入 _legacy_shared、新根干净、服务 healthy；**二次重启幂等**（_legacy_shared 不重复嵌套、允许集条目不动）；老文件下载仍通（tee 副本对面证）
  验收标准: 演练三段全绿 + 幂等实证

## 组 3：收口

- [x] T-05 E2E 矩阵：暗号双智能体实测（A 记 alpha→B 答不出→A 答得出）+ 对面五连（legacy 对话/技能加载/文件下载/MCP 链抽查含麦当劳绑定与回环测试连接/verify 13-13）+ admin/front 对话回归 + 夹具全清
  关联: 全部 R
  依赖: T-02, T-03, T-04
  验证方式: 如上逐项留档（暗号双向断言为 R-02 一锤定音证）
  验收标准: 全矩阵绿、现场零残留

- [x] T-06 台账收尾：completion/artifacts/MILESTONE 第三需求行/BUG-67、BUG-68 翻已修复→已验证(v1.6.0在途)/记忆重置提示移交 M3 RELEASE-NOTES+UPGRADE
  关联: 流程
  依赖: T-05
  验证方式: completion 与勾选核对；bugs 状态机与证据对齐
  验收标准: 台账五件齐、偏差与去向写明

## 自检
- R 覆盖：R-01(T-01/02) R-02(T-01/05) R-03(T-02/03) R-04(T-04) R-05(T-05 对面五连)——全覆盖无孤儿
- 依赖无循环；改动面=后端 5 文件（2 新 3 改），前端/DB 零改动
- 共享面验证规则：身份矩阵（plan §三）6 对象对面断言分布 T-01(构建链)/T-02·03(扫描语义)/T-04(存量+下载)/T-05(legacy+技能+MCP+verify)
- 粒度：6 任务各一次会话可完成可独立验证


---

## v1.1.0 增量任务（R-06）

> 版本: v0.2.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05

- [ ] T-11 会话级工作区根：registry 键加 sessionId + factory 接收会话工作区 + WorkspacePaths 增 `sessionRoot(root, agentKey, sessionId)`
  关联: R-06 | 依赖: 无
  验证: 新会话首轮构建日志含会话路径；file 工具落点实测=`{agentKey}/{sessionId}/{uid}/`
  验收: 落点符合约定且日志可观测
  进展(2026-10-05): 代码已落地并编译通过（commit 3b385fb）；**运行时实测待部署**
- [ ] T-12 shell cwd 统一（BUG-79）：先查框架 shell/exec 规格 cwd 支持；支持则配置，否则包装工具注入 `cd {sessionDir} && `（shell-local/pwsh/后台 job 三入口均覆盖）
  关联: R-06 | 依赖: T-11
  验证: 会话内 shell 执行 `pwd` 输出=会话目录；`echo x > probe_sh.txt` 落会话目录
  验收: 三入口实测一致
  进展(2026-10-05): 查证框架**支持**（`LocalFilesystemSpec.project` = shellCwd，唯一 shell 入口 ShellExecuteTool）→ 走配置路线，弃包装注入；**pwd 实测待部署**
- [ ] T-13 读路径统一（BUG-78）：产物扫描+面板 API 以会话目录为根递归；legacy 多根只读兼容
  关联: R-06 | 依赖: T-11
  验证: 面板列出同会话三工具产物；跨会话列表为空；旧会话仍可见
  验收: 三场景实测通过
  进展(2026-10-05): 代码已落地并编译通过；顺带揪出 BUG-78 真根因（扫描根用请求 sn 而写入根用库中 sn）；**三场景实测待部署**
- [ ] T-14 `/app` 散落清理 + 迁移说明：清理容器内 `/app` 历史产物（登记去向），文档写明存量兼容策略
  关联: R-06 | 依赖: T-12
  验证: 清理前后文件清单对照，无业务文件误删
  验收: 清单留档
  进展(2026-10-05): 清单已取（见 changelog）；`/app` 零业务产物，无需删除动作，仅留档
- [ ] T-15 E2E 四项验收 + 回归：verify 13/13、跨会话隔离、旧会话可见、MCP/技能链不回退
  关联: R-06/全 | 依赖: T-11~T-14
  验证: 四项验收实测留档（用户可见面为准）
  验收: 全绿
- [ ] T-16 台账收口：BUG-78/79 翻已验证、completion 增量、MILESTONE 备注、待发批次清单更新
  关联: 流程 | 依赖: T-15
