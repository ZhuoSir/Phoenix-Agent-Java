# v2.0.0 升级操作与回滚（UPGRADE）

> 冻结日期 2026-10-10 | **适用：从 v1.7.0 升到 v2.0.0** | 部署基线 = tag `v2.0.0`
> 本版为 **MAJOR·破坏性**升级：删 3 张组织表 + 用户表组织列 + 三方平台配置表，菜单与权限模型重建。
> **执行序条数 = `sql/` 正向件数 = 16**，回滚 = 反序 16 项。

---

## 一、升级前置（逐项确认）

- [ ] **数据库全量备份**：`pg_dump -U phoenix -d phoenix -Fc -f phoenix_pre_v2.0.0.dump`
- [ ] **`/app/uploads` 卷快照**（含知识库原件与会话工作区；本版不动其结构，但回滚需完整现场）
- [ ] **维护窗口**：升级**会重启 backend**，会话与工具调用状态在进程内存 ⇒ **在线用户全部掉线需重登**
- [ ] **确认无在跑智能体会话**（教训 L-87：重启会打断进行中的会话 `AgentShuttingDownException`，且旧容器日志随容器销毁）：
      `select count(*) from tbl_data_chat_message where metadata->>'status'='generating' and create_time > now() - interval '30 minutes';` → 期望 **0**
- [ ] **迁移台账核对**：确认 `tbl_phoenix_release` 中 v2.0.0 各件状态（注意 BUG-115：该表曾漏记 V1.7.0_01~04）
- [ ] **镜像基座确认**：本版发布镜像基于 **UBI9 + Elasticsearch JDK**（`JAVA_BIN=/usr/share/elasticsearch/jdk/bin/java`）。`.env` 中的 `JRE_BASE_IMG=eclipse-temurin:21-jre-alpine` **与实际不一致**（见第四节·已知偏差），重建镜像时须显式传参，否则会拉取不同基座。

---

## 二、升级步骤

### 步骤 1：构建后端

```bash
cd <repo>
export JAVA_HOME=<jdk21>            # 需 JDK 21（release 21）
mvn -q package -DskipTests -Dspring-javaformat.skip=true -pl phoenix-admin/phoenix-admin-manager -am
cp phoenix-admin/phoenix-admin-manager/target/phoenix-admin.jar docker/.stage/phoenix-admin.jar
```

> 无 JDK21 环境可用容器内构建：`docker run --rm -v "$PWD":/src -w /src --entrypoint mvn <maven-jdk21镜像> -B package -DskipTests -Dspring-javaformat.skip=true -pl phoenix-admin/phoenix-admin-manager -am`

### 步骤 2：构建前端

```bash
cd web-frontend
pnpm -F @vben/web-ele build        # 需 node ^22.18 || ^24，pnpm 11.x
cd ..
rm -rf docker/.stage/dist && cp -r web-frontend/apps/admin-ui/dist docker/.stage/dist
```

> **构建产物断言（教训 L-84）**：前端构建后必须断言 `dist/` 含本轮新增字符串（如 `ollama`）且 build exit=0，否则会带着 stale dist 继续部署。

### 步骤 3：重建镜像

```bash
cd docker
IMAGE_TAG=v2.0.0 \
JRE_BASE_IMG=docker.elastic.co/elasticsearch/elasticsearch:9.2.5 \
JAVA_BIN=/usr/share/elasticsearch/jdk/bin/java \
NGINX_BASE_IMG=nginx:1.27-alpine \
docker compose build backend nginx
```

### 步骤 4：执行数据库升级（**顺序即执行序，不可打乱**）

**方式 A（推荐，compose 内置 migrator 自动执行）**：`docker compose up -d` 会先跑 `migrator` 容器按序号执行 `releases/v2.0.0/sql/` 下全部件，再启 backend。

**方式 B（手工按序）**：

```bash
for f in releases/v2.0.0/sql/V2.0.0_*.sql; do
  echo "== $f"; psql -U phoenix -d phoenix -v ON_ERROR_STOP=1 -f "$f" || break
done
```

**执行序明细（16 件，全部幂等可重入）**：

| 序号 | 文件 | 类型 | 来源 | 前置 | 回滚 |
|---|---|---|---|---|---|
| 01 | `V2.0.0_01__org_dimension_drop_ddl.sql` | DDL | user-role-group-model (T-14 / R-01·R-02·R-11) | 无 | `rollback/…_01_…` |
| 02 | `V2.0.0_02__role_backfill_dml.sql` | DML | user-role-group-model (T-05) | 无 | `rollback/…_02_…` |
| 03 | `V2.0.0_03__org_menu_cleanup_dml.sql` | DML | user-role-group-model (T-05) | 无 | `rollback/…_03_…`（含原始 8 行保真重建） |
| 04 | `V2.0.0_04__acl_baseline_rebuild_dml.sql` | DML | user-role-group-model (T-06) | **02、03** | `rollback/…_04_…` |
| 05 | `V2.0.0_05__three_party_menu_cleanup_dml.sql` | DML | user-role-group-model (T-15) | 无 | `rollback/…_05_…` |
| 06 | `V2.0.0_06__menu_merge_system_management_dml.sql` | DML | user-role-group-model (T-18 / R-12) | **01~05** | `rollback/…_06_…` |
| 07 | `V2.0.0_07__account_prune_dml.sql` | DML | user-role-group-model (T-19 / R-13) | **01~06** | `rollback/…_07_…`（保真重建账号及关联） |
| 08 | `V2.0.0_08__usertype_code_drop_ddl.sql` | DDL/DML | user-role-group-model (T-20 / R-14) | **01~07**；**应用后后端必须为新版** | `rollback/…_08_…` |
| 09 | `V2.0.0_09__agent_owner_backfill_dml.sql` | DML | user-role-group-model (T-23 / R-17) | **01~08**；后端需 v2.6.0+ | `rollback/…_09_…` |
| 10 | `V2.0.0_10__menu_state_backfill_dml.sql` | DML | user-role-group-model（BUG-135） | **01~09** | `rollback/…_10_…` |
| 11 | `V2.0.0_11__menu_system_category_backfill_dml.sql` | DML | user-role-group-model（BUG-135 收尾） | **01~10** | `rollback/…_11_…` |
| 12 | `V2.0.0_12__acl_release_sn_normalize_dml.sql` | DML | user-role-group-model（BUG-138） | **01~11**；后端需含 BUG-138 修复 | `rollback/…_12_…` |
| 13 | `V2.0.0_13__skill_creator_and_kbase_owner_dml.sql` | DDL+DML | user-role-group-model (CR-01 / T-25 / R-18) | **01~12** | `rollback/…_13_…` |
| 14 | `V2.0.0_14__skill_name_uniq_per_creator_ddl.sql` | DDL | user-role-group-model (CR-02 / T-27 / R-19) | **13** | `rollback/…_14_…`（回滚前不得已存在跨用户同名行） |
| 15 | `V2.0.0_15__chat_attachment_ddl.sql` | DDL | chat-attachment-understanding (T-02 / R-01~R-04·R-10·R-11) | **01~14** | `rollback/…_15_…` |
| 16 | `V2.0.0_16__chat_session_source_ddl.sql` | DDL+DML | chat-attachment-understanding (CR-03，v1.5.0) | **15**（同族） | `rollback/…_16_…` |

**顺序要点**：

- **04 依赖 02、03**（ACL 基线重建依赖角色已回填、组织菜单已清理）；
- **06 起各件逐级依赖前序**，因均基于 04 重建后的 ACL 基线（超管=存活菜单、普通角色=7 项）；
- **08 是本版"代码/库同版本"分水岭**：执行后旧后端因实体已不映射被删列而报错 ⇒ **必须同窗口部署新版后端**；
- 全新库重放注意：`tbl_unified_account_map` 不在基线 `all_schema.sql` 中，01 件已按 `ALTER TABLE IF EXISTS` 写法加固。

### 步骤 5：启动服务

```bash
cd docker && IMAGE_TAG=v2.0.0 docker compose up -d
docker compose ps                    # 期望 backend/nginx/postgres/redis = healthy；migrator = exited(0)
```

### 步骤 6：配置变更

仅 1 处：compose 新增命名卷 `pylibs:/app/.local`（BUG-164：让运行期 `pip install` 落盘并在容器重建后保留）。**无需手工创建**，`up -d` 自动建立并从镜像预置内容。详见 `config/changes.md`。

---

## 三、升级后验证（逐项执行，留证）

| # | 验证项 | 命令 / 操作 | 期望 |
|---|--------|------------|------|
| 1 | 后端存活 | `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8090/echo/ok` | `200` |
| 2 | 前端可访问 | `curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8090/` | `200` |
| 3 | 组织表已删 | `psql -c "\dt tbl_privilege_company"` 等三表 | 均不存在 |
| 4 | 菜单过滤生效 | 超管登录 | 左侧为「系统管理」等存活菜单；**普通角色仅 7 项** |
| 5 | 账号集合 | `select count(*) from tbl_privilege_user where is_deleted=0` | 仅保留 `admin` + 在用账号 |
| 6 | 权限可维护 | 后台「系统管理 → 角色 → 分配权限」勾选并保存 → 重新登录 | 勾选项生效；取消可撤销 |
| 7 | 技能上传 | 后台技能管理上传一个 ZIP | 成功，无「未能读取到有效 token」 |
| 8 | 资源归属 | 以非超管账号查看智能体/知识库/技能列表 | 只见 own ∪ myGroups ∪ public |
| 9 | 登录方式 | 尝试原三方免登入口 | 404 / 已下线 |
| 10 | 对话附件 | 前台 chat 上传 1 份 docx + 1 张 png | 均成功理解；白名单外格式（如 `.exe`）被拒 |
| 11 | 会话空间隔离 | 后台运行页与前台 chat 各自看会话列表 | **互不可见**；跨空间访问 = 404 |
| 12 | Ollama 接入 | 模型配置页新增 Ollama（Key 留空）→ 连通性测试 → 拉取模型列表 | 测试通过、列表可拉取 |
| 13 | 向量维度 | 用 Ollama embedding 模型建知识库文档并检索 | 维度按模型实际值（768/1024…），**不再固定 512**；维度不一致显式报错 |

---

## 四、已知偏差与注意事项

1. **镜像基座声明不一致（待修）**：`.env` 的 `JRE_BASE_IMG=eclipse-temurin:21-jre-alpine` 与本版实际发布镜像（UBI9 + Elasticsearch JDK）不符。本文件步骤 3 已给出实际可用参数。**直接按 `.env` 默认值构建会得到不同基座的镜像**。
2. **升级会打断在线会话**：重启 backend 后，正在执行的智能体回合会以 `AgentShuttingDownException` 中断（教训 L-87）；请在前置检查确认无在跑会话，或提前告知使用方。
3. **Bug-149（干净环境打包）**：`pnpm-lock.yaml` 已入库，但台账状态仍为「新建」；若在干净环境打包遇到 `ERR_PNPM_LOCKFILE_CONFIG_MISMATCH`，先确认 lockfile 存在且与 `pnpm-workspace.yaml` 的 catalog overrides 一致。
4. **运行期额外 pip 包**：升级前智能体自行 `pip install` 到旧可写层的包，重建后不可见，需重装（镜像预装的 python-docx/openpyxl/python-pptx/Pillow 不受影响）。

---

## 五、整体回滚（**逆序 16 → 01**）

```bash
# 1) 停服务（避免新旧代码与库结构错配）
cd docker && docker compose stop backend nginx

# 2) 逆序执行回滚脚本
for n in $(seq -w 16 -1 1); do
  f=$(ls ../releases/v2.0.0/sql/rollback/V2.0.0_${n}__*.sql 2>/dev/null | head -1)
  [ -n "$f" ] && { echo "== $f"; psql -U phoenix -d phoenix -v ON_ERROR_STOP=1 -f "$f" || break; }
done

# 3) 回退镜像到 v1.7.0
IMAGE_TAG=v1.7.0 docker compose up -d backend nginx

# 4) 最坏情况：用第一节的全量备份还原
pg_restore -U phoenix -d phoenix --clean --if-exists phoenix_pre_v2.0.0.dump
```

**回滚注意事项**：

| 序号 | 注意 |
|------|------|
| 14 → | 回滚 14 前**不得存在跨用户同名技能行**，否则重建 `UNIQUE(name)` 冲突 |
| 08 → | 回滚 08 会恢复 `user_type`/`code` 列与原值，但**需同时回退后端到 v1.7.0 代码**（旧实体才映射这些列） |
| 07 → | 回滚脚本保真重建被收敛的账号及其关联（含聊天与向量记忆），**但升级后新建的账号不受影响** |
| 03 / 05 / 06 | 回滚含被删菜单行的保真重建（原始 id 与顺序） |
| 全部 | 回滚 ≠ 无痕：升级期间产生的业务数据（如新会话、新附件）**不会**回滚，按业务口径另行处置 |

---

## 六、升级记录（执行后回填）

| 项 | 值 |
|---|---|
| 执行人 / 日期 | |
| 备份文件名 / 位置 | |
| SQL 执行结果（01~16） | |
| 镜像摘要（backend / frontend） | |
| 验证结果（第一节 13 项） | |
| 异常与处置 | |
