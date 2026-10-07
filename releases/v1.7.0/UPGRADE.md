# v1.7.0 升级操作与回滚（UPGRADE）

> 冻结日期 2026-10-06 | 适用：从 v1.6.0 升到 v1.7.0

## 一、升级前置

- [ ] **备份**：DB 全量 dump（`pg_dump`）+ `/app/uploads` 目录快照（含知识库原件）
- [ ] 确认迁移台账状态（**注意**：`tbl_phoenix_release` 当前**未含** V1.7.0_01~04，详见 `checklist.md` 与 BUG-115）
- [ ] 确认维护窗口（本次升级**会重启 backend**，会话在进程内存 ⇒ 在线用户**全部掉线需重登**）

## 二、升级步骤（生产实测路径，2026-10-06）

1. **后端**（含本次全部代码）：
   ```bash
   JAVA_HOME=<jdk> mvn -q -Dmaven.repo.local=.mvn-home package -DskipTests -Dspring-javaformat.skip=true \
     -pl phoenix-admin/phoenix-admin-manager -am
   cp phoenix-admin/phoenix-admin-manager/target/phoenix-admin.jar docker/.stage/phoenix-admin.jar
   JRE_BASE_IMG=docker.elastic.co/elasticsearch/elasticsearch:9.2.5 JAVA_BIN=/usr/share/elasticsearch/jdk/bin/java \
     docker compose -f docker/docker-compose.yaml build backend
   docker compose -f docker/docker-compose.yaml up -d backend
   ```
   实测：`backend started=2026-10-06T11:43:18Z health=healthy`、`/echo/ok=200`
2. **前端**（admin-ui）：
   ```bash
   pnpm -F @vben/web-ele build
   rm -rf docker/.stage/dist && cp -R web-frontend/apps/admin-ui/dist docker/.stage/dist
   docker compose -f docker/docker-compose.yaml build nginx && docker compose -f docker/docker-compose.yaml up -d nginx
   ```
3. **数据库**：本版本**无新增 DDL**（`V1.7.0_01~04` 早前已应用，库中对象与菜单行实测在）；**若为全新环境**，按 `sql/V1.7.0_01 → 04` 顺序执行。
4. **验证**（升级后必跑）：
   ```bash
   sh docker/scripts/verify.sh            # 期望 13/13 PASS
   curl -sI http://127.0.0.1:9080/uploads/data-agent/agent-knowledge/<uuid>.md   # 期望 404
   curl -sI http://127.0.0.1:9080/uploads/data-agent/avatars/<file>.jpg          # 期望 200
   # 受控接口：未登录=业务码 401；无权=403 无正文；属主/管理员=200+正文
   ```

## 三、配置与环境变量

| 变量 | 默认 | 说明 |
|---|---|---|
| `PHOENIX_KB_PATH_GUARD` | `observe` | 知识库路径护栏模式；`enforce` 会摘除命中禁止路径的工具调用 —— **启用前须先解决 BUG-113（误伤面）** |
| （其余沿用 v1.6.0） | — | 数据源/Redis/工作区根等 |

## 四、回滚

1. **代码**：镜像回退到上一版本 tag → `up -d backend`（会话失效一次）；
2. **数据库**：一般**无需**回滚（本版本零新增 DDL；`V1.7.0_01~04` 属 v1.7.0 内容，若要回到 v1.6.0 需按
   `sql/rollback/V1.7.0_04 → 01` **逆序**执行）；
3. **⚠️ 风险告知**：回滚代码会**恢复 BUG-108 的暴露面**（`/uploads/**` 可无鉴权下载知识库原件与会话工作区文件）。

## 五、升级影响

- **中断**：一次 backend 重启（本版本实测 `11:43:18Z`），**全员重登**；nginx 重建不影响会话；
- **兼容**：前台 `POST /auth/login` 保留；旧账号 id 已按映射表迁移（`tbl_unified_account_map`）；
- **人工动作**：无（除上述维护窗口告知）。
