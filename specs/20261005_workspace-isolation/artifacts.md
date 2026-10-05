# Artifacts: workspace-isolation

> 版本: v1.0.0 | 更新: 2026-10-05

## SQL
零 DDL/零 DML/零 DB 重写（下载走 tee 副本实证，workspace 搬家不碰 tbl_data_agent_file）。

## 后端（5 文件：2 新 3 改，前端零改动）
- 新增 phoenix-agent-core/util/WorkspacePaths.java（runtimeKey/agentRoot 单一实现，factory/scanner/migration 三方同源）
- 新增 phoenix-agent-core/service/file/WorkspaceMigrationRunner.java（ApplicationRunner 归档迁移：允许集 DB 实时查/只 move 不删/冲突加时间戳/失败不阻塞/幂等）
- 改 harness/factory/HarnessAgentFactory.java（.workspace 下沉 {root}/{runtimeKey}；runtimeKey 委托 WorkspacePaths）
- 改 service/file/WorkspaceArtifactScanner.java（扫描根同源下沉；storeKey 加 runtimeKey 前缀防跨 agent 误撞）
- 改 service/file/AgentFileServiceImpl.java（existsByStoreKeyAnySession 全局首占）
- 改 agent-rest/controller/AgentFileController.java（补扫窄窗=最近 assistant 消息起，无消息不补扫）

## 数据/现场（dev 栈已生效）
- /app/uploads/agent-workspace/_legacy_shared/ ← 88MB 存量归档（16 项）
- /app/uploads/agent-workspace/agent-36/ ← 新结构首例（随用随建）

## 文档/台账
- requirements/plan/tasks 均 v1.0.0 已确认；changelog 全程；completion/artifacts 本件
- bugs：BUG-67/68 翻已验证(v1.6.0在途)；MILESTONE 第三需求行；记忆重置提示移交 M3 RELEASE-NOTES/UPGRADE
