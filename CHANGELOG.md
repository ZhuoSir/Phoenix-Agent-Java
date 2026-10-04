# Changelog

本项目版本记录。语义化版本 + 里程碑制（细则：bryanchen-spec skill / releases/ 目录）。
tag 之前无正式发版记录，历史 1.1.x 分支线（phoenix-1.1.1-release / 1.1.2-dev）未打过 tag，不追溯。

## [v1.5.0] - 2026-10-04
### 新增（交付工具链——源码到目标机服务全自动，零 DDL、应用代码零变化）
- 一键 bootstrap（R-10）：Windows `bootstrap.ps1` / Linux·mac `bootstrap.sh`，目标机源码一把梭（WSL2 就绪→引擎→打包→安装→收据卡），重启/失败重跑自动续接
- 一键打包 package.sh：自包含 tar.gz（Phoenix 双侧+基础运行时五镜像/compose 资产/双平台安装脚本/manifest+sha256/文档）；国内源全链默认（mirrors.list 竞速、阿里云 Maven 含 30s 超时、npmmirror），--overseas 一键切官方；容器内编译=构建机零 JDK/Node；编译一次+跨架构薄组装
- 九步安装 install.sh/install.ps1：环境三查（架构/OSType=windows 拒绝/磁盘）→引擎自动装（阿里源/离线 deb 降级）→sha 拒坏包→load→.env（随机密码 600）→compose→healthy 确定性轮询→verify 失败=安装失败→收据卡；幂等/断点/--env-from 升级承接/IMAGE_TAG 自动跟版
- 运维控制 phoenix-ctl.sh/.ps1：start/stop/restart/status/logs/verify/purge 一条入口
- compose 自愈：长驻服务 restart: unless-stopped（BUG-63）
- 工具箱文档 docker/scripts/README.md（脚本地图/四场景/故障速查）
### 修复（2）
- BUG-62 multistage settings XML 注释双横线致 Maven Non-parseable（v1.4.0 交付暗雷，打包流水线首炸排雷）
- BUG-63 compose 长驻服务无 restart 策略（宿主重启栈不自愈）
### 已知边界
- T-12 Windows 真机全链延期在案（ps1 过 Parser 实检+桩测；WSL 内九步与 Linux 同码已全链实证）；真机随时可销账
- 离线 engine-debs 首发仅 Ubuntu 22.04；mirror 竞速测响应不测带宽（--mirror 可手工指定，吞吐型探活在 BL-23）
### 升级说明
既有部署零操作（应用无变化）；要重启自愈则换 compose 后 `up -d --force-recreate --pull never`。新机安装直接 bootstrap（docker/scripts/README.md）

## [v1.4.0] - 2026-10-03
### 新增
- 断线续传/流恢复（BL-22）：关页面≠杀任务——服务端脱离式执行、5s 增量落库单行 upsert、重进自动追流（150ms 节流防风暴）、显式停止接口、HITL 断线暂存并轮回原轮、10min 兜底时限、一轮一约束
- 计划模式确认体验：确认卡三级取源显示提炼计划（plan summary/待执行命令，不再展示原始思考）+ 白卡新设计 + 点击整卡移除；批准即退出计划模式真执行（BUG-59 根治链）
- 知识库 QA/FAQ「问+答」联合向量化 + 按库幂等重刷（面板按钮 + POST /api/knowledge-base/{id}/re-embed）
- 智能体删除级联五表 + 存量孤儿清理运维脚本
### 修复（16）
BUG-56 登出500(nginx方法分流) / BUG-57 admin渲染四连修+md-card体系 / BUG-58 切智能体乱建会话 / BUG-11 前台confirm 404真身 / BUG-59 计划确认三连修 / BUG-60 文件面板复用不可见(会话维度去重+create_time回填95行) / BUG-61 追流重放风暴 / BUG-24 取消令牌+节点早停 / BUG-02 核账关闭 / BUG-03 建号密码校验 / BUG-05 登录码23009 / BUG-08 test.yml对齐 / BUG-14 删除孤儿 / BUG-18 问答联合向量 / BUG-21 模型类型列 / BUG-25 dev端口5777
### 不修复/延期（批准）
BUG-04 双账号体系（不修复，批准:陈卓）；BUG-24③ 底层硬中断（延期技术债）
### 交付
零 DDL；compose 三运维键（TURN_TIMEOUT/FLUSH/BUFFER）；nginx /auth 方法分流随镜像；multistage 国内源默认化（阿里云 Maven + npmmirror）；verify 13 断言；UPGRADE 含 Windows-WSL2 路线

## [v1.3.0] - 2026-10-02
### 新增
- 会话文件面板（BL-19）：智能体产物自动登记，聊天页 📁 抽屉列表/下载/预览(CSP)/删除，SSE 实时事件，50MB+属主鉴权，产物随 uploads 卷持久
- 独立知识库模块：知识库⇄智能体多对多绑定 + 组维度双向分配；召回改绑定语义；存量自动迁移零感知（V1.3.0_02 含迁移段）
- 深度思考分离展示（BL-21）：Thinking…/Think Done·Ns 折叠交互，正文纯净，metadata 持久回显，前台/admin 双端
- 工具迭代上限可配（V1.3.0_03）：运行时配置 1~100 留空=默认；超限提示带生效值；语义实证=模型推理轮次
### 修复（18）
- BUG-33/38/39/40/41/42/43/44/45/46/47/48/49/50/51/52/54/55：双前缀根治、组语义、缓存策略、侧栏风暴、文件时序与乱码、预设只读、harness 终止可见、镜像工具链(pip/cairosvg/CJK)、QA 答案回表、组件注册×2、思考白名单、登出幂等、SSE 心跳+900s 超时
### 交付
- 升级件 V1.3.0_01~03 + rollback 配对；UPGRADE.md 全新/离线/升级三路径均一键（migrator 全链演练通过）；verify 11 断言

## [v1.2.1] - 2026-10-01

### 修复
- **BUG-37（P1）** deepseek「测试连接」/AI 生成间歇失败——Spring AI 出站宣告支持 brotli 但无解码器，deepseek 回 br 时响应体解不出致 JSON EOF（同为 v1.2.0 期 BUG-22/23/32 间歇截断的总根因）；统一对 OpenAI 兼容调用钉 `Accept-Encoding: identity`
- **BUG-31（P1）** 全新空库首启：Java 自注册智能体先读库后落行致 NPE，应用无法启动；注册流程改为先落行再创建 + 判空双保险
- **BUG-34（P2）** 模型配置列表接口回显明文 apiKey；出口脱敏 sk-****尾4，编辑/测试回传脱敏值按 id 回源，日志不落真实 key
- **BUG-32（P2）** AI 生成对裸换行/非标准 JSON 容错不足致 42013；空内容重试一次 + 控制字符修复再解析 + 字段级兜底

_无库表变更、无配置变更、无新接口；直接替换制品即可。_

## [v1.2.0] - 2026-10-01

### 新增
- 技能管理：ZIP 上传自动识别、发布/下线/删除、按组授权、按智能体绑定、前台技能区与单轮显式执行
- 对话智能体运行配置：每智能体独立配置对话模型/知识库检索参数/数据库取数/深度分析/计划模式/记忆/文件系统策略
- AI 生成智能体描述与提示词：名称 → 描述（纯文本）+ Markdown 提示词（角色/描述/能力/安全范围四段），走模型管理默认对话模型，确认后随表单保存
- 模型管理「默认模型」+ 多启用集合：CHAT/EMBEDDING/AUDIO 每类型唯一默认（部分唯一索引兜底），启用不再互斥
- 提示词 Markdown 编辑器（自研轻量组件，零新增依赖）
- **一键部署 Docker 交付包**（`docker/`）：compose 六服务（nginx/backend/pg/redis/双初始容器）、首启自动建库+本版本升级件全序、离线 save/load 包、备份/升级/回滚/断言脚本

### 变更
- 智能体列表仅显示平台内创建的智能体、去类型标签（Java 自注册存量可对话不入列表）
- 运行时取模型改判「类型默认」，未选模型按默认加载并可在下拉处显式选择
- 交付包 nginx 内置「/api 剥一层」折叠适配前端双重前缀缺陷（BUG-33，根治 BL-18）

### 修复
- 21 条（BUG-01~01、06/07/09/10/12/13/15/16/17/19/20/22/23/26~30、33(规避)/35/36）：全新环境建库链、EMBEDDING 404、SQL 安全误杀、登录态取用、AI 生成 JSON 回吐/超时链路、长回复截断(maxTokens 未传)、表单上限、技能上传 415/校验文案/ReturnVo 重载误用等——明细见 `specs/_project/bugs.md`

### 升级件
- `releases/v1.2.0/sql/`：V1.2.0_01~05 + 配对回滚 R1.2.0_01~05；配置汇总 `config/changes.md`；操作手册 `UPGRADE.md`（方式A手工/方式B一键包）
