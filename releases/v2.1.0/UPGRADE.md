# v2.1.0 升级说明（部署顺序 / 升级件 / 配置 / 验证 / 回滚）

> 状态：**已冻结（2026-10-25，M3 已汇总）** ｜ 适用：v2.0.x（含 hotfix/2.0.1）→ v2.1.0
> 交付形态：Docker Compose 交付包（amd64 / arm64）或本仓 `docker/` 目录就地升级

## 一、升级前检查

| 检查 | 命令/动作 | 通过标准 |
|---|---|---|
| 当前版本 | `docker compose -p phx210 ps images` / 查看 `IMAGE_TAG` | 确认是 v2.0.x |
| 备份 | `pg_dump -U phoenix phoenix > phoenix_v2.0.x_$(date +%F).sql` | 备份文件存在且非空 |
| 磁盘 | `df -h` | 至少 5GB 可用（镜像 + SQL + 备份） |
| 网络（内网场景） | **无需外网**：图标/报表资源已随前端产物本地化（见 RELEASE-NOTES §四之二） | — |

## 二、升级件执行顺序（SQL，PostgreSQL）

> 根目录 `sql/` 与 `releases/v2.1.0/sql/` 内容一致（sha256 已核对）。

| 顺序 | 文件 | 必跑 | 说明 |
|---|---|---|---|
| 1 | `V2.1.0_01__kb_embedding_binding_ddl.sql` | ✅ | 知识库向量模型绑定列（幂等 ALTER） |
| 2 | `V2.1.0_02__kb_rebuild_job_ddl.sql` | ✅ | 向量重建任务表（断点续跑/失败清单） |
| 3 | `V2.1.0_03__vector_tables_pk_ddl.sql` | ✅（条件） | 历史向量表补主键/唯一约束；**已有约束自动跳过**，重复 id 会告警跳过 |
| 4 | `V2.1.0_04__brand_config.sql` | ✅ | 品牌配置单行表 + 默认行 + 「外观设置」菜单行（幂等） |

执行方式（示例，容器内）：
```bash
for f in V2.1.0_01__kb_embedding_binding_ddl.sql V2.1.0_02__kb_rebuild_job_ddl.sql \
         V2.1.0_03__vector_tables_pk_ddl.sql V2.1.0_04__brand_config.sql; do
  docker exec -i phx210-postgres-1 psql -U phoenix -d phoenix < "sql/$f"
done
```
**幂等性**：四件均可重复执行（重跑不产生重复行/重复约束）。

## 三、配置变更（详见 `config/changes.md`）

必读三项默认值变更：附件上限 **5→50**、首帧超时 **180→600**、熔断 **3（累计）→5（连续）**。
新增：ollama `think`、附件分批 6 项、品牌 2 项。全部可环境变量覆盖；**短横线去掉**再大写。

## 四、部署顺序

1. 停旧服务：`docker compose -p phx210 down`（保留卷）
2. 载入镜像：`docker load -i phoenix-2.1.0-<arch>.tar`
3. 执行 SQL（第二节，按顺序）
4. 起服务：`docker compose -p phx210 up -d`（首次启动 backend 会等 migrator 完成）
5. 等健康：`docker ps` 三容器 `healthy`（backend/nginx/postgres）

## 五、升级后验证（逐项可判定）

| # | 验证 | 方法 | 期望 |
|---|---|---|---|
| 1 | 登录 | `POST /api/privilege/auth/login`（admin） | 200 且返回 token |
| 2 | 首页 | `GET /` | 200 |
| 3 | 品牌接口（匿名） | `GET /platform/brand-config` | 200，含 4 个公开字段；默认 `Phoenix Agent / #006BE6` |
| 4 | 图标本地化 | `GET /icons/lucide.json` | 200 `application/json` |
| 5 | 图标缺失行为 | `GET /icons/nope.json` | **404**（不是 index.html） |
| 6 | 报表 vendor | `GET /js/vendor/echarts.min.js` | 200 |
| 7 | 附件上限 | `GET /api/chat/attachment/limit` | `{maxCount:50}` |
| 8 | 对话思考 | 发一轮对话 | SSE 出现 `thinking` 帧（ollama 模型） |
| 9 | 长文档分批 | 上传 >24000 字文档并提问 | 出现 `正在处理第 x/N 批…` 与结束标注 |
| 10 | 外观设置 | 登录 → 系统管理 → 外观设置 | 页面可打开、可保存、刷新生效 |
| 11 | 向量门禁 | `scripts/vector_acceptance.sh`（如有） | R-09 五项 PASS |

## 六、回滚

| 层级 | 动作 |
|---|---|
| 应用 | `docker compose -p phx210 down` → `docker load` 旧镜像 → 改 `IMAGE_TAG` → `up -d` |
| SQL（按需） | 执行对应 rollback：`V2.1.0_04__brand_config_rollback.sql`（删表）→ `…_03_…_rollback.sql` → `…_02_…` → `…_01_…`（**逆序**） |
| 数据 | 用第一节的 `pg_dump` 备份恢复（`psql < backup.sql`） |
| 注意 | 04 回滚只删表；菜单行需另 DELETE（`tbl_privilege_module` id=`b7a1c2d3e4f5061728394a5b6c7d8e9f`） |

## 七、已知限制（带缺陷冻结口径 A）

9 条 P1 未随本版修复，**详见 `RELEASE-NOTES.md` §四之三**，随 `BL-75` 在 v2.1.1 排期。
其中对升级影响最大的是 **BUG-200**（前台聊天对 sn 为空的平台智能体 500）——内网升级后建议前台只使用已配置 sn 的智能体。
