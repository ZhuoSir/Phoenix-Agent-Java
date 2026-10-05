# Tasks: workspace-isolation

> 版本: v1.0.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-05 | 更新: 2026-10-05

## 组 1：隔离主刀

- [x] T-01 WorkspacePaths 共享工具 + factory 换根：runtimeKey(agentId,sn)/agentRoot(root,key) 单一实现；HarnessAgentFactory .workspace 下沉 {root}/{runtimeKey}（BUG-68 主刀；MCP 变体经 buildUncached 自动继承）
  关联: R-01, R-02
  依赖: 无
  验证方式: 编译绿；容器内目录实证（新对话后 root 下出现 agent-{id}/ 树，根级零新增散文件）；runtimeKey 规则与 factory 原 name() 逐字一致（同源引用断言）
  验收标准: 目录树实证 + 同源引用（scanner 复用在 T-02 验证）

- [ ] T-02 scanner 新根适配 + 全局首占去重：candidateAgentDirs 根走 WorkspacePaths.agentRoot；登记 storeKey 去重从按会话改全局（任何会话已登记→跳过）（BUG-67 主刀一）
  关联: R-01, R-03
  依赖: T-01
  验证方式: 同智能体双会话实测——S1 轮末产物登记 S1；S2 打开面板/轮末扫描**不收编** S1 文件（DB 行归属+面板 API 双侧取证）；S1 面板自身文件仍在（首占不回溯）
  验收标准: 归属双侧取证全绿

- [ ] T-03 抽屉补扫窄窗：AgentFileController windowStart 从 session.createTime 改最近 assistant 消息 create_time（无消息→跳过补扫，轮末兜底）（BUG-67 主刀二）
  关联: R-03
  依赖: T-02
  验证方式: 构造 S1 老会话（创建时间早于 S2 产物）→ S1 抽屉 scan=true → 不收编 S2 之后产生的他会话文件；本会话最后一轮尾写文件仍可补捞（窄窗有效性正向断言）
  验收标准: 反向不收编+正向可补捞双证

## 组 2：迁移

- [ ] T-04 WorkspaceMigrationRunner：启动迁移（允许集=_legacy_shared ∪ DB 全量 runtimeKey；其余条目 move 入 _legacy_shared/；幂等；单条失败 WARN 继续、整体失败不阻塞启动）
  关联: R-04
  依赖: T-01
  验证方式: **迁移演练**——容器内造 legacy 假数据（MEMORY.md/散文件/假 userId 树）→ 重启 → 假数据全量入 _legacy_shared、新根干净、服务 healthy；**二次重启幂等**（_legacy_shared 不重复嵌套、允许集条目不动）；老文件下载仍通（tee 副本对面证）
  验收标准: 演练三段全绿 + 幂等实证

## 组 3：收口

- [ ] T-05 E2E 矩阵：暗号双智能体实测（A 记 alpha→B 答不出→A 答得出）+ 对面五连（legacy 对话/技能加载/文件下载/MCP 链抽查含麦当劳绑定与回环测试连接/verify 13-13）+ admin/front 对话回归 + 夹具全清
  关联: 全部 R
  依赖: T-02, T-03, T-04
  验证方式: 如上逐项留档（暗号双向断言为 R-02 一锤定音证）
  验收标准: 全矩阵绿、现场零残留

- [ ] T-06 台账收尾：completion/artifacts/MILESTONE 第三需求行/BUG-67、BUG-68 翻已修复→已验证(v1.6.0在途)/记忆重置提示移交 M3 RELEASE-NOTES+UPGRADE
  关联: 流程
  依赖: T-05
  验证方式: completion 与勾选核对；bugs 状态机与证据对齐
  验收标准: 台账五件齐、偏差与去向写明

## 自检
- R 覆盖：R-01(T-01/02) R-02(T-01/05) R-03(T-02/03) R-04(T-04) R-05(T-05 对面五连)——全覆盖无孤儿
- 依赖无循环；改动面=后端 5 文件（2 新 3 改），前端/DB 零改动
- 共享面验证规则：身份矩阵（plan §三）6 对象对面断言分布 T-01(构建链)/T-02·03(扫描语义)/T-04(存量+下载)/T-05(legacy+技能+MCP+verify)
- 粒度：6 任务各一次会话可完成可独立验证
