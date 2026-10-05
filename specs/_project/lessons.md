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
