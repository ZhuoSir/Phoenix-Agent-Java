# 缺陷清单 Phoenix-Agent-Java
> 首建：2026-09-27（本地部署 + Skill 验证过程实测发现）| 每条附证据；修复需逐条确认后动手
> 勘误：早前会话中两条口头推断经核实**不成立**，未列入——① buildLoginResult 对 NULL 密码 NPE（实际只走"用户名或密码错误"分支）② yml 有 server.port=3333（文件与日志中均无此值）

## P1 — 阻塞/数据类

### B-01 `all_schema.sql` 缺 5 个序列 → 全新环境导入必失败
- **现象**：demo 表 `tbl_data_categories / order_items / orders / products / users` 建表报 `relation "..._id_seq" does not exist`（CREATE TABLE 引用 `nextval()` 但全文没有对应 CREATE SEQUENCE）
- **影响**：任何全新初始化必复现；本次已在运行容器内手工补齐（**SQL 文件未修**），重导仍会炸
- **附带**：这批表 + `tbl_tmp_*` 疑似早期 demo 遗留，应用代码零引用，可考虑整体移出种子
- **证据**：`sql/all_schema.sql` 1590 行区段；本次导入日志
- **修复方向**：文件内补 `CREATE SEQUENCE`（或改 IDENTITY）；另立 spec 清理 demo 遗留

### B-02 HumanInTheLoop 智能体未挂 skillRepository（同框架行为不一致）
- **现象**：两个 harness 智能体里只有 `RulesHarnessAgent` 能加载技能，`HumanInTheLoop` 静默加载不到
- **根因**：`HumanInTheLoop.createHarnessAgent()` 的 builder 缺 `.skillRepository(postgresSkillRepository)` 一行（grep 计数 1 vs 0）
- **修复方向**：补一行；属小 bugfix 可直接修

## P2 — 登录/账号体系（本次排障重灾区）

### B-03 前台账号创建时密码无必填校验 → 制造永久无法登录的"死账号"
- **现象**：后台"前台账号"新建表单密码留空也能保存成功；此后任何密码登录都返回「用户名或密码错误」，无"未设密码"提示
- **根因**：前端 `account-info/data.ts:186` password 字段无 required 规则（placeholder「留空则不修改」是编辑场景文案被带到新建）；后端 `AccountInfoServiceImpl.save():298` `isNotBlank` 才加密，NULL 直接入库
- **实测**：chenzhuo 前台账号 12:19 创建后 password=NULL
- **修复方向**：新建必填（前端 rules + 后端 create 校验），编辑保留"留空不改"

### B-04 双账号体系：两张表、状态语义相反、密码互不相通
- 管理端 `POST /api/privilege/auth/login` → `tbl_privilege_user`（status **1=禁用**）
- 前台 `POST /auth/login` → `tbl_platform_account_info`（status **0=禁用**）
- 同名账号是两条独立记录，后台改了 A 表密码、前台登 B 表——本次部署在此耗费大量排障时间
- 列表接口还把 password 脱敏成 null（`AccountInfoServiceImpl:108`），界面永远"看起来没密码"，加剧误判
- **修复方向**：属设计问题 → 建议记技术债另行立项（统一账号中心或至少加同页提示）

### B-05 登录密码错误返回了「原密码错误」的错误码
- `LoginServiceImpl:69-71`：密码比对失败返回 message=`PASSWORD_ERROR(密码错误)` 但 code=`OLD_PASSWORD_ERROR(23007)`
- 实测多次：`{"code":"23007","msg":"密码错误"}`——码文不符，误导排障
- **修复方向**：一行改 `PASSWORD_ERROR.getCode()`；小 bugfix

## P3 — Skill / Harness 能力缺口

### B-06 Skill 无任何管理入口，且技能池全局共享
- 前端 0 页面、后端 0 REST；只能手工 INSERT `tbl_harness_skills`(+resources 表)
- 表无 agent 维度字段 → 所有 harness 智能体共享同一技能池
- `phoenix.agent.skillPath` 属性定义了但全仓无引用（死配置）
- **修复方向**：缺失功能非 bug → 立项 spec「智能体 Skill 管理」

### B-07 harness 的 shell 能力与远程文件系统硬绑互斥
- `ShellExecuteTool` 只接受 `AbstractSandboxFilesystem`；项目用的 `RemoteFilesystemSpec`(redis/pg) 只实现 `AbstractFilesystem` → 想用脚本类技能必须换 Local FS 且去掉 `disableShellTool()`，两处都写死在 Java builder 里，无配置开关
- **修复方向**：把 filesystem 策略与 shell 开关提为 `phoenix.agent.*` 配置项（配合权限规则）

### B-08 `application-test.yml` 与实际部署环境不一致
- datasource `password: 123456`（本机容器为 phoenix）；注释里的 maven settings 路径指向他人机器
- AGENTS.md 宣称「零 resource 文件」，本模块实际有 application.yml + application-test.yml（profile=test 默认激活）
- **修复方向**：改文档或改配置二选一，另议

---

## 工作区遗留状态（非缺陷，处置需确认）
- `RulesHarnessAgent.java` 有**未提交实验改动**（开 shell + LocalFilesystemSpec），已编译进 `.mvn-home`；还原：`git checkout -- phoenix-agent/phoenix-agent-core/src/main/java/com/phoenix/agent/harness/agent/rules/RulesHarnessAgent.java` 后重新 install
- 库中实验数据：技能 `py-fib-demo`（含 scripts/fib.py）、`phx-poem-weather`；前台账号 chenzhuo 密码现=12345678；两套账号表密码现均=12345678
- 未跟踪：`.mvn-home/`、`.pnpm-store/`、`diagrams/`（建议进 .gitignore）；`AGENTS.md` 有 init 追加段（备份 `AGENTS.md.bak.*`）

## 修复状态跟踪
| ID | 级别 | 状态 |
|---|---|---|
| B-01 | P1 | 未修（容器内已临时补序列） |
| B-02 | P1 | 未修 |
| B-03 | P2 | 未修 |
| B-04 | P2 | 不修（设计问题→技术债/spec） |
| B-05 | P2 | 未修（一行） |
| B-06 | P3 | 不修（缺功能→待立项） |
| B-07 | P3 | 未修 |
| B-08 | P3 | 未修 |
