# Changelog: mcp-client-tools

## T-04 完成（2026-10-04）交集判定服务 + 六态矩阵全绿
- 交付：FrontMcpAccessService(+Impl)——前台三重交集 SQL 直查（镜像 MY_SKILLS_SQL 同构）；admin 口径=绑定∧启用（A-5 实勘定案：admin 技能走 SkillExplicitInjection 显式选择无组过滤，MCP 同构对齐）；诊断端点 GET /api/mcp/effective（admin 限定，只回 id/name/transport 防 config 泄漏）
- 六态矩阵实测：a admin=[A](B停用排除)✓ b front-u1=[A]且DB侧=1(双侧取证)✓ c 空账号=[]✓ d 撤权=[]✓ e 停用双口径=[]✓ f 恢复双=[A]✓
- 插曲如实记：夹具清理顺序反了——绑定未删先 DELETE 被防悬挂 46104 拦截（防御行为反向实证），补删后双表零残留；U2（G1 外账号）现网不存在，c 态以空账号路径+撤权态(d)联合覆盖"未授权=空集"语义

## T-03 完成（2026-10-04）MCP 管理后端 + 13 断言全绿
- 交付：api 9 件（三实体/DTO/VO/46xxx 错误码）+ core（Mapper×3/McpSecretCipher AES-GCM+平台同款掩码/McpAdminServiceImpl 549行）+ rest McpAdminController 七端点
- 13 断言实测全绿：创建/分页(授权绑定计数)/详情掩码 sup****t123/重名46102/掩码回传保原密文/落库密文true明文false/启停/组授权+假组46105/悬挂拒删46104(列名agentIds)/删除+404=46101/日志无明文/无token=401
- 过程抓 3 真 bug 即修：①WebFlux 无自动装配 ObjectMapper bean(启动崩溃)→自持实例 ②ReturnVo.ok(String) 重载歧义 id 落 message→ok(msg,data) ③Reactor 事件循环禁 block()(测试连接崩)→Mono.fromCallable+boundedElastic(AgentFileController 先例)
- 如实注记：测试连接「不可达」分类现显示"连接失败：failed to initialize"（可读但未命中"不可达"分类词）——分类词库 T-08 用本地 MCP stub（含 401 鉴权桩）校准时一并精修；鉴权失败分类逻辑已在码

## T-02 完成（2026-10-04）Flyway 两件+回滚两件
- 交付：V1.6.0_01 三表 DDL / V1.6.0_02 三层菜单+ACL DML（md5 常量 id+NOT EXISTS 防重，镜像 V1.2.0_02）/ R 两件回滚
- 列形对照表（与技能三件套逐列核对）：
  - 注册表：tbl_harness_skills（应用建管/bigint id）→ tbl_mcp_server（DDL 建管/varchar32 id 对齐绑定表雪花串风格 + transport/config jsonb/status/审计列/del_flag + name 部分唯一索引 WHERE del_flag=0）——差异属自有域合理项
  - 组授权：group_skill(group_id varchar, skill_id bigint) → group_mcp(group_id varchar, mcp_id varchar32)，列型对齐各自注册表
  - 绑定：agent_skill(agent_id bigint, skill_id bigint) → agent_mcp(agent_id bigint, mcp_id varchar32)——**agent_id 用 bigint，避开 group_agent varchar 历史坑**
- 验证（全新库 phoenix_fresh 实跑）：migrate exit=0 apply×10；台账 11 行(9+2)；表 59(56+3)；三层菜单+ACL 6 行(3菜单×2角色自智能体列表复制)；幂等复跑无错无重复；回滚逆执行表0菜单0；既有智能体列表 ACL=2 不变；演练库已清

## T-01 spike 报告（2026-10-04）—— 路线乙定案
- **五问结论**（反编译 agentscope core/harness 2.0.0 取证）：
  ① 重复注册=ToolRegistry Map.put 撞名 last-wins 静默覆盖；同名 server 重复注册需先 removeMcpClient
  ② 框架原生工具名=裸 MCP tool 名（无前缀，wrapper 名仅入日志）→ 原生跨 server 同名互覆盖
  ③ 卸载完备：removeMcpClient/removeTool/removeToolIfSame/removeToolGroups 全公开
  ④ 并发：activeGroups=共享 volatile，ReActAgent 每轮 setActiveGroups；ExecutionConfig 无每调用过滤器 → 共享实例 per-user 过滤不安全
  ⑤ enableTools=注册时过滤（shouldRegisterTool include/exclude）
- **定案：路线乙**（甲判死于④）：agent 实例按 (agentId+交集签名) 缓存多实例；无 MCP 绑定走现有单例零变化；配置/授权/绑定变更 bump 全局版本→签名变→惰性重建+LRU 淘汰(关 wrapper)
- **增益发现**：McpTool 公开构造器+registerAgentTool 公开 → 自建带前缀注册路径（server__tool），R-03 同名区分**无需改需求**即满足（原生注册器做不到）；工具名格式细节 T-05 定（满足模型工具名字符约束）
- plan 未改动（§1.3 定案机制为 plan 预授权，不触发确认作废）

## tasks v1.0.0（2026-10-04）—— 确认③通过，三重门全绿
- 用户「确认」；tasks v0.1.0→v1.0.0 已确认（陈卓）；三重门自检后切 feature/mcp-client-tools（基点 v1.6.0），进入 Phase 4，首任务=T-01 框架 spike

## tasks v0.1.0（2026-10-04）
- Phase 3 拆解：T-01~T-09 三组（技术定案与数据层/后端服务与挂载/前端与收口）；T-01 spike 为风险闸（定案前禁写挂载代码，皆不可行=铁律6合法出口）；身份矩阵对面断言分布 T-02/05/06/07/08；泄漏断言双跑（T-05+T-08）；回环 dogfood 入 T-08

## plan v1.0.0（2026-10-04）—— 确认②通过
- 用户「确认」（spike 概念已应用户要求解释后过门）；plan v0.1.0→v1.0.0 已确认（陈卓）；进入 Phase 3 Tasks

## plan v0.1.0（2026-10-04）
- Phase 2 落盘：技能三件套同构三表（tbl_mcp_server/group_mcp/agent_mcp，列形以真实 DDL 对照）+ Flyway 两件 + phoenix-agent 同模块三服务一控制器 + 前端插件市场三层；**T-01 spike 前置定案挂载路线**（agent 实例缓存共享 vs per-user 交集的核心矛盾：路线甲=调用面过滤/路线乙=按组签名多实例缓存）；身份矩阵 6 对象；被否案 5；风险 5（Toolkit 语义=头号，泄漏断言进矩阵）；坑核对 14 条全过重相交 4

## v1.0.0（2026-10-04）—— 确认①通过
- 用户「确认」；requirements v0.3.0→v1.0.0 已确认（陈卓）；进入 Phase 2 Plan

## v0.3.0（2026-10-04）
- Q2/Q3 用户裁决落定（stdio v1 全开 / 对话端按现有工具轨迹样式）；Q 区全关闭转决议记录；状态 草稿→待确认（第一重确认门待「确认」口令）

## v0.2.0（2026-10-04）—— 用户重塑形态
- Q1 用户裁决（超出原两选项）：**admin 插件市场→插件管理→MCP 统一管理**（列表/详情/配置/CRUD/启停）+ **组授权与智能体/skill 同构** + 智能体绑定 + 前台交集挂载；API 插件明示列待办 → **BL-25 已记**
- requirements 重写 v0.1.0→v0.2.0（草稿期正常升版）：条款重排 R-01 插件市场与统一管理 / R-02 组授权 / R-03 绑定与挂载（删除防悬挂、交集语义）/ R-04 故障隔离 / R-05 安全边界（管理面仅 admin）/ R-06 连接测试 / R-07 Server 端零变化；旧 R-01（智能体维度内嵌配置）被 R-01/02/03 取代，映射记录于此
- Q2/Q3 仍开放待裁决
- 过程注记：60f21f2 提交语声称含 BL-25/changelog，实际 python 语法错（encoding 关键字重复）整段未执行——本笔补正（账实不符当场纠，L-01 族）

## v0.1.0（2026-10-04）
- 初始化创建（BL-01 立项，用户口令「做一下BL-01吧，新建需求」+「挂 v1.6.0」）
- 挂载: v1.6.0（2026-10-04，用户确认「挂 v1.6.0」）
- requirements.md v0.1.0 草稿落盘：R-01~R-06 全 EARS+验收场景（配置管理/挂载调用/故障隔离/安全边界/连接测试/Server端零变化）+Non-goals 6+假设 A-1~A-6+阻塞问题 Q1~Q3；勘察事实入档（Registrar.register(Toolkit,Map)+getToolkit() 公开+四传输+平台 Server 端实存=McpServerToolUtil 非台账记忆的 McpServerService，类名偏差已核实纠正）
- 双建完成: releases/v1.6.0/（MILESTONE 状态头进行中+需求表）+ 裸号分支 v1.6.0（基点 main tip 270fcfa）；BL-01 翻已立项(v1.6.0) 留档不删
