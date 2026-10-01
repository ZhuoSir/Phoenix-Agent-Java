# Bug 列表 · Phoenix-Agent-Java
> 编号永久不复用 | 状态必须带版本 | 修 bug 的 commit footer 必带 `Bug: BUG-xx`
> 状态机: 新建 → 已规划(vX.Y.Z) → 已修复(vX.Y.Z) → 已验证(vX.Y.Z) → 已发布(vX.Y.Z)；旁路: 已延期(vA→vB) / 不修复(原因+批准人)
> **v1.2.1 发布注记（2026-10-01）**：本版本 4 条修复（BUG-31/32/34/37）全部「已发布(v1.2.1)」，随 tag v1.2.1 出；均经 A 交付栈实测。
> **v1.2.0 发布注记（2026-10-01）**：本版本全部 21 条修复（16 已修复+5 已验证→已发布+1 已规避）随 tag v1.2.0 出；仅复验过的 5 条按状态机标「已发布」，其余「已修复(v1.2.0)」即表示修复代码已含于 v1.2.0（已发布未逐条复验口径），追溯以 tag 内容为准。
> **编号迁移（2026-09-27）**：早期清单用 `B-01~B-20`，按细则一并迁为 `BUG-01~BUG-20`（一一对应，旧编号保留在各条「关联」列，历史 commit/changelog 里的 `B-xx` 仍可对照）。
> **版本列说明**：里程碑 **v1.2.0** 已立项（2026-09-27，见 `releases/v1.2.0/MILESTONE.md`），本版本挂接的 19 条修复项中 BUG-20/22/23/30 经用户界面走查/复测于 2026-09-30 转「已验证(v1.2.0)」，其余为「已修复(v1.2.0)」；
> 「已验证(v1.2.0)」需重跑复现步骤且验证人=用户或其明确委托；M4 发版时批量转「已发布(v1.2.0)」+日期。
> 本表是状态唯一权威；每条的现象/根因/证据/验证输出见文末 **明细留档**（历史条目只增不删）。
> 勘误留存：早前两条口头推断经核实不成立，未登记——① buildLoginResult 对 NULL 密码 NPE（实际只走「用户名或密码错误」分支）② yml 有 `server.port=3333`（文件与日志均无此值）。

| 编号 | 标题 | 严重度 | 发现于 | 状态 | 修复版本 | 关联 |
|---|---|---|---|---|---|---|
| BUG-01 | `all_schema.sql` 缺 5 个序列 → 全新环境导入必失败 | P1 | 本地部署(2026-09-27) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | 原 B-01; commit 34877da；已合并 main(c90071d)；09-30 用户委托 agent 复验（复现步骤全过，明细见文末） |
| BUG-02 | HumanInTheLoop 未挂 skillRepository，技能静默加载不到 | P1 | 本地部署(2026-09-27) | 新建 | - | 原 B-02; 一行修复 |
| BUG-03 | 前台账号创建时密码无必填校验 → 制造永久无法登录的死账号 | P2 | 本地部署排障(2026-09-27) | 新建 | - | 原 B-03 |
| BUG-04 | 双账号体系：两张表、状态语义相反、密码互不相通 | P2 | 本地部署排障(2026-09-27) | 新建 | - | 原 B-04; 设计问题，建议立项/不修复**待用户批准** |
| BUG-05 | 登录密码错误返回了「原密码错误」的错误码（23007） | P2 | 本地部署排障(2026-09-27) | 新建 | - | 原 B-05; 一行修复 |
| BUG-06 | 技能无管理入口且技能池全局共享（缺失功能 + 死配置） | P3 | 本地部署(2026-09-27) | 已修复(v1.2.0) | v1.2.0 | 原 B-06; Spec: 20260927_agent-skill-management（`phoenix.agent.skillPath` 仍是死配置） |
| BUG-07 | harness 的 shell 能力与远程文件系统硬绑互斥、无配置开关 | P3 | 本地部署(2026-09-27) | 已修复(v1.2.0) | v1.2.0 | 原 B-07; Spec: 20260927_dynamic-agent-types T-05（filesystem_policy 配置化，remote 自动关 shell） |
| BUG-08 | `application-test.yml` 与实际部署环境不一致（密码/他人机器路径/与 AGENTS.md 冲突） | P3 | init体检(2026-09-27) | 新建 | - | 原 B-08 |
| BUG-09 | 前台与 harness 智能体无对话通道（前端指向不存在的端点） | P2 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | 原 B-09; Spec: 20260927_agent-skill-management T-11/T-14 |
| BUG-10 | 后台新建智能体 type 为空、且无类型选择入口（harness 无法后台创建） | P2 | 对话中(2026-09-27) | 已修复(v1.2.0) | v1.2.0 | 原 B-10; Spec: 20260927_dynamic-agent-types T-04/T-14（存量自注册类保留属 BL-03，非缺陷） |
| BUG-11 | 前台 HITL 确认接口缺失，前端调用必然 404 | P2 | spec Implement中(20260927_dynamic-agent-types) | 新建 | - | 原 B-11 |
| BUG-12 | SqlSecurityValidator 子串匹配误杀只读查询（`create_time` 等） | P2 | spec Implement中(20260927_dynamic-agent-types T-08) | 已修复(v1.2.0) | v1.2.0 | 原 B-12; jshell 8 例验证 |
| BUG-13 | EMBEDDING 模型测试恒 404（base_url 多带 `/v1` + 模型名不被兼容模式支持） | P2 | 对话中(2026-09-27) | 已修复(v1.2.0) | v1.2.0 | 原 B-13; 配置修正 + 实测矩阵 |
| BUG-14 | 删除智能体残留孤儿数据（运行配置/技能绑定/组授权） | P2 | spec Implement中(20260927_dynamic-agent-types T-16) | 新建 | - | 原 B-14; 本次孤儿行已手工清理 |
| BUG-15 | 智能体列表关键字搜索在 PG 下 500（`CONCAT` 参数类型不可推断） | P2 | spec Implement中(20260927_dynamic-agent-types T-16) | 已修复(v1.2.0) | v1.2.0 | 原 B-15; commit 00eb0ff |
| BUG-16 | 三张 Spring AI 向量表缺主键 → `ON CONFLICT` 插入必失败 | P2 | spec Implement中(20260927_dynamic-agent-types T-13) | 已修复(v1.2.0) | v1.2.0 | 原 B-16; commit c0fe1b9（基线 + 运行库双修） |
| BUG-17 | 图链路在非 HTTP 调用方取 Sa-Token 登录态直接抛异常 | P2 | spec Implement中(20260927_dynamic-agent-types T-09) | 已修复(v1.2.0) | v1.2.0 | 原 B-17; commit e74e7ec |
| BUG-18 | QA/FAQ 类型知识只向量化「问题」，答案不参与检索 | P2 | spec Implement中(20260927_dynamic-agent-types T-16) | 新建 | - | 原 B-18; 需产品定口径 |
| BUG-19 | harness 对话入参缺失时返回 500（应给明确错误码） | P3 | spec Implement中(20260927_dynamic-agent-types T-11) | 已修复(v1.2.0) | v1.2.0 | 原 B-19; commit dc9b333 |
| BUG-20 | 启用模型会把同类型其他模型一并置为启用（SQL 与注释相反） | P1 | spec Implement中(20260927_agent-config-ai-generate T-02) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | 原 B-20; commit b8f728a（启用改多值集合，方法已删）；已随 merge c90071d 合并 main |
| BUG-21 | 模型管理「模型类型」列把 AUDIO 显示成「嵌入模型」 | P3 | 对话中(2026-09-27) | 新建 | - | `views/modelconf/index.vue:458`（三类型都能设默认后才暴露） |
| BUG-22 | AI 生成「描述」返回整段 JSON（用户实测） | P2 | 对话中(2026-09-27) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | Spec: 20260927_agent-config-ai-generate; commit edd9ad9；已合并 main(c90071d)；用户复测确认(09-30) |
| BUG-23 | 生成超时 60s 切断**已成功**的调用（实测耗时 45~70s） | P2 | spec Implement中(20260927_agent-config-ai-generate T-09) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | 同 commit edd9ad9（缓解：90s 且可配置）；已随 merge c90071d 合并 main |
| BUG-24 | 响应式超时无法中断底层阻塞调用（超时后仍在消耗 token） | P3 | spec Implement中(20260927_agent-config-ai-generate T-09) | 新建 | - | plan 风险⑦已接受该限制，建议转技术债 |
| BUG-25 | 前端 dev 命令未按 `.env.development` 的 `VITE_PORT` 起端口（5777 起成 5173） | P3 | 对话中(2026-09-27) | 新建 | - | 临时规避：启动加 `--port 5777` |
| BUG-26 | 技能上传前端未带 multipart 头 → 上传 HTTP 415 | P3 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | `api/core/skill.ts:80` 补 `Content-Type: multipart/form-data` |
| BUG-27 | 技能 ZIP 校验报错信息误导（真实规则是「条目须有根目录」） | P3 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | 新增 `SkillZipSanitizer`（剥 `__MACOSX/`、`.DS_Store`、`._*`，统一包一层合成根） |
| BUG-28 | `ReturnVo.ok(String)` 命中 msg 重载 → 误把 data 当 msg 传出 | P3 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | `phoenix-tool/.../ReturnVo.java:84`；改用两参 `ok(msg, data)` |
| BUG-29 | `tbl_platform_group_agent_info.agent_id` 为 varchar，与 bigint 的 agentId 比较报错 | P3 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | 代码侧改 `String.valueOf(agentId)`；**列类型不一致的根因仍在**，建议统一 |
| BUG-30 | AI 生成请求走全局 30s 超时（实测生成 45~90s）→ 前端掐断请求、按钮转圈无结果（用户实测） | P2 | 界面走查(2026-09-30) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | Spec: 20260927_agent-config-ai-generate T-11；`api/core/agentProfile.ts` 单独设 `timeout: 120_000` |
| BUG-31 | 全新库首启 NPE：自注册智能体 `createHarnessAgent()` 在 `saveBySn` 之前读库，`HumanInTheLoop.java:87` 对 null agent 取 description 崩溃 | P1 | 交付包首启实测(20260930_allinone-docker-packaging) | 已发布(v1.2.1) 2026-10-01 | v1.2.1 | v1.2.1 根治=register() 先 saveBySn 再 create + HumanInTheLoop 判空双保险；**实测**：dump 克隆库删光 5 sn 行 → 新镜像首启 Started 成功零 NPE；交付包种子保留作双保险（挤入后归属 v1.2.0 发现） |
| BUG-32 | deepseek 模型走 AI 生成双字段时 42013（一次空 content、一次 JSON 未被解析出对象；qwen 同链路正常） | P2 | 交付包实测(20260930_allinone-docker-packaging) | 已发布(v1.2.1) 2026-10-01 | v1.2.1 | 疑输出形态（reasoning 混排/转义）与解析容错不足；v1.2.1 修复=空内容自动重试一次 + JSON 裸换行控制字符修复再解析 + 字段级正则兜底；**实测** deepseek 双项 19s 成功（43 字描述+595 字四段提示词） |
| BUG-33 | 前端生产包 API 双重 /api 前缀：axios baseURL=/api 且代码路径自带 /api（fetch 处 API_BASE_URL 同），**生产构建从未成功部署过**，dev 全靠 vite proxy rewrite 掩盖；浏览器实测登录 401 | P1 | 交付包 9080 用户实测(20260930_allinone-docker-packaging) | 已修复(v1.2.2) | v1.2.2 | 交付包 nginx 完全复刻 dev proxy 语义：凡 /api/* 剥一层再转后端（代码两种写法并存：双前缀域 login/agent/model + **单前缀 platform 域**，第一版只折双前缀导致 /api/platform/group-info/page 404→500，10-01 用户实测抓到已修正）；两形态 9/9 断言全绿；**根治=前端 baseURL 统一**（BL-18），nginx 折叠属包层适配非产品修复；**v1.2.2 已根治**(BL-18)：baseURL 置空+URL 直写真实路径+代理透传，折叠已删，verify 负断言防回潮；连带痊愈 logicalRelation/prompt-config/模板下载三个单前缀历史坏点 |
| BUG-34 | `GET /api/model-config/list` 响应体返回**明文 apiKey**（全局接口行为，非 docker 包引入） | P2 | 交付包验证接口巡检出 | 已发布(v1.2.1) 2026-10-01 | v1.2.1 | v1.2.1 修复=list 出口 sk-****尾4 脱敏 + 脱敏值按 id 回源（update 既有 **** 守护）+ 测试日志不落真实 key；前端编辑态提示语；**实测** 列表全脱敏、脱敏回传测试连接成功 |
| BUG-35 | 模型管理 maxTokens/temperature 从未传给 harness 对话请求（HarnessModelRegistry 两处 builder 缺 generateOptions），长回复被服务端默认上限截半（用户实测"开画执行到一半不执行"） | P1 | 9080 用户实测(2026-10-01) | 已修复(v1.2.0) | v1.2.0 | Spec: 20260930_allinone-docker-packaging 会话暴露；修复=两处 builder 补 GenerateOptions(maxTokens,temperature)；实测 400 行数到完（fix 前同类 14K 字符断在半句） |
| BUG-36 | 模型管理表单 max_tokens 硬编码上限 10000，用户无法为高上限模型配置 | P3 | 交付包验收期用户反馈(20261001) | 已修复(v1.2.0) | v1.2.0 | Spec: 20260930_allinone-docker-packaging 连带；`modelconf/index.vue` 去 :max 与校验 max（后端/库表本无限制） |
| BUG-37 | Spring AI 底层 HTTP 栈宣告支持 brotli（Accept-Encoding: …, br）但无 br 解码器，deepseek(CloudFront) 命中 br 时响应体解不出 → 连接测试/生成 JSON EOF、空内容（间歇，按 CDN 节点分布；curl 不带 br 故正常） | P1 | 交付包 deepseek 连接测试复现(2026-10-01) | 已发布(v1.2.1) 2026-10-01 | v1.2.1 | Spec: 20260930_allinone-docker-packaging；修复=`DynamicModelFactory.noBrotli()` 对所有 OpenAI 兼容 RestClient（含代理分支）钉死 `Accept-Encoding: identity`；实测 deepseek×3 + qwen 连接测试全过、双项生成 21s 四段齐全。亦为 BUG-22/23/32 间歇截断的总根因 |

---

## 明细留档（历史证据，只增不删）

### BUG-01 `all_schema.sql` 缺 5 个序列 → 全新环境导入必失败
- **现象**：demo 表 `tbl_data_categories / order_items / orders / products / users` 建表报 `relation "..._id_seq" does not exist`（CREATE TABLE 引用 `nextval()` 但全文没有对应 CREATE SEQUENCE）；连带 `tbl_tmp_*` 3 张表也不存在，数据段进入 aborted 事务后级联报错
- **影响**：任何全新初始化必复现；本次先在运行容器内手工补齐（SQL 文件当时未修），重导仍会炸
- **修复（已完成）**：`sql/all_schema.sql` 扩展/角色段后补 5 条 `CREATE SEQUENCE IF NOT EXISTS`（tbl_data_categories/order_items/orders/products/users 的 id 序列），文件末尾按既有风格补 5 条 `ALTER SEQUENCE ... OWNED BY`
- **验证**：全新空库重放基线**零报错**（修前 64 处）；在同一空库继续跑 `01→02→03→04→05` 全链 `ON_ERROR_STOP=1` 零报错，二次重放同样零报错；05 回滚零报错
- **附带**：这批表 + `tbl_tmp_*` 疑似早期 demo 遗留、应用代码零引用，可考虑整体移出种子（另议）
- **复验（2026-09-30，验证人=用户明确委托，agent 执行）**：全新临时库 `phx_verify_09301810` 按全序重放 `all_schema.sql → 01 → 02 → 03 → 04 → 05`（`ON_ERROR_STOP=1`）六个文件全部 errors=0；断言 `is_default` 列存在、部分唯一索引 `uk_dmc_type_default` 建成；05 重跑幂等 errors=0；05 回滚 errors=0 且列消失；临时库已 DROP。验证人：用户委托（ask_user_question 记录在会话）

### BUG-02 HumanInTheLoop 智能体未挂 skillRepository（同框架行为不一致）
- **现象**：两个 harness 智能体里只有 `RulesHarnessAgent` 能加载技能，`HumanInTheLoop` 静默加载不到
- **根因**：`HumanInTheLoop.createHarnessAgent()` 的 builder 缺 `.skillRepository(postgresSkillRepository)` 一行（grep 计数 1 vs 0）
- **修复方向**：补一行；小 bugfix 可直接修

### BUG-03 前台账号创建时密码无必填校验 → 制造永久无法登录的死账号
- **现象**：后台「前台账号」新建表单密码留空也能保存成功；此后任何密码登录都返回「用户名或密码错误」，无「未设密码」提示
- **根因**：前端 `account-info/data.ts:186` password 无 required 规则（placeholder「留空则不修改」是编辑场景文案被带到新建）；后端 `AccountInfoServiceImpl.save():298` `isNotBlank` 才加密，NULL 直接入库
- **实测**：chenzhuo 前台账号 12:19 创建后 password=NULL
- **修复方向**：新建必填（前端 rules + 后端 create 校验），编辑保留「留空不改」

### BUG-04 双账号体系：两张表、状态语义相反、密码互不相通
- 管理端 `POST /api/privilege/auth/login` → `tbl_privilege_user`（status **1=禁用**）
- 前台 `POST /auth/login` → `tbl_platform_account_info`（status **0=禁用**）
- 同名账号是两条独立记录，后台改了 A 表密码、前台登 B 表——本地部署排障在此耗费大量时间
- 列表接口还把 password 脱敏成 null（`AccountInfoServiceImpl:108`），界面永远「看起来没密码」，加剧误判
- **处置**：属设计问题 → 建议记技术债/立项（统一账号中心或至少同页提示）。**状态不修复需用户批准，故仍为「新建」**

### BUG-05 登录密码错误返回了「原密码错误」的错误码
- `LoginServiceImpl:69-71`：密码比对失败返回 message=`PASSWORD_ERROR(密码错误)` 但 code=`OLD_PASSWORD_ERROR(23007)`
- 实测多次：`{"code":"23007","msg":"密码错误"}`——码文不符，误导排障
- **修复方向**：一行改 `PASSWORD_ERROR.getCode()`

### BUG-06 技能无任何管理入口，且技能池全局共享
- 修复前：前端 0 页面、后端 0 REST；只能手工 INSERT `tbl_harness_skills`(+resources 表)；表无 agent 维度字段 → 所有 harness 智能体共享同一技能池
- **已交付**（`agent-skill-management`）：技能管理菜单/上传/发布/删除、智能体-技能绑定、按智能体隔离的技能池（`AgentScopedSkillRepository`）、前台技能区与显式执行
- **遗留**：`phoenix.agent.skillPath`（`PhoenixAgentProperties:12`）定义了但全仓仍无引用（死配置）→ 建议清理或接线

### BUG-07 harness 的 shell 能力与远程文件系统硬绑互斥
- `ShellExecuteTool` 只接受 `AbstractSandboxFilesystem`；项目原用的 `RemoteFilesystemSpec`(redis/pg) 只实现 `AbstractFilesystem` → 想用脚本类技能必须换 Local FS 且去掉 `disableShellTool()`，两处都写死在 Java builder 里，无配置开关
- **已修复**（`dynamic-agent-types` T-05）：文件系统策略提为库配置 `tbl_data_agent_runtime_config.filesystem_policy`（local/remote）——local 走本地沙箱（可 shell），remote 自动 `disableShellTool()`，不再需要改 Java

### BUG-08 `application-test.yml` 与实际部署环境不一致
- datasource `password: 123456`（本机容器为 phoenix）；注释里的 maven settings 路径指向他人机器
- AGENTS.md 宣称「零 resource 文件」，本模块实际有 `application.yml` + `application-test.yml`（profile=test 默认激活）
- **修复方向**：改文档或改配置二选一，另议

### BUG-09 前台与 harness 智能体无对话通道
- **现象**：前台对话页 auth 存在 harness 分支，但指向 `/api/front/harness/chat` —— 后端**无此端点**
- **已修复**（`agent-skill-management` T-11/T-14）：后端新增 `/platform/harness/chat`（前台身份 + 组可见性 + 技能三重校验），前端 transport 改指该端点
- **注**：该 spec 的 requirements R-09 引用 B-09 即本条（原清单漏登记，2026-09-27 补记）

### BUG-10 后台新建智能体 type 为空，且无类型选择入口
- **现象**：新建抽屉提交 payload 不含 `type`，`tbl_data_agent.type` 无 DB 默认值 → type=NULL；列表类型列显示空白（用户实测：新建后类型概念不清）
- **连带影响（已消解）**：原「harness 无法后台创建」——harness 由 Java `@Component` 启动时自注册，运行时经 `HarnessStaticLoader` 按 sn 取内存实例。`dynamic-agent-types` 把 harness 构建参数数据驱动化（运行配置 + Factory/Registry），后台可建可跑
- **已修复**（`dynamic-agent-types`）：新建服务端强制 `type=harness` + `03` 升级件回填并设默认值；列表去掉四个类型标签；存量 5 个自注册类保留（其迁移/删除属 backlog BL-03，不是缺陷）

### BUG-11 前台 HITL 确认接口缺失（前端调用 404）
- **现象**：前台对话的人工确认走 `POST /api/front/harness/confirm`（`api/front/chat.ts` `confirmFrontHarnessChat`），但后端全仓库无此端点（后台为 `/api/admin/harness/confirm`；前台控制器只有 `getMySkills`/`chat`）
- **影响**：前台对话一旦触发需要确认的工具调用（HITL），确认按钮必然失败（404）
- **修复方向**：在 `FrontHarnessController` 增加 `/harness/confirm`（复用 `HarnessChatService.confirmStream`，带组可见性校验）；或前台直接打后台确认端点（需权限口径确认）

### BUG-12 SqlSecurityValidator 子串匹配误杀只读查询
- **现象**：`SqlSecurityValidator.validate` 用 `upperSql.contains(keyword)` 判危险关键字 → `create_time`、`update_time`、`delete_flag`、`last_update` 等普通列名一律命中 `CREATE`/`UPDATE`/`DELETE`，只读 SELECT 被误判
- **影响面**：任何取数/问数链路的只读 SQL 都会被大面积误杀（取数工具一上线即暴露）；`BpmToolSearch` 同受影响
- **修复**：改**整词匹配**（`\b(...)\b`）并先剔除字符串字面量/引用标识符
- **验证（jshell 直调，JDK23）**：`select create_time, update_time from t` → SAFE；`select delete_flag, last_update from t` → SAFE；`WITH ... select` → SAFE；`DROP TABLE x` → BLOCKED；`select * from t; delete from t` → BLOCKED[DELETE]；`UPDATE t SET a=1` → BLOCKED；`select ... where b='drop table x'` → SAFE

### BUG-13 EMBEDDING 模型测试恒 404
- **两个独立原因**：① base_url 多带 `/v1`——应用用 Spring AI `OpenAiApi`，自动在 base_url 后拼 `/v1/embeddings`，故 `…/compatible-mode/v1` → 实际请求 `…/v1/v1/embeddings` → 404（对照 CHAT 配置 `https://api.deepseek.com` 不带 `/v1` 所以正常）；② 模型名 `qwen3-vl-embedding` 不被兼容模式支持 → `404 model_not_supported`
- **实测矩阵**（同一 key，直连 + 经应用测试接口双向验证）：

  | base_url | model | 结果 |
  |---|---|---|
  | `…/compatible-mode/v1` | `qwen3-vl-embedding` | 404（双重错误） |
  | `…/compatible-mode/v1` | `text-embedding-v4` | 404（仅 base_url 问题） |
  | `…/compatible-mode` | `qwen3-vl-embedding` | 404（仅模型名问题） |
  | `…/compatible-mode` | `text-embedding-v4` | ✅ 成功 |

- **处理**：id=6 改为 `base_url=https://dashscope.aliyuncs.com/compatible-mode` + `model_name=text-embedding-v4`（v3 亦可；v2 固定 1536 维与 `vector(512)` 不匹配）。原值：`…/compatible-mode/v1` + `qwen3-vl-embedding`
- **验证**：应用「测试」→ `连接测试成功！模型可用。`；真实写入 → `Schema初始化成功`，`tbl_vector_store_simple_data` 出现 agentId=25 的 28 条 512 维向量

### BUG-14 删除智能体残留孤儿数据
- **现象**：`DELETE /api/agent/{id}` 只删 `tbl_data_agent` 行；实测删除智能体 29 后，`tbl_data_agent_runtime_config`、`tbl_data_agent_skill_info`、`tbl_platform_group_agent_info` **各残留 1 行**
- **影响**：孤儿行长期占用/污染统计，组授权随历史累计
- **修复方向**：删除智能体时级联清理三张关联表（或查询侧统一加 `exists` 校验）；本次回归产生的孤儿行已手工清理

### BUG-15 智能体列表关键字搜索在 PG 下 500
- **现象**：`GET /api/agent/list?keyword=xxx` → `500 {"message":"服务器内部错误"}`，日志 `PSQLException: ERROR: could not determine data type of parameter $1`，SQL 为 `name LIKE CONCAT('%', ?, '%')`
- **根因**：PostgreSQL 无法从 `CONCAT` 推断未定类型参数
- **修复**：`AgentMapper.searchByKeyword` 改 `'%' || CAST(#{keyword} AS text) || '%'`
- **验证**：keyword=制度/巡逻（存量）→ `[]`；智能体/销售 → 命中平台内创建的智能体，均 HTTP 200

### BUG-16 三张 Spring AI 向量表缺主键 → ON CONFLICT 插入必失败
- **现象**：embedding 修好后 `POST /api/agent/{id}/datasources/init` 仍失败：`BatchUpdateException: INSERT INTO public.tbl_vector_store_simple_data …` → `PSQLException: there is no unique or exclusion constraint matching the ON CONFLICT specification`
- **根因**：`all_schema.sql` 建的 `tbl_vector_store_simple_data / rag / user_memory` 只有 HNSW 向量索引、**没有主键/唯一约束**，而 Spring AI `PgVectorStore` 的 upsert 依赖 `ON CONFLICT (id)`
- **影响**：所有 schema/知识文档写入向量库的路径全部不可用 → 也解释了为何 `/api/agent/{id}/datasources/init` 从来没成功过
- **修复**：① 运行库三表补 `PRIMARY KEY (id)`；② 基线 `all_schema.sql` 按文件既有风格在末尾 ALTER 块补 3 条（含一次「误把语句插到 CREATE TABLE 之前」的返工，已改为文件末尾追加并复验）
- **验证**：空库重放报错数与基线一致（当时 64 处，全为 BUG-01 既有问题，无一条与向量表相关），三张向量表主键建成

### BUG-17 图链路在非 HTTP 调用方取 Sa-Token 登录态直接抛异常
- **现象**：`SaTokenContext 上下文尚未初始化`（图内 `handleNewProcess` → `builerLoginVo()` → `StpUtil.getSession()`）
- **根因**：`GraphServiceImpl.builerLoginVo()` 无条件取当前登录态；深度分析工具运行在 AgentScope 工具线程（`boundedElastic`），Sa-Token 上下文不随行 → 抛异常打断整条图链路；MCP 工具回调等非 HTTP 调用方同理
- **修复**：捕获无上下文异常并降级为空串（与原 `loginVO == null` 分支同义）
- **验证**：重跑深度分析 → `深度分析完成: agentId=25, datasourceId=11, elapsedMs=93485, chars=6024`，产出按 type 分组 + 时间分布归因的完整报告

### BUG-18 QA/FAQ 类型知识只向量化「问题」，答案不参与检索
- **现象**：`type=QA` 建知识（question+content）后，向量库只有 1 条文档且 `content` = **question**，metadata 仅 `{agentId, vectorType:agentKnowledge, agentKnowledgeId, concreteAgentKnowledgeType:QA}`，答案文本没进向量库。检索工具返回的就是这个问题，模型据此无法作答（模型原话：「疑似入库时内容为空或索引只存了标题」）
- **影响**：QA/FAQ 类型知识走「向量检索 → 作答」链路都拿不到答案（只能用 DOCUMENT）
- **可能修复**：① 检索工具在 metadata 含 `agentKnowledgeId` 且类型为 QA/FAQ 时回查 `tbl_data_agent_knowledge.content` 一并返回；② 或嵌入端把 answer 写入文档文本。**需产品确认口径**
- **对照验证**：改 `type=DOCUMENT`（上传 .md）后向量内容为文件正文，检索作答正常

### BUG-19 harness 对话入参缺失时返回 500
- **现象**：`POST /api/admin/harness/chat` 既不传 `agentId` 也不传 `harnessSn` → `HTTP 500`（落到 `loadAgent(null)` 抛 IllegalArgumentException）
- **修复**：`HarnessChatServiceImpl` 显式校验，两者皆缺抛 `InvalidInputException` → 400 + 「agentId 与 harnessSn 至少需要一个」；`confirmStream` 同理
- **验证**：三分支复测（仅 agentId / 仅 harnessSn / 皆缺）分别为 200 / 200 / 400

### BUG-20 启用模型会把同类型其他模型一并置为启用
- **位置**：`phoenix-data/phoenix-data-core/.../mapper/ModelConfigMapper.java`（原 50-52 行）
- **证据**：方法注释「将指定类型的其他模型配置设为**非启用**状态」，SQL 却是 `UPDATE tbl_data_model_config SET is_active = true WHERE model_type = ? AND id != ? …` → 每次「启用」都把同类型其他所有条置为 `is_active=true`
- **连带影响**：`selectActiveByType(...) LIMIT 1`（无 ORDER BY）在多条启用时取到哪条不确定 → 对话/向量化用的模型可能静默漂移；这也是 `agent-config-ai-generate` 把「启用/默认」拆开的直接动因
- **修复**：删除该方法与调用点，启用改「可多选集合」，取模型改判「每类型唯一默认」（部分唯一索引兜底）

### BUG-21 模型管理「模型类型」列把 AUDIO 显示成「嵌入模型」
- **位置**：`web-frontend/apps/admin-ui/src/views/modelconf/index.vue:458` —— `scope.row.modelType === 'CHAT' ? '对话模型' : '嵌入模型'`
- **影响**：`ModelType` 实际有 CHAT/EMBEDDING/**AUDIO** 三种；三种类型现在都能设默认，音频模型会被错显示为「嵌入模型」，运维判断配置类型时被误导
- **修复方向**：改成三元映射（或按枚举渲染），一行

### BUG-22 AI 生成「描述」返回整段 JSON（用户实测）
- **现象**：点「AI 生成描述」后，描述框里出现 `{"description":"...","prompt":"..."}` 整段 JSON
- **根因**：输出协议只按 `withDescription` 分支决定，只要描述时也要求模型输出两项 JSON；服务又把整段原文塞进描述字段（`AgentProfilePromptTemplates` / `AgentProfileGenerationService`）
- **修复**：新增 `outputContract(withDescription, withPrompt)` 三态协议（两项才要 JSON；只要提示词→只要 md 正文；只要描述→只要一句话纯文本）+ 防御性取 `description` 字段
- **验证**：`targets=[DESCRIPTION]` → 「面向法务、采购和管理人员，自动审阅合同条款…」49 字，`含JSON=False`、`prompt=None`

### BUG-23 生成超时 60s 切断已成功的调用
- **现象**：两项同时生成返回 42012 失败，但日志显示底层调用随后完成（`AI 生成完成: 描述字数=40, 提示词字数=461`）
- **根因**：两项一次出要模型输出 JSON（含整段 md 提示词），实测 45~70s；原响应式超时 60s 先触发
- **修复**：超时 90s 且可配置 `phoenix.agent.profile-generate-timeout-seconds`
- **验证**：重跑两项生成 → 45.7s 返回，描述 55 字 + 提示词 544 字四段齐备

### BUG-24 响应式超时无法中断底层阻塞调用
- **现象**：`Mono.timeout()` 触发后响应已返回失败，但底层 `ChatClient.call()` 仍在 `boundedElastic` 上跑完并记日志（继续消耗 token）
- **影响**：超时只是"前端不再等"，不节省成本；深度分析工具（180s）与 AI 生成（90s）同源
- **处置**：`agent-config-ai-generate` plan 风险⑦ 已接受该限制（前端忽略迟到响应）；**建议转技术债**（真取消需改用异步/可中断客户端）

### BUG-25 前端 dev 命令未按 `.env.development` 的 `VITE_PORT` 起端口
- **现象**：`cd web-frontend/apps/admin-ui && pnpm vite --mode development` 起在 **5173**，而 `.env.development` 里 `VITE_PORT=5777`（此前由 `pnpm dev:ele` 启动时才生效）
- **影响**：重启前端后端口与既有访问地址/书签不一致，需人工发现
- **规避**：启动时显式 `--port 5777`；建议在 package.json 的 dev 脚本里固定端口

### BUG-26 技能上传前端未带 multipart 头 → HTTP 415
- **现象**：上传 ZIP 报 `415 Unsupported Media Type: Content type 'application/json;charset=UTF-8' not supported`
- **根因**：前端上传未显式声明 multipart
- **修复**：`api/core/skill.ts:80` 请求头补 `'Content-Type': 'multipart/form-data'`
- **验证**：上传 skill zip 返回 `{"code":"100","data":<id>}`

### BUG-27 技能 ZIP 校验报错信息误导
- **现象**：正常 macOS 压缩的技能包被拒，报 `Zip entries must be under a single root directory.`
- **根因（字节码核实）**：上游真实规则是「每个条目在 index>0 处必须含 `/`」，而 `__MACOSX/`、`.DS_Store`、AppleDouble `._*` 等系统垃圾条目会破坏该规则，报错文案与真实原因不符
- **修复**：新增 `SkillZipSanitizer`（剥系统垃圾、按最浅 SKILL.md 定根、统一包一层 `skill-package/` 合成根）
- **验证**：5 种 zip 形态（含仅 SKILL.md、带 __MACOSX、多根目录）均通过

### BUG-28 `ReturnVo.ok(String)` 命中 msg 重载
- **现象**：`ReturnVo.ok(sn)` 编译通过但数据落到了 `msg` 字段，前端 `data` 为 null
- **根因**：`phoenix-tool/.../ReturnVo.java:84` 存在 `ok(String msg)` 重载，单 String 入参优先命中它
- **修复**：统一改用两参 `ReturnVo.ok("操作成功!", data)`
- **附注**：属 API 设计陷阱（重载歧义），新代码调用 `ReturnVo.ok` 一律显式两参

### BUG-29 `tbl_platform_group_agent_info.agent_id` 为 varchar，与 bigint 比较报错
- **现象**：按 agentId 查组授权时报 `operator does not exist: character varying = bigint`
- **根因**：该类表 `agent_id` 建为 `varchar`，而 `tbl_data_agent.id` 是 bigint（同项目里 `tbl_data_agent_skill_info.agent_id` 又是 bigint，口径不统一）
- **修复**：代码侧统一 `String.valueOf(agentId)` 比较
- **遗留根因**：列类型不一致仍在，建议后续统一为 bigint（涉及迁移，另议）

---

### BUG-30 AI 生成请求走全局 30s 超时（实测 45~90s）
- **现象**：点「AI 生成描述与提示词」→ 报「timeout of 30000ms exceeded」/按钮转圈拿不到结果；后端日志显示生成实际在跑（甚至已出结果）
- **根因**：`request-client.ts` 全局默认 `timeout: 30_000`；本 spec 只放宽了**后端** 90s（BUG-23），前端这一跳没人管。用户此前反馈的「提示30秒超时」是真实缺陷，非旧包残留（我初判有误，走查证实）
- **叠加环境因素**：走查当天 macOS 系统代理残留指向已退出的 Clash(127.0.0.1:7890)，JDK 把 `socksProxyHost/http.proxyHost` 注入 JVM → 后端出站连接被拒 + JDBC 走 SOCKS 失败。处置：Clash 代理配置清空后干净重启（未加启动参数亦恢复）；pgjdbc 42.4.1 的 SOCKS 读取路径是长期风险，见关联说明
- **修复**：`generateProfileApi` 单独 `timeout: 120_000`（覆盖全局 30s，与后端 90s+余量对齐）；错误分支已有 `finally` 清 loading，无需改
- **验证**：待用户复测生成（deepseek 直连可达已验证：401/0.27s）

### BUG-33 前端生产包 API 双重 /api 前缀
- **现象**：9080 浏览器登录 401「未授权」；技能管理发布/授权弹窗 /api/platform/group-info/page 500
- **根因**：requestClient baseURL=/api 且 api 文件路径自带 /api/...（fetch 处 API_BASE_URL 拼接同）→ 浏览器实发 /api/api/*；platform 域 Controller 无 /api 前缀 → 该域实发单前缀。dev 全靠 vite proxy 剥一层 /api 掩盖，**生产构建从未成功部署过**
- **修复（包层）**：交付包 nginx 完全复刻 dev 语义——凡 /api/* 剥一层转发；两形态实测全通（verify [5][9]）
- **遗留**：根治=前端 baseURL 统一（BL-18）；nginx 折叠属包层适配非产品修复
- **教训**：初版断言测了浏览器不会发的 URL 形态，误导一轮修复方向——断言必须按真实抓包形态写

### BUG-34 model-config 列表接口回显明文 apiKey
- **现象**：GET /api/model-config/list 返回体含 apiKey 原值（交付包接口巡检发现）
- **影响**：登录用户可经列表接口取得全部模型密钥，接口层无脱敏
- **建议**：出口脱敏（masked/尾4位）；涉及契约变更另立需求。状态保持新建待排期

### BUG-35 harness 对话链路不传 maxTokens/temperature
- **现象**：智能体长回复执行到一半停（diagram「开画」断在"我就直接生成文件。"）
- **根因**：HarnessModelRegistry 两处 OpenAIChatModel.builder() 未设 generateOptions，模型管理配置对对话链路从未生效（Spring AI 路径有传，仅 harness 漏）
- **修复**：两处补 GenerateOptions(maxTokens,temperature)；部署后实测 400 行数到 400|160000 完整返回

### BUG-36 模型管理表单 max_tokens 上限 10000
- **现象**：高上限模型无法配置更大值
- **根因**：前端两处硬编码（校验 max:10_000 + ElInputNumber :max），后端/库表本无限制
- **修复**：去上限纯手填（min 100 保留），提示语补 token 语义与"超模型上限按模型截断"说明

### BUG-37 brotli 协商导致 deepseek 响应体解不出（连接测试/生成间歇失败总根因）
- **现象**：点 deepseek「测试连接」报「连接测试失败: Error while extracting response for type [ChatCompletion]」；生成侧早前偶发空内容/JSON 截断（BUG-22/23/32 同源）
- **根因**：Spring AI 的 RestClient 请求头带 `Accept-Encoding: gzip, x-gzip, deflate, br`，其中 br(brotli) 客户端并不能解码；deepseek 经 CloudFront/ELB 会择优回 `content-encoding: br` → 响应体无法解码 → Jackson 读到的是截断/乱码 → EOF
- **取证**：nc 抓出站请求头含 `br`；带同头 curl deepseek 返回 `content-encoding: br`（HTTP/2）；hc5 直连不带 br 则 200 正常
- **修复**：`DynamicModelFactory.noBrotli(RestClient.Builder)` 用 requestInterceptor 将出站 `Accept-Encoding` 统一置 `identity`（chat/embedding/audio 全分支，含代理分支）
- **验证**：部署 v1.2.1-dev 镜像后 deepseek 连接测试连续 3/3 + qwen 1/1 全过；双项生成 21s 成功、描述 68 字 + 四段提示词 592 字
- **注**：identity 关闭压缩，OpenAI 补全响应体本就小（KB 级），对带宽无实质影响

## 工作区遗留状态（非缺陷，处置需确认）
- `RulesHarnessAgent.java` 有**未提交实验改动**（开 shell + LocalFilesystemSpec），已编译进 `.mvn-home`；还原：`git checkout -- phoenix-agent/phoenix-agent-core/src/main/java/com/phoenix/agent/harness/agent/rules/RulesHarnessAgent.java` 后重新 install
- 环境改动（验证所必需，已记录）：数据源 id=11「本地测试」的 `host` 由不可达的 `192.168.66.19` 改为 `127.0.0.1`、`connection_url` 同步、`password` 由 `123456` 改为容器实际密码 `phoenix`
- 环境问题：曾出现**僵尸 JVM 占用 8066**（`pkill -f phoenix-admin.jar` 无效，需 `lsof -tiTCP:8066 -sTCP:LISTEN | xargs kill`）；本机代理（TUN）开启时 JDK23 解析 `127.0.0.1` 报 `UnknownHostException`，关代理即恢复
- 库中实验数据：技能 `py-fib-demo`（含 scripts/fib.py）曾用于验证；前台账号 chenzhuo 密码现=12345678；两套账号表密码现均=12345678
- 未跟踪：`.mvn-home/`、`.pnpm-store/`、`diagrams/`、`scripts/`（建议进 `.gitignore`）；`AGENTS.md` 有 init 追加段（备份 `AGENTS.md.bak.*`）
