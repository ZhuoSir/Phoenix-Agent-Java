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
| BUG-02 | HumanInTheLoop 未挂 skillRepository，技能静默加载不到 | P1 | 本地部署(2026-09-27) | 已发布(v1.4.0) | - | 原 B-02; 一行修复；T-02 核实原缺陷对象（未挂仓旧路径）已随技能体系重构消亡，现网技能加载正常 |
| BUG-03 | 前台账号创建时密码无必填校验 → 制造永久无法登录的死账号 | P2 | 本地部署排障(2026-09-27) | 已发布(v1.4.0) | - | 原 B-03；T-01 controller+service 双层密码必填 |
| BUG-04 | 双账号体系：两张表、状态语义相反、密码互不相通 | P2 | 本地部署排障(2026-09-27) | 不修复(技术债;批准:陈卓 2026-10-02) | - | 原 B-04; 设计问题，建议立项/不修复**待用户批准**；双账号系现状产品形态（privilege=管理员/platform=终端用户），合并属架构级改造收益不明（batch Q1 拍板） |
| BUG-05 | 登录密码错误返回了「原密码错误」的错误码（23007） | P2 | 本地部署排障(2026-09-27) | 已发布(v1.4.0) | - | 原 B-05; 一行修复；T-01 码值 PASSWORD_ERROR(23009) 归位，改密 23007 对面零变化 |
| BUG-06 | 技能无管理入口且技能池全局共享（缺失功能 + 死配置） | P3 | 本地部署(2026-09-27) | 已修复(v1.2.0) | v1.2.0 | 原 B-06; Spec: 20260927_agent-skill-management（`phoenix.agent.skillPath` 仍是死配置） |
| BUG-07 | harness 的 shell 能力与远程文件系统硬绑互斥、无配置开关 | P3 | 本地部署(2026-09-27) | 已修复(v1.2.0) | v1.2.0 | 原 B-07; Spec: 20260927_dynamic-agent-types T-05（filesystem_policy 配置化，remote 自动关 shell） |
| BUG-08 | `application-test.yml` 与实际部署环境不一致（密码/他人机器路径/与 AGENTS.md 冲突） | P3 | init体检(2026-09-27) | 已发布(v1.4.0) | - | 原 B-08；T-03 对齐 docker 环境入库，常驻脏文件清零 |
| BUG-09 | 前台与 harness 智能体无对话通道（前端指向不存在的端点） | P2 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | 原 B-09; Spec: 20260927_agent-skill-management T-11/T-14 |
| BUG-10 | 后台新建智能体 type 为空、且无类型选择入口（harness 无法后台创建） | P2 | 对话中(2026-09-27) | 已修复(v1.2.0) | v1.2.0 | 原 B-10; Spec: 20260927_dynamic-agent-types T-04/T-14（存量自注册类保留属 BL-03，非缺陷） |
| BUG-11 | 前台 HITL 确认接口缺失，前端调用必然 404 | P2 | spec Implement中(20260927_dynamic-agent-types) | 已发布(v1.4.0) | - | 原 B-11；T-08 真身：前台 confirm 误走 sn-only 重载致库配置智能体必挂——改双寻址重载+payload 带 agentId；前台端到端复验（confirm 2494帧、行单行done、确认产物生成） |
| BUG-12 | SqlSecurityValidator 子串匹配误杀只读查询（`create_time` 等） | P2 | spec Implement中(20260927_dynamic-agent-types T-08) | 已修复(v1.2.0) | v1.2.0 | 原 B-12; jshell 8 例验证 |
| BUG-13 | EMBEDDING 模型测试恒 404（base_url 多带 `/v1` + 模型名不被兼容模式支持） | P2 | 对话中(2026-09-27) | 已修复(v1.2.0) | v1.2.0 | 原 B-13; 配置修正 + 实测矩阵 |
| BUG-14 | 删除智能体残留孤儿数据（运行配置/技能绑定/组授权） | P2 | spec Implement中(20260927_dynamic-agent-types T-16) | 已发布(v1.4.0) | - | 原 B-14; 本次孤儿行已手工清理 |
| BUG-15 | 智能体列表关键字搜索在 PG 下 500（`CONCAT` 参数类型不可推断） | P2 | spec Implement中(20260927_dynamic-agent-types T-16) | 已修复(v1.2.0) | v1.2.0 | 原 B-15; commit 00eb0ff |
| BUG-16 | 三张 Spring AI 向量表缺主键 → `ON CONFLICT` 插入必失败 | P2 | spec Implement中(20260927_dynamic-agent-types T-13) | 已修复(v1.2.0) | v1.2.0 | 原 B-16; commit c0fe1b9（基线 + 运行库双修） |
| BUG-17 | 图链路在非 HTTP 调用方取 Sa-Token 登录态直接抛异常 | P2 | spec Implement中(20260927_dynamic-agent-types T-09) | 已修复(v1.2.0) | v1.2.0 | 原 B-17; commit e74e7ec |
| BUG-18 | QA/FAQ 类型知识只向量化「问题」，答案不参与检索 | P2 | spec Implement中(20260927_dynamic-agent-types T-16) | 已发布(v1.4.0) | - | 原 B-18; 需产品定口径 |
| BUG-19 | harness 对话入参缺失时返回 500（应给明确错误码） | P3 | spec Implement中(20260927_dynamic-agent-types T-11) | 已修复(v1.2.0) | v1.2.0 | 原 B-19; commit dc9b333 |
| BUG-20 | 启用模型会把同类型其他模型一并置为启用（SQL 与注释相反） | P1 | spec Implement中(20260927_agent-config-ai-generate T-02) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | 原 B-20; commit b8f728a（启用改多值集合，方法已删）；已随 merge c90071d 合并 main |
| BUG-21 | 模型管理「模型类型」列把 AUDIO 显示成「嵌入模型」 | P3 | 对话中(2026-09-27) | 已发布(v1.4.0) | - | `views/modelconf/index.vue:458`（三类型都能设默认后才暴露）；T-07 三分 map（对话/嵌入/音频） |
| BUG-22 | AI 生成「描述」返回整段 JSON（用户实测） | P2 | 对话中(2026-09-27) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | Spec: 20260927_agent-config-ai-generate; commit edd9ad9；已合并 main(c90071d)；用户复测确认(09-30) |
| BUG-23 | 生成超时 60s 切断**已成功**的调用（实测耗时 45~70s） | P2 | spec Implement中(20260927_agent-config-ai-generate T-09) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | 同 commit edd9ad9（缓解：90s 且可配置）；已随 merge c90071d 合并 main |
| BUG-24 | 响应式超时无法中断底层阻塞调用（超时后仍在消耗 token） | P3 | spec Implement中(20260927_agent-config-ai-generate T-09) | 已发布(v1.4.0) | - | plan 风险⑦已接受该限制，建议转技术债；T-06 ①取消令牌+②节点入口守卫（Q3 批准档）；断链后零后续活动实测；③底层硬中断=已延期(v1.4.0→技术债，收益边际，批准:陈卓) |
| BUG-25 | 前端 dev 命令未按 `.env.development` 的 `VITE_PORT` 起端口（5777 起成 5173） | P3 | 对话中(2026-09-27) | 已发布(v1.4.0) | - | 临时规避：启动加 `--port 5777`；T-07 vite loadEnv VITE_PORT，实测 dev 起 5777 |
| BUG-26 | 技能上传前端未带 multipart 头 → 上传 HTTP 415 | P3 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | `api/core/skill.ts:80` 补 `Content-Type: multipart/form-data` |
| BUG-27 | 技能 ZIP 校验报错信息误导（真实规则是「条目须有根目录」） | P3 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | 新增 `SkillZipSanitizer`（剥 `__MACOSX/`、`.DS_Store`、`._*`，统一包一层合成根） |
| BUG-28 | `ReturnVo.ok(String)` 命中 msg 重载 → 误把 data 当 msg 传出 | P3 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | `phoenix-tool/.../ReturnVo.java:84`；改用两参 `ok(msg, data)` |
| BUG-29 | `tbl_platform_group_agent_info.agent_id` 为 varchar，与 bigint 的 agentId 比较报错 | P3 | spec Implement中(20260927_agent-skill-management) | 已修复(v1.2.0) | v1.2.0 | 代码侧改 `String.valueOf(agentId)`；**列类型不一致的根因仍在**，建议统一 |
| BUG-30 | AI 生成请求走全局 30s 超时（实测生成 45~90s）→ 前端掐断请求、按钮转圈无结果（用户实测） | P2 | 界面走查(2026-09-30) | 已发布(v1.2.0) 2026-10-01 | v1.2.0 | Spec: 20260927_agent-config-ai-generate T-11；`api/core/agentProfile.ts` 单独设 `timeout: 120_000` |
| BUG-31 | 全新库首启 NPE：自注册智能体 `createHarnessAgent()` 在 `saveBySn` 之前读库，`HumanInTheLoop.java:87` 对 null agent 取 description 崩溃 | P1 | 交付包首启实测(20260930_allinone-docker-packaging) | 已发布(v1.2.1) 2026-10-01 | v1.2.1 | v1.2.1 根治=register() 先 saveBySn 再 create + HumanInTheLoop 判空双保险；**实测**：dump 克隆库删光 5 sn 行 → 新镜像首启 Started 成功零 NPE；交付包种子保留作双保险（挤入后归属 v1.2.0 发现） |
| BUG-32 | deepseek 模型走 AI 生成双字段时 42013（一次空 content、一次 JSON 未被解析出对象；qwen 同链路正常） | P2 | 交付包实测(20260930_allinone-docker-packaging) | 已发布(v1.2.1) 2026-10-01 | v1.2.1 | 疑输出形态（reasoning 混排/转义）与解析容错不足；v1.2.1 修复=空内容自动重试一次 + JSON 裸换行控制字符修复再解析 + 字段级正则兜底；**实测** deepseek 双项 19s 成功（43 字描述+595 字四段提示词） |
| BUG-33 | 前端生产包 API 双重 /api 前缀：axios baseURL=/api 且代码路径自带 /api（fetch 处 API_BASE_URL 同），**生产构建从未成功部署过**，dev 全靠 vite proxy rewrite 掩盖；浏览器实测登录 401 | P1 | 交付包 9080 用户实测(20260930_allinone-docker-packaging) | 已发布(v1.3.0) | v1.3.0 | 交付包 nginx 完全复刻 dev proxy 语义：凡 /api/* 剥一层再转后端（代码两种写法并存：双前缀域 login/agent/model + **单前缀 platform 域**，第一版只折双前缀导致 /api/platform/group-info/page 404→500，10-01 用户实测抓到已修正）；两形态 9/9 断言全绿；**根治=前端 baseURL 统一**（BL-18），nginx 折叠属包层适配非产品修复；**v1.3.0 已根治**(BL-18，原误挂 v1.2.2 已并入)：baseURL 置空+URL 直写真实路径+代理透传，折叠已删，verify 负断言防回潮；连带痊愈 logicalRelation/prompt-config/模板下载三个单前缀历史坏点 |
| BUG-34 | `GET /api/model-config/list` 响应体返回**明文 apiKey**（全局接口行为，非 docker 包引入） | P2 | 交付包验证接口巡检出 | 已发布(v1.2.1) 2026-10-01 | v1.2.1 | v1.2.1 修复=list 出口 sk-****尾4 脱敏 + 脱敏值按 id 回源（update 既有 **** 守护）+ 测试日志不落真实 key；前端编辑态提示语；**实测** 列表全脱敏、脱敏回传测试连接成功 |
| BUG-35 | 模型管理 maxTokens/temperature 从未传给 harness 对话请求（HarnessModelRegistry 两处 builder 缺 generateOptions），长回复被服务端默认上限截半（用户实测"开画执行到一半不执行"） | P1 | 9080 用户实测(2026-10-01) | 已修复(v1.2.0) | v1.2.0 | Spec: 20260930_allinone-docker-packaging 会话暴露；修复=两处 builder 补 GenerateOptions(maxTokens,temperature)；实测 400 行数到完（fix 前同类 14K 字符断在半句） |
| BUG-36 | 模型管理表单 max_tokens 硬编码上限 10000，用户无法为高上限模型配置 | P3 | 交付包验收期用户反馈(20261001) | 已修复(v1.2.0) | v1.2.0 | Spec: 20260930_allinone-docker-packaging 连带；`modelconf/index.vue` 去 :max 与校验 max（后端/库表本无限制） |
| BUG-37 | Spring AI 底层 HTTP 栈宣告支持 brotli（Accept-Encoding: …, br）但无 br 解码器，deepseek(CloudFront) 命中 br 时响应体解不出 → 连接测试/生成 JSON EOF、空内容（间歇，按 CDN 节点分布；curl 不带 br 故正常） | P1 | 交付包 deepseek 连接测试复现(2026-10-01) | 已发布(v1.2.1) 2026-10-01 | v1.2.1 | Spec: 20260930_allinone-docker-packaging；修复=`DynamicModelFactory.noBrotli()` 对所有 OpenAI 兼容 RestClient（含代理分支）钉死 `Accept-Encoding: identity`；实测 deepseek×3 + qwen 连接测试全过、双项生成 21s 四段齐全。亦为 BUG-22/23/32 间歇截断的总根因 |

| BUG-38 | 组管理启用/禁用语义判反：`group-info/index.vue` 的 `onToggleStatus` 用 `status===1` 判启用，与同文件 statusSlot/getActions 及后端(status=0 为有效组)相反，致"点启用后状态永远停在禁用、按钮不变"（写库正常，纯前端标签镜像循环） | P2 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | Spec: 交付包验收期；根因是 account(1=启用) 与 group(0=启用) 两域语义相反（双账号体系 BL-05）；修复对齐 0=启用并加防误伤注释 |
| BUG-39 | 前端 index.html 无 Cache-Control：部署新 dist 后浏览器沿用旧缓存 bundle，旧包依赖的 `/api/api` 双前缀折叠已删 → 升级后接口成片 404/500（本次 logout 500 即此因，非 logout 本身） | P3 | 9080 logout 排查(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 已在交付包 nginx 落地：index.html/SPA fallback no-cache，js/css/assets 前缀 30d immutable；实测响应头到位；受影响用户需最后强刷一次清掉当前旧缓存 |
| BUG-40 | ChatSessionSidebar 的 EventSource 硬编码 `/api/api/agent/{id}/sessions/stream`（BL-18 全量审计漏网：fetch/EventSource 不过 axios，当时只查了 api/core 与视图拼接）→ 页面开着时后端持续 404→500，EventSource 原生重连放大成"会话结束后自动再发起会话"风暴 | P2 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 改真实路径单前缀；dist 全文扫描双前缀=0；教训：全局改写必须把 fetch/EventSource/Worker 等旁路通道纳入审计 |
| BUG-41 | BL-19 验收反馈三连：①轮末扫描的 agentFiles 帧落在 end=true 之后，前端已收尾→面板不更新、且补帧被渲染成"幽灵新会话框"；②文件写在轮末 2s 内被 settle 门槛跳过且不重试→本轮登记不上；③抽屉打开只查库不补扫，漏网文件永不见日 | P1 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 四层修复：END 从 body 剥离改 concat(core,files,end) 保证事件在结束帧前；轮询 3×1.2s 兜写尾；settle 2s→1s；list 支持 scan=1 开抽屉即补扫（storeKey 幂等）。回归实测：agentFiles 帧位 69 < end 帧位 70，同轮文件入列，补扫捞回历史漏网 4 件；memory/ 日记文件入黑名单 |
| BUG-42 | 文件预览中文乱码：下载/inline 响应 Content-Type 不带 charset（text/plain），浏览器按规范对 text/* 无参默认 ISO-8859-1 渲染 UTF-8 字节流；前端 blob 预览继承同一头，新标签页同样乱码 | P2 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | withUtf8ForTextual()：text/*、json、xml、javascript 一律追加 charset=UTF-8（二进制类不加）；实测头变 text/plain;charset=UTF-8，正文与 blob 预览中文完好 |
| BUG-43 | 前台对话页默认态（历史面板展开）无文件抽屉入口：入口按钮只放在折叠态 collapsed-bar（v-if 折叠），展开态整页找不到文件列表；admin 页不受影响 | P2 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | ChatHistoryPanel header 动作区「收起」按钮右侧增 📁（emit open-files→页面开抽屉），折叠态入口保留在 collapsed-bar，两态恒可见 |
| BUG-44 | 新会话点「文件列表」抽屉打不开：chat store 新会话用本地 temp-{ts} id 占位、首条消息才落库（persistCurrentSessionIfNeeded 设计），面板拿 temp id 请求后端→属主校验必败(403)→异常路径吞掉后抽屉无内容呈现 | P2 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 面板对 temp id 短路：不发请求直接空态，文案提示"发送第一条消息后出现"；真实 id 才走补扫+列表 |
| BUG-45 | 前台聊天页预设问题面板带「添加/删除」管理功能：预设是智能体级共享资源，前台普通用户可增删影响所有人；管理动作应只在 admin 侧 | P2 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | front/components/PresetQuestions.vue 重写为只读（列表+点击填入），增删逻辑与 UI 全删；⚠️ 遗留（用户拍板 2026-10-01）：add/delete API 暂不做角色限制，随 BL-05 账号体系统一一并解决（现强做易误伤 admin 自身操作） |
| BUG-46 | harness 事件面缺失致"执行一半戛然而止+输出消失"：①ExceedMaxItersEvent 未被映射，工具迭代超限静默终止（agent25 svg转png 实测触发）；②AgentResultEvent 承载的最终 Msg 被丢弃，无增量文本的轮次回吐全丢；③前端流循环遇服务端/代理断流（无 end 帧）不触发 onComplete，已生成内容不落库；④ToolResult*/ModelCall* 事件刷 WARN 噪音 | P1 | 9080 用户实测+复现(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 后端：ExceedMaxIters→⚠️可见警告帧；AgentResult→按轮内 textDeltaSeen 状态兜底补发内容；生命周期事件静默；前端：reader done 未收到 end 帧时兜底 onComplete 保存。复现验证：同 prompt 重现时警告入流、Unhandled WARN 从刷屏降至 1；快轮 end 帧居末 PASS |
| BUG-47 | 【勘误版】原诊断（EventSource 无法带 token 头）系过时误判——BUG-40 改造时该侧栏已重写为 fetch+phoenix-token 头+重试；真实噪音源=登出残留页 localStorage 清空后仍每 3s 空 token 重连触发 NotLoginException（近 15m 已随页面更新归零实证） | P3 | BUG-46 排查连带发现(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 加固两处：无 token 时不发请求改 15s 慢轮询；连续 3 次 401 停止重连自动静默。教训入规范：登记 bug 前必须重读现状代码，不得沿用早期会话印象 |
| BUG-48 | 交付镜像缺智能体工具链：UBI/ES9 分支只装 python3 无 pip，且无任何 svg→png 转换器与 CJK 字体（librsvg2 在该基座不含 rsvg-convert CLI）——智能体做图转码时被迫自装工具，烧光迭代触发 ExceedMaxIters（用户实测 gantt svg→png 半途中断的直接推手） | P1 | 9080 用户实测+排查(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | Dockerfile.backend 三基座分支统一补 python3-pip；UBI 分支加 fontconfig+cairo；构建期下载 NotoSansCJKsc 烧入（jsdelivr/清华源双兜底）；pip 装 cairosvg。E2E 实证：agent30 write_file svg→shell cairosvg→test.png 双文件自动入面板 |
| BUG-49 | QA/FAQ 知识召回只返回**问题文本**不含答案：向量 content=question（注释明示 answer 放关系库）但检索工具（KnowledgeRetrievalTool/RulesRagTool）不回表取 answer——QA 知识事实上不可用 | P2 | knowledge-base T-10 E2E(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 存量设计缺口（非 knowledge-base 引入，DOCUMENT 类型不受影响）；修法候选：工具命中后按 agentKnowledgeId 回表拼 question+answer 返回；处置与优先级待用户拍板 |
| BUG-50 | 知识库管理页 ElDialog 组件漏 import——未注册组件被当未知标签裸渲染，新建/编辑弹窗的名称描述表单裸露在列表底部且常显 | P3 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 补 ElDialog import；教训：element-plus 组件全部显式 import 后应过一遍「模板用到的组件名 ⊆ import 列表」自查（本次 ElDialog/ElDrawer 一对照即穿） |
| BUG-51 | 知识库页「知识管理」抽屉切换不同库时列表不刷新：AgentKnowledgeConfig 无 props watch（原为智能体抽屉静态组件），复用实例残留上一库数据——两库点开内容相同 | P3 | 9080 用户实测(2026-10-01) | 已发布(v1.3.0) | v1.3.0 | 抽屉内容挂 :key="activeKb.id" 强制随库重建；后端双维查询分库正确性 curl kb1/kb2 同步实证 |
| BUG-52 | thinking-display T-04 自引入：admin graph.ts streamHarnessChat 的 nodeResponse 字段白名单未透 thinking，admin 运行页思考区永不出现（用户实测"啥都没看到"；curl 证据：nginx 只有 admin 通道流量而前端补丁生效处在前台链路）——排查中途另澄清：用户测试页为 admin 运行页，前台链路本身完好未证伪 | P2 | 9080 用户实测(2026-10-02) | 已发布(v1.3.0) | v1.3.0 | dispatch 补 thinking 字段；同笔补历史消息 ThinkingBlock 回显 | 
| BUG-53 | 流式回答期间刷新页面：已生成内容整轮丢失且不继续生成——助手消息仅在前端 onComplete 落库、执行随 SSE 连接取消被 Reactor 静默放弃（实测证据：12:32 会话仅存 user 行无 assistant 行）。处置方向已定：**A′ 前端快照止血 + B 服务端脱离执行（BL-22）共同保底**，三接缝设计（回显去重/落库所有权移交 upsert/停止语义独立化）见 BL-22 注记；A′ 原型已趟过插入点（stash 中） | P2 | 9080 用户实测(2026-10-02) | 已发布(v1.4.0预) | detached-stream T-06 | 关账：B 主体六演练全绿+用户浏览器复验 AC-01/03；所有权移交后刷新零丢失；A′ 降级秒级兜底 |
| BUG-54 | 登出报错「服务器内部错误」：前后域 logout 裸调 StpUtil.logout()，token 已失效场景（重复登出/过期后被前端最后调/乱 token）抛 NotLoginException 冒 500 信封——/api/privilege/auth/* 在过滤器排除名单内，直达 service 无兜底。注：早前 logout 500 曾归因旧包双前缀（BUG-39 现场），本单是**残留的服务端非幂等真身** | P3 | 9080 用户实测(2026-10-02) | 已发布(v1.3.0) | v1.3.0 | 后台 privilege + 平台 platform 两域登出幂等化（无登录态=登出终态，静默吞 NotLogin）；四态 curl 全回退出成功 |
| BUG-55 | 长工具任务 SSE 被代理掐死：模型/工具阶段可现 >5 分钟完全静默（实测 13:30:39 png 已产出后下一跳模型调用静默 5m19s），nginx `proxy_read_timeout 300s` 到点斩流（access log 110 upstream timed out）→ 无 end 帧/不落库/不扫描，用户端表现为"一直没执行完"；且 SSE 无心跳、模型调用零日志静默根因不可追 | P1 | 9080 用户实测+nginx取证(2026-10-02) | 已发布(v1.3.0) | v1.3.0 | 三件套：①SseSupport 心跳封装，5 个 SSE 端点 15s 无数据注入 ":ping" comment 帧（前端解析器天然忽略，旧客户端零扰）②nginx /api /platform 读超时 300s→900s 双保险 ③ModelCall 起止 INFO 留痕；实测：强制 25s 静默窗口出现 2 帧 ping、end/agentFiles 帧齐、产物登记正常。内容丢失的彻底消灭属 BL-22（已立项） |
| BUG-56 | 登出后 500 信封（用户二报，真身换人）：交付 nginx regex `^/(platform\|auth)` 吞了前端登录页**路由** `/auth/login`——登出成功→浏览器整页导航 GET /auth/login→被代理到后端(仅POST)→405→data 域兜底包成 {data,message,success} 500；BUG-54 幂等修复经核实均在位（登出 API 本身 200） | P2 | 9080 用户实测(2026-10-02) | 已发布(v1.4.0) | v1.4.0 | nginx exact location `= /auth/login` `/auth/register` 回落 SPA index.html（exact 优先级最高，POST API 不受影响）；实测导航200+SPA、登出API/组API/verify 11项全绿。教训：代理裸域 regex 必须给同前缀的 SPA 路由留豁免（verify 宜补导航断言） |
| BUG-57 | 刷新后回答样式偶发丢失（渲染层）：BL-22 落库移交后服务端行存**原始 markdown**，而历史装载沿用旧启发式「内容含 <xxx> 即视为已渲染 HTML 跳过转译」（旧前端存 HTML 时代遗产）——凡回答内嵌 SVG/HTML/代码块样式整体丢光走 v-else 裸文本；纯 markdown 行不受影响故"偶发"。前台与 admin 双端同患 | P2 | 9080 用户实测(2026-10-02) | 已发布(v1.4.0) | - | 服务端行（metadata 含 turnId）无条件 markdown 转译（marked 本就正确处理内嵌 HTML）；旧行启发式保留兼容历史数据；双端各修一处 \|---；三装载点（首载/轮询/重选）统一 applyServerRowRender，轮询口漏改为二发根因已闭合 ；**四发=真终端病灶**：admin 对 markdown 元素 CSS 为 0 条（转换一直是好的，标题/表格/代码全浏览器默认样式）→ 补 .message :deep 一族对齐前台观感｜坑引: L-01/L-06/L-11 |
| BUG-58 | 前台每次右侧切换智能体都自动新建一个（临时）会话并强占选中——侧栏堆「新会话」垃圾条目、打断"回到该智能体上次会话"的预期。用户要求：不自动建；落未选择会话默认页或该智能体既有会话 | P3 | 9080 用户实测(2026-10-02) | 已发布(v1.4.0) | - | **v2 真身**：右侧面板 AgentListPanel.handlePick 自带「找不到名为新会话就强制建」逻辑（首修只改了 chat.vue selectAgent 另一链路，用户复测仍现）——改为进该智能体最近一条既有会话否则清选中落默认页；composer 惰性建链不动。教训：同一交互多入口必须清点全部调用方（BUG-47 同族）｜坑引: L-06 |
| BUG-59 | 计划模式两段式确认：第二次 confirm 放行后 AgentScope resume 对 plan_exit 工具自动生成 error 结果并 RequestStopEvent，轮次挂起至看门狗 10min 超时定稿（无脏数据/无双行/超时兜底有效）。单段确认（shell 类危险操作）全链正常（T-08 实证 done+产物）。BL-22 信号架构本身已验证无涉 | P3 | 9080 演练取证(2026-10-02) | 已发布(v1.4.0) | - | 待处置方向：查 AgentScope 原生二次确认 resume 用法或 plan 模式确认合并为单段；挂账不急修；**根治**：确认放行时显式调框架公开 API exitPlanMode（此前缺失=模型困在 plan 阶段只思考不执行，2/3 无正文的真身）+ 续跑文案断言已退出计划模式；2/2 演练全绿（正文定稿+产物落盘+exitPlanMode 日志）。框架 maybePatchPendingToolCalls 先于 applyConfirmResults 的顺序缺陷已留档（上游问题，现方案绕过）。**二发（2026-10-03）**：isPlanModeActive(ctx) 误报 false 致 exitPlanMode 被静默跳过（会话 fcca0457：模型独白 19.7K 字"Let's go"后零工具零正文）——改无条件调用+查询态留痕日志；curl 演练复验 done/739字/产物落盘。另揪出记忆污染：MEMORY.md 被模型写入"交付到 workspace root"错误惯例（历史误会的自我强化），已消毒+根目录散文件清理+写入正确惯例｜坑引: L-02/L-09/L-10 |
| BUG-60 | 会话文件面板「模型明明生成/复用了文件却看不到」：①登记去重 storeKey(相对路径+大小) 是**全局**维度——旧会话登记过的文件被新会话复用/覆写后永不再现（用户实测：模型执行落盘+正文报告完成，面板只有 PLAN.md）②二级根因：tbl_data_chat_session.create_time **95/95 全空**（创建路径从未写入），补扫窗口回退 EPOCH 一度灌洪 18 条历史文件（已清理含物理副本） | P2 | 9080 用户实测(2026-10-03) | 已发布(v1.4.0) | - | 去重改按会话维度；补扫窗口收窄至会话创建时间（缺失则跳过补扫，轮末扫描兜底）；createSession 补写时间戳+存量 95 行按首条消息回填；实测：用户会话补扫后恰好 3 文件（weekly×2+PLAN），旧会话不受扰 |
| BUG-61 | 前台刷新追流「卡死」：join 重放把上千历史帧一瞬灌入，每帧触发全量 markdownToHtml+Vue 渲染，主线程饱和假死（服务端该轮实际正常完成 done/1551字）。直播路径帧速受网络节流无此问题，纯追流风暴 | P2 | 9080 用户实测(2026-10-03) | 已发布(v1.4.0) | - | join onProgress 150ms 节流合并（end/收尾强制终渲染）；顺带清理两条死会话 pending_confirm 残留键 |
| BUG-62 | multistage 国内源默认件 settings.aliyun.xml 的 XML 注释含 `--build-arg`（注释内连续双横线非法）→ Maven「Non-parseable settings」构建必挂；随 d0b4552 进 v1.4.0 tag 但零消费未暴雷，package.sh(T-02) 首个真实构建 1.3 秒炸出；settings.default.xml 同病 | P3 | 交付件 | 已发布(v1.5.0) | - | 注释改写避开双横线+minidom 解析自证；教训：交付件入库前应过格式校验（XML/JSON/YAML lint），「没人用过」≠「是对的」 |
| BUG-63 | compose 四个长驻服务(postgres/redis/backend/nginx)无 restart 策略——宿主/Docker 重启后栈不自愈，服务器场景必须人工拉起；migrator 两个一次性服务 restart:"no" 属正确配置不受影响 | P2 | 交付件 | 已发布(v1.5.0) | - | 四服务补 restart: unless-stopped + dev 栈 force-recreate 实证生效 + verify=0；连带事故：前日镜像清理误删 compose 默认引用的三个裸名 tag(pgvector/redis:7-alpine/postgres:16-alpine)致 recreate 拉 Hub 失败——已全部复原(retag+daocloud 重拉)，L-06 三犯记账 |
| BUG-64 | GlobalExceptionHandler 把 ResponseStatusException(404) 包装成 500「服务器内部错误」——DELETE 不存在资源时状态语义失真（应回 404 语义） | 低 | mcp-client-tools T-07 级联演练（重复 DELETE 已删 agent 触发） | 已发布(v1.6.0) | v1.6.0 | AgentController.checkAgentExists:163 抛 404 → handler 统一 500；修复=handler 按 ResponseStatusException 原状态透传 **【修复 2026-10-05】** `GlobalExceptionHandler` 增 `@ExceptionHandler(ResponseStatusException.class)`：按**原状态**透传（`HttpStatus.resolve` + `ResponseEntity.status(...)`），reason 取异常自身、缺失则用状态短语；更具体 handler 优先于通用 `Exception` 分支，故 404 不再被包成 500。编译通过（**待部署验证**：`DELETE /api/agent/{不存在id}` 应回 404 而非 500） **【已验证 2026-10-05，部署实测】** 18:05 部署后：`DELETE /api/agent/999999999` → **HTTP 404** + `{"message":"agent with id: 999999999 not found","success":false}`（修前 500「服务器内部错误」）；重启清扫 0 个 generating（无轮被打断）；部署后 `verify 13/13` 复跑全 PASS（无回归） |
| BUG-65 | MCP 工具名含连字符时模型回传 name=null → 框架调度 NPE 崩轮（"生成异常中断"） | 高 | mcp-client-tools T-07 用户走查（麦当劳真 MCP 两轮复现） | 已发布(v1.6.0) | v1.6.0 | A/B 隔离：同服务器仅改净化规则即愈；sanitize 连字符→下划线+折叠+首字符字母化（McpMountService）；PRE_ACTING name=m_MCP__query_nearby_stores 实证 |
| BUG-66 | 变体签名缺 name 列——MCP 改名后命中旧缓存变体，工具前缀漂移且跨重启不确定 | 低 | BUG-65 修复复测（改名后日志仍旧前缀） | 已发布(v1.6.0) | v1.6.0 | 签名加入 name（McpMountService.signature）；中文名复原实测前缀 m_MCP__ 即时生效 |
| BUG-67 | 会话文件面板跨会话污染：列表显示同智能体其它会话的文件（「logo设计」会话实测挂 17 行）——scan=true 补扫按 agent 共享 workspace+「会话创建时间」时间窗归属文件，老会话窗口大，把窗口内所有新文件收编进本会话（BUG-60 窗口收窄方案的回归面） | 中 | 2026-10-04 用户实用发现（logo设计会话，session 264458e0，agent 33，建于10-03） | 已发布(v1.6.0) | - | AgentFileController.list:61 windowStart=session.createTime + WorkspaceArtifactScanner 扫描归属；修复方向：归属判定从时间窗收窄为轮次边界或排除已归属其它会话的文件；需小设计，关联 BL-19/BUG-60 |
| BUG-68 | 跨智能体记忆污染：不同智能体的不同会话出现其它智能体其它会话的记忆 | 高 | 2026-10-04 用户实用发现 | 已发布(v1.6.0) | - | 根因实勘：HarnessAgentFactory:110 `.workspace(Path.of(workspaceRoot))` 固定值——**全部智能体共享同一 workspace 目录**，harness 记忆文件/会话转录/产物同目录互相读写；Redis 分布式存储键含 agentName 段（agents␀名␀users␀uid）但默认本地文件系统无隔离；PostgresAgentStateStore 键=userId:sessionId 亦无 agentId 维（结构性弱点）。**与 BUG-67 同根**（文件面板跨会话污染=共享 workspace 另一症状）。修复方向（2026-10-05 勘察终版精化）：真通道={userId}/MEMORY.md+memory/ 用户级记忆**无 agent 维**+根级散文件共享；会话转录({userId}/agents/agent-{id}/sessions)与任务区与 Postgres 状态键均已隔离排除。修复=workspace 根按 agent 分目录（{root}/{runtimeKey}）一刀全隔+存量归档；立 spec 20261005_workspace-isolation |
| BUG-69 | MCP 绑定智能体的实时 SSE 流全空帧（28帧 content="" +END），UI 无输出但 DB 落库完整——用户暗号会话三连问全部"没回答就提示会话完成" | 高 | 2026-10-05 用户实用发现（admin 对话，agent-36 会话 0fa806a7） | 已延期(v1.6.0→观察复盘) | v1.6.0（仅缓解：变体TTL+金丝雀） | 范围已隔离：无 MCP 智能体实时流正常（agent-33 37非空帧）→ 病灶=MCP 变体路径（T-05 doStream 接线回归）；DB 完整=TurnManager 侧累积正常，丢失在 SSE 帧内容层；昨日演练 1426 字节即空帧被误判截断（L-07：DB 面绿≠用户可见面绿）；排障实录（2026-10-05）：六场景复现全绿（新变体/缓存复用/工具调用轮/轮后轮/stub死连接/金丝雀轮），**不可复现**——故障仅现于长运行实例（9h uptime，用户12:10三连问+探针12:30），重启即消失；核心矛盾未解（TurnManager 帧累积有文=DB满 vs SSE 订阅侧空帧，同一 replay sink 两视图背离）。处置：①变体缓存 30min 闲置 TTL 重建（封顶陈旧状态生命周期，缓解非根治）②[b69-canary] 每轮体检日志（contentLen>0∧textFrames=0 告警；局限：sink→订阅段丢失测不到，复现时需配合出口抓包）③逐事件仪表已撤。复现请记准确时刻对表 canary 行。**〔2026-10-05 用户裁决：留存待后续复盘〕**——金丝雀+TTL 已布防随 v1.6.0 走，再犯即有决定性证据 **【用户裁决 2026-10-05：留存观察（批准人=陈卓）】** 不可复现（六场景全绿、重启即消失）→ 不做盲修；缓解已随 v1.6.0 上线（MCP 变体 30min 闲置 TTL 重建 + `[b69-canary]` 每轮体检：contentLen>0∧textFrames=0 告警）；**再犯即取决定性证据**（复现时记准时刻对 canary 行 + 需配合出口抓包，局限：sink→订阅段丢失 canary 测不到）。复盘触发条件：用户再遇空帧，或长 uptime 实例（>8h）+ MCP 会话组合复现 |
| BUG-70 | 长任务轮前端假死+600s 看门狗腰斩：logo 设计类多轮工具任务服务端持续工作（model-call 连发实证）但 UI 不渲染进度似卡死；turn-timeout-seconds=600 到点强杀="执行一半中断"主因（调大 max_iterations 无效，卡在轮时长非轮数） | 高 | 2026-10-05 用户实用发现（agent-33 logo设计，会话 14264bfe/0690b2d0 现场活体） | 已发布(v1.6.0) | v1.6.0（long-turn-resilience） | 现场证据：turn/status=true+12:54 仍 ModelCall 连发；watchdog @Value 600s；0690b2d0 轮 st=timeout 实证腰斩；**前端病灶实勘**：api-transport 每150ms 全量 markdownToHtml(textBuf) 重渲=O(n²) 主线程饱和（用户纠正：浏览器整体无响应非仅不渲染）；修复走 spec 20261005_long-turn-resilience **【状态修正 2026-10-05】** 修复已上线：服务端帧合并 100ms（实测 600→6.6 帧/s，`HarnessTurnManager.mergeJanitor`）+ 前端增量渲染（incrementalMarkdown/400ms 节流）；**待用户界面复验「长轮不再卡死浏览器」** **【已验证 2026-10-05，验证人=陈卓（T-04/T-07 实测覆盖其复现路径）】** 用户长轮实测"刷新续流没问题 / E2E 基本通过"；join 原位续渲 + ≥150ms 节流替代逐帧全量重渲，长轮期间页面可交互 |
| BUG-71 | 刷新追流（join）后仅显示 think done 无正文：运行中轮刷新→replay+live 续渲断链，或轮已死（如部署重启清扫）→单 end 帧+旧快照误导 | 中 | 2026-10-05 用户实用发现（同场测试问题1） | 已发布(v1.6.0) | v1.6.0（long-turn-resilience） | bufferFrames=2000 排除缓冲溢出；**12:47"复发"查实为误会**：用户重进 12:23 旧会话，回显旧轮"服务重启"定稿文案（后端 RestartCount=0 无幻影重启，12:47 匹配行实为只读 Row dump，saveMessageApi 有 harness 拦截未复活——两度猜错均已证伪）；修复=join 渲染+历史中断文案归属标注，走 spec 20261005_long-turn-resilience **【状态修正 2026-10-05】** 修复已上线：join 渲染 + 历史中断文案归属标注（`applyServerRowRender` 统一解析 metadata）；且「12:47 复发」已证伪（实为只读回显旧轮定稿文案）；**待用户界面复验** **【已验证 2026-10-05，验证人=陈卓（T-04 实测覆盖"刷新运行中轮"复现路径）】** 用户实测运行中刷新后正文续显、不再出现"Think Done 无正文"；join 渲染 + 中断文案时间归属已上线 |
| BUG-72 | 运行页思考块自行消失：刷新后可见，约 5 秒后消失（正文输出前后不稳定） | 中 | 2026-10-05 用户实测（运行页长轮） | 已发布(v1.6.0) | v1.6.0（long-turn-resilience T-04） | 根因：5s 轮询 tick 只走 `applyServerRowRender`，而 metadata.thinking 解析仅存在于首载路径 → tick 覆盖消息即丢 thinking 字段 → `v-if="message.thinking"` 卸载思考块；修复=解析统一收进 applyServerRowRender（thinking/thinkingMs/streaming 一次到位），首载与轮询同源 |
| BUG-73 | 运行页刷新后误判空闲：任务仍在跑但输入区可编辑可发送、终止按钮变回发送（应禁用编辑但可终止） | 中 | 2026-10-05 用户实测 | 已发布(v1.6.0) | v1.6.0（long-turn-resilience T-04） | 根因：detached-stream T-05 轮询只刷新消息（currentMessages），**从不置 isStreaming** → 刷新后 isStreaming=false → 输入框 `:disabled="isStreaming"` 为假、发送按钮 `v-if="!isStreaming"` 显示；修复=初始 turnStatus 为真即 `isStreaming=true`，tick 检测到结束再置 false 并收敛终稿 |
| BUG-74 | 会话串消息：任务执行中新建/切换会话，旧会话正在输出的内容显示在新会话里（跨会话污染） | 高 | 2026-10-05 用户实测 | 已发布(v1.6.0) | v1.6.0（long-turn-resilience T-04） | 根因：detached-stream T-05 轮询闭包**无会话守卫**——`tick` 无条件 `currentMessages.value = applyServerRowRender(旧session消息)`；切到新会话后旧 tick 仍运行并覆盖视图；且 BUG-73 加的 `isStreaming=true` 亦为全局无条件；修复=闭包入口与每 tick 前后三处 `currentSession.id === session.id` 守卫（切走即停轮询、不再触碰视图），视图态随会话隔离 |
| BUG-75 | 运行页刷新后双输出窗口：上面一个（已加载行）下面还一个（空的直播区） | 中 | 2026-10-05 用户实测（BUG-73 修复引入） | 已发布(v1.6.0) | v1.6.0（long-turn-resilience T-04） | 根因：BUG-73 用 `isStreaming=true` 表达"轮次在跑"，而直播区渲染条件同为 `v-if="isStreaming \|\| nodeBlocks.length>0"` → 直播区凭空多出一个空窗口；修复=拆双状态 `remoteRunning`（只控输入禁用/终止按钮），直播区仍只由 isStreaming/nodeBlocks 驱动；并补：轮询态下终止按钮走服务端 `harnessTurnCancelApi`（原先只认本地 closeStream，点了无反应） |
| BUG-76 | 长轮执行一段时间后前端提示「超时」并"中断"，但服务端仍在跑、状态仍 thinking | 高 | 2026-10-05 用户实测（运行页 logo 长轮） | 已发布(v1.6.0) | v1.6.0（long-turn-resilience T-04） | 取证：nginx 零超时记录（proxy_buffering off + read_timeout 900s 在位）、后端零超时/挂起日志、两个会话 DB 仍 generating 且 model-call 连发 → 服务端正常；**前端 vben 请求客户端默认超时在负载下触发「请求超时」toast**，且 detached-stream 轮询 catch 分支把单次异常**误判为"轮次已结束"**（remoteRunning=false）→ UI 退回空闲/中断态，而服务端继续生成；修复=①轮询容错：异常=不确定态继续重试，仅连续失败≥12次(≈1min)才收敛 ②getSessionMessagesApi 超时放宽 60s |
| BUG-77 | 中断后紧接一轮必失败：提示「模型或流错误中断：BadRequestException」 | 高 | 2026-10-05 用户实测（会话 90525497「继续」） | 已发布(v1.6.0) | v1.6.0（long-turn-resilience） | 根因（provider 原文取证）：`400 invalid_request_error: The \`reasoning_content\` in the thinking mode must be passed back to the API`——思考模式下助手消息的 reasoning_content 必须回传，而**被中断的轮次（重启/超时/取消）丢失了 reasoning_content/tool 配对**，导致紧接的下一轮请求被 provider 拒绝；证据：DB 该会话极小（≈922 token，排除上下文超限）、失败后 15:32:52 一轮自行恢复（框架 pending-tool-recovery 生效）→ 属「一次性状态不一致」而非永久中毒；处置：①错误文案带上 provider 原文（截断 300 字，免翻日志）②深层修复（见下方根因细化）列 T-04 后续。**根因细化（2026-10-05 二轮取证）**：查框架状态表 `tbl_harness_store_state`（session_id 带 uid 前缀 `uid:sessionId`）——该会话 29 条消息结构**完全合法**（每个 tool_use 均有配对 tool_result、每条 assistant 均含 `{"type":"thinking"}` 块），即**数据没坏、reasoning 也持久化了**；真因指向**框架把持久化状态转 provider 报文时未回填 reasoning_content**（重启后重新加载历史的转换路径），故表现为"重启/中断后紧接一轮 400，之后又能跑"。**计划修法（下批实施）**：Turn 内保存 source 供应商，`onError` 识别该 400 签名（含 reasoning_content/400）→ **自动原样重试一次**（此前观测到重试即可成功），仅重试仍失败才按现文案定稿；**决定性根因取证（2026-10-05 三轮）**：扫描该会话框架状态 44 条消息 → **17 条助手消息含 thinking 块，但有 5 条"仅 tool_use、无 thinking 块"**（索引 3/7/11/15/19）；DeepSeek 思考模式硬规则="每个带 tool_calls 的助手消息必须回传 reasoning_content" → 这 5 条即违规源。**它们的产生路径**：中断/重启后框架 `enablePendingToolRecovery` 合成的 tool_use 消息不带 thinking（已确认我方 registry 已正确使用 DeepSeekFormatter，见 HarnessModelRegistry:52，故排除 formatter 选型问题）；**为何是"一次性"**：违规消息不在队尾时不再触发校验（此点未完全证明，如实标注）。**根治候选（下批）**：在轮次开始前做状态修复——为"仅 tool_use 无 thinking"的助手消息注入空 thinking 块（或剔除），经框架 stateStore API 写回后再发请求；自动重试实现已落码（源流供应商留存 + 零产出时按签名自动重试一次 + 仍失败才定稿），**按用户指令"先改不发布"未部署，待验证**；另可评估把该智能体切非思考模型作临时代偿 |
| BUG-78 | 会话产物已落盘但文件面板列表为空（用户实测：会话 a467aecf「初次问候」logo 任务完成且产出 owl-kids 文件） | 高 | 2026-10-05 用户实测 | 已发布(v1.6.0) | v1.6.0（long-turn-resilience / workspace-isolation 回归） | 根因：`WorkspaceArtifactScanner.candidateAgentDirs` 只扫 `root/{userId}` 与 `root/agents/{key}` 老布局；**workspace-isolation 把智能体根下沉一层**后，产物直接落在 `root/owl-kids/...`（无 {userId} 前缀）→ 扫不到 → 不登记 → 面板 0 条（已实证：磁盘有文件、tbl_data_agent_file 该会话零行、接口 scan=true 仍 0 条）；修复=候选目录补"智能体根自身"（isInternal 已过滤 .agentscope/agents/会话等内部件）；**该版已部署并实测 0→82 条（缓解生效）**。**真根因（2026-10-05 三轮取证）**：扫描根用**请求入参 sn** 算 runtimeKey（admin/前台请求不带 sn → `agent-33`），而 factory 写入根用**库中 `agent.sn`** 算（`owl-kids`）→ 扫描根与写入根天生错位，兜底候选根只是碰巧命中；根治=scanner `resolveAgentKey` 改为查库取 sn（与 factory 同源），且扫描根下沉到会话目录（R-06，commit f7a0964，**待部署验证**）；同批过滤 `call_*` 工具调用占位文件与 `large_tool_results` 目录（广扫噪音，实测库内 5 条 `call_*`）**【已验证 2026-10-05 部署后实测】** 新会话面板列出本会话 file+shell 两产物（`sh_probe.txt`/`file_probe.txt`）；新建会话 B=0 条（跨会话零交叉）；旧会话属主仍可见（3a3f8fe1=3、31f42c23=10）；`call_*`=0；另发现并修复 BUG-82（技能缓存噪音） |
| BUG-79 | shell 工具工作目录=/app（容器根）而非智能体工作区 → shell 产物逃出工作区：面板不可见、容器重建即丢、跨智能体/用户混放 | 高 | 2026-10-05 实测取证（探针会话 b79） | 已发布(v1.6.0) | v1.6.0（workspace-isolation 未覆盖面） | 实证：①file 工具相对写 → `agent-33/{uid}/probe_ft.txt` ✓ 工作区内 ②**shell 相对写 → `/app/probe_sh.txt`** ✗ 完全在工作区之外（全容器搜得）③另有 root 级目录 `agent-33/owl-kids`（绝对路径/python 直写）；**根因一锤（2026-10-05 框架规格取证）**：`LocalFilesystemSpec.toFilesystem` 把 `project`（缺省 `Paths.get(System.getProperty("user.dir"))`）作为 `LocalFilesystemWithShell.shellCwd` 构造入参，`resolveExecuteCwd` **优先返回 shellCwd**（非空即覆盖 namespace 解析）→ 我方从未设 project ⇒ shell cwd 恒=进程 cwd=`/app`；修复=`.project(会话目录)`（T-12 代码已落地，commit f7a0964，**待部署实测 `pwd`**）；另注：`/app` 实测零业务产物（逃逸件随容器重建蒸发，反证不持久）**【已验证 2026-10-05 部署后实测】** 会话内 shell `pwd` 输出=`/app/uploads/agent-workspace/agent-33/{sessionId}`（修前 `/app`）；`echo shell_probe > sh_probe.txt` 落会话目录且面板可见；另有前置补丁（factory 建实例先 createDirectories，否则 ProcessBuilder 对不存在 cwd 抛 error=2 会让每条 shell 命令全废） |
| BUG-80 | 文件面板「删除」失效（用户实测：列表里点删除删不掉） | 中 | 2026-10-05 用户实测 | 已发布(v1.6.0) | v1.6.0（long-turn-resilience） | 实测取证：`DELETE /api/agent/files/{id}` 返回 `code=42031「无权访问该文件」`（用 admin token 删前台用户产物）；前端链路正常（`deleteAgentFileApi` → ChatFilesPanel:104 确认后调用）；根因=**服务端归属校验把 admin 面板挡住**（文件 userId=前台用户，当前登录=admin）；**修复（commit f7a0964，待部署验证）**：新增 `canAccessSession`=属主 本人 或 后台用户表 `tbl_privilege_user` 命中者（跨属主访问 INFO 留痕）；实测判定表 admin(`461681072489615360`)✓ / 前台(`461671714812850176`)✗ 正确；同批修孤儿行（会话行已不存在→回落产物创建者本人，此前连自己的残留都删不掉，实测库内 84 条孤儿行）；抽屉补扫同步放行管理员；另注：`/api/agent/file/{id}`（单数）不存在→500（BUG-64 家族老毛病，前端未使用，未修）**【已验证 2026-10-05 部署后实测】** admin token 列前台用户会话 → code=100/30 条；admin 删 `catalog.json` → code=100 且库内 `del_flag=1`；属主本人删自己文件 → 100（正对照）；前台 token 访问他人会话（90525497）→ 42031（负对照，越权仍拦） |
| BUG-81 | 有任务在跑时，切到其它/新建会话也是「终止」按钮且输入框不可写（用户实测"别的会话都发不出去了"） | 高 | 2026-10-05 用户实测 | 已发布(v1.6.0) | v1.6.0（long-turn-resilience 回归，上一批我引入） | 根因=**`components/run/index.vue` 的 `remoteRunning` 是页面级 ref，未按会话分片**（BUG-75 拆"本地直播/服务端轮询"时新引入）；泄漏路径：A 会话在跑（status=true）→ `remoteRunning=true` → 切到 B/新建会话时 `selectSession` 里 `if (!(await harnessTurnStatusApi(B))) return;` **提前 return 未复位**，且 B 的轮询因会话守卫不再启动 → 永久卡 true → `:disabled="isStreaming 或 remoteRunning 或 showHarnessConfirm"` 锁死输入框、按钮只剩终止；同族第二处：`showHarnessConfirm`/`pendingConfirmButtons` 一族同样是页面级单例（A 待确认会锁死 B，旧确认条还会在切回时复活）；**非服务端问题**（`turn/status` 已按 sessionId 分片 `turnManager.hasActive(sessionId)`）；前台 chat 面按 `sendingSessions:Set<sessionId>` 分片，**未受影响**（已核对 ChatComposer 无 disabled 绑定）；修复=remoteRunning 与确认条全族进 `SessionRuntimeState` 分片存取（+镜像 watch 防旧条复活），前端构建通过、待部署 **【已验证 2026-10-05，验证人=陈卓（用户界面复验）】** 用户实测复验通过：任务执行中切到历史其他会话/新建会话，输入框可写、按钮为「发送」；修复随 nginx 重建上线（线上 bundle 与本地修复构建 sha256 一致 `a500b1c9…ad056`） |
| BUG-82 | 文件面板被框架内部件灌满：技能缓存（`.skills-cache/logo-design/**`）被当成「用户产物」登记，单会话 28 条噪音（catalog.json/search_library.py/svg_audit.py…） | 中 | 2026-10-05 R-06 验收实测发现 | 已发布(v1.6.0) | v1.6.0（workspace-isolation 收尾） | 根因：`WorkspaceArtifactScanner.isInternal` 的目录判据只有显式 `SKIP_DIRS` 名单（sessions/tasks/.index/memory），点开头目录仅当「文件名点开头且不含点」才跳——`.skills-cache` 含点故漏过；影响：面板噪音（老会话 82 条/新会话 30 条里大多是这个）+ 每会话重复 tee 一份技能包（磁盘与 DB 膨胀）+ storeKey 每会话唯一导致无法幂等去重；修法=目录段凡以 `.` 开头即内部件（末段文件不判，保留原文件级规则）；验证：新会话 C 同类 30 条 → 修后 1 条（c_probe.txt） **【已验证 2026-10-05，验证人=陈卓（用户界面确认）】** 用户实测："这个没了"——新会话文件面板不再出现技能缓存条目（`catalog.json`/`search_library.py`/`svg_audit.py` 等） |
| BUG-83 | 文件工具落点与会话根差一层：file 工具写 `{sessionId}/{uid}/`，shell cwd=`{sessionId}/`（模型当场指出并要求确认遵循哪种约定） | 低 | 2026-10-05 R-06 验收实测 | 已延期(v1.6.0→BL-26/M3) | 待裁决 | 事实：框架 `IsolationScope.USER` namespace=[uid]（file 工具在其下），shellCwd 由 `.project` 固定为会话根；两者都在会话目录内（隔离要求满足），但同一会话内「shell 写的文件 file 工具看不见、反之亦然」；选项 A=接受并列文档化（当前）；选项 B=`.project(sessionRoot/uid)` 精确对齐——需把 userId 传进 factory，而 `RuntimeContext.userId` 随「谁在跑这轮」变化（admin 跑前台用户会话时不同），有错位风险；待用户裁决 **【2026-10-05 定案取证与建议】** 关键事实：①技能包物化在**会话工作区根**（实测 `{sessionRoot}/.skills-cache/postgresql_public_tbl_harness_skills/logo-design/**`），而框架 `USER` namespace 只把**文件工具**下沉到 `{uid}/`，`memory/` 也在 `{uid}/` 下 ②老会话（a467aecf）模型自述实证了代价：「`read_file` 打不开 `.skills-cache/...`；改用 execute+cat」——文件工具相对根到不了技能包（差一层）③B 会把 shell 也挪进 `{uid}/`，于是**两个工具都够不到技能包的相对路径**（都要 `../.skills-cache`），且需把 userId 穿到 factory+registry 缓存键、并复刻框架「userId 空→[sessionId]」回退；更麻烦的是 shell cwd 会随**谁在跑这轮**（admin 跑前台用户会话）或重启后续跑换账号而漂移 → 新增一类不一致。**建议=A（保持现状，已部署已验收）**，可选 A+（在系统提示里明确告知两个根：shell cwd=X、文件工具相对根=X/{uid}）；**若追求"一个根"的正解=C**：去掉 USER namespace 层（`IsolationScope.AGENT` + `workspace=sessionRoot`）→ 文件工具根=shell cwd=技能包根=memory 根；C 触及已确认 plan 的「框架内再拼 uid」决策，需走 requirements/plan 增量重确认，且需先做一次 AGENT 档位的落点/记忆/技能 spike <br> **【用户裁决 2026-10-05】先做 A（保持现状：shell cwd=会话根、文件工具在 {uid}/ 子层），C 方案（去 USER namespace 层做单根）列入待办 `BL-26`（建议 M3 批次，立项第一步做 AGENT 档位落点/记忆/技能三件 spike，并走 requirements/plan 增量重确认）** |
| BUG-85 | 单次模型调用无超时/无静默可视：一轮内出现 **8 分 23 秒"零帧、零日志、零错误"静默期**，界面只有计时器，用户判定"任务僵死" | 中 | 2026-10-05 用户实测（会话 6e9c09e0「梅西蓝白 logo」长轮） | 已验证(v1.7.0) | v1.6.0（long-turn-resilience 未覆盖） | **证据（同一轮，实测）**：该轮共 132 次模型调用、最长单次仅 2m11s；但 `17:39:34.717 ModelCallStartEvent(messages=27)` 之后**直到 17:47:57.666 才出现下一个事件（间隔 503s）**，其间**无 ModelCallEnd、无 WARN/ERROR/重试日志**（全量日志 0 条）→ TurnManager 无帧 → SSE 无输出 → 前端只剩「正在执行(mm:ss)」计时器。**为何看门狗不杀**：空闲判据 600s（503<600 刚好不触发）＋总时长闸=0（设计上关闭）→ 34 分钟长轮按设计允许，**它也确实没死**：18:02:42 自行恢复并正常收尾（status=done、正文 2948 字）。**同轮金丝雀**：frames=179917 / emitted=5481 / dropped=47279 / textFrames=1280 —— `dropped` 是**空生命周期帧的设计内丢弃**（非 sink 溢出），合并闸把源帧压 ≈33:1（客户端≈3.5 帧/秒）→ **前端未被压垮**；我方先前的"思考增量未参与合并导致帧风暴"怀疑**经查代码不成立并已撤回**（`isPureTextDelta` 同时判 content 与 thinking）。**修复方向**：①模型调用级超时（首帧/整体，如 180s 无首帧即中止并明确报错）②"当前步骤 + 已静默 N 秒"心跳帧（现在只有计时器，用户无法区分"在算"与"卡死"）③单步静默超阈值提示"仍在执行，可中断" 〔2026-10-05：随 `specs/20261005_visibility-filetree-hygiene` 立项（R-01 长轮静默可见性 + 模型调用超时）〕 **【已验证 2026-10-05/06，实测】**T-07 阶段标记（`MODEL→TOOL(execute)→12s→IDLE→MODEL`，日志可查）；T-08 静默心跳（SSE `silenceMs=18451/23452`，5s 节流，标签按阶段正确，canary `heartbeats=`）；T-09 首帧超时（1s 阈值触发 `status=model_timeout`；**负对照** 5s 阈值 + 工具 sleep 12s 未误杀）；T-10 两面显示「· 阶段 · 已静默 Ns」。修复中另纠两处：ToolResultStart 才是工具执行起点；`finish()` 文案条件漏新状态（会留空气泡） ||
| BUG-86 | 智能体读到了**未关联的知识库**内容：智能体03 只关联「产品手册库」，却读到了「制度汇编的知识库」的内容 | **P0 最高优先**（用户 2026-10-05 口令「优先级最高」） | 2026-10-05 用户实测口述 | 新建 | 待裁决（建议优先复现定性） | **已确认事实（本次取证）**：① 智能体33 的绑定**正确**——`tbl_data_agent_kbase_bind` 只有一条：`knowledge_base_id=2`（产品手册库）；② 但**组级授权**里，通用组（`433382915273580544`，智能体33 与前台账号都属该组）**同时被授权 KB1「制度汇编的知识库」与 KB2「产品手册库」**（`tbl_platform_group_kbase_info` 两条 del_flag=0）；③ 绑定期校验口径=「与智能体所在组有交集才可选」（`AgentKbaseServiceImpl.bindable` 的 `hasCommonGroup`），**无组时全量可选**——即两库在 UI 里都是"可选"的，用户只勾了产品手册；④ 检索端**设计上按绑定库过滤**：`KnowledgeRetrievalTool` → `AgentVectorStoreServiceImpl.search` → `DynamicFilterService` 的 `AGENT_KNOWLEDGE` 分支 = `vectorType eq agentKnowledge AND agentKnowledgeId IN (selectRecalledKnowledgeIdsByBindings(agentId))`，该 SQL 仅取 **绑定库∩启用∩is_recall=1** 的条目 → 实测智能体33 的过滤集=条目 34/35，**均属 KB2**；⑤ 存储面：向量在 `tbl_vector_store_simple_data`（2442 片 agentKnowledge：KB2=1899 / KB1=543），切片元数据**只有 agentId（"0"/"20"）与 agentKnowledgeId，没有 knowledgeBaseId**；⑥ **反证**：KB2 的绑定条目里含「制度」字样的切片仅 **3 片**、含「汇编」**0 片** → 用户所见文本**不是**直接来自 KB1 切片（过滤若失效本应大量命中）。**待验证路径（未证实，逐条可查）**：ⓐ 前台提示面是否把**组授权库全列**给了模型（如 `buildScopeHint`/技能口径），导致模型"知道并谈论"制度汇编 → 需查前端 KB 提示构造；ⓑ 是否存在**另一条按组检索**的入口（组级 KB 授权的消费点目前只见于"可绑定/授权管理"，未见检索侧，但需穷举）；ⓒ 绑定库文档本身讨论"制度"（如「北科软业务调研报告」）→ 语义上被判为"读到了制度汇编"；ⓓ 切片 `agentId="0"`/`"20"` 与知识库维度的**历史回填**是否留下错位（`knowledge_base_id` 在条目表，切片侧仅靠 `agentKnowledgeId` 反查）。**下一步取证（复现即定性）**：以智能体33 提问一个**仅存在于制度汇编**的专有词（如库内独有术语）→ 抓取本次命中的 chunk 内容与其 `agentKnowledgeId`→反查所属 KB；若命中 KB1 即**真串库**（去查 ⓑ/ⓓ），若零命中而模型仍在谈制度即 **ⓐ/ⓒ**（提示面或语义问题）。**影响面**：多智能体/多库共用同一向量表且切片无库维度 → 任何按库隔离的能力都只靠"条目 id 集合"这一道；一旦提示面或二次路径漏筛即越权 **〔2026-10-05 用户裁定：优先级最高，先于 v1.7.0 其余任务处置〕**|
| BUG-87 | **会话文件删除后"复活"**：用户删除文件后界面刷新文件又出现（感受=删除失败） | 高 | 2026-10-05 用户实测（T-06 走查：「文件树没问题，但是删除文件又失败了」） | 已验证(v1.7.0) | v1.7.0（T-06 附带修复） | **根因（日志实锤）**：`20:30:52 会话文件逻辑删除: id=464694292936441856` **删除本身成功**；随即扫描器按 `store_key` 去重查询 `SELECT COUNT(*) FROM tbl_data_agent_file WHERE store_key=? AND del_flag=0` → **count=0（逻辑删的行不算"已登记"）** → 判定为新文件 → `会话文件登记: id=464694458447872000`（同 store_key）⇒ 文件复活。**放大因素**：T-06 面板删除后 `notifyFilesChanged()` → 刷新 → 树接口带 `scan=true` → 立即重扫，使复活**必然**发生（此前需下次开抽屉才偶发）。**影响面（实测）**：全库 **4 组 store_key**「既有活行又有删行」（hello.txt / probe_ft.txt / harness-讲义.md / diez-horizontal-1color.pdf），活行 create_time 均晚于被删行 ⇒ 均为复活产物。**修复**：`existsByStoreKeyAnySession` 改为**墓碑优先**（同 store_key **只要登记过（含已逻辑删）即视为已知，不再重新登记**）。代价（已如实记入 spec changelog）：若智能体日后生成**同名且同字节数**的文件，会因 store_key 相同而被视为已登记（不复活）；换名或变大小即正常登记。**验证**：夹具 materialize→删除→带 scan 重扫 → 不复活；新文件仍正常登记；4 组复活行按「删除意图优先」回填 del_flag=1 并可回滚 **【已验证 2026-10-05，实测】**真根因追加：`del_flag` 被框架按**逻辑删列**处理——QueryChain 查询会被**自动追加 `del_flag=0`**（日志实测 2 参数 SQL），故"墓碑优先"若用 QueryChain 仍查不到墓碑行；改**原生 SQL** `select count(*) from tbl_data_agent_file where store_key = ?` 后：用户被删的 `harness-讲义.md`（4 行全删）连续 3 次扫描**无复活**；全局"复活键"清零（仅剩空 store_key 的物化行分组，与扫描无关）；新文件登记正常（OCR 层 16 文件）；`verify` 交付面不回退。**lesson L-21 已记** ||
| BUG-88 | **部署后打开页签内容区空白**，强制刷新（F5/⌘R）后才有内容 | 中高 | 2026-10-06 用户实测（admin 界面） | 已修复(v1.7.0) | v1.7.0（前端容错） | **排查（先证伪三条）**：① 服务端头正常——`index.html` 为 `Cache-Control: no-cache, must-revalidate`、哈希 chunk 为 `max-age=2592000, immutable`；② 无 Service Worker；③ 线上 `index.html` 与本地构建 **sha256 一致**，其引用的全部入口 chunk **均 200**。**根因（高度吻合）**：SPA 懒加载路由 chunk 带**内容哈希**，部署替换整套产物后，**仍在运行的旧标签页**再打开新页签时会去请求**已被替换掉的旧 chunk** → 动态 import 失败 → 内容区空白；刷新会重新拉 `index.html`（no-cache）→ 拿到新 chunk → 正常。今日前端多次部署（T-06/T-10/BUG-88）使该窗口被频繁命中；而代码中**没有任何 chunk 加载失败容错**（全仓仅 SSE 相关 onError）。**修复**：新增 `router/chunk-recovery.ts` 并接线——捕获 vue-router `onError` + Vite `vite:preloadError` + `unhandledrejection` 中的「chunk 加载失败」类错误 → **整页自动重载一次**（`sessionStorage` 10s 防循环；启动成功即清标记，使下次部署仍可自动恢复一次）；重载后仍失败则不再循环、留控制台原始错误。**验证**：typecheck **213=基线（0 新增）**；构建产物已部署，含该逻辑的 `js/bootstrap-DSG4dysK.js` 线上哈希与本地一致（`7629c1a1cc3da6a8`）。**现场复现待用户**：再开页签应自动恢复；若仍空白请提供控制台首条报错（用于排除 token/路由守卫竞态等次因） **〔2026-10-06 定性修正〕**用户反馈"只有一条 storage 警告、无报错"，且实测**旧 chunk 在镜像内仍可访问（200）**→ chunk 404 未发生；现场主因改判为 **BUG-89（守卫自我重定向）**。本条的自动恢复逻辑作为**容错加固保留**（真·chunk 失效时仍有用）|
| BUG-89 | **打开页签内容区空白**（强制刷新才显示）——路由守卫**自我重定向**致导航被中止 | 中高 | 2026-10-06 用户实测（admin；**无控制台报错**，仅一条 vben storage 警告） | 已修复(v1.7.0) | v1.7.0（前端守卫） | **取证（日志实证）**：nginx 日志中这些请求的 **Referer 是 `/auth/login?redirect=%2Fauth%2Flogin`**，却在拉 run 页/会话/文件树接口（`/api/agent/33`、`/api/sessions/…/messages`、`/api/agent/files/tree`…）⇒ **URL 停在登录页、组件却已渲染目标页**的状态错位；30 分钟内 `/auth/login` 命中 388 次（反复态），且**全程无 401/403**（排除鉴权失败）。**根因**（`router/guard.ts` 两处自我重定向）：① 无 token 分支判据 `to.fullPath !== LOGIN_PATH`——落在 `/auth/login?redirect=…` 时 fullPath 必然不等，于是**把登录页自身再包一层 redirect**（逐次嵌套），且末尾 `return to` 同样是"导航到自己"；② token 存在分支直接 `decodeURIComponent(to.query.redirect)`，若该值就是登录页 → 又跳自己。vue-router 对"导航到同一位置"会**中止导航** → 内容区不渲染（因此**没有 JS 报错**，与现场一致）；刷新后时序变化才恢复。**修复**：无 token 分支改为 `to.path === LOGIN_PATH → return true`（放行渲染登录页，不再嵌套）；token 分支对 redirect 做**解码容错 + 自指检测**（空或指向登录页 → 回落 `homePath`/`/agent/list`）。**验证**：typecheck **213=基线（0 新增）**；已部署（`js/bootstrap-BhdfaQZY.js` 线上哈希与本地一致 `34b28b1c78745266`）。现场复验待用户（打开页签应直接出内容；若浏览器仍停在带 `redirect=/auth/login` 的 URL，现在会正常渲染登录页而不再空白） |
| BUG-90 | 打开页签**空白/停在登录页**（强刷才出内容）——**两套 token 来源不同步** | 高 | 2026-10-06 用户复测「还是空白」+ 服务端日志实证 | 已修复(v1.7.0) | v1.7.0（前端守卫） | **实证（决定性）**：nginx 日志显示**同一时刻** `/api/privilege/auth/menus` 与 `/api/agent/list` **均 200**（=请求层带着有效凭据），而浏览器 URL 停在 `/auth/login?redirect=%2Fauth%2Flogin`（=守卫判"未登录"）⇒ 二者对"是否已登录"的结论相反。**根因**：token 有两套来源且互不同步——请求层 `apps/admin-ui/src/api/request.ts` 读 `localStorage['phoenix-token']`；守卫读 `accessStore.accessToken`（vben 另一套持久键）。当后者缺失/失效而前者仍在时 → 接口全通但被守卫按未登录处理 → 停在登录页（登录页在已登录态下无内容 ⇒ 表现为"空白"）；刷新时序不同偶可恢复。**修复**：守卫入口加**单向回填**——`!accessStore.accessToken && localStorage.getItem('phoenix-token')` → `setAccessToken(...)`，使守卫与请求层同源（不反向覆盖，避免误清请求层凭据）；与 BUG-89（禁止登录页自我重定向）互补。**验证**：typecheck **213=基线**；已部署 `js/bootstrap-D33uGtdQ.js`（线上哈希一致，含 `phoenix-token` 回填逻辑）。现场复验待用户（**需先强刷一次**以载入新包） |
| BUG-91 | 点「运行」进入 tab 页**空白**——守卫落地目标取 `from.query.redirect`，来源页是登录页时把刚打开的运行页**再踢回登录页** | 高 | 2026-10-06 用户实测（复现路径：智能体列表→运行）+ 服务端轨迹实证 | 已修复(v1.7.0) | v1.7.0（前端守卫） | **决定性轨迹（nginx 日志）**：点运行后运行页**其实完整加载成功**——`/api/agent/36`、`/api/agent/36/sessions`、`/api/sessions/{sid}/messages`、`/api/agent/files/tree`、`/api/admin/harness/turn/status` **全 200**；但**这些请求的来源页 URL 全是 `/auth/login?redirect=%2Fauth%2Flogin`**，且 `/api/agent/36/sessions/stream` 被 **499**（客户端中断=组件被卸载）⇒ 页面渲染成功后被踢走 ⇒ 内容区空白（无 JS 报错，故此前一直"看不到错误"）。**根因**：`guard.ts` 生成动态路由后的落地目标为 `from.query.redirect ?? …`**——`from` 是**来源页**，当来源页正是登录页（带 `redirect=/auth/login`）时，落地目标=登录页 ⇒ 刚打开的 `/agent/36/run` 被立即重定向回登录页。**修复**：落地目标**不再接受指向登录页的 redirect**（仅接受非登录页来源的 redirect，否则回落 `to.fullPath`/`homePath`）。**同时**：A/B 对照（临时摘除运行页 T-10 改动）证明空白与本 spec 的运行页改动**无关** → 已把心跳提示功能**原样还原**；BUG-89（自我重定向）/BUG-90（token 双源不同步）/BUG-88（chunk 容错）保留为互补加固。**验证**：typecheck **213=基线**；已部署（health 200）。现场复验待用户（**需先强刷一次**） |
| BUG-92 | 打开页签**持续空白**——我今日新增的「chunk 失败自动整页重载」进入**10 秒周期死循环**，页面永远渲染不完 | 高（自引入） | 2026-10-06 服务端日志实证 | 已修复(v1.7.0) | v1.7.0（前端） | **决定性证据**：nginx 访问日志中 `GET /` 以**精确 ~10 秒**周期连续出现（03:26:12→03:26:22→03:26:33→…→03:28:23，持续 2 分钟以上），而 10 秒正是我在 BUG-88 引入的自动重载防循环窗口 `RELOAD_GUARD_MS=10_000`。**根因**：BUG-88 的 `chunk-recovery.ts` 监听 `vite:preloadError` / `unhandledrejection` / `router.onError`，命中"chunk 失败"类文案即整页重载；实际环境中存在持续触发的失败（预载失败/被误判的 rejection），于是每过 10 秒防循环窗口就再刷一次 ⇒ **应用始终在启动中被撕掉**，表现为**永久空白**（因此无 JS 报错、且我后续 BUG-89/90/91 三项守卫修复全都被这个循环掩盖，看起来"怎么修都不好"）。**修复**：**整体拆除**该机制（删除 `router/chunk-recovery.ts` + 移除 `router/index.ts` 接线），产物中已无 `phoenix:chunk-reload-at` 字符串。**验证**：typecheck **213=基线**；部署后 25 秒内 `GET /` **0 次**（循环停止）；health 200。**教训**：为"防白屏"引入的自动重载本身就是白屏风险源 → 应改为**只提示不自动重载**（或最多重载一次且用 `sessionStorage` 长期标记），并**上线前必须核验是否形成周期**（本条已入 lessons） |
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
- 〔**2026-10-05 销账（v1.7.0 T-03）**〕`RulesHarnessAgent.java` **已无未提交改动**（`git status` 复核为空）→ 原"有未提交实验改动"注记**作废**；`.mvn-home` 中的旧编译产物随 `.gitignore` 生效（T-01）一并归档不再影响工作树。
- 环境改动（验证所必需，已记录）：数据源 id=11「本地测试」的 `host` 由不可达的 `192.168.66.19` 改为 `127.0.0.1`、`connection_url` 同步、`password` 由 `123456` 改为容器实际密码 `phoenix`
- 环境问题：曾出现**僵尸 JVM 占用 8066**（`pkill -f phoenix-admin.jar` 无效，需 `lsof -tiTCP:8066 -sTCP:LISTEN | xargs kill`）；本机代理（TUN）开启时 JDK23 解析 `127.0.0.1` 报 `UnknownHostException`，关代理即恢复
- 库中实验数据：技能 `py-fib-demo`（含 scripts/fib.py）曾用于验证；前台账号 chenzhuo 密码现=12345678；两套账号表密码现均=12345678
- 〔**2026-10-05 销账（v1.7.0 T-01）**〕未跟踪噪声**已清零**：`.mvn-home/`、`.pnpm-store/` 进 `.gitignore`（check-ignore 命中）；`WSL`、`或在`（两枚 0 字节误建）、`AGENTS.md.bak.20260927101837`、`scripts/fib.py` 已删；`diagrams/phoenix-architecture.html` 入库。`git status` 现只剩有意保留项。
