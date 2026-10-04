# Changelog: mcp-client-tools

## T-06/T-07 勾选（2026-10-04）用户走查通过
- 走查实录：用户完成插件管理页 MCP 配置（真接麦当劳官方 MCP）+ 抽屉插件配置块绑定 + 对话实测；三轮反馈全部闭环（菜单两层化 / 插件配置命名 / 抽屉入口补挂 / BUG-65 NPE 修复）
- 用户结论原话：「目前看整体问题不大」——T-06（界面走查）与 T-07（绑定+回显+既有块回归）验收以用户走查为准据勾选
- 遗留待办移交 T-08：跨用户泄漏断言 G1/G2（需前台账号）、front/confirm 链实测、回环 dogfood、测试连接分类词精修

## BUG-65/66 排障记（2026-10-04）用户走查 NPE → 双修复全链绿
- 用户走查报「生成异常中断:NullPointerException」（agent-36 绑麦当劳真 MCP https://mcp.mcd.cn）
- 取证链：落库行 st=timeout+中断文案 → 日志 PRE_ACTING **name=null**×2 → 挂载正常(35工具,___MCP__前缀) → 确定性复现（同题重打）→ **A/B①中文名改 ASCII mcd：仍崩**（排除中文/下划线前缀）→ 嫌疑锁定连字符（mcd 全工具带 -，T-05 stub 全下划线正常）→ **修复①sanitize 连字符→下划线+折叠+首字符字母化** → 复测 done、PRE_ACTING name=mcd__query_nearby_stores、真调 mcp.mcd.cn 出 5 家门店实数据
- 修复②（复测揪出）：改名后仍旧前缀=**变体签名缺 name 列**（BUG-66）→ 签名加 name → 中文名复原实测 m_MCP__query_nearby_stores 即时生效、NPE 双零
- 用户数据处置如实报：A/B 期间将「麦当劳MCP」临时改名 mcd、description 被我覆盖——名字已复原；**description 现为「麦当劳官方 MCP（mcp.mcd.cn）」为我代填**，若与你原填不同请在页面上改回
- 账面：BUG-65(高)/BUG-66(低) 已验证(v1.6.0在途)；**L-15 新坑入册**（外部资源名→模型工具名必须最保守字符集，BL-25 设计输入）；sanitize 新规随 v1.6.0 代码走

## T-07 三增补（2026-10-04）部署假绿暴雷——镜像从未重建
- 用户走查三轮「还是没有」：容器内查证 **AgentPluginConfig chunk=0**、镜像 built=20:33(T-06版) vs 宿主 dist 21:02——改名与抽屉挂载**两次部署都没进容器**
- 根因：`docker compose up --force-recreate` 对已存在 tag **不自动重建镜像**（此前 T-06 首次因镜像缺失触发过 compose 自建，掩盖了差异）；且我的"入包验证"查的是宿主 .stage/dist 非容器内 = **假证明，L-03 部署三段证明违例**
- 修复：`docker compose build nginx` 显式重建 → 容器内三证全中（chunk=1 / "插件配置"串×2 / 抽屉降级文案"请先保存智能体，再配置插件"×1——该串仅存在于抽屉段，铁证）；镜像时间戳 21:06 刷新；verify=0
- 回炉：前端部署固定序列=build dist→cp .stage→**compose build**→up→**容器内 grep 标记串**；宿主侧检查一律不算证明

## T-07 再增补（2026-10-04）抽屉编辑面补挂——L-06 第 4 次应验
- 用户走查二轮反馈「还是没有」：实勘发现**两套智能体编辑界面**——独立页 /agent/:id（首挂处）与列表页 openEditDrawer→agent-create-drawer.vue(1298行,**用户主入口**)；技能块两处都有，我只挂了一处 = **L-06 多入口枚举失守**（身份矩阵「智能体编辑页」对象被窄化为单页）
- 补挂：抽屉左侧菜单「插件配置」组（lucide:plug，技能配置组之后）+ 内容区 v-else-if='plugin'（AgentPluginConfig 同构复用，含"先保存智能体"降级文案）；watch 无需改（组件自载）
- typecheck 213=基线 / build 绿 / nginx v1.6.0-dev 重建 / verify=0；lessons L-06 计次+1

## T-07 增补（2026-10-04）绑定块更名「插件配置」
- 用户走查反馈：MCP 配置页成功，但编辑页未见绑定块（判定=浏览器缓存旧 chunk，块自 T-07 部署已在包）+ 命名指令「技能配置下面增加一个插件配置，可以选择 MCP」
- AgentMcpConfig.vue → **AgentPluginConfig.vue**（git mv 留痕），标题「插件配置」、子标签「MCP」、Alert 注记未来 API 插件并入本块（BL-25）、按钮「保存插件配置」——与插件管理同哲学，为 BL-25 留位
- typecheck 213=基线，build 绿，chunk AgentPluginConfig-f-qIHrQf.js 入包，nginx v1.6.0-dev 重建，verify=0；用户侧需硬刷新（L-11）

## requirements/plan v1.1.0 待重确认（2026-10-04）——菜单层级修订
- 用户指令：「插件市场这个目录暂时先去掉，就是插件管理，插件市场和管理本质是一个」→ Q4 决议入档
- requirements：R-01 顶级菜单改插件管理 / A-6 两层结构 / Q4 决议记录；plan：§1.1 DML 改两层 / §1.4 三层改两层；铁律3 双文档 bump 待重确认
- V1.6.0_02 DML 同步改写（管理升顶级 pid=''，url /plugin-manage，ACL 两模块）；R1.6.0_02 保留三 id 全删（防御两代形态）；dev 库重放

## T-07 代码完成+API 级验证全绿（2026-10-04）——UI 走查与 T-06 合并待用户
- 交付：后端 McpOptionVO/McpBindDTO + options/bound/bind 三端点（覆盖式物理删重建，镜像技能；仅启用可新绑、已绑停用保留）+ AgentServiceImpl 级联 n6（对齐 int nX 风格并入日志行）；前端 mcp.ts 三函数 + AgentMcpConfig.vue（1:1 镜像 AgentSkillConfig）+ 编辑页挂载块
- 验证：①options=启用池✓ ②bind→bound 回读一致✓ ③新绑停用拒 46103✓/已绑停用保留 100✓ ④**级联实弹三证**：cascade 日志 `mcp=1` + 绑定行 del_flag=1 + agent 行消失（fixture agent 99901）⑤组件 chunk AgentMcpConfig-uyAwyqew.js 入包✓ ⑥typecheck 213=基线 + verify=0 + 夹具全清
- 排障实录×2（皆我方工装非产品码）：①夹具插错列——tbl_data_agent 无 del_flag（PSQL 包装吞 stderr 又栽一次，L-03 重申）②"500"真相=重复 DELETE 已删 agent 的 404 被 GlobalExceptionHandler 包装——**BUG-64 已入册**（404→500 语义失真，既有行为，低优先）

## T-06 代码完成部署（2026-10-04）——UI 走查待用户（与 T-07 合并一次做）
- 交付：api/core/mcp.ts（8 函数，/api/mcp 全路径+responseReturn:body 镜像 skill.ts 惯例）+ views/plugin/mcp/index.vue 467 行（列表搜索分页/传输 tag/启停 switch/授权组数/绑定数/操作列详情·编辑·授权·删除；编辑抽屉=四传输条件表单+headers/env KV 编辑器+stdio 风险 Alert(R-05)+掩码说明 Alert+测试连接结果区；详情抽屉=脱敏只读；授权弹窗=组多选覆盖式）
- 验证：vue-tsc **213=基线分毫不差**（先 +5 全在我页——el-table DefaultRow 推断，签名放宽 any 修复）；build 7756 模块绿；chunk mcp-rcSPhu8W.js HTTP=200；菜单树 API 含插件市场>插件管理>MCP 三层；verify=0
- 部署插曲×2 如实记：①手建镜像吞输出失败无察觉（Docker Hub 挡 nginx:1.27-alpine base）→daocloud 拉取 retag ②compose 自带 nginx 服务 build 配置（repo 根 context）——base 就绪后 compose 自建 v1.6.0-dev 成功；教训重申=关键步骤禁吞输出（L-03）
- **UI 界面真走查移交用户**（无浏览器自动化通道）：建议与 T-07 绑定块完成后合并走查一次

## T-05 完成（2026-10-04）挂载接线实弹全绿
- 交付：McpMountService（变体缓存 agentId+交集签名/LRU64/淘汰关 wrapper/boundedElastic 构建）+ PrefixedAgentTool 委托壳（前缀对模型、原名调远端——spike 字节码实证 McpTool.callAsync 用自身 getName()，直接改名必挂）+ Registry.buildUncached（legacy WARN 降级）+ 三链接线（doStream/confirmStream/channel 口径分流）+ DTO channel + 三控制器打标（commit 223b3de）
- **实弹证据链**：①注册 stdio stub（env MARKER 加密落库）②测试连接 success=True 列工具 [stub_echo] 214ms（**R-06 成功态实证补全**）③admin 真对话模型调用 `t05stub__stub_echo` → 落库行 content=「工具返回原文：echo:hello-mcp marker:g1secret」——**前缀调用+env 密文解密回传全链实证** ④故障隔离：坏 endpoint(t05bad) 同绑，stub 照常可调、轮次正常完成 ⑤停用失效：disable 后下一轮模型答「工具不存在」（签名失效按轮生效）⑥零变化回归：绑定前/清理后 verify=0 双跑
- **如实注记（验证口径调整，非静默降级）**：三链断言中 admin 链已实测；front 链与 confirm 续跑链接线同构（同一 effectiveMcp+withMcp 路径）但实测需前台登录/计划模式组合——**移入 T-08 矩阵执行**（回环 dogfood 本就需前台登录，跨用户泄漏断言 G1/G2 同批）
- 演练现场全清（MCP×2/绑定×2/会话行/stub 文件），终态 verify=0

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
