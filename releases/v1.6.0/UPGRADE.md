# v1.6.0 升级指南

> 本版**有 DDL**（3 件，迁移器自动执行，幂等）+ **工作区布局变更**（有既定副作用，见 A.3）
> 冻结基线 `16225cf`；升级前建议 `pg_dump` 备份（迁移器不删数据，但布局变更不可自动回滚）

## A. 既有部署（v1.5.0 及更早 → v1.6.0）

```bash
cd <repo> && git fetch && git checkout v1.6.0
cd docker
JRE_BASE_IMG=<你的 JRE 基座> docker compose build backend nginx      # 发布包用户直接用包内镜像
docker compose run --rm migrator                                     # 自动跑 V1.6.0_01~03（已应用的会 skip）
docker compose up -d backend nginx
sh scripts/verify.sh                                                 # 13 断言全绿
```

### A.1 迁移件（3 件，逆序回滚件已配对）

| 序号 | 文件 | 类型 | 内容 |
|---|---|---|---|
| 01 | `V1.6.0_01__mcp_plugin_tables.sql` | DDL | MCP 三表：`tbl_mcp_server` / `tbl_data_agent_mcp_info` / `tbl_platform_group_mcp_info` |
| 02 | `V1.6.0_02__mcp_plugin_menu_acl_dml.sql` | DML | 插件管理菜单与 ACL（md5 常量 id + NOT EXISTS 防重） |
| 03 | `V1.6.0_03__runtime_compaction_columns.sql` | DDL | 运行配置加 `compaction_trigger_tokens` / `compaction_keep_messages` / `tool_result_max_chars`（nullable） |

**全新库全序重放已实测**：`sequences → all_data → seed_admin → seed_runtime_agents → V1.2.0_01~05 → V1.3.0_01~03 → V1.6.0_01~03` 全绿（结构断言：三列=3、三表=3、插件菜单=1、台账 12 行）；**回滚逆序实测零残留**（三列=0、三表=0、菜单=0）。

### A.2 应用面变化（要用新版镜像）
后端：`HarnessTurnManager`（看门狗/帧合并/join/状态修复）、`HarnessStateRepairService`（新）、`HarnessAgentFactory`/`Registry`（会话级工作区+上下文治理）、`McpMountService`、`WorkspacePaths`（新）、`WorkspaceMigrationRunner`（新）、`WorkspaceArtifactScanner`、`AgentFileService`、`GlobalExceptionHandler`（BUG-64）、运行配置四层（实体/DTO/VO/Service）。
前端：`api/core/graph.ts`（join 追流）、`components/run/index.vue`（状态分片/续渲/时间归属）、`views/front/*`（增量渲染/思考块）、插件管理页、运行配置三输入。

### A.3 ⚠️ 布局变更的影响（必读）

1. **记忆与技能缓存按会话隔离 → 长期记忆从新开始**：工作区由 `{root}/{agentKey}` 变为 `{root}/{agentKey}/{sessionId}`（框架内再拼 `{uid}`）。旧数据**不删**，启动迁移器会把无法归属的条目归档到 `{workspace}/_legacy_shared/`（只 move 不删，冲突名加时间戳）。
   对应 ROLLBACK 提示：**回退到 v1.5.0 时布局不会自动还原**——旧会话在新目录里，记忆需要人工从 `_legacy_shared/` 搬回 `{root}/{agentKey}/{uid}/`。
2. **shell 工作目录变更**：由容器根 `/app` 改为**会话目录**（BUG-79 修复）。会话内 shell 产物从此落会话目录并进文件面板；`/app` 下历史逃逸产物**不随迁移**（它们在容器层，重建即失，本就不持久）。
3. **存量文件面板噪音不会自动清理**：扫描器已加过滤（技能缓存 `.skills-cache`、依赖树 `.pylibs`、工具结果 `large_tool_results`、`call_*` 占位等），但**升级前产生的历史噪音行仍在库里**。如需清理（逻辑删、可回滚），在库上执行：
   ```sql
   BEGIN;
   UPDATE tbl_data_agent_file SET del_flag = 1
   WHERE del_flag = 0 AND (
        session_id NOT IN (SELECT id FROM tbl_data_chat_session)
     OR store_key LIKE '%/.pylibs/%'      OR store_key LIKE '%/.skills-cache/%'
     OR store_key LIKE '%/.agentscope/%'  OR store_key LIKE '%/.index/%'
     OR store_key LIKE '%/sessions/%'     OR store_key LIKE '%/tasks/%'
     OR store_key LIKE '%/memory/%'       OR store_key LIKE '%/large_tool_results/%'
     OR file_name LIKE 'call\_%');
   COMMIT;
   ```
   回滚：同谓词 `SET del_flag = 0`（幂等）。dev 栈实测：4643 存活 → 清理 2499 → 剩 2144，真产物零误伤。
4. **旧会话仍可见**：面板按会话查库，历史登记行不受影响；会话目录缺失时的回落扫描有时间窗与他会话目录排除双重约束，不会把老产物灌进新会话。

### A.4 回滚（库）
```bash
# 逆序执行，逐件零残留（已在临时库实测）
psql -v ON_ERROR_STOP=1 -f releases/v1.6.0/sql/rollback/R1.6.0_03__runtime_compaction_columns.sql
psql -v ON_ERROR_STOP=1 -f releases/v1.6.0/sql/rollback/R1.6.0_02__mcp_plugin_menu_acl_dml.sql
psql -v ON_ERROR_STOP=1 -f releases/v1.6.0/sql/rollback/R1.6.0_01__mcp_plugin_tables.sql
```
镜像回退到 v1.5.0 时**必须先回滚 03**（否则旧应用读不到新列不报错，但运行配置页会忽略三列）——01/02 回滚会丢弃 MCP 配置数据（等价于放弃 MCP 功能），按需执行。

## B. 新机器安装
- **Windows**：源码 zip → 管理员 PowerShell → `.\docker\scripts\bootstrap.ps1`（全自动：WSL2/引擎/打包/安装/收据）
- **Linux**：`bash docker/scripts/bootstrap.sh`；**mac**：先装 Docker Desktop/Colima 再 bootstrap.sh
- **离线内网机**：有网机 `package.sh` 出 tar.gz → 拷入 → 解包 → `bash install.sh [--offline]`
- 本版**无需改安装脚本**（v1.5.0 的 bootstrap/ctl 原样可用）

## C. 升级既有安装（包对包）
```bash
tar -xzf phoenix-v1.6.0.tar.gz && cd phoenix-v1.6.0
bash install.sh --env-from <旧安装目录>/docker/.env    # 密码/端口承接，数据卷保留，IMAGE_TAG 自动跟版
```
安装器会重跑 `migrator`（幂等 skip）+ 拉起新镜像。

## D. 配置（可选覆盖）
新增 5 键全部有默认值，**不配即可用**；需要覆盖时写入 `docker/.env`（详见 `config/changes.md`）。

## E. 验证
```bash
sh docker/scripts/verify.sh        # 13 断言全绿（冻结基线轮实测 PASS）
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:<端口>/echo/ok    # 200
```
- 全新安装首次 [7] 出现 WARN 属预期（首轮对话后自动齐）。
- 长轮自检：起一个长任务 → 运行中刷新页面 → 应**接着流**（而非每 5 秒跳一次）；文件面板应出现本会话产物。
