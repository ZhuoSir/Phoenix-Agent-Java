> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-06 | 挂载: v1.7.0（在途）

# 技术方案：kb-access-isolation（知识库访问面隔离与提示面对齐）

> **待编**：本文件在 requirements **确认①** 之后编写（Phase 2）。
> 届时必含：① 坑核对节（逐条过 `specs/_project/lessons.md` 全部 active 坑，含本轮新增 L-34~L-37）；
> ② 采用方案与被拒替代方案；③ 共享面身份矩阵（`/uploads/**` 的既有使用方逐个枚举：知识库原件、会话工作区、头像…）；
> ④ 数据迁移与回滚设计（drill 先行）；⑤ 风险与规避。

## 待 Plan 阶段解决的已知技术未知

1. **框架提示段能否关闭/覆盖**：`agentscope-harness:2.0.0` 的 "## Domain Knowledge" 段是否由 builder 配置控制；
   若不可关闭，则只能"物化 `knowledge/`"或"以强指令覆盖"（需实测取证）。
2. **移出可读面的落点**：凭证/文件位置从 `/app/uploads/data-agent/agent-knowledge` 移到何处，
   既能被后端读取、又不被 agent 运行时（shell/文件工具）读到；是否需改 `FileStorageProperties.path` 或新增独立挂载。
3. **shell 护栏钩子点**：框架 `LocalFilesystemWithShell` 是否有可插拔的命令拦截点；若无，评估在工具层包装/白名单实现。
