# v1.3.0 配置变更清单

## 新增环境变量（compose backend 服务）
| 键 | 值（交付包） | 说明 |
|---|---|---|
| PHOENIX_AGENT_WORKSPACE_ROOT | /app/uploads/agent-workspace | 智能体 workspace 根迁入 uploads 卷（产物持久化） |
| PHOENIX_AGENT_FILES_ROOT | /app/uploads | 会话文件登记/下载根 |
不配置时默认 `.agentscope/workspace` 与 `./uploads`（裸机开发零变化）。

## compose / 镜像
- `.env` 的 IMAGE_TAG 升为 `v1.3.0`（pg/redis 等基础镜像键不变）
- 后端镜像新增工具链层：python3-pip + cairosvg + NotoSansCJKsc 字体（构建机需联网，交付机免装）

## nginx（phoenix.conf）
- index.html / SPA fallback `Cache-Control: no-cache`，hash 资源 300 天 immutable（BUG-39）
- `/api`、`/platform` 读写超时 300s→900s（BUG-55）；`/api/api/*` 返回 410（旧页止血，确认无旧客户端后可移除）

## 数据库
- 自动：migrator 按台账重放 V1.3.0_01~03（见 sql/，回滚成对置于 sql/rollback/）
- V1.3.0_02 含存量迁移段（幂等锚 MIGRATED_FROM_AGENT:{id}）：为每个有知识条目的智能体自动建库+绑定+条目回填

## 运行时配置（管理页操作项）
- 智能体「运行时配置」新增「工具迭代上限」（1~100，留空=框架默认）
