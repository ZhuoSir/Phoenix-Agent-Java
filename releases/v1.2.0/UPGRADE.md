# v1.2.0 升级操作手册

> 目标库：PostgreSQL（含 pgvector）。所有升级件**幂等可重入**（V1.2.0_03 的 type 回填属数据单向变更，重跑 no-op、回滚不还原，见注意事项①）。
> 单实例应用：`phoenix-admin.jar`（8066）+ 管理端前端（admin-ui）。SQL 先于新代码执行、后于新代码执行均不报错；但**运行时「默认模型」功能要求先执行 V1.2.0_05**。

## 升级前
- 备份：`pg_dump -U phoenix -Fc phoenix > phoenix_pre_1.2.0.dump`（必须可恢复，回滚 SQL 只撤销结构，不还原数据）
- 确认：当前版本 ≥ v1.1.x；`SELECT version();` 记录 pgvector 可用；磁盘余量 > 库大小×2
- 通知：需**停服窗口**（分钟级：停服 → SQL → 起新包），无破坏性 DDL，预估 SQL 总耗时 < 5s

## 升级步骤（严格按序）
1. 停服：停 `phoenix-admin.jar` 进程（8066）
2. 部署制品：替换 `phoenix-admin.jar` 为 v1.2.0 构建；管理端前端整包替换（本次前端**零新增依赖**）
3. 执行 SQL（逐个执行并确认输出，`psql -v ON_ERROR_STOP=1 -f`）：
   - `sql/V1.2.0_01__harness_skill_status_and_binding_tables.sql` → 预期: 新表 `tbl_data_agent_skill_info`、`tbl_platform_group_skill_info` 建成；`tbl_harness_skills.status` 列存在（若表尚不存在会有 NOTICE 跳过，应用启动自建后**需重跑本件**补 status 列）
   - `sql/V1.2.0_02__skill_menu_and_acl_dml.sql` → 预期: 「技能管理」菜单 1 行、ACL 按智能体管理角色复制
   - `sql/V1.2.0_03__agent_runtime_config_table_and_type_backfill.sql` → 预期: `tbl_data_agent_runtime_config` 建成 + `uk_arc_agent` 索引；`tbl_data_agent.type` 默认值=harness 且存量 NULL/'' 回填为 harness（打印回填行数 NOTICE）
   - `sql/V1.2.0_04__knowledge_tool_params_columns.sql` → 预期: 运行配置表新增 `knowledge_top_k`/`knowledge_similarity_threshold` 两列（默认 10/0.65）
   - `sql/V1.2.0_05__model_default_column_index_and_backfill.sql` → 预期: `tbl_data_model_config.is_default` 列 + COMMENT；每个「尚无默认」的类型回填恰好 1 条默认（启用优先→最近更新优先）；`uk_dmc_type_default` 部分唯一索引建成
   - 校验（每步后可跑）：`SELECT model_type, count(*) FILTER (WHERE is_default) FROM tbl_data_model_config WHERE is_deleted=0 GROUP BY 1;` 每类型 ≤1
4. 应用配置（对照 `config/changes.md`）：
   - 本版本全部键均有代码默认值，**默认不动作**；需调优才显式设置（6 个键见 changes.md #1~#6）
   - ⚠️ **changes.md #7 必须执行**（除现网已改过的库）：EMBEDDING 行 `base_url`→`https://dashscope.aliyuncs.com/compatible-mode`、`model_name`→`text-embedding-v4`，否则向量化 404
5. 启服：`java -jar phoenix-admin.jar`（若环境有系统代理且代理进程未运行，确认 JVM 无残留 `socksProxyHost/http.proxyHost` 注入——见注意事项④）
6. 验证（逐条勾选）：
   - [ ] `GET /echo/ok` 返回 200
   - [ ] 模型管理页：每类型可见「默认」标记；能设默认/停用非默认；停用默认被拒并有文案
   - [ ] 技能管理页可打开（菜单可见、列表加载）
   - [ ] 任一已保存智能体：运行配置面板可见、对话模型下拉列已启用 CHAT、未选时占位显示默认
   - [ ] AI 生成：输入名称点生成，≤90s 返回描述+md 四段提示词；日志出现 `默认对话模型: configId=…`
   - [ ] 一轮真实对话正常（含技能绑定智能体，日志有 `Registered tool 'load_skill_through_path'` 与 tool_call 轮次）
   - [ ] 存量纯文本提示词智能体打开编辑器不报错、保存后内容逐字不变

## 回滚步骤（逆序）
1. 停服
2. 回退制品至 v1.1.x（**必须先回退应用再回滚 SQL**：旧代码不读新列/新表，保留无害；反之新代码在缺列库上会起不来）
3. 回滚 SQL（逆序号，逐个 `ON_ERROR_STOP=1`）：
   - `sql/rollback/R1.2.0_05__model_default_column_index_and_backfill.sql`（丢默认标记；is_active 不受影响）
   - `sql/rollback/R1.2.0_04__knowledge_tool_params_columns.sql`（丢已配置检索参数→回落代码默认）
   - `sql/rollback/R1.2.0_03__agent_runtime_config_table_and_type_backfill.sql`（**全部智能体运行配置丢失**；type 回填不可逆）
   - `sql/rollback/R1.2.0_02__skill_menu_and_acl_dml.sql`（菜单与授权消失）
   - `sql/rollback/R1.2.0_01__harness_skill_status_and_binding_tables.sql`（技能绑定/授权表与 status 列删除，技能数据 `tbl_harness_skills` 行不删）
4. 配置：本版本代码默认键无需动作；#7 若已改且旧模型可用，可按 changes.md 记录值还原
5. 启服并验证旧版核心功能（对话、智能体列表、模型管理）
6. 数据兜底：如结构回滚后仍有脏数据顾虑，用升级前 `pg_restore` 整库还原

## 注意事项
- ① `V1.2.0_03` 的 `type` NULL→harness 回填是**单向数据变更**（回滚脚本只撤对象与默认值）；依赖「NULL 型智能体不再被列表展示」语义的环境回滚后行为等同旧版，无破坏
- ② `V1.2.0_01` 与 harness 表创建顺序：`tbl_harness_skills` 由应用启动自建，全新环境先跑 SQL 会有 NOTICE 跳过 status 列，**应用首启后需重跑 V1.2.0_01**（幂等，第二次会补列）
- ③ 本手册全部路径基于 `releases/v1.2.0/sql/`，序号一经发布不再变动（后续插队用补号如 `_05a`）
- ④ macOS 部署机若开过系统代理（Clash 等）且代理进程已退出，JDK23 会把死代理注入 JVM 导致出站连接失败（2026-09-30 实测案例，见 BUG-30 明细）；升级后异常时先查 `scutil --proxy` 与启动日志
