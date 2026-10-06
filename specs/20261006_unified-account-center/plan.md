> 版本: v2.1.0 | 状态: 待重确认 | 确认人: 陈卓 | 确认日期: 2026-10-06 | 更新: 2026-10-06（v2.1.0：新增**决策 8**——智能体中心改为**新浏览器窗口**打开独立全屏 chat 页（撤销 T-19 对 `/front/chat` 的重定向），并去掉该页「系统设置」入口；坑核对重核至 31 条） | 更新: 2026-10-06（v2.0.1：决策 7 澄清——「智能体中心」改**一级菜单置顶**；可见性口径按实测纠正：现网 `getUserMenus` 为开发期全放开 ⇒ 本期实际可见性 = 任一登录用户，ACL 行仅作二期启用过滤的预置数据、**按角色过滤属 R-05 二期**） | 更新: 2026-10-06（v2.0.0：R-11 引入"智能体中心并入 admin、单套登录/访问" ⇒ 决策 3 与过渡期 D9 被取代） | 更新: 2026-10-06（v1.1.0 变更：T-01 取证后并入三项裁定——M2 匹配规则、M8 测试账号清理、回滚项⑤）

# 技术方案：unified-account-center（统一账号中心 / 消除双账号体系）

## 坑核对（必填，确认②审这一节）

已核对 `specs/_project/lessons.md` **全部 31 条 active 坑**，与本方案相交并已避开的有 **18 条**，其余 13 条（L-04/L-08/L-09/L-10/L-12/L-15/L-17/L-18/L-20/L-23/L-25/L-26/L-27）经核对无相交。
> v2.1.0 复核：上次确认时台账为 24 条，其后新增 L-24~L-31 已一并核对（新增相交项见下表末四行；L-25/L-26/L-27 与本方案无相交）。

| 坑 | 本方案如何避开 |
|---|---|
| **L-01** 文本补丁静默脱靶 | 迁移/对账全部走**文件化 SQL**（`psql -f`）与带 `assert count==1` 的文档编辑；不用裸 `s.replace` 改生产脚本 |
| **L-02** 日志截断误导取证 | 迁移前后对账用**完整 SQL 计数**（先 `wc -l`/`count(*)` 明确规模），不靠截断日志下结论 |
| **L-03** 部署三段证明 | 本方案含 DB 迁移 → 部署后三段证明：镜像/容器（`docker inspect` 核对 Image+StartedAt）+ 迁移版本（`flyway_schema_history` 式记录或 SQL 标记表）+ 行为（登录/菜单/前台对话各一次） |
| **L-05** shell/平台方言雷区 | 迁移 SQL 写文件后 `psql -f` 执行，禁止三层以上引号内联；PostgreSQL 方言（无 MySQL `ON UPDATE`） |
| **L-06** 多入口枚举缺失 | 认证面按**身份矩阵**（本文件「共享面身份矩阵」节）逐身份枚举：**3 个前端 app**（admin-ui/pc-ui/mobile-ui）+ 12 个认证端点 + 前台/后台两侧；矩阵外的身份视为未评估 |
| **L-07** 假阳性验证 | 迁移验证**双侧取证**：DB 直查（计数/抽样行）+ API 回读（登录/菜单/用户信息/会话）各一次，两者都落盘才算通过 |
| **L-11** 用户环境 ≠ 我以为的环境 | 登录入口/落地页变更必须**用户硬刷新实测**；每条复测指令写明 URL 与硬刷动作（v2.1.0 新增"新窗口打开"行为同样交用户实测，见 T-22） |
| **L-13** git add 面过宽 | 迁移脚本、导出备份、演练产物**先入 `.gitignore`**（或明确路径），提交按文件列举，禁 `git add -A` |
| **L-14** md 表格错乱 | 本次台账/文档每次写入后跑 `mdtable_check.py`，exit 0 才算完成 |
| **L-16** 用户实测期间禁止静默部署 | 迁移含**强制重登**（会话失效）→ 必须与用户约**时间窗**并提前告知，不得静默执行；**v2.1.0 实践**：T-18/T-19 部署前均先告知，且选择"不重启后端"的部署方式避免掉线 |
| **L-19** 路径归属同源 | 本方案正是 dual-id 的**同源化**：账号归属解析统一到 `tbl_privilege_user.id`（唯一事实源），不再"看入口猜 id" |
| **L-21** 逻辑删列隐形过滤 | **高危相交**：platform 系 `del_flag`（0=已删/**1=未删**）与 privilege 系（0=存在/**1=删除**）**语义相反**；迁移与对账的"存在性"判断按表逐条判读并写进脚本注释，禁止套用同一条件 |
| **L-22** 构建串行 | 迁移脚本执行、后端构建、前端构建**一律串行**，执行前确认无在跑构建 |
| **L-24** 路由页面模板必须单一根节点 | v2.1.0 触碰路由页 `views/front/chat.vue`（恢复为独立页）与 `ChatHistoryPanel.vue`：改动后**不得**新增顶层兄弟节点（`chat.vue` 现为 `v-if/v-else` 双分支根，属允许形态，不误改） |
| **L-28** 部署过的代码必须同批入库 | 每个任务的 commit 同时含源码与文档；收尾前 `git status --short` 必须干净 |
| **L-29** 外键/归属判定必须实测 join | v2.1.0 还要改菜单行 `url`（`agent/chat` → `#/front/chat`）：改后**以接口回读 + 实体点击**取证，不用 `module_sn` 之类冗余列反查 |
| **L-30** 管道吞退出码 | 前端构建/typecheck 命令一律 `set -o pipefail` + `PIPESTATUS[0]`，并指认成功标志行（`✓ built in …`）后才算通过 |
| **L-31** 数据驱动抄行须逐列核对 | 升级件 04 改菜单行采用**显式列清单**并做与母版行的字段 diff（T-18 曾因漏 `image` 列被用户当场发现） |

## 方案概述

**终态路线 = 「以 `tbl_privilege_user` 为唯一账号主表」**（调研路线 C 的收敛版，非路线 B）：

1. `tbl_privilege_user` 升级为**唯一账号源**；前台账号 `tbl_platform_account_info` 的数据**一次性迁入**，旧表一期保留为**只读历史视图**（二期评估下线）。
2. **id 策略（本方案核心）**：按"该自然人是否已有后台账号"分两支，**最小化业务列改写**：
   - 分支 A（已有后台账号）：业务列**重写**为后台 id（8 类列），前台账号行标记 `merged` 并登记映射；
   - 分支 B（无后台账号）：在 `tbl_privilege_user` **新增一条并沿用原前台 id**（`id` 为 varchar，可直接沿用）⇒ **该自然人的业务列零改写**。
3. **认证统一**：后台既有 `LoginServiceImpl` 成为唯一校验实现；前台 `POST /auth/login` 保留为**兼容入口**（内部走同一 service、响应结构不变），三端前端不必一次性切换。
4. **凭据统一**：密码一律 `MD5("phoenix"+pwd)`；迁移时把前台表中**非 32 位 hex** 的密码按同算法重算（修复 BUG-95），改密链路改为写哈希。
5. **登出/用户信息/放行面/调用面**分别按 R-06~R-09 收敛（BUG-96~BUG-99）。
6. **落地页判别**：不改 `user_type` 语义（DB 注释=自建/IDM），改为**按角色判定**（该账号是否持有后台角色），解决"三重语义冲突"，且**无需加列**。
7. **B 组（BUG-86）**：先复现定性（专有词提问 → 命中 chunk → 反查所属库），再按定性结果择一修复路线（见「关键决策 6」）。

对应满足：R-01~R-04、R-06~R-10、R-20、R-21（**R-05 按裁定 D3 移入二期**）。

## 涉及模块与数据流

| 层 | 模块/文件 | 改动性质 |
|---|---|---|
| 认证核心 | `phoenix-privilege/phoenix-privilege-core`（`LoginServiceImpl`、`LoginHelper`、`PrivilegeUserServiceImpl`、`AccountInfoServiceImpl`） | 统一校验实现；补全/删除 `LoginTypeEnm.USER` 空壳；密码哈希修正；清理死代码 `generateToken` |
| 认证入口 | `phoenix-privilege-rest`（`LoginController`）、`phoenix-platform-rest`（`AccountLoginController`） | 放行面收敛（移除 `doLogin`）；前台入口改为委派同一 service |
| REST/权限 | `phoenix-privilege-rest`（`PrivilegeUserController`、菜单/角色/ACL） | 账号来源统一后的查询口径 |
| 平台账号 | `phoenix-platform/*`（`AccountInfoServiceImpl`、`AbstractPlatformSyncStrategy`） | 同步策略改为"写唯一账号源"；只更新不新建的口径复核 |
| 数据 | `releases/v1.7.0/sql/`（新增迁移件 + rollback） | 映射表 + 数据迁移 + 密码重算 |
| 前端 | `web-frontend/apps/admin-ui/src/store/auth.ts`、`api/core/{auth,user}.ts`、`api/front/*`；`apps/pc-ui`、`apps/mobile-ui` 的登录调用 | 落地区按角色；登出/用户信息/失效端点调用面修正（三端） |
| 会话 | sa-token（`phoenix-token`，**继续进程内存**） | loginId 语义统一为 `privilege_user.id`；**不落 Redis**（裁定 D2） |

**关键数据流（登录，统一后）**：
```
前端(admin/pc/mobile) → POST /api/privilege/auth/login 或 POST /auth/login（兼容入口）
        → 同一 LoginServiceImpl：查 tbl_privilege_user（唯一源）+ 状态判定 + MD5("phoenix"+pwd) 校验
        → StpUtil.login(privilege_user.id)  → 签发 phoenix-token（会话在内存）
        → 返回统一信封；前端按"是否持有后台角色"决定落地页（/agent/list 或 /front/chat）
```

## 接口设计

遵守 `standards/api-design.md`（本项目为既有 `/api/**` + `ReturnVo<T>` 信封，按「项目既有约定优先」不动信封与版本前缀）。

| 端点 | 方法 | 变更 | 说明 |
|---|---|---|---|
| `/api/privilege/auth/doLogin` | POST | **删除** | BUG-94：演示桩 + 放行名单内；删除后同步核对放行名单 |
| `/api/privilege/auth/login` | POST | 行为不变（内部成为唯一校验实现） | 入参 `LoginInfoDTO` 不变；错误码沿用 |
| `/auth/login` | POST | 保留为**兼容入口**，内部委派同一 service | 响应结构保持三端兼容；不新增字段 |
| `/auth/thirdLogin` | POST | 不变（回归验证） | 三方登录不在范围但要保证不回归 |
| `/auth/logout` | POST | 成为前台唯一登出端点 | 前端三端的登出调用改指此处（BUG-96） |
| `/api/privilege/auth/logout` | POST | 不变 | 后台登出 |
| `/api/privilege/auth/getLoginUserInfo` | GET | 改为按唯一账号源查询（前台账号不再 null） | BUG-97；出参 VO 不含密码/删除标记 |
| `/auth/refresh`、`/auth/codes` | — | 前端调用移除（后端不存在） | BUG-98；或按需最小实现（plan 决策：**移除**，除非前端流程必需） |
| `/auth/updatePassword` | PUT | 改为写 `MD5("phoenix"+pwd)` | BUG-95；入参加 `@Validated` 校验 |
| `/api/privilege/user/reset-password/{id}` | PUT | 不变（管理员重置） | 与自助改密共用同一哈希工具 |

## 数据模型变更

**新增 1 张映射/审计表**（迁移与回滚的事实锚，遵循 database-design 命名与 COMMENT 要求；本项目 PostgreSQL + varchar id 惯例）：

```sql
-- releases/v1.7.0/sql/V1.7.0_01__unified_account_map.sql
CREATE TABLE IF NOT EXISTS tbl_unified_account_map (
  id            varchar(64)  NOT NULL,
  old_account_id varchar(64) NOT NULL,   -- 原 tbl_platform_account_info.id
  new_user_id    varchar(64) NOT NULL,   -- 统一后 tbl_privilege_user.id
  merge_type     varchar(16) NOT NULL,   -- 'merged'(并入既有后台账号) | 'created'(沿用前台 id 新建)
  employee_id    varchar(64),
  migrated_at    timestamp    NOT NULL DEFAULT now(),
  remark         varchar(255),
  CONSTRAINT pk_tbl_unified_account_map PRIMARY KEY (id)
);
COMMENT ON TABLE  tbl_unified_account_map IS '统一账号迁移映射与审计（一期；二期可下线）';
COMMENT ON COLUMN tbl_unified_account_map.merge_type IS 'merged=并入既有后台账号 created=沿用前台id新建';
CREATE UNIQUE INDEX IF NOT EXISTS uk_uam_old_account ON tbl_unified_account_map (old_account_id);
CREATE INDEX IF NOT EXISTS idx_uam_new_user ON tbl_unified_account_map (new_user_id);
```

**数据迁移（DML，按分支执行）**：

| 步骤 | 动作 | 校验 |
|---|---|---|
| M1 | 备份：导出两表全量 + 8 类业务列的**引用计数快照** | 快照文件落盘、行数记录 |
| M2 | 识别同自然人，**匹配优先级（T-01 裁定 D5，写死）**：① `username`+`code` 全等（强）→ ② `username` 全等 → ③ `employee_id` 全等**且该 employee_id 在后台侧唯一**；三档都不满足 → 人工清单。**禁止仅凭 employee_id 合并**（实测陷阱：`admin` 与 `chenzhuo` 共用 `employee_id=461670914812276736`，前台 chenzhuo 会被并入 admin） | 输出三张清单（可对应 / 不可对应 / 疑似重复）逐条落盘；**人工确认后才继续** |
| M3 | 分支 B（无对应后台账号）：`INSERT INTO tbl_privilege_user (...) SELECT 原字段` **并沿用原 id** | 插入行数 = 分支 B 清单行数（assert） |
| M4 | 分支 A（已有后台账号）：把 8 类列中值为旧前台 id 的行 **UPDATE 为新 id**；写入映射表 `merge_type='merged'` | 每类列 UPDATE 行数 = 迁移前引用计数快照（assert，逐列） |
| M4b | **合并账号的密码口径（D8 澄清）**：分支 A **只重写业务列，不动账号行** ⇒ 合并后**保留后台密码**，前台旧密码作废（映射表 `remark` 记「前台原密码作废」，不落任何密码值）；用户忘记后台密码时走既有 `PUT /api/privilege/user/reset-password/{id}` 重置 | 合并后仅后台密码可登录（两入口一致）；`remark` 有留痕且**不含密码** |
| M5 | 密码重算：前台来源账号中**非 32 位 hex** 的 `password` → `MD5('phoenix'||明文)` | 迁移后校验"全部为 32 位 hex" |
| M6 | 旧表标记：`tbl_platform_account_info` 增加只读约束/标记（不删数据，保回滚） | 应用侧读写路径已切至唯一源 |
| M7 | 对账：8 类列在迁移后的引用**全部命中** `tbl_privilege_user.id`；**孤儿引用按裁定 D6 保持原样不映射**（`433383317100486656` 5 行、`1000000000000000001` 5 行、`system` 哨兵 2 处），登记为历史遗留并挂 BL 待查 | 账内引用孤儿 0 行；**账外孤儿清单与计数落盘**（不视为失败） |
| M8 | **测试账号清理（T-01 裁定 D7）**：删除前台测试残留 `thinktest`（id `9000000000000090001`，建于 2026-10-02，实测零会话/零文件/零记忆/零预设，仅 1 行 `tbl_platform_account_group_info`）。按 database-design §23：先 `SELECT` 验证行数 → 备份该账号行与其组关系行 → 脚本执行删除 → 计数 assert | 删除前 `SELECT` 行数=1（账号）+1（组关系）；备份文件落盘；删除后两处计数=0 |

**8 类待重写列清单**（来自调研，逐列 assert）：

| # | 表.列 | 混用证据 |
|---|---|---|
| 1 | `tbl_agent_user_agent_info.user_id` | 已实测同列存两套 id |
| 2 | `tbl_agent_user_memory_info.user_id` | 同上 |
| 3 | `tbl_agent_user_profile_info.user_id` | 同上 |
| 4 | `tbl_data_chat_session.user_id` | 后台/前台分别写入 |
| 5 | `tbl_data_agent_preset_question.account_id` | 前台写入 |
| 6 | `tbl_data_agent.admin_id`（int8） | **类型不符**：两套 id 均 varchar → 需显式转型核对（0/空值不误伤） |
| 7 | `tbl_data_agent_file.creator` | 文件归属 |
| 8 | `tbl_data_knowledge_base.creator` + `tbl_data_agent_kbase_bind.creator` | 知识库归属 |

**行级审计（v1.2.0 新增，演练发现的必修项）**：迁移 M4 每类列在 UPDATE **前**先把命中行的主键写入
`tbl_unified_account_migration_rows(table_name, pk_value, old_id, new_id)` ⇒ 回滚按**行**精确反向。

**回滚（v1.2.0 修正）**：`sql/rollback/V1.7.0_02__unified_account_migration_rollback.sql`
按行级审计逐行 `SET <col> = old_id WHERE <pk> = pk_value`（**不再**按 id 集合批量反向），随后硬门禁：
`audit 中每行都已回退` + 逐列行数与迁移前快照一致（如 chat_session 旧 id=28、新 id=115）。原设计（仅映射表反向）
经演练证伪：会把目标账号自有的 115 条会话误迁到旧 id。

**回滚（DDL 部分）**：`releases/v1.7.0/sql/rollback/V1.7.0_01__unified_account_map_rollback.sql`
① 按映射表反向重写 8 类列（`new_user_id` → `old_account_id`）；② 删除分支 B 插入的账号行；③ 恢复前台密码列原值（备份表）；④ **恢复 M8 删除的 `thinktest` 账号行与其组关系行**（从备份回插）；⑤ 校验反向计数 = 迁移前快照。**必须先在备份库演练一次并记录耗时**。

**不加列**：不新增 `account_scope` 等判别列（落地页按角色判定，见决策 4）⇒ 迁移面更小、无"先加后删"负担。

### 过渡期行为（D9，2026-10-06 用户裁定；**迁移后自动消失**）

前台兼容入口在**迁移完成前**允许"统一账号密码失败 → 回退校验旧前台账号密码"（命中即放行并记 WARN 日志）。
理由：迁移前 chenzhuo 这类双身份用户的前台密码与后台密码不同，一刀切会把人锁在门外。
边界：① 仅前台 `/auth/login`；② 仅在"统一源存在同名账号但校验失败"时触发；③ **M4/M8 执行后该分支不可达**（旧表降只读、无在用旧账号）⇒ 迁移完成后即为 R-02 的单一凭据语义；④ 每次回退必须记 WARN（含 username 与来源），便于迁移后确认该分支已死。

### T-01 取证后的三项裁定（2026-10-06，用户裁定 → v1.1.0 变更来源）

| 裁定 | 内容 | 依据（T-01 证据） |
|---|---|---|
| **D5 匹配规则** | username+code 全等 > username 全等 > employee_id（且后台侧唯一）；其余人工 | 实测 `admin` 与 `chenzhuo` **共用** `employee_id`，仅凭 employee_id 会把前台 chenzhuo 并入 admin |
| **D6 孤儿引用** | **保持原样不映射**，登记为历史遗留并挂 BL 待查 | 12 行孤儿：`433383317100486656`（1+4）、`1000000000000000001`（5）、`system` 哨兵 2 处；均既不在后台表也不在前台表 |
| **D7 测试账号** | 删除 `thinktest`（含其 1 行授权组关系），走 M8 脚本化删除（先 SELECT 验行数 + 备份 + assert） | 实测零业务足迹（会话/文件/记忆/档案/预设全为 0），仅 1 行 `tbl_platform_account_group_info` |

**同时确认的正面事实**（缩小改写面）：`data_agent.admin_id` 全为 NULL → 该列零改写；`agent_user_memory_info`、`agent_user_profile_info`、`data_agent_preset_question` **三表为空** → 零改写；实际需改写仅 3 类列（`agent_user_agent_info.user_id` 1 行平台引用、`chat_session.user_id` 28 行、`agent_file.creator` 213 行）。

## 共享面身份矩阵（触碰共享面必填）

判据自检：「还有谁依赖这个路径/列/配置？」——认证端点、账号 id 列、前端登录路由、token 名均为共享面。

| 对象 | 身份（方法 × 调用方 × 端点） | 变更后预期行为 | 断言归属 |
|---|---|---|---|
| `/api/privilege/auth/login` | POST × admin-ui × 后台登录 | 不变（账号源统一后仍成功） | T-1x |
| `/auth/login` | POST × admin-ui 前台页 / pc-ui / mobile-ui × 前台登录 | 结构不变、内部委派同一 service | T-1x |
| `/auth/logout` | POST × 三端 前台登出 | 真正登出前台会话（此前无效） | T-1x |
| `/api/privilege/auth/logout` | POST × admin-ui 后台登出 | 不变 | T-1x |
| `/api/privilege/auth/getLoginUserInfo` | GET × admin-ui（前台+后台都会调） | 后台账号返回原信息；**前台账号不再 null** | T-1x |
| `/api/privilege/auth/menus` | GET × admin-ui 后台 | 不变（权限口径未变） | T-1x |
| `/api/privilege/auth/captcha` | GET × 登录页 | 不变 | T-1x |
| `/api/privilege/auth/doLogin` | POST × **无（演示桩）** | **404/已移除** | T-1x |
| `/api/privilege/user/**`（建号/改密/重置/查用户） | GET/POST/PUT × 后台管理页 | 账号来源统一后行为等价 | T-1x |
| `/platform/account-info/**` | GET/POST/PUT/DELETE × 前台账号管理页 | 一期改读写唯一源（或标只读），接口形态不变 | T-1x |
| `tbl_privilege_user.id` | 被 8 类业务列 + 权限/角色/ACL 引用 | 成为唯一账号 id；迁移后不再出现第二套 id | T-1x |
| `tbl_platform_account_info` | 被前台登录/账号管理/同步策略引用 | 一期保留为只读历史源；引用方全部改指唯一源 | T-1x |
| 前端路由 `/auth/login` | GET × SPA 导航（与前台登录 **API 同字符串**） | 不变（**注意**：路由与 API 同名，改任一侧都要分别验证） | T-1x |
| `localStorage['phoenix-token']` | 三端读写 | 语义不变（loginId 变统一值） | T-1x |
| `tbl_platform_group_*`（4 表授权） | 前台资源授权链路 | 一期不变（R-05 二期纳管） | 二期 |

## 关键决策

### 决策 1：唯一账号主表 = `tbl_privilege_user`（复用后台既有权限设施）
- 采用：以 `tbl_privilege_user` 为唯一源，前台账号迁入；保留映射表用于回滚/审计（一期使用，二期可下线）。
- 理由：后台侧独占 `role/user_role/module/acl/pvalue` 全部权限设施，且 `tbl_privilege_acl.release_sn` 已预留用户级 ACL；以前台表为主则需重建整套权限设施。
- 被拒绝：**路线 B（保留两表 + 映射层）**——映射层无法满足 R-01「同一自然人只有一个身份」，且 dual-id 问题原样保留；重新评估条件：若迁移风险被证明不可接受（演练失败 ≥2 次）。

### 决策 2：id 策略——保留后台 id，无后台账号者沿用前台 id
- 采用：分支 A 重写业务列、分支 B 沿用前台 id 新建。
- 理由：把 8 类列的改写面压缩到"**双身份自然人**"这一最小集合（分支 B 零改写），显著降低漏改风险与回滚复杂度。
- 被拒绝：① 统一重新生成新 id（全量改写 8 类列，风险与工作量最大）；② 保留前台 id 为主（权限设施全需迁移）。

### 决策 3 [已被 v2.0.0 取代]：前台 `/auth/login` 保留为兼容入口（不一次性切换三端）
> 取代理由：R-11 要求**单套登录/访问逻辑** ⇒ 不再保留前台独立入口；改为 admin 单入口 + chat 以「智能体中心」菜单挂载（见决策 7）。
- 采用：前台入口委派同一 service，响应结构不变；三端前端仅改登出/用户信息等必要调用。
- 理由：前端 **3 个 app** 直连该端点，一次性切换的回归面远超收益（L-06 风险）。
- 被拒绝：一次性把三端改为调 `/api/privilege/auth/login`（回归面大、收益仅"少一个端点"）；重新评估条件：二期下线旧入口时。

### 决策 4：落地页按**角色**判定，不重定义 `user_type`、不新增判别列
- 采用：`user_type` 保持 DB 原语义（0 自建/1 IDM）；前端落地页改为"该账号是否持有后台角色"。
- 理由：`userType` 现存**三重语义冲突**（前端当"普通用户=1"、枚举 `ADMIN/COMMON`、DB 注释"自建/IDM"），重定义任一者都会造成新的隐性耦合；按角色判定语义清晰且**零 DDL**。
- 被拒绝：新增 `account_scope` 列（多一次 DDL 与回填，且与角色信息冗余）。

### 决策 5：会话继续进程内存（裁定 D2）
- 采用：不落 Redis；但**登录态语义统一**（loginId = 唯一账号 id）。
- 理由：用户裁定；且会话改造（多实例/持久化）是独立议题（BUG-100 挂账）。
- 被拒绝：本 spec 内落 Redis（扩大范围与会话风险）；重新评估条件：出现多实例部署需求时。

### 决策 6：BUG-86 先定性、后择一修复路线
- 采用：Phase 4 首个任务做**复现定性**（专有词提问 → 命中 chunk → 反查所属库），据结果择一：
  ① 若命中未绑定库切片 → 修检索过滤/切片归属（`agentKnowledgeId` 维度）；
  ② 若零命中而模型仍谈论 → 修**提示面**（scope hint 不得列未绑定库）；
  ③ 若命中且来自绑定库 → 语义误判，按提示面与用词澄清处理（并在台账改判）。
- 理由：调研已排除"绑定错误"，但**未确证越库路径**；先定性再改，避免改错面（L-01/L-06 教训）。
- 被拒绝：直接按最可能路径（提示面）改（无证据即改 = 违反诚实性；若定性为①则白改）。

### 决策 7（v2.0.0 新增；v2.0.1 澄清可见性口径与菜单层级）：chat 以「智能体中心」并入 admin，单套登录/访问
- 采用：① 复用现 `views/front/chat.vue` 及其子组件（ChatMessages/ChatFilesPanel/预设问题等）作为 admin 路由页；② 菜单表加一行**一级菜单**（`pid=''`、`type=1`、`url=/agent/chat`、`component=#/views/front/chat.vue`、`name=智能体中心`、`sn=AgentChatCenter`、`order_no=-1` 置顶；字段形状继承现有一级菜单「知识库」行），并按**每个角色一行**预置 `tbl_privilege_acl` 授权；③ 认证/权限/用户信息全走 admin 同一套（token 键 `phoenix-token` 已统一 ✓）；④ 前台独立入口（`/auth/login`、`/front/*`）下线或重定向到 admin（一期先重定向 + 保留页面文件，二期删代码，避免一次性破坏 mobile-ui/pc-ui）
- **可见性口径（v2.0.1 澄清，实测取证）**：菜单可见性的唯一载体是 `tbl_privilege_module`；`tbl_privilege_acl` 是「角色 → 菜单」授权表（`module_id → module.id`，唯一键 `(release_id, module_id)`），但现网 `LoginServiceImpl.getUserMenus()` 为**开发期全放开**（`getModelTreeByUserId(...)` 调用被注释，改用 `list()` 全量 + 全权限位）——实测：零 ACL 的「普通角色」账号同样拿到全部 26 个菜单。⇒ **本期实际可见性 = 任一登录用户**（不按角色过滤）；ACL 行为「二期启用过滤即生效」的预置数据，**按角色过滤本身属 R-05 二期**，不在本 spec。
- 理由：前台与后台请求层/token 已统一（T-03/T-16 已证），挂载成本低；chat 页面本身不依赖前台登录分支；置于一级菜单置顶 = chat 为登录后主入口（用户 2026-10-06 指定）
- 被拒绝：把 chat 代码复制一份进 admin（双份维护 ✗）；保留前台入口并行（违反 R-11 ✗）；本 spec 内启用 ACL 过滤（要放开被注释的 `getModelTreeByUserId`，影响全部 26 个菜单的可见性、且「普通角色」ACL 零行会只剩个别菜单 —— 属二期 R-05，风险大 ✗）
- 影响：`mobile-ui`/`pc-ui` 两个 app 直连 `/auth/login` ⇒ 一期保留端点做重定向，二期评估下线；`LoginHelper` 的 `LoginTypeEnm.USER` 空壳可随之清理
- 风险：chat 页内若有"前台专属"分支（如按前台账号 id 取会话）需改为统一账号 id（已由迁移完成 ✓）
- **v2.1.0 修订（见决策 8）**：④ 中「`/front/chat` 也重定向到 admin」一条**撤销** —— 该路径改为**独立全屏 chat 页**，
  作为「智能体中心」新窗口的落地页（仍复用同一登录态与同一套接口，不构成第二套登录/访问体系）；`/front/agent` 仍重定向到 `/agent/list`。

### 决策 8（v2.1.0 新增）：智能体中心改为**新浏览器窗口**打开独立 chat 页，并去掉该页「系统设置」入口
- 采用：
  ① **新窗口打开**：菜单行 `url` 由 `/agent/chat` 改为 `#/front/chat`，并在前端 `access.ts` 引入**外链菜单约定**——
     `url` 以 `#/` 或 `http` 开头 ⇒ 视为外链菜单：生成路由用**合成 path**（`/external/<sn>`，避免与独立路由撞路径），
     同时写入 `meta.link = url` ⇒ vben 侧栏点击走 `use-navigation.ts` 的 `openWindow(link, '_blank')`（**不改共享包**）；
     同时该菜单不再渲染任何 admin 内页面 ⇒ **不占页签**（满足 R-12「admin 当前页不变」）。
  ② **独立全屏 chat 页**：撤销 T-19 对 `/front/chat` 的重定向，恢复为**不在 BasicLayout 下**的独立路由
     （无侧栏/标签栏/页签容器）⇒ 新窗口内天然没有后台外壳（满足 R-12「不叠加后台外壳」）。
  ③ **去掉「系统设置」**：`ChatHistoryPanel.vue` 的 footer 用户卡删除「系统设置」按钮与其 `SystemSettingsModal`
     引用/状态/处理函数（含其中的账号信息与修改密码入口），**保留**头像、用户名与「退出登录」（满足 R-13）。
- 理由：用户 2026-10-06 明确要求 —— chat 应在**新浏览器窗口**中使用（当前在 admin 内开页签），且该页不应出现用户设置入口。
  复用既有独立路由 + vben 原生 `meta.link` 能力，改动面最小（前端 3 文件 + 1 行菜单数据 + 1 个升级件）。
- 被拒绝：
  ① 新窗口加载 `/agent/chat`（会带后台外壳与侧栏用户菜单，左下角出现**两处**用户区，与 R-12/R-13 冲突 ✗）；
  ② 给 `tbl_privilege_module` 加 `link`/`open_in_new_window` 列（为一行菜单改表结构，收益低且引入 DDL ✗）；
  ③ 改共享包 `packages/effects/layouts/.../menu-ui`（跨 3 个 app 影响面，违反最小改动 ✗）；
  ④ 按 `sn` 在前端硬编码白名单（数据驱动优先；`url` 本就是"链接"语义，用前缀约定更自解释 ✓）。
- 影响：菜单行 `url` 变更 ⇒ 升级件 **04**（含回滚）；`/agent/chat` 不再作为 admin 内路由（深链会落 404 兜底页，可接受）；
  独立页与菜单路由**共用** `views/front/chat.vue`（无复制，双份维护为 0）。
- 风险与规避：
  - 新窗口被浏览器拦截（`window.open` 需在点击手势内）⇒ vben 在点击事件里直接调用，属用户手势内；T-22 交用户实测确认。
  - 新窗口里 localStorage 登录态丢失 ⇒ 同源共享 localStorage/SecureLS 持久化 store，T-22 断言"无二次登录"。
  - `url='#/front/chat'` 与合成 path 造成菜单管理页显示怪异 ⇒ 在升级件注释与 completion 中说明该约定。

## 风险与规避

| 风险 | 规避 |
|---|---|
| 迁移不可逆/数据损坏 | 映射表 + 回滚脚本 + **备份库演练**（含反向计数校验）；M1 全量备份，演练未过不进生产 |
| 8 类列改写遗漏 → 数据"孤儿" | 逐列 assert（迁移前引用计数 = 迁移后 UPDATE 行数）+ M7 孤儿查询必须 0 行 |
| `del_flag`/`status` 语义相反导致误判 | L-21：按表逐条判读并写进脚本注释；platform 系 0=已删、privilege 系 1=删除 |
| 强制重登引发用户中断 | 与用户约时间窗 + 提前告知；会话本就在内存，重启即掉线（影响面已确认） |
| 三端前端漏改 | 身份矩阵逐身份断言；admin-ui + pc-ui + mobile-ui 三端各跑登录/登出/用户信息 |
| BUG-86 定性后路线与预期不符 | 定性为先（决策 6），必要时回改本 plan 并重新确认（铁律 6） |
| 迁移期间账号管理页写入竞态 | 迁移窗口内**停写**（前台账号管理与同步任务），或迁移前先导出 + 迁移后对账 |

## 依赖与前置

- **必须前置**：① 生产/演练库**可恢复备份**；② 用户确认**迁移时间窗**（含强制重登告知）；③ 8 类列引用计数快照脚本（只读）先产出并留档。
- 依赖模块：`phoenix-privilege-core/rest`、`phoenix-platform-core/rest`、`phoenix-agent-core`（同步策略）、`web-frontend`（三端）。
- 不依赖外部服务；三方登录仅做回归。

## 二期（R-05）概要（不在本 spec 交付）

- 目标：前台「授权组」（`tbl_platform_group_*` 4 表 + `tbl_platform_account_group_info`）纳入统一权限判定。
- 已知难点（调研结论）：**group 语义（资源授权）与 role 语义（菜单权限）不同构、非 1:1 可转** → 二期需先定"资源授权与菜单权限的合并模型"，再谈迁移。
- 本 spec 的产物为该期提供前提：唯一账号 id 与统一认证（二期的账号维度已就绪）。
