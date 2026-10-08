# 项目坑台账（Lessons）
> L-xx 永久编号不复用 | 每条 ≤10 行 | active 条目在每次 Plan 被强制核对（SKILL §3）
> 边界：产品缺陷进 bugs.md，过程/工具/环境/判断跟头进这里，互相引用不混记
> 建账：2026-10-03（BL-22 断线续传 + fix-bug-batch 清仓两日实战复盘，陈卓指令）

## L-01 文本补丁静默脱靶（本项目头号惯犯）
- 分类: 工具链
- 触发场景: python replace / sed / 正则包裹改源码或文档
- 坑: 锚点差一字（`export async function` vs `export function`）、大小写（`AND a.del_flag` vs `where`）、正则插入点错位（括号插到错误分号）、甚至 `write(c)` 把变量写错成路径字符串**覆盖了 changelog**。历史复发 ≥5 次，多次造成"改了但没生效"的连环排查
- 根因: 替换不校验命中数，改后不回读
- 防再犯规则(回炉v2): 台账/文档编辑**禁止裸 `s.replace`**，一律走带 `assert s.count(old)==1` 的 edit 助手；锚点必须**先读实文再复制**（禁止凭记忆默写——M4 落账「发版」vs 实文「发布（演练+tag）」即默写脱靶）；批量编辑后**逐文件回读**被动过的关键行；commit message 声称的内容必须与 `git show --stat` 实际文件清单核对后才允许提交
- 状态: active
- 复发: ×2 ⚠（建账当日 M4 落账再犯×2：MILESTONE 裸 replace 表格挤压+M4 漏勾被用户抓出；lessons 自身锚点默写脱靶致 commit message 与内容不符——规则回炉如上）
- 关联: BUG-57 二发/四发、detached-stream changelog「漏提交找回」

## L-02 日志截断误导取证，差点冤枉用户
- 分类: 验证盲区
- 触发场景: grep 管道 + tail -N 取日志做故障定论
- 坑: `tail -10` 把关键 `[hitl] 计划已批准` 行截掉 → 断定服务端收到 allowed=false → 推断"用户点了取消/旧包"；用户澄清点的是确认后再查，日志行一直都在。另一轮把无关会话 8e2773b0 的日志误读进当前会话时间线
- 根因: 用截断视图下全称结论；会话归因未先核对 sessionId
- 防再犯规则: 取证 grep 先 `wc -l` 确认未截断（或放大窗口复查一遍）再下结论；每行日志必须核对 sessionId 归属；**"用户操作错误"类结论必须有服务端入参级证据**，没有就先加埋点再等复现；**探测命令先自证存在**（`command -v curl` 或改用必存在的原语如 bash /dev/tcp、cat /proc）——ubuntu 极简镜像无 curl/ip，「DEAD×3+路由表全空」全是 command-not-found 被 `|| echo DEAD` 吞掉（2026-10-04 VPN 修网后复测全通才暴雷，差点误导升级 Docker Desktop）
- 状态: active
- 复发: ×2 ⚠（同族二犯：截断视图/失效探针都是"观测通道不可靠却下全称结论"）
- 关联: BUG-59 三报（f2851c1 更正注记）、[confirm-in] 埋点 8d3d108

## L-03 部署三段证明缺失——"以为部署了"三连
- 分类: 流程
- 触发场景: 改码→build→compose up 后直接进入验证
- 坑: ①每个 bash 调用是新 shell，docker PATH 忘 export → build 悄悄失败（输出还被 >/dev/null 吞）②`&&` 链中一步失败，后续 `.env` sed 没执行 → 容器跑旧 tag（IMAGE_TAG 漂移 rc5/rc10/v1.3.0 共 3 次）③镜像重建了但 `.env` tag 被回退 → 新包根本没上车。每次都表现为"修复无效"假象，浪费用户复测轮次
- 根因: 把"命令跑过"当"部署生效"
- 防再犯规则: 每次部署后必做三段证明——①`docker inspect` 核对 `.Config.Image`+`StartedAt` ②容器内 grep 产物标记串（新文案/新 chunk 名）③curl 功能探针打新行为；关键步骤禁止 >/dev/null 吞输出（至少 tail -1）；docker PATH 每个 shell 显式 export
- 状态: active
- 复发: ×3（历史）→ 规则建立后 ×1 ⚠（2026-10-04 mcp-client-tools T-07：compose up --force-recreate 对已存在 tag 不重建镜像，两轮前端改动未进容器；"入包验证"查宿主 dist 冒充容器内证明，用户走查三轮才暴雷。回炉：**部署证明只认容器内 grep 标记串**；前端固定序列=build→cp→compose build→up→容器内证）
- 关联: d145c6b 部署轮（grep -c 断链）、8da028e 部署轮（.env 回退内鬼）

## L-04 Docker VM 盘满级联 + prune 误删在跑容器
- 分类: 环境
- 触发场景: 高频镜像迭代（rc 系列）后突发接口 500 / 清理磁盘
- 坑: Docker.raw 60G 打满 → Redis MISCONF 写不进 → 登录 500，症状像代码 bug 差点查错方向；`docker container prune -f` 把在跑的 backend 一起删了
- 根因: 资源层故障伪装成应用层故障；prune 无差别
- 防再犯规则: 突发 500/行为异变先跑体检三件套 `docker system df` + `redis-cli ping` + `docker ps` 再怀疑代码；有栈在跑时禁用裸 prune（必须 --filter 或点名）；rc 镜像每迭代 5 个清一次旧 tag
- 状态: active
- 复发: ×1
- 关联: v1.3.0 发布日事故；bugs.md「交付 VM 盘 60G 会吃穿」备注

## L-05 shell/平台方言雷区（macOS + busybox + psql）
- 分类: 工具链
- 触发场景: 在 macOS 宿主和容器内写单行命令
- 坑: `UID` 是 bash 只读变量赋值即错；macOS 无 `timeout`/`cat -A`；`sed -n "N-2,..."` 非法；psql `-c` 内联引号与 `string_agg` 单引号打架屡战屡败；nginx 注释是 `#` 不是 `//`；compose `ps --format` 老版本不支持
- 根因: 拿 Linux/GNU 直觉写 macOS/busybox 命令；引号嵌套超过两层
- 防再犯规则: 复杂 SQL/JSON 一律写文件后 `docker cp` + `-f` 执行，禁止三层以上引号内联；变量名避开 shell 保留字（先 `declare -p 名` 探测）；陌生平台命令先最小样本试跑再进正式链
- 状态: active
- 复发: ×2（历史）
- 关联: T-05 孤儿清理脚本调试轮、psql jsonb 转型错误

## L-06 多入口枚举缺失——修一条链漏一条链
- 分类: 流程
- 触发场景: 修交互/渲染/状态类缺陷
- 坑: selectAgent 有 chat.vue 和 AgentListPanel 两条链，只修前者，用户复测立刻打脸（BUG-58 真身在第二条）；服务端行渲染有首载/轮询/重选三个装载点，漏了轮询口导致"刷新一下又变丑"（BUG-57 二发）
- 根因: 从症状点直接下钻，没有先横向枚举同行为的全部调用方
- 防再犯规则: 动手前 `grep` 该行为/组件/接口的**全部调用点**，在回复里列成清单逐一标注改或不改及理由；清单不列全不得宣布修复；**删除/清理共享资源（镜像tag/文件/列）前同样要枚举消费方**——含配置默认值引用（2026-10-04 三犯：清镜像没查 compose 默认 tag 引用，删掉 pgvector/redis-alpine/postgres-alpine 三裸名，recreate 时拉 Hub 当场卡死；靠 retag+重拉复原）
- 状态: active
- 复发: ×5 ⚠（规则再回炉：清理类操作纳入"消费方枚举"义务；2026-10-04 四犯[mcp-client-tools T-07]：绑定块只挂独立编辑页漏了用户主入口抽屉 agent-create-drawer——回炉条款：**UI 挂点必须 grep 同功能既有组件的全部 import 处**逐一挂载；2026-10-04 五犯险情[agent-publish-group-grant]：`git stash -u` 未枚举吞入面把 .pnpm-store 数十万文件卷入卡死被击杀，幸无损恢复——回炉条款：**stash -u/clean 类操作前先 `git status --short | wc -l` 评估吞入面，海量未跟踪目录在库时禁用 -u；drop 快照前必须 --include-untracked 核查删除面**（五犯实际代价：.mvn-home 被清掉大半，编译卡远程下载才暴露，"无损"结论下早了）)
- 关联: BUG-47/57/58；plan.md 身份矩阵机制（同源制度化）

## L-07 假阳性验证——记了"通过"其实没通过
- 分类: 验证盲区
- 触发场景: 状态清理/置空类断言、类型基线核对
- 坑: planMode 清理记"✓"，实际 MyBatis-Flex update 忽略 null 字段根本没清（后来 confess 回炉）；vue-tsc 基线 215±N 波动未归因就想放行
- 根因: 只看了命令没报错，没看状态真的变了
- 防再犯规则: 状态类断言必须双侧取证（DB 直查 + API 回读各一次）；"通过"字样落盘必须附真实输出片段；基线类指标波动必须归因到具体文件行才允许放行；**断言必须显式绑定目标**（项目名/容器名/连接串参数化）——禁止裸 compose exec 这类靠默认解析的全局查询（2026-10-04 二犯：verify 裸 exec 被 compose name: 解析到开发栈，drill-1 四断言假绿一天后才暴雷）
- 状态: active
- 复发: ×2 ⚠（回炉：新增"断言显式绑定目标"条款）
- 关联: detached-stream changelog「planMode cleanup reset confession」

## L-08 时序演练靠运气窗口
- 分类: 时序
- 触发场景: 断线/取消/超时类演练
- 坑: T-06 断链演练前两次 sleep 固定时长后 kill，都撞上任务自然完成窗口，测了个寂寞；第三次改成"轮询等确认帧出现/帧流停滞再动手"才命中
- 根因: 用固定延时对赌不确定的执行时长
- 防再犯规则: 时序类演练必须设确定性触发点（等特定帧/等 DB 状态/等日志标记出现再动作）；演练结果与预期不符时先核对时间窗是否命中，再怀疑代码
- 状态: active
- 复发: ×1
- 关联: fix-bug-batch T-06（bf6e6e1）

## L-09 curl 超时参数太小制造"链路不通"假象
- 分类: 工具链
- 触发场景: 对 SSE/异步/信号式端点做 curl 演练
- 坑: confirm POST 用 `-m 3`，客户端提前掐断，服务端控制器未被调度（或响应未完成），误判"确认信号没到/链路问题"排查半天；后改 `-m 15` 一次就通
- 根因: 把客户端超时当服务端行为
- 防再犯规则: 打流式/异步端点 curl 一律 `-m ≥10`（SSE 演练 ≥120）；判定"请求是否到达"只认服务端日志或 nginx access log，不认 curl 退出码
- 状态: active
- 复发: ×1
- 关联: BUG-59 演练轮（b59-fix-1/2）

## L-10 智能体持久记忆污染自我强化
- 分类: 数据
- 触发场景: 排查"模型怪行为复发"/用户误会解除后
- 坑: 一次"用户看不到文件"的误会中，模型把「交付到 workspace root 便于查看」写进 MEMORY.md 当惯例，此后每个会话复读该行为（根目录垃圾反复出现，两次被当新 bug 排查）
- 根因: 会话内误会固化进了跨会话记忆，无人消毒
- 防再犯规则: 模型行为类误会解决后，必须检查并消毒智能体持久记忆（MEMORY.md/memory/*.md），把正确惯例显式写回；排查"怪行为复发"第一步先读该 agent 记忆文件
- 状态: active
- 复发: ×1
- 关联: BUG-59 二发连带（c38a098 记忆消毒）

## L-11 用户环境 ≠ 我以为的环境
- 分类: 流程
- 触发场景: 用户报 UI/渲染问题、要求复测
- 坑: "admin 的前端渲染不好"——我在 front 前台修样式，用户实际看的是 admin run 页（反方向也发生过一次）；浏览器旧 chunk 让点击行为与源码对不上（确认卡"弹窗不下去"排查轮）
- 根因: 没先锚定用户所在的准确页面与资源版本
- 防再犯规则: UI 问题开工前先问/核对准确 URL 与入口；复测指令必须带「硬刷新」；行为与源码矛盾时先比对线上 chunk（容器内 ls/grep 标记串）再怀疑逻辑
- 状态: active
- 复发: ×2（历史）
- 关联: BUG-57 系列、d145c6b 确认卡轮

## L-12 交付目标机架构/引擎/网络三查缺失
- 分类: 环境
- 触发场景: 指导他人机器部署、跨架构出包
- 坑: Windows Server 那台跑的是 **Windows 容器引擎**（`OSType: windows`），Linux 镜像一个都跑不了，换源也救不了；`.env.example` 陈旧 tag v1.2.0 直接误导；Docker Hub 间歇性断连（上午通中午断），本机 multistage 验证构建也当场翻车；arm64 Mac 出的镜像默认给不了 amd64 机器
- 根因: 默认对方环境和自己一样
- 防再犯规则: 远程部署排障第一步收 `docker info`（OSType/架构）+ `docker context ls` + 三个源连通性 curl；交付模板文件（.env.example 等）里的版本 tag 随每次发版同步改；Hub 依赖构建准备 `DOCKER_BUILDKIT=0` 本地缓存兜底 + 镜像源前缀双路
- 状态: active
- 复发: ×1
- 关联: Windows Server 2022 部署支援轮、f31c3f2/d0b4552

## L-13 git add 面过宽吞大产物（差点废掉整条分支推送）
- 分类: 工具链
- 触发场景: `git add <目录>` 收工提交，目录下混有构建产物
- 坑: T-06 提交用 `git add docker/`，把 docker/dist 三个测试包（**3.9GB**，单文件超 GitHub 100MB 硬限）整体吞进历史；push 卡死，用户点破才发现。幸而未推出去，filter-branch 重写 30 提交排爆
- 根因: add 面大于意图面 + 产物目录未 ignore + 提交前不看 staged 清单
- 防再犯规则: ①构建产物目录（dist/.stage/*.tar.gz/engine debs）**先入 .gitignore 再开写**（本条事后已补 docker/.gitignore）②提交前 `git diff --cached --stat | tail -3` 扫一眼，出现 >1MB 非预期文件立即 `git reset` 重挑③禁止对含产物子树的目录整体 `git add`，逐文件或已 ignore 兜底
- 状态: active
- 复发: ×1
- 关联: docker-auto-pipeline fbb20f7(重写前)；用户抓出

## L-20 视图态凡与"会话"绑定就必须按会话分片（单例 ref 是跨会话锁死的主因）
- 现象: BUG-75 我把"服务端轮次在跑"拆成页面级 `remoteRunning` ref，切换会话时探测函数提前 return 未复位 → A 在跑就把 B/新会话的输入框锁死、按钮只剩"终止"（BUG-81 用户实测）；同族还有 `showHarnessConfirm` 一族（A 待确认 → B 也锁死 + 切回时旧确认条复活）
- 防再犯规则: 任何控制**输入可用性/按钮形态**的状态都必须进 `SessionRuntimeState` 一类按会话存取的容器（save/sync 成对），**禁止**用页面级 `ref` 表达"某会话正在运行中"；新增这类 ref 时先问"切会话后它该是什么值"
- 复发: ×2（BUG-74 轮询闭包、BUG-81 状态分片；同族=BUG-67/78 路径错位）

## L-19 路径归属必须与写入方同源解析（请求入参 ≠ 库中事实）
- 现象: BUG-78 我判定为"扫描器漏扫 root 级"，加了候选根兜底（碰巧修好）；本轮查代码发现真因是 **scanner 用请求入参 sn** 算 runtimeKey（admin/前台请求不带 sn → `agent-33`），**factory 用库中 `agent.sn`** 算（`owl-kids`）→ 扫描根与写入根天生错位
- 防再犯规则: 任何"该去哪个目录找它"的解析必须与**写入方同一数据源**（同实体/同函数）；禁止用调用方透传的可选入参决定落点；候选根兜底是掩盖不是修复——修完要能解释"为什么原来错、现在为什么一定对"
- 复发: ×1（BUG-78；同族=BUG-67/74 跨会话/跨用户错位）

## L-18 改框架行为前先读规格（本项目框架无源码，字节码就是规格）
- 现象: BUG-79 规划里我已准备"包装 shell 工具注入 `cd {sessionDir} && `"（还要自己覆盖 shell-local/pwsh/后台 job 三入口）；查 `javap` 发现 `LocalFilesystemSpec.project` 就是 shell cwd 开关（缺省回落 `user.dir`），`IsolationScope` 本身就有 SESSION/USER/AGENT 三档 namespace 工厂——一行配置解决，包装方案脆弱且漏入口
- 防再犯规则: 需要框架层行为变更时，先 `unzip -l` + `javap -p -c` 读规格类（构造参数/字段/分支），确认**没有**现成开关再考虑包装；框架能力清单沉淀到 spec plan 的"框架规格取证"小节
- 复发: ×0

## L-17 优化前先证组件可达（import 链核验）
- 现象: 2026-10-05 T-03 按"admin 运行页"修渲染管线，改完 `components/run/index.vue` 才发现**全仓零引用=死组件**（真身在 `views/front/chat.vue`+`views/front/api-transport.ts`+`ChatMessages.vue`，核心逻辑还分居共享包 chat-shared）；误改已还原
- 防再犯规则: 动任何 UI 组件前先跑一次可达性核验（`grep -rn "<相对路径片段>" src` 零命中即死代码）；**入口枚举（L-06）须以 import/路由链实证，不以目录名猜**
- 复发: ×0

## L-16 用户实测期间禁止静默部署
- 现象: 2026-10-05 用户正走查测试（12:22 起 logo 长轮在跑），我 12:42 部署 BUG-69 布防版重建 backend，启动清扫把用户在跑的轮次标"服务重启中断"——用户以为产品又炸了
- 防再犯规则: **后端/前端部署前先确认用户不在实测中**（问一句或看 turn/status 有无活跃轮）；用户报告"正在测/正在卡着"期间，部署要么等、要么先声明"我要重启了会打断在跑轮次"；活跃轮次存在时原则不重建 backend
- 复发: ×0

## L-15 外部协议命名净化必须最保守字符集
- 现象: MCP 工具名带连字符（query-nearby-stores）时，DeepSeek 回传 tool_call name=null → 框架 PRE_ACTING 调度 NPE 整轮崩（BUG-65）；而全下划线名（stub_echo）同模型同框架完全正常
- 根因: 模型供应商/框架流式 tool_call 组装对 `-` 的兼容缺口；schema 里定义带连字符合法，但回传丢名
- 防再犯规则: **凡把外部资源名（MCP/API 插件/数据源表列名）转成模型可见的工具名/函数名，一律净化到 [a-zA-Z0-9_]、折叠连续下划线、首字符字母化**；原始名仅存服务端（委托调用时用），绝不透给模型；新协议接入先跑"名字字符集 A/B"再放量
- 关联: BUG-65/66（mcp-client-tools）；BL-25 API 插件立项时本条为设计输入
- 复发: ×0

## L-14 md 表格错乱（台账类文档的系统性暗伤）
- 分类: 工具链
- 触发场景: python 追加/改写台账行、表格前后排版、单元格内容含特殊字符
- 坑: 多文件表格错乱被用户抓出——三类病灶：①历史状态翻新时旧单元格未合并（bugs.md 9 行 9格vs表头7格）②单元格内裸竖线/三重反引号未转义（healthy|completed、```json 破解析）③表前无空行整表不渲染（7处）；backlog BL-05 还缺行尾
- 根因: 台账追加脚本只关心内容不校验表结构；渲染错乱肉眼难察（终端里照样"像表格"）
- 防再犯规则: **每次写/改 md 产物后必跑 `python3 <skill>/scripts/mdtable_check.py <文件>`，exit 0 才算写入完成**（已升格为 skill 铁律8 表格闸）；追加台账行的脚本内嵌列数 assert；单元格内容含 | 或 ``` 时先转义/改写
- 状态: active
- 复发: ×1（用户抓出后全库扫描 7 文件 24 处一次清零）
- 关联: bugs.md/backlog.md/4个spec文档 2026-10-04 修复轮

<!-- 去重：记前先 grep "L-" 找同类，同类加复发计数不新开号；
     长度控制：active 超 ~80 条触发季度审，休眠/退休挪到文件尾"冷库"区 -->

## L-21 MyBatis-Flex 逻辑删列会让"墓碑行"在任何 QueryChain 查询里隐形
- **现象**：按 `store_key` 去重时，逻辑删（`del_flag=1`）的行查不到 → 扫描器把用户已删的文件当新文件重新登记 → **删除后文件复活**（BUG-87）。
- **根因**：`del_flag` 被 MyBatis-Flex 当作逻辑删列，**QueryChain 生成 SQL 时自动追加 `del_flag = 0`**——即使代码里已经删掉显式 `.eq(AgentFile::getDelFlag, 0)`，SQL 里仍会出现（实测日志：`WHERE ("store_key" = ?) AND "del_flag" = ?`，参数只有 storeKey 与 0）。
- **对策**：凡需**看到墓碑/已删行**的判定，必须走**原生 SQL**（`Db.selectObject("select count(*) ... where store_key = ?")`）绕开自动注入；不要以为"删掉显式条件"就等价于"不过滤"。
- **验证纪律**：我第一次的"修复已生效"验证是**假阳性**——用物化文件（`store_key` 为空、盘上落在 uploads tee 而非会话工作区）做删除→扫描试验，扫描器根本不会去登记它。**验证必须打在真实受控路径上**（本例=会话工作区里被扫描登记过的文件）。

## L-22 交付物构建严禁并发（同一 stage 文件 + 同一镜像 tag）
- **现象**：为验证 T-09 我起了旁路容器（需构建镜像），同时**守候式部署任务**也在构建同一镜像；两个 `cp jar → docker/.stage/` 与两次 `compose build backend` 交叠，镜像里被打进**半截 jar** → 生产 backend 启动即 `Invalid or corrupt jarfile`，restart 循环、health 502（约 1 分钟）。
- **防再犯规则**：① 同一 `.stage/<artifact>` 与同一镜像 tag **永远串行构建**（先确认无在跑的构建任务，再动手）；② 需要"另一套配置"的验证优先用 `compose run --rm -d -p ... -e ...`（**复用已有镜像**，不重建）；③ 部署脚本里的 `cp` 之后加一条 jar 完整性校验（`unzip -t` / sha 比对）再进 `build`；④ 事后必须核对 `health=200` 且容器 `running`（不看 502 就报"部署完成"=假绿）。

## L-23 自动恢复类逻辑必须先证明"不会形成周期"
- **现象**：为防"部署后旧标签页白屏"我加了 chunk 加载失败自动整页重载（10s 防循环窗口），结果真实环境中该失败被**持续触发**，页面每 10 秒重载一次 → **永久白屏**，且**掩盖了同片区其它三项守卫修复**（看起来"怎么修都不好"）。
- **防再犯规则**：① 任何"自动重载/自动重试"逻辑上线前，必须**从服务端日志核验时间序列是否呈固定周期**（本例 `GET /` 每 ~10s 一次即为实锤）；② 默认策略改为**只提示不自动重载**；③ 若必须重载，用**长期标记**（非 10s 窗口）限制为"每次会话最多一次"；④ 排查"白屏"类问题时，先看**请求时间序列**（周期=自愈逻辑作祟；单次=渲染异常）。

## L-24 路由页面模板必须单一根节点（Transition out-in 会被 fragment 卡死）
- **现象**：智能体列表页模板顶层是 `<Page>` + `<ElDialog>` 两个**无条件兄弟节点** ⇒ 组件运行时渲染为 **Fragment（多根）**；布局用 `<Transition mode="out-in">` 包裹页面组件时，**离场(leave) 事件永不完成** ⇒ 新页面**永不进场** ⇒ 内容区只剩注释节点（**永久空白、控制台无任何报错**），且**一旦触发，之后打开每个 tab 都空白，只有 F5 整页重载才恢复**（重载没有 leave 阶段）。这就是"从列表点运行进 tab 就空白、强制刷新才有内容"。
- **定位手法（本次验证有效，可复用）**：① **A/B**：整页重载正常 / 应用内跳转空白 ⇒ 锁定客户端渲染路径；② 在布局内容区打**可见诊断条**（`renderRouteView`/`domCached`/`Component`/`matched`/`keepAlive`/`hasSetup`），一次分清"开关为假"还是"组件为空"；③ **用偏好开关做二分**：关掉「页面切换动画」立刻正常 ⇒ 元凶是 Transition；④ 用 `@vue/compiler-sfc` 编译模板，看根表达式是 `createElementBlock(Fragment…)` 还是三元/单元素；⑤ 用 `git log -S"<新增节点>"` 精确定位引入提交（本例 `0d666b1`）。
- **实例边界**：`v-if`/`v-else` 双分支根**不是** Fragment（编译成三元表达式，运行时单根）——前台 `views/front/chat.vue` 即此类，**不要误改**（加包裹 div 会破坏其布局）。
- **防再犯规则**：① 任何**路由页面**（菜单 `component` 指向的 .vue）模板顶层**只能有一个元素**；弹窗/抽屉要么放进主容器内部，要么 `<Teleport>`；② 新增顶层节点后必须跑"顶层根节点计数"自检；③ 组件内部子组件多根不受此限，仅**被 Transition 直接包裹的页面组件**受限。

## L-25 脚本化写账"断言失败但后续照跑"⇒ 提交信息与事实不符
- **现象**：用 Python 断言写入 BL-30/BUG-101 时，`assert lines[-1].startswith('| BL-29')` 因**文件尾空行**失败 → 脚本中止、两条账**根本没写进去**；但同一命令串里随后的 `mdtable_check` 与 `git commit` 仍照跑，**提交信息却已声明"已挂 BL-30 / 已登记 BUG-101"**（差点留下假账）。
- **防再犯规则**：① 脚本化写账必须**写入后立刻 grep 复核**（`grep -c "BL-30" file` 期望 1），复核通过才算完成——不能只看脚本"跑完了"；② 多步命令用 `&&` **串联**，让前一步失败阻断后续提交（本次失败的原因正是用换行分隔）；③ 对"文件尾/空行"类断言改为 `rstrip('\n')` 后再判断，别假设 `split('\n')[-1]` 是最后一行数据；④ 提交信息中的"已做 X"字样，必须能在**同一提交的 diff 里**被指认（本次靠 `--amend` 补齐并保留了同一 message）。

## L-26 本项目"业务失败"也返回 HTTP 200 —— 验证必须断言业务码
- **现象**：验证 T-04 改密时我只看 `curl -o /dev/null -w '%{http_code}'`，得到 **HTTP 200** 就判"新口令可登录"，实际响应体是 `{"code":"101","msg":"用户名或密码错误"}` ⇒ **假阳性**，差点把未修复的功能记成"已验证"（本项目信封约定：HTTP 层恒 200，成败在 `code`：100/200 成功，其它失败）。
- **防再犯规则**：① 本项目所有接口验证**一律解析响应体的 `code`/`success`**，HTTP 码只用于区分网关层错误；② 断言脚本写成"取 `code` 判等"，禁止用 `-w '%{http_code}'` 单独下结论；③ 负对照与正例都要断言业务码（正例 code=100 且有 token，负例 code≠100）；④ 同类假阳性已在 BUG-88/96/98 的复核中出现过——**"HTTP 200" 永远不等于"业务成功"**。

## L-27 改"身份源"后必须对**迁移后的状态**回归，不能只用新建的一次性数据验证
- **现象**：统一账号迁移把前台登录 id 换成 `tbl_privilege_user.id` 后，前台"改密"仍用 `getById(登录id)` 查
  **旧前台表** ⇒ 查不到 ⇒ 恒 false ⇒ 用户实测报「原密码错误」（我此前用**未迁移**的一次性账号 `pwcheck2026`
  验证 T-04，流程全绿 ⇒ 假绿）。
- **防再犯规则**：① 凡改动"身份/主键来源"，验证矩阵必须含**迁移后状态**的用例（此处=用已迁移账号跑改密/查询/登出）；
  ② 一次性新建账号只能证明"新数据路径"，不能代表"存量迁移数据路径"；③ 迁移类改造的回归清单要按
  **"谁引用了这个 id"**（L-19 同源原则）逐个入口过一遍，不能只跑主流程；④ 用户实测报错优先按"id 语义变了"排查。
## L-28 生产部署的代码改动必须与文档同批入库（否则"证据指向的代码"在仓库里不存在）
- **现象**：T-04 回归修复（`AccountInfoServiceImpl.updatePassword` 走统一账号改密）已**构建部署并跑出实证**，
  但 `ab7a853` 只提交了 completion/evidence/lessons 三件文档，**源码遗留在工作区未入库**（次日才发现）。
  后果：证据文件声称的修复在仓库中无法复现（`git show` 里搜不到那段代码），审计链断裂；若期间有人重装依赖或清理工作区，修复即丢失。
- **防再犯规则**：① 凡"改了代码 → 构建/部署 → 出证据"的闭环，**同一次提交必须含源码**；文档与源码可分多个 commit，但**不得只提交文档**；
  ② 收尾前跑 `git status --short`，工作区有未提交源码 = 收尾未完成，禁止报"已完成"；
  ③ 证据文件里的关键行号/函数名，落库前用 `grep` 在 **HEAD**（不是工作区）上复核一次。

## L-29 判断"某列指向哪张表"必须实测 join，禁止凭列名与样例值推断
- **现象**：查 T-18 菜单可见性时，我看 `tbl_privilege_acl` 的 `module_sn` 列值（PlatformAccount/DepartmentManagement…）
  就断言"这是权限点(pvalue)表、module_id 指向 pvalue id"，并把这句**推断**当作"根因"写进证据文件
  与任务注解（`T-18_menu_blocked` 原文）。同日一次 `left join tbl_privilege_module m on m.id=a.module_id`
  即推翻：module_id 就是菜单 id、module_sn 是**过期冗余标签**（4 行菜单的 module_sn 全写 'agentManagerIndex'）。
  真正的失败根因（照过期列复制 4 行 ⇒ 同 release_id 撞唯一键）被这条误判掩盖，白绕一轮。
- **防再犯规则**：① 判定外键归属一律**跑一次 join**（看匹配率 + 关联出的名称是否讲得通），样例值只能生成假设，不能定论；
  ② `*_sn`/`*_name` 这类**冗余反范式列**默认视为**可能过期**，定位行一律用主键或业务唯一键（本例应用 `url`/`id`）；
  ③ 证据文件里的"根因"必须标明取证方式（实测 join / SQL 计数 / 仅推断），**推断不得写成结论**；
  ④ 已被推翻的旧结论**保留原文 + 追加更正段**（不删改历史），避免下次从残档里再学一遍错的。

## L-30 管道（`| tail`）会吞掉退出码 —— "跑完了"不等于"跑成功了"
- **现象**：T-19 构建前端时我用 `pnpm … build 2>&1 | tail -25` 执行，工具回报 **exit code 0**，
  但输出正文第一行是 `sh: pnpm: command not found` / `ERR_PNPM_RECURSIVE_RUN_FIRST_FAIL … spawn ENOENT`
  —— **构建其实失败了**。管道退出码取的是 `tail` 的 0，真实失败被吞掉；若我没读正文就会误报"构建通过"。
- **防再犯规则**：① 任何"要看输出尾部"的命令一律 `set -o pipefail`，或用 `${PIPESTATUS[0]}` 显式取首段退出码；
  ② 后台 job 的 `[status: completed, exit code: 0]` **只说明命令跑完**，必须同时读输出正文再下结论；
  ③ 报"构建/测试通过"前，必须能在输出里指认成功标志行（本例是 `✓ built in …`）。

## L-31 数据驱动的"抄一行"必须 dump 整行并逐列核对（漏列 = 交付缺件）
- **现象**：T-18 插入「智能体中心」菜单行时，我照「知识库 / 智能体列表」行的形状复制字段，
  却**没把 `image` 列纳入核对范围**（主观以为"菜单表没有图标"）⇒ 落库 `image=NULL` ⇒
  用户第一眼就看出来"没有图标"。实际现网 27 行里 **24 行都有** iconify 图标名。
- **防再犯规则**：① 复制既有枚举行（菜单/配置/模板）时，先 `select *`/`\d` 把**列清单**完整列出，
  逐列勾"继承/覆盖/置空"三态，禁止只挑"看起来相关"的列；② 交付前对目标行做一次
  **与母版行的字段 diff**（`select` 两行并排比对），差异非空即逐条说明理由；
  ③ 面向用户可见的属性（图标/排序/显隐）属"验收面"，必须写进任务验证方式，不能只当实现细节。

## L-32 用 edit 删函数时别连带相邻函数签名；typecheck 必须看"增量错误数"而非只看退出码
- **现象**：T-23 删除 `ChatHistoryPanel.vue` 的 `handleSystemSettings()` 时，我的 old_string 一路覆盖到
  下一个函数的签名 `async function handleLogout() {`，而 new_string 只写了 `function handleLogout() {`
  ⇒ **丢了 `async`** ⇒ 里面两处 `await` 报 TS1308，构建直接 `Exit status 1`。
  能一眼抓到，是因为我把 typecheck 错误总数与基线对比：**212 → 214**（增量即定位到该文件）。
- **防再犯规则**：① 删代码块时 old_string 的**边界不要含下一个声明**；若必须含，new_string 必须**逐字保留**
  被包含声明的原文（修饰符/参数一个不缺）；② 本项目 typecheck 是"既有债务型"（非零退出码是常态），
  必须记录**基线错误数**并断言"无新增 + 改动文件 0 命中"，只断言 exit code=0 既永远失败也会掩盖真错；
  ③ 每次改动后跑一次 typecheck 并把总数与基线对比，异常增量立刻定位文件。

## L-33 脚本化写中文台账：引号冲突 + 「凭记忆写 old_string」
- **现象**：本次连续两次写 `bugs.md` 失败——① Python 字符串用 `"` 包裹，正文里却有中文直引号 `"…"` ⇒ **解析期 SyntaxError**；
  ② `old_string` 凭记忆写成 `…未纳入本版本〕|`，实际文件里是 `…未纳入本版本〕**|`（更早一次改动已经改过该行）⇒ 断言命中 0。
  两次都被 `assert count == 1` 在**写入之前**拦下，文件零损坏，但白花两轮。
- **防再犯规则**：① 脚本承载中文正文一律用三引号 `'''…'''`，且正文内引号统一写「」；
  ② `old_string` **必须取自当前文件**（先 `grep` 或用 `python -c "print(repr(line))"` 打印真实内容），禁止凭记忆拼；
  ③ 任何脚本化写账：先 `assert count == 1` → 再写 → 写完立刻 `grep` 复核（与 L-25 同源）。

## L-34 包装异常必须"既记日志又把 cause 摘要回传"，否则排障只剩无信息量文案
- **现象**：知识库检索工具每次都返回给模型 `Error during parallel search execution`——出自
  `AbstractHybridRetrievalStrategy:92` 的 `catch (ExecutionException e) { throw new RuntimeException("Error during parallel search execution", e); }`：
  **cause 被包装后既没有 `log.error(…, e)` 落日志，也没有把 cause 摘要回传给工具调用方**，
  于是模型与人都只能看到"并行检索失败"这句无信息量的话；真实原因（阿里云 embedding **欠费 Arrearage**）
  要靠我另写 curl 探针才挖出来。
- **防再犯规则**：① 任何 `catch (X e) { throw new RuntimeException("概述", e) }` 必须同时 `log.error` 打全栈
  （至少 ERROR 级 + 关键入参摘要）；② **面向模型/外部调用方的错误文本要带可诊断信息**
  （`"检索失败: " + rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage()`），
  否则 Agent 只能"盲目重试"或退化到歪路（本次就退化成了直读文件系统）；
  ③ 多路并行（`CompletableFuture.get()`）的异常必须**分别定位是哪一路**（向量 vs 关键词），别合成一句。

## L-35 Agent 行为类问题的第一手证据在"会话上下文落库"，而不是靠猜
- **现象**：本次"为什么智能体不去知识库检索"的完整因果链（`domain_knowledge_context` 为空、按框架提示去
  `knowledge/` 扑空、`getRagInfo` 连续报错、转去 shell 直读共享目录、并把该路径写进长期记忆），
  全部是**模型自述 + 工具调用/返回原文**里逐条读出来的；而最初我以为"是提示词问题/开关问题"，
  单看代码与日志都不够。
- **防再犯规则**：① 排查 Agent 行为偏差，先取**它自己的轨迹**：DB 侧
  （`tbl_data_chat_message` / AgentStateStore 的 `context` 里含 thinking + tool_use + tool_result 原文），
  或 `docker logs` 里的 tool 调用/返回；**模型自述当"线索"、当"证据的索引"，不当结论**；
  ② 每条线索都要用**独立探针**复验（本次：embedding 接口 curl、`tbl_data_model_config` 比对、
  `find` 容器路径、框架 jar 里 `strings` 抽提示词原文）；③ 结论必须能对齐"现象 ← 触发条件 ← 代码/配置位置"三要素。

## L-36 外部服务的"能力清单"接口（如 `GET /models`）不是权威，不能据此下"不支持"的全称结论
- **现象**：排查"向量模型测试 404"时，我直连该端点 `GET /models` 拿到 16 个模型、其中没有任何 embedding，
  就直接在台账里写成「**该端点不提供向量模型**」——用户当场纠正：「token Plan 支持所有模型，不可能没有向量模型」。
  实际上很多产品的 `/models` 只列 chat 模型、或按开通/权限裁剪，**它证明不了"不存在"**。
  （真实现象是：用当前 base_url + key + 我试的 12 个名字跑不通——这是"我试过的组合不通"，不是"产品没有"。）
- **防再犯规则**：① 涉及外部服务能力，只能下"在这些条件下我实测不通"的限定结论，
  **禁止**升级为"该服务不支持 X"的超范围结论；② 要下"不支持"必须有**权威来源**（官方文档/控制台/服务商确认）或
  **穷举+规格说明**双证据；③ 写进台账时把"事实"与"推断"分行标注（本例已按此改写 BUG-107，并注明结论已撤回）；
  ④ 用户对产品能力的断言优先于我的清单推断 —— 立刻改写台账并说明修正过程，而不是维护原结论。

## L-37 用"状态码 + 响应长度"定性之前，必须核对**响应内容身份**
- **现象**：探测 `/uploads/../etc/passwd` 得到 `HTTP 200 / 3200 字节`，我据此对用户断言"**路径穿越成功、未鉴权任意文件读取（P0）**"；
  随后核对响应体首字节发现是 SPA 的 `index.html`（`<!doctype html>…`，恰好 3200 字节），而容器内真实 `/etc/passwd` 只有 **595 字节**
  ⇒ nginx 把 `..` **归一化**后落到 `location /` 的 `try_files … /index.html` 兜底，**根本不是穿越**。结论已当场撤回。
- **防再犯规则**：① 任何"读到了不该读的东西"的判断，必须给出**内容级**证据（首行/文件头/已知标记），不能只看状态码与长度；
  ② 下安全结论前先自问"这个 200/长度还有没有别的解释"（本例 SPA 兜底就是最可能的解释），并**主动证伪**（换编码/换路径/比对真实文件大小）；
  ③ 安全类结论按**最坏情况**取证：先证明"能拿到什么内容"，再定级；④ 结论一旦被推翻，立刻撤回并把过程与教训留痕（不得悄悄改口）。

## L-67 Windows PowerShell 下 `$ErrorActionPreference="Stop"` + 原生命令 stderr ⇒ 直接抛异常（`2>$null` 拦不住）
- **分类**：工具链（Windows 脚本）
- **触发场景**：读/写 `.ps1`；脚本里 `$ErrorActionPreference = "Stop"` 之后调外部命令做探测（`docker info`、`wsl -l` 之类）
- **现象**：本机 Windows PowerShell 5.1.26100.9444 实测——`& cmd /c "echo boom 1>&2" 2>$null` **抛 NativeCommandError**（`2>$null` 只丢输出，ErrorRecord 先生成）；而 `& cmd /c "exit 3"` **不抛**（单纯退出码非零不致错）。调查"怎么用 bootstrap.ps1"时照此复刻脚本原句 `& docker info --format '{{.OSType}}' 2>$null` → 同样抛（本机 docker 在 PATH、引擎未起）。
- **根因**：PS 5.1 把原生命令的 stderr 行包成 ErrorRecord，EAP=Stop 使其升级为终止错误——与 `$LASTEXITCODE` 无关；`2>$null` 只是丢弃流输出，不阻止 ErrorRecord 生成
- **防再犯规则**：① ps1 里探测外部命令一律 `cmd /c "<cmd> 2>nul"`，或 `(& <cmd> 2>&1 | Out-String)` + `try/catch`，**不得假定 `2>$null` 能吞掉 native stderr**；② 用"跑片段"判断脚本行为时必须在**同款 shell**（`powershell.exe` 5.1 与 `pwsh` 7 分开）复刻原句，不得跨版本外推；③ 探测类命令必须为「命令不存在 / 引擎未起 / 权限不足」三条分支各给可见文案，不得靠未捕获异常收场
- **状态**：active
- **复发**：×1（本次核实 bootstrap.ps1 安装路径时发现）
- **关联**：BUG-141、`docker/scripts/bootstrap.ps1:36-39`、`docker/scripts/install.ps1:31-40`。**注意**：本条为片段复现（同 shell 同命令），用户 2026-10-08 现场实跑因引擎已启动而**未命中**——下次遇到"脚本莫名红堆栈"仍按此条先查 native stderr。

## L-68 「文件是 UTF-8」不等于「PowerShell 读得对」——无 BOM 的 `.ps1` 在中文 Windows PS 5.1 下按 GBK 解码
- **分类**：工具链（编码）
- **触发场景**：仓库交付的 `.ps1` 含中文；在中文 Windows（系统 ACP=936）用 Windows PowerShell 5.1 运行
- **现象**：`.\bootstrap.ps1` 直接抛 ParseException（`<` 保留运算符 / `&&` 非法语句分隔符 / 缺 `}`），且报错行里的中文是 `涓嬭浇鍚?` 这类乱码。第一反应容易往"用户命令打错 / 路径不对 / 脚本本身有语法错"上找——实际是 UTF-8 中文字节把**后面的 ASCII 引号吞掉**，双引号字符串永不闭合 → 吞掉后续行 → 连锁报错。
- **根因**：PS 5.1 对**无 BOM** 的 .ps1 按系统 ANSI 代码页解码（本机 ACP=936 即 GBK），PS 7 才默认 UTF-8；英文 Windows(CP1252) 下同样内容只是乱码不致错 ⇒ **该缺陷只在中文环境致命**，最容易被"我这儿能跑"掩盖。
- **防再犯规则**：① 仓库内 `.ps1` **一律 UTF-8 带 BOM**（正文一个字节不动、只加 3 字节，PS 5.1/7 双兼容）；② **每次用编辑工具写回 `.ps1` 后都要复查 BOM**——本次 `edit` 工具写回 `bootstrap.ps1` 就把 BOM 抹掉了，解析错误立刻从 0 回到 4 个（`ReadAllBytes` 看前三字节是否 `EF BB BF`）；③ 判断"脚本能不能跑"用 `[System.Management.Automation.Language.Parser]::ParseFile($path,[ref]$null,[ref]$errs)` **数错误**，不要靠肉眼看文件——读工具与编辑器会自动认 UTF-8，看不出问题；④ 用户报"某 .ps1 语法错误"时，先查 BOM 与系统 ACP（`(Get-ItemProperty 'HKLM:\SYSTEM\CurrentControlSet\Control\Nls\CodePage').ACP`），再谈命令对不对。
- **状态**：active
- **复发**：×2（安装入口不可用 1 次；编辑工具抹 BOM 致复发 1 次）
- **关联**：BUG-142、`docker/scripts/*.ps1`。**现场验证**：补 BOM 后用户 2026-10-08 13:55 实跑，脚本越过解析进入 WSL 就绪段（exit 2 待重启）

## L-69 「Windows 工作树」不等于「Linux 检出」——autocrlf 的 CRLF 会从入口脚本一路带进容器
- **分类**：工具链（跨平台）
- **触发场景**：把 Windows 上的工作树整棵交给 Linux 消费——`cp` 进 WSL、挂进容器、打成 tar 再解包执行
- **现象**：WSL 内 `bootstrap.sh` 起手即崩：`set: pipefail: invalid option name`、`$'\r': command not found`、`syntax error near $'in\r'`（第 7/10/13 行）。第一反应容易怀疑"脚本逻辑 / 权限 / 路径 / WSL 坏了"，实际是每行结尾多了个 `\r`。
- **根因**：Git for Windows 默认 `core.autocrlf=true`（本机系统级 gitconfig 实测 true）⇒ **工作树 CRLF、对象库 LF**；只要把工作树整棵搬到 Linux，`\r` 就跟着走。同一份代码在作者机器（mac/Linux 或 `autocrlf=false`）完全正常，故属"只在 Windows 复现"的隐性交付缺陷。
- **防再犯规则**：① 会被跨 OS 消费的文本资产用 `.gitattributes` **钉死 `eol=lf`**，不要指望检出配置（`*.sh`/`Dockerfile*`/`.env*`/`*.conf`/`*.yaml`）；② 入口脚本要"自我规整"时，**必须选在被消费代码之前执行的那一层**——本例 `bootstrap.sh` 第 13 行就崩，自愈代码写进它等于没写，只能放 Windows 侧（`bootstrap.ps1` 拷贝后、起 bash 前）；③ 取证口诀：`git cat-file -s <rev>:<path>` 与工作树字节数一比，**差值 ≈ 行数就是 CR 差**（本例 4347−4272=75）；④ 规整时按扩展名挑文件（`find … -name '*.sh' … -exec sed -i 's/\r//g'`），**禁止整树 sed**（会毁二进制资产）
- **状态**：active
- **复发**：×1（本次 bootstrap.ps1 Windows 全链）
- **关联**：BUG-143、`docker/scripts/bootstrap.ps1`、根 `.gitattributes`

## L-70 `wsl -l -q` 的输出在 PowerShell 里"字间夹 NUL"——拿它做 `-match` 判定必然误判
- **分类**：工具链（Windows/WSL 互操作）
- **触发场景**：在 PowerShell 里调 `wsl.exe` 列举/判定发行版（`wsl -l -q`、`wsl -l -v`），据此决定"要不要装"
- **现象**：脚本把"已装好的发行版"判成"没装" ⇒ 重复 `wsl --install` ⇒ `Wsl/InstallDistro/ERROR_ALREADY_EXISTS`（错误码 -1）硬失败。首次安装碰巧能过（那时确实没装），**再跑必挂**——症状看起来像"WSL 坏了 / 得用导入法"，很容易被带偏去手工 `wsl --import`。
- **根因**：`wsl.exe` 写 UTF-16LE，PowerShell 5.1 按控制台编码解码 ⇒ 每个字符后多一个 `\0`（实测码点 `85,0,98,0,117,0,…` = `U\0b\0u\0…`），`-match 'Ubuntu-22.04'` 永不命中；`[regex]::Escape()` 也救不了（病根不在正则）。清洗 NUL 后同一判据立刻变 False，可自证。
- **防再犯规则**：① 判"某发行版能不能用"**只认直接探测**——`cmd /c "wsl -d <名> -u root -- true 1>nul 2>nul"` 看退出码（0=就绪，-1=不可用），不要解析列表文本；② 万不得已要解析列表，先 `-replace "\`0",''` 清洗再匹配；③ 调 `wsl`/`docker` 这类会往 stderr 说话的外部命令一律走 `cmd /c "… 2>nul"`（配合 `$ErrorActionPreference=Stop` 才不炸，见 L-67）；④ 幂等脚本的"二次重跑"必须真跑一遍才算验证过——首次成功的路径很可能掩盖了检测逻辑的错。
- **状态**：active
- **复发**：×1（本次 bootstrap.ps1 / install.ps1 第二次重跑）
- **关联**：BUG-144、`docker/scripts/bootstrap.ps1`、`docker/scripts/install.ps1`
