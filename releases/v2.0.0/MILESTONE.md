# 里程碑 v2.0.0

> 状态：**Implement 完成**（M0/M1 立项 2026-10-07；在途 spec **28/28** 任务完成，含 CR-01/CR-02 合入；**M3 汇总完成（2026-10-10，见审计记录）**；M2 冻结与 M4 发布待口令）

> 创建: 2026-10-07 | 类型: **MAJOR** | 验证日期: -
> **版本分支**: `v2.0.0`（2026-10-07 建，基点 = main tip `9a4b6c0`；v1.7.0 已发布并合 main，**无悬空**）
> 前言：立项前发现 `v1.7.0` 分支上有 3 个**发布后补账提交**未回 main（`5520ad6` / `1161913` / `93ae791`），经用户 2026-10-07 口令先合 main（merge `9a4b6c0`）后自 main 切本分支，偏离已记 `lessons.md` **L-42**

**版本语义**：MAJOR——**破坏性变更**。删除企业（`tbl_privilege_company`）、部门（`tbl_privilege_department`）、员工（`tbl_privilege_employee`）三条组织维度及其在用户表上的归属列，用户体系收敛为 **用户 / 角色 / 组** 三要素；组直接关联智能体、技能、知识库、MCP 插件。

## 一、需求挂接表（M1）

| spec | 版本 | 状态 | 新增 | 变更 | 摘要 |
|---|---|---|---|---|---|
| specs/20261008_chat-attachment-understanding | requirements **v1.1.0 已确认** / plan **v1.1.0 已确认** / tasks **v1.1.0 已确认**（确认人 陈卓 2026-10-08；含 CR-01 micro 合入） | **Implement 完成（12/12，含 CR-01/CR-03 合入）**（2026-10-10 审计补正：原记 1/9） | R-01~R-12 | CR-01（多模态模型改 `qwen3.8-max`；迁移件只 DDL、模型行经管理页配置） | 对话附件上传与大模型理解：文档（word/pdf/excel/txt/md）+ 图片（常见格式）白名单，其他一律不支持；**admin-ui + mobile-ui 两端**；图片走**真多模态视觉模型**（拟在 `tbl_data_model_config` 增 `model_type=VISION`）；复用既有 Tika 解析与 FileStorage 存储 |
| specs/20261007_user-role-group-model | requirements **v2.8.0 已确认（陈卓 2026-10-07；v2.0.0 重确认① + R-12~R-19 逐版确认）** / plan **v1.8.0 已确认** / tasks **v1.8.0 已确认** | **Implement 完成（T-01~T-28，28/28）**；R-12（信息架构）、**R-13（账号集合收敛）** 与 **R-14（用户类型/IDM + 工号下线）**、R-15~R-17 与 **CR-01(R-18)/CR-02(R-19)** 追加（requirements v2.2.0→v2.8.0 / plan v1.3.0→v1.8.0 / tasks v1.2.0→v1.8.0 均 已确认 陈卓） | 全部 R-01~R-19 均已实施（T-01~T-28 全勾，见 tasks 覆盖矩阵） | 删除组织三表 + **用户模型三表**组织列 + 后台数据组 + **三方平台配置表**；下线三方集成三层（同步/免登/平台配置，含 SDK、`PlatformTypeEnm` 家族、`third_party_id`、前端页面与菜单行）；**只保留账号密码登录**；ACL 基线重建 | 需求 **19 条 R**（R-01~R-19）；Q1~Q6 + Q-P1~Q-P6 + Q7~Q13 全部裁定；调研登记 BUG-116~122（后续本轮 BUG-127~140）；教训 L-43/L-44（后续 L-48~L-66） |
| specs/20261009_ollama-model-support | requirements **v1.0.0 已确认** / plan **v1.0.0 已确认** / tasks **v1.0.0 已确认**（陈卓 2026-10-09） | **实现完成（13/13）**（2026-10-10 审计补正：原记 10/13；T-09/T-10 浏览器实测已通过） | R-01~R-11（11 条） | 向量维度解析器替代 512 硬编码；ollama embedding 路径 | Ollama 本地/内网接入：provider 一等项 + 三类模型 + 模型列表 + 工具调用 + 768 维迁移（含存量恢复） |

## 二、纳入缺陷（随版修复批）

| （无 spec 目录）**hotfix/ps1-utf8-bom** 安装链修复包 | 不单独挂版（用户 2026-10-08 裁决「不用」开 v1.7.1） | **已合入 v2.0.0 在途线**（2026-10-08 用户口令「把他合并到2.0.0里」，执行 version.md 预录动作） | 无新增 R | 构建/安装链：`.gitattributes` 钉 eol、`.ps1` 补 UTF-8 BOM、CRLF→LF、发行版判定改直接探测、引擎安装国内回退、镜像探活抗抖动、长跑进度透传、前端 `pnpm-lock.yaml` 入库 + `.m2`/pnpm 缓存挂载、`V1.7.0_02` 容忍全新库无 thinktest（migrator 判重只看 seq、无 checksum ⇒ 存量环境不重跑） | Windows/WSL2 中文环境一键安装连续踩坑的 11 条缺陷（**BUG-141~151**）+ 教训 **L-67~L-73**；账本按并集合并（我方止于 BUG-140/L-66，无撞号），版本列统一改挂 v2.0.0 |
| 编号 | 标题 | 严重度 | 状态 |
|---|---|---|---|
| BUG-116 | 角色↔菜单授权界面失效（ACL 只能靠 SQL 维护） | P1 | **已修复(v2.0.0)**（审计对齐 bugs.md） |
| BUG-117 | `tbl_privilege_acl` 数据大面积失真（孤儿 module_id / 脏 release_id） | P1 | **已验证(v2.0.0)** |
| BUG-118 | `tbl_privilege_user_role` 孤儿角色关联 | P2 | **已验证(v2.0.0)** |
| BUG-121 | 平台侧组授权表读侧不过滤逻辑删（已撤销授权仍可见） | P2 | **已验证(v2.0.0)** |
| BUG-122 | 预设问题后台写接口零权限校验 | P2 | **已验证(v2.0.0)**（写接口） |
| BUG-123 | 用户默认角色兜底因大小写从未生效（API 建号恒零角色） | P1 | **已验证(v2.0.0)** |
| BUG-124 | 角色授权弹窗残留 `debugger;` | P3 | **已修复(v2.0.0)** |
| BUG-127 | 基线 `sql/all_data.sql` DROP TABLE 连带删序列致导入中断 | P2 | **已验证(v2.0.0)** |
| BUG-130 | 账号管理关键字搜索未覆盖手机号（`mobile`）⇒ 手机号搜索一直失效 | P2 | **已验证(v2.0.0)** |
| BUG-132 | 删除账号端点零保护 ⇒ 可删除自己、可删除最后一个启用的超管（乃至内置 admin） | P1 | **已验证(v2.0.0)** |
| BUG-133 | 智能体列表不做用户过滤 + 创建人（`admin_id`）从不写入 ⇒ 人人可见全部智能体 | P2 | **已验证(v2.0.0)** |
| BUG-134 | 角色删除端点零保护 ⇒ 可删除「系统管理员」角色（连带删掉其全部用户绑定与 ACL） | P1 | **已验证(v2.0.0)** |
| BUG-135 | 技能管理/插件管理/MCP/知识库 在角色授权树里无法勾选权限 ⇒ 无法分配 | P2 | **已验证(v2.0.0)** |
| BUG-136 | 角色列表→「分配权限」弹窗点「确定」毫无反应（不关窗、无提示） | P2 | **已修复(v2.0.0)** |
| BUG-137 | 角色「分配权限」弹窗保存时机错误 | P2 | **已修复(v2.0.0)** |
| BUG-138 | 角色授权行的 `release_sn` 被误写为角色业务 sn ⇒ 授权对登录菜单不可见 | P1 | **已验证(v2.0.0)** |
| BUG-139 | `KnowledgeBaseServiceImpl.queryByConditionsWit | P2 | **已修复(v2.0.0)** |
| BUG-140 | 技能上传报「未能读取到有效 token」（CR-01 引入的回归） | P1 | **已修复(v2.0.0)** |
| BUG-141 | `bootstrap.ps1` / `install.ps1` 在「docker CLI 在 | P2 | **已修复(v2.0.0，待现场复验)** |
| BUG-142 | 交付脚本 `.ps1` 为 UTF-8 无 BOM + 全中文 ⇒ 在中文 Windows  | P1 | **已修复(v2.0.0，未发版)** |
| BUG-143 | Windows 一键安装路径被 CRLF 打死 | P1 | **已验证(v2.0.0，现场 2026-10-08 14:24)** |
| BUG-144 | 发行版就绪判定恒误判 ⇒ 第二次重跑必失败 | P1 | **已验证(v2.0.0，现场 2026-10-08 14:24)** |
| BUG-145 | 受限网络下引擎装不上 | P1 | **已修复(v2.0.0，待现场复验)** |
| BUG-146 | 长跑零进度输出 | P2 | **已修复(v2.0.0，待现场复验)** |
| BUG-147 | `mirrors.list` 带 CRLF ⇒ 每个镜像源都被判死 ⇒ 打包段步骤 2/8  | P1 | **已修复(v2.0.0，待现场复验)** |
| BUG-148 | 基础镜像 blob 截断逃过 pull 校验 ⇒ 打包段 3/8 死于晦涩报错 | P2 | **新建**（现场已修复；脚本加固未实施） |
| BUG-149 | 前端打包在干净环境必然失败（结构性矛盾） | P1 | **新建**（待裁决修复方向；lockfile 已入库） |
| BUG-150 | frontend 构建对慢/不稳 npm 镜像不健壮 | P2 | **已修复(v2.0.0)** |
| BUG-151 | v1.7.0 升级件 V1.7.0_02（统一账号迁移）M8 写死开发数据集特有账号 thi | P1 | **已修复(v2.0.0)** |

### 第三批（安装链 / 前端 / 迁移）BUG-174~187

> 来源：`hotfix/install-chain-hardening`（2026-10-10 并入 v2.0.0，merge `a9bb1ea`）。
> **编号迁移**：该线并行开发时取号 BUG-161~173，与 v2.0.0 线撞号；经用户裁决「v2.0.0 侧不动」**整批 +13 改号**为 BUG-174~186（旧号 165~173 空置、不得复用），详 `bugs.md` 顶部注记。

| 编号 | 标题 | 严重度 | 状态 |
|---|---|---|---|
| BUG-174 | `bootstrap.ps1`/`install.ps1` 位置参数零校验 ⇒ 误用 ` | P2 | 已验证(v2.0.0) |
| BUG-175 | 引擎回退源只硬编码 `mirrors.aliyun.com` 单源、无 fallback | P1 | 已验证(v2.0.0) |
| BUG-176 | `phx_mirror_pick` 只探 `/v2/` HTTP 响应码、不测带宽与完整 | P2 | 已验证(v2.0.0) |
| BUG-177 | `wsl --import` 导入的发行版无 `/etc/wsl.conf` ⇒ sys | P2 | 已验证(v2.0.0) |
| BUG-178 | 引擎「开机自启」只静默兜底、从不核实，且重启/休眠语义从未告知用户 | \ | \ |
| BUG-179 | `bootstrap.ps1` 的源码同步用 `cp -ru` | P1 | 已验证(v2.0.0) |
| BUG-180 | mobile UI 未纳入打包链 ⇒ 从干净环境打包必在步骤 4/8 失败 | P1 | 已验证(v2.0.0) |
| BUG-181 | `package.sh` 断点状态机只记步骤号、不含逻辑指纹 ⇒ 改了某步逻辑后旧状态仍 | P3 | 不修复(转 BL-47；批准:用户 2026-10-10) |
| BUG-182 | 新建智能体 | P2 | 已验证(v2.0.0) |
| BUG-183 | 重打包后容器不换新镜像 | P1 | 已验证(v2.0.0) |
| BUG-184 | v2.0.0 迁移链的「开发数据集断言」再犯 | P1 | 已验证(v2.0.0) |
| BUG-185 | `V2.0.0_02` 角色回填把 `admin` 也补成普通角色 | P1 | 已修复(v2.0.0)（种子侧根治：建号即绑 ROLE_ADMIN） |
| BUG-186 | 前端 `platform-agent-request.ts` 三个接口路径漏了 `/ap | P2 | 已验证(v2.0.0) |
| BUG-187 | `phoenix-ctl.ps1` 位置参数绑定错位 ⇒ Windows 侧服务控制命令 | P2 | 已修复(v2.0.0)（本机四路实测通过，待用户复测 start/s |

## 三、升级项（对外口径，M3 时从各 spec 的 R 条款聚合）

- 变更：用户体系删除企业/部门/员工维度，收敛为 用户·角色·组（**破坏性**，升级说明须含数据处置口径与回滚步骤）
- 变更：组可直接关联智能体 / 技能 / 知识库 / MCP 插件，组内用户据组获得资源可见性
- 变更：后台菜单按角色过滤生效（原先为全员可见）
- 变更（R-12）：后台信息架构收敛 —— 「权限管理」+「前台管理」合并为 **「系统管理」**（`/system-management`），账号管理唯一化；角色/账号/组同处系统管理之下；存活菜单 21 → 19
- 新增（对话附件）：对话可上传文档与图片并按白名单校验；文档解析并入提示词、图片走真多模态视觉模型；附件持久化与历史回看、按归属鉴权
- 新增（会话空间隔离）：后台运行页与前台 chat 会话语义分离，互不可见
- 新增（Ollama 模型接入）：新增 Ollama 提供商（API Key 可留空）、三类模型覆盖、本机模型列表拉取、离线/内网可用
- 变更（向量维度）：Embedding 维度由解析器按模型实际值决定（支持 768），不再硬编码 512；维度不一致显式拒绝

## 四、汇总进度（由里程碑审计从事实重算，手工勾而无判据 = 无效勾）

- [x] 全部 spec 已合并（M2 冻结前置；以 git 合并记录为准）—— **2026-10-09 审计补勾**：`user-role-group-model`（58 commit）/ `chat-attachment-understanding`（41 commit）均已并入 v2.0.0（判据 = `git log v2.0.0 --grep="Spec: specs/<目录>"`）
- [x] SQL 汇总编号 + rollback 配对（`sql/` 件数 == artifacts 行数）—— **2026-10-10 重算：正向 16 / 回滚 16**，一一配对（原记 14/14；增量来自 CR-01 的 `_13`、CR-02 的 `_14`、附件的 `_15`、CR-03 的 `_16`）
- [x] 配置汇总 `config/changes.md`（覆盖全部 artifacts 配置行）—— 2026-10-10 落盘；本版仅 1 处配置变更（compose 新增 `pylibs` 卷），artifacts 配置行数 0，一致
- [x] `RELEASE-NOTES.md`（「包含需求」覆盖纳入表）—— 2026-10-10 落盘；3 个 spec 全覆盖；「修复」节由 bugs.md 过滤生成（44 条：已验证 24 / 已修复 18 / 未修复或转办 2）
- [x] `UPGRADE.md` + 回滚步骤（执行序条数 == `sql/` 件数）—— 2026-10-10 落盘；执行序 16 条 == sql 正向 16 件；含 13 项验证与逆序回滚节（16→01）
- 演练（唯一人工留证项，空=未过）：验证人 **陈卓**（2026-10-07 选定「隔离栈 + 真实浏览器」方案并参与 UI 断言）/ 日期 **2026-10-07** / 结论 **14 项端到端断言全绿**（`specs/20261007_user-role-group-model/evidence/T-16_result.txt`）
- [x] `checklist.md` 落盘（M3 节全勾）—— 2026-10-10
- [ ] tag 待口令（tag 存在性可自动核）

## 五、审计记录

- **2026-10-07 立项（M0+M1）**：
  - 启动版本问句（§1 步骤 3）：报账后由用户拍板「新建 **v2.0.0**」——依据 BL-31 备忘「删除属破坏性变更，需 MAJOR 版本」；**在途唯一 = v2.0.0**（v1.7.0 已发布）
  - 前置处置：`v1.7.0` 分支 3 个发布后补账提交经用户口令 `--no-ff` 合入 main（merge `9a4b6c0`），双建基点自 main tip；**该偏离=tag 之后修账**，已在 `releases/v1.7.0/MILESTONE.md` §九 如实登记
  - 双建完成：本目录 + `releases/v2.0.0/` + 裸号版本分支 `v2.0.0`；`specs/20261007_user-role-group-model/` 四件套（requirements/plan/tasks/changelog）落盘
  - 待办挂接：`BL-31 → 已立项(v2.0.0)`；反查命中 `BL-05`（用户裁定 `≠BL-31`，翻「已交付(v1.7.0)」）、`BL-30`（列 Non-goals，独立排期）
  - 状态：**待 requirements 确认①**（Q1~Q6 阻塞问题未答复前不进入 Phase 2）
- **2026-10-07 Q1~Q6 裁定（同轮，需求 v0.1.0 → v0.2.0 草稿）**：
  - Q1 合并一套 → **R-09 组语义唯一化**（保留平台侧资源授权组；后台数据组经检索确认零业务引用=死代码，单向下线）
  - Q2 一次做完（不分期）／Q3 组织数据直接删除／Q5 无外部 IDM·HR 对接（`idm_company_id`/`third_id`/`it_user_id`/`employee_id` 可移除）／Q6 纳入 R-08
  - Q4 移除三方同步 → **R-06 方向推翻改写**（原「保留用户同步」→「同步能力整体下线」），并新增共享面边界：**第三方免登（应用内 SSO）与其平台配置 `tbl_platform_platform_info` 必须保留**（同步与登录是两条链路、共用一个配置表）⇒ 已配回归断言
- **2026-10-07 Phase 2 与账实核对（新增）**：
  - plan 完整稿落盘（坑核对 L-01~L-42 全 42 条 / 决策 1~6 / 共享面身份矩阵 / DDL 与回滚草案 / 风险 12 条），三路只读调研全部回填，无占位残留
  - **账实不符自身纠正**：本表 spec 行此前仍写「requirements v0.2.0 草稿（待确认①）」，实际 requirements 已于本日经陈卓确认① 并随后修订至 v1.1.0 ⇒ 按事实改正（同类陈旧第 N 次，L-14/L-25 家族）
  - Q-P1~Q-P4 裁定落账（补默认角色+超限豁免+提示兜底 / BUG-116 纳入→新增 R-10 / 三表去组织列 / 无授权行=公开）
  - 调研登记既有缺陷 **BUG-116~BUG-122**（7 条：授权界面失效、ACL 数据失真、孤儿 user_role、类型不匹配、可见性判据错位、组授权墓碑行、预设问题零校验）+ 教训 **L-43**
  - 状态：**待 requirements v1.1.0 重新确认①** → 随后提交 plan 确认②
- **2026-10-07 范围变更（user 口令「扫码登录去掉，不要，只保留现在的密码登录」）**：
  - 追问裁定 **Q-P5**（三方平台配置：表/CRUD/页面/菜单行随免登一起下线）/ **Q-P6**（`apps/mobile-ui` 保留，只删 SSO 自动登录）
  - requirements → **v2.0.0（MAJOR，待重确认）**：新增 **R-11**（三方集成三层同批下线，只留密码登录）；R-06 边界 1 作废、边界 4 解除；Non-goals 反转
  - plan → **v0.2.0**：决策 3 重写为「三层同批整删」；前端整删 15→18 文件；数据模型 +`tbl_platform_platform_info` 表 + `third_party_id` 列
  - 术语纠错：原文「扫码登录」实为**第三方免登（应用内 SSO）**（教训 **L-44**）
  - 状态：**待 requirements v2.0.0 重新确认①**
  - 状态：**待确认①**（v0.2.0 仍为草稿；等用户明确「确认」口令 + 确认人名字，铁律 2）

- **2026-10-07 Implement 完成（T-01~T-17，本会话）**：
  - 需求 11 条（R-01~R-11）全部落地：组织三维度下线（含 3 表 + 9 列）、用户·角色·组三维度化、
    后台菜单按角色过滤生效、组侧技能/MCP 授权补齐、预设问题写接口鉴权、三方集成三层（同步/免登/平台配置）下线且只留密码登录
  - 升级件 **10 件（正向 5 + 回滚 5，配对）**：`V2.0.0_01`(DDL，新增) / `02` / `03`（T-05）/ `04`（T-06）/ `05`（新增）
  - 演练：drill 正反向 + **洁净库重放**（`all_schema`/`all_data` → 01~05，两轮全 exit=0）；
    **隔离栈真实升级序列**（T-01 全量备份 → 01~05）→ 迁移前 27 菜单/61 ACL → 迁移后 **21 菜单/28 ACL/组织表 0/零角色用户 0**
  - 端到端 14 项断言全绿（含超管 21 vs 普通 7、同一 token 改角色即时生效、按钮级位受控 '31'→5/'1'→1/'3'→2、
    越权 URL 404、零授权不白屏、组侧授权落库/撤销、预设问题 403/200、三端点 404、前台密码登录、
    只填账号+1 角色建号成功、普通角色导航仅 3 项、mobile-ui 免登请求为 0）
  - 台账：BUG-116/117/118/121/122/123/124/127 → 已修复/已验证(v2.0.0)；新登记 BUG-123~129；
    教训 L-43~L-50（含 L-32 复发、L-48 探针同构、L-49 IF EXISTS 作用域、L-50 假绿）
  - **未做（发版步骤）**：`releases/v2.0.0/` 的 UPGRADE.md / RELEASE-NOTES.md / checklist / config 汇总（M3）、
    活库 `phoenix` 迁移、v2.0.0 镜像构建与 release 栈重启、tag 与冻结
  - `BL-31 → 实现完成(v2.0.0，待发版冻结)`（如实标注，未冒认"已冻结"）

- **2026-10-07 构建与部署（用户口令「整体走个编译，打包镜像，然后部署，8090端口」）**：
  - 编译：`mvn clean package -DskipTests -Dspring-javaformat.skip=true` → **BUILD SUCCESS / 0 错误**；
    产物 `phoenix-admin/phoenix-admin-manager/target/phoenix-admin.jar`（413M）→ `docker/.stage/phoenix-admin.jar`
  - 前端：`pnpm build`（admin-ui）→ `docker/.stage/dist`（9.1M）
  - 升级件聚合：`specs/.../sql/V2.0.0_0{1..5}.sql` → **`releases/v2.0.0/sql/`**（正向 5 + `rollback/` 5，配对）
  - 镜像：`phoenix-backend:v2.0.0`（1.57GB，基座 `docker.elastic.co/elasticsearch/elasticsearch:9.2.5`
    + `JAVA_BIN=/usr/share/elasticsearch/jdk/bin/java`，因 Docker Hub 不可达而走本地缓存基座）、
    `phoenix-frontend:v2.0.0`（58.2MB，nginx:1.27-alpine）
  - 迁移前**再次全量备份**：`backups/pre_v2.0.0_deploy_20261007_183127.sql`（44M，安全网）
  - 部署：`docker compose up -d`（`IMAGE_TAG=v2.0.0`，`PHOENIX_HTTP_PORT=8090`）→ 迁移器按台账应用
    **V2.0.0_01~05**（5 行入 `tbl_phoenix_release`）→ 后端/nginx 重建为 v2.0.0
  - **验证**（`specs/20261007_user-role-group-model/evidence/T-18_deploy-verify.txt`）：
    nginx `0.0.0.0:8090->80` healthy ｜ backend healthy ｜ 容器内 `/echo/ok` 200 ｜ **8090 首页 200** ｜
    旧 9080 已无监听 ｜ 库终态 **菜单 21 / 组织表 0 / ACL 28 / 零角色用户 0** ｜
    **启动期 SQL 报错 0** ｜ 普通角色 menus=7（顶层 智能体管理/个人中心/知识库/智能体中心）｜
    `/auth/thirdLogin` 与 `/platform/platform-info/getEnabledPlatform` 均 **404** ｜
    前端 chunk 中组织维度残留 **0**
  - 配置变更（`.env` 被 `docker/.gitignore` 忽略，故不入库，仅登记于此）：`PHOENIX_HTTP_PORT=9080 → 8090`、
    `IMAGE_TAG=v1.6.0-dev → v2.0.0`
  - 已知无害告警：迁移器用 `psql -1` 包事务，而升级件自带 `BEGIN/COMMIT` ⇒ 日志出现
    `WARNING: there is already a transaction in progress` / `there is no transaction in progress`；
    各件均幂等且自检 NOTICE 通过，台账 5 行齐全（后续可将升级件改为不带显式事务以消除告警）
  - 仍未做：`UPGRADE.md` / `RELEASE-NOTES.md` / `checklist.md` / `config/changes.md`（M3 汇总件）、
    合并 main、打 tag 与**冻结**

- **2026-10-07 R-12 追加并落地（T-18，用户口令「权限管理和前台管理合并成系统管理…」）**：
  - 流程：requirements **v2.1.0** 新增 R-12 → plan **v1.2.0** 追加 §R-12 → tasks **v1.1.0** 追加 T-18 →
    用户口令「确认执行（确认人：陈卓）」+「URL 一并改成 /system-management」→ 实施
  - 升级件 **`V2.0.0_06__menu_merge_system_management_dml.sql`**（+ rollback，配对 6/6）：
    权限管理原地更名系统管理（id 不变保 ACL）、组管理迁入并相邻、5 个子菜单 URL 前缀统一、
    删前台「账号管理」与空目录「前台管理」及其 ACL
  - 前端删 4 文件（`views/account/account-info/**`）；**3 个 API 模块保留**（仍被 user.ts / 账号表单 / 组管理页引用）；
    基线两份同步（各删 4 行、改写 5 行 URL）
  - 验证：drill 正反向（01~06 两轮 ×2 + 回滚 + 重放）与洁净库重放全 exit=0；**活库经 migrator 应用 06**
    （台账 6 行）；部署后实测 菜单 19 / 系统管理 6 子项次序与 URL 全对 / 超管 19 普通 7 /
    `/platform-account/account-info` → 404；typecheck 204 < 基线 207
  - 自身缺陷修正：06 首版自检硬编码"存活菜单=19"，在种子库（14 条）失败 ⇒ 改为**环境无关不变量**校验，
    记 **L-51**

- **2026-10-07 R-13 追加并落地（T-19，用户口令「只保留 admin 和 chenzhuo 两个账号，其他的全部删掉，包括初始化数据也要改」）**：
  - 流程：requirements **v2.2.0** 新增 R-13 → plan **v1.3.0** → tasks **v1.2.0** 新增 T-19 →
    用户确认「确认执行（确认人：陈卓）」+ 追问裁定「被删账号的聊天数据一并删除」→ 实施
  - 升级件 **`V2.0.0_07__account_prune_dml.sql`**（+ rollback，配对 **7/7**）：删除 5 个账号及其账号域数据
    （用户/角色绑定/登录日志/智能体绑定/聊天会话与消息/向量记忆），自检为环境无关不变量
  - **初始化数据重写**：基线两份各删 **88 条**种子语句，注入 chenzhuo 一套 5 条；admin 仍由
    `docker/init/10_seed_admin.sql` 提供 ⇒ 全新库初始化后账号恰为 `{admin, chenzhuo}`
  - 验证：drill 升级路径（01~07 两轮全 exit=0，反向可复原 5 账号 + 聊天数据）与洁净库重放
    （migrate.sh 首启顺序，两轮全 exit=0）；活库经 migrator 应用 07（台账 7 行）；
    部署后实测 **恰 2 账号**、旧 5 账号登录**全部失败**、菜单 19 / 7、残留账号域行 0
  - 过程缺陷修正：① 按行编辑 dump 切断多行 INSERT（记 **L-52**）；② 迁移后库导出的种子缺迁移前 NOT NULL 列
    （记 **L-53**）
  - 遗留处置（用户裁定「不用加 sql，直接数据清理掉就好了」）：**未新增迁移件**，于 2026-10-07 20:20 直接清理活库孤儿账号域数据 ——
    `tbl_privilege_user_role` 12 行、`tbl_privilege_login_log` 1 行、`tbl_data_chat_message` 39 行、`tbl_data_chat_session` 5 行；
    清理前已备份至 `backups/orphan_cleanup_20261007_202052.sql`（gitignore，仅本地）；
    复核：孤儿全为 0，`user_role` 仅两个账号的绑定（admin 活跃+1 墓碑 / chenzhuo 活跃），登录复验 admin ✅ chenzhuo ✅，
    5 个旧账号全部失败 —— 证据 `evidence/T-19_orphan_cleanup.txt`

- **2026-10-07 R-14 追加并落地（T-20，用户口令「工号和用户类型还有用吗，没用就删掉吧。用户类型idm是什么时候才会用到」→「确认执行 R-14」+「工号也删掉」）**：
  - 流程：requirements **v2.3.0** 新增 R-14 → tasks **v1.3.0** 新增 T-20 → 用户确认 → 实施
  - 结论依据（`evidence/T-20_scan.txt`）：`user_type` 仅 2 处后端引用且无逻辑分支、库内 `user_type=1` **0 条**；
    IDM 同步包与三条 IDM/工号异常枚举均为**定义未用**；工号则有 6 处真实承重（查询/绑定冗余列/搜索/审计/UserProfile）
  - **IDM 何时才会用到**：仅接外部 IT/HR（IDM）做用户同步时；R-02/Q5 已确认无此对接 ⇒ 当前永不使用
  - 升级件 **`V2.0.0_08__usertype_code_drop_ddl.sql`**（+ rollback，配对 **8/8**）：删 5 列 + 组归属名称改用户名
  - 后端 17 文件（删字段/接口/死枚举与死异常；工号语义统一替换为用户名）、前端 7 文件、4 处会话读取点加固 Jackson
  - 验证：drill 整链一次 + 08 幂等 + 反向复原列与原值；洁净库重放全 exit=0；活库 migrator 应用 08（台账 8 行）；
    部署后实测 目标列消失 / 登录响应无 userType / 账号列表无 code,userType / `GET /code/{code}` 404 /
    前台 `userCode`=用户名 / 菜单 19-7 不变 / typecheck 零新增 / 后端 0 ERROR
  - 过程教训：删除会话缓存实体字段的兼容风险（**L-54**）、升级链可重放性的终结（**L-55**）
  - 仍存留同类死列（0 引用、数据空，本次未动）：is_leader / sex / address / fax / fail_month /
    failure_time / pwd_ftime / acl_timestamp

- **2026-10-07 R-15 追加并落地（T-21，用户口令「账号管理里搜索框支持手机号搜索；要有启用/禁用的按钮，可以批量按钮操作，也支持操作增加按钮；把分配权限放到编辑里」）**：
  - 流程：requirements **v2.4.0** 新增 R-15 → plan **v1.4.0** / tasks **v1.4.0** 追加 T-21 → 用户确认 → 实施
  - **无新增迁移件**（`status` 列已存在；台账仍 **8 件**）
  - 后端：`pageByQuery` 补 **`mobile`**（修 **BUG-130**：手机号存 `mobile`，原检索只搜 `phone` 座机 ⇒ 手机号搜索一直失效）；
    新增 `PUT /status`、`PUT /status/batch`；`isSuperAdmin` 口径统一（LoginServiceImpl 委托）
  - 前端：搜索提示改「用户名 / 姓名 / 手机号」；勾选列 + 批量启用/禁用；操作列启用/禁用（按状态切换）；
    **删「分配权限」按钮与独立弹窗**（角色/组分配统一走编辑）
  - 安全保护（两条，实测均生效）：禁止操作**当前登录账号本人**；禁止禁用**最后一个启用的超管**
  - 实测：手机号全量/片段、姓名、用户名均可搜；禁用后登录被拒「用户已被禁用」；批量返回更新行数；
    0 ERROR；`typecheck` 203（较前 204 零新增）
  - 新登记 **BUG-131**（用户管理端点缺管理员角色守卫，未修，建议另立）

- **2026-10-07 R-16 追加并落地（T-22，用户口令「admin 超管账号，不能被删除和禁用，这个是基础」）**：
  - 流程：requirements **v2.5.0** 新增 R-16 → plan/tasks **v1.5.0** 追加 T-22 → 用户确认（范围：只保护 admin 这一个）→ 实施
  - **无迁移件**（台账仍 **8 件**）
  - 保护（服务端强制，对任何调用者生效）：① 内置超管账号（`username=admin`，忽略大小写）不可禁用
    （单个/批量含之整体拒绝、不部分执行）② 不可删除 ③ **不可改名**（封堵"改名后禁用/删除"的绕过路径）
  - 顺带补齐 **BUG-132**：删除端点原**零保护**（可删自己/可删最后一个启用超管）⇒ 补三条保护
  - 前端：操作列对 admin 的「启用/禁用」「删除」置灰 + 原因提示；批量预检
  - 实测：admin 本人与普通角色两种身份发起的禁用/删除均被拒且行仍在；改名被拒；其他字段仍可编辑；
    终态三账号齐全均启用、admin 可登录且菜单 19、0 ERROR
  - 过程记录：`PrivilegeUserDTO` 不含 `status` ⇒ 编辑路径本就无法改状态（实测确认，无需额外强制）

- **2026-10-07 R-17 追加并落地（T-23，用户口令「每个人只能看到自己创建的智能体，然后系统管理员能看到所有人的，
  系统管理员这个角色也不可以删除」）**：
  - 流程：requirements **v2.6.0** 新增 R-17 → plan/tasks **v1.6.0** 追加 T-23 →
    用户确认 + 存量回填 admin + 连编辑删除一并限制 + 角色规则原话「role_admin 不可以删除，除此之外都可以删，但是有用户不能删」
  - **升级件 V2.0.0_09**（+rollback，配对 **9/9**）：10 个无主智能体回填给内置 admin（子查询动态解析 id，跨环境）
  - 后端：创建写 `admin_id`；列表 owner 过滤（超管豁免）；`checkAgentExists` 统一归属校验（6 类单对象端点实测 403）；
    `deleteRoleById` 按 sn/持有者判定
  - 修 **BUG-133**（智能体列表无归属过滤 + 创建人从不写入）与 **BUG-134**（角色删除零保护，可删 ROLE_ADMIN 致失管）
  - 实测：超管 5 条 / 普通角色 0 条；越权全 403；新建归属正确且双方列表随之变化；
    ROLE_ADMIN 拒 / COMMON（有持有者）拒 / 无持有者角色可删且行消失；0 ERROR
  - 注意：普通角色（如 chenzhuo）的智能体列表现在**只显示自己创建的**，因此其列表当前为空（历史 10 个已归 admin）

- **2026-10-07 BUG-135 修复（升级件 V2.0.0_10）**：技能管理/插件管理/MCP/知识库 在角色授权树无法勾选。
  - 根因（三段证据）：`tbl_privilege_module.state`（varchar 位掩码）这 4 行为 NULL ⇒ 后端 `tree()` 以
    `state?:0` 过滤权限位 ⇒ `pvalues` 空 ⇒ 前端 `v-if="pvalues?.length"` 不画复选框；
    这 4 菜单由功能迁移（V1.2.0_02/V1.3.0_02/V1.6.0_02）插入时漏写 state，且不在基线 ⇒ 全新库复现。
  - 修复：`V2.0.0_10` 把「存活且 state 空」的菜单补 `'31'`（不写死 id、幂等、软删不动）+ 回滚件（4 id 复位置空）。
  - 验证：drill 正反向×2 全绿；活库 migrator 应用 10（台账 10 件，自检"state 空 0 个"）；
    授权树这 4 菜单各返回 5 权限位；技能管理 勾选查询→ACL acl_state=1→读回 enabled→还原，闭环通过。
  - 教训：L-59（位掩码列为空致下游过滤出空集合、功能静默不可用）。

- **2026-10-07 BUG-135 收尾（V2.0.0_11）**：补齐 5 个空 `system_id/category_id` 菜单为全站统一 `110/111`（MCP/技能管理/插件管理/智能体中心/知识库）。
  实测对鉴权**惰性**（登录树用 list() 不按 system_id 过滤、getModuleTreeApi 不带 systemId、category_id 后端零引用）。
  回归中一次 chenzhuo 7→8 经排查**非本件所致**，系我闭环测试残留的 `acl_state=0` 授权行（"有行即可见"），已按 id 删除、普通角色 ACL 回到 7 条基线。教训 **L-60**。
  证据 `specs/20261007_user-role-group-model/evidence/T-25_menu-systemid-backfill.txt`；台账 11 件。

- **2026-10-07 BUG-138 修复（升级件 V2.0.0_12）**：角色授权行 `release_sn` 被误写为角色业务 sn（'COMMON'），登录菜单按 `release_sn='role'` 过滤 ⇒ 授权不生效。后端 `saveModuleAcl` 双分支强制 'role' + 前端改传 + V2.0.0_12 归正存量。实测 chenzhuo 菜单 7→10。教训 L-63。台账 **12 件**。

- **2026-10-07 CR-01 合入（R-18 / T-24~T-26，major，三确认 陈卓）**：资源归属可见性 ——
  知识库/技能/MCP 管理页 own-only（超管全量）+ 单对象 403 + 智能体编辑选择器 own∪myGroupspublic（bound 灰显）。
  升级件 **V2.0.0_13**（skill 加 creator + 回填 admin + kbase 'system' 归 admin），台账 **13 件**。
  顺带修 **BUG-139**（`QueryChain.and` 的 `{0}` 占位符不生效），教训 **L-64**。
  验证：admin 全量 / chenzhuo 仅本人；他人资源 403；受控反例（公共技能授权给非本人组 → 从其选择器消失）。
  证据 `evidence/T-24_r18_cr01_result.txt`；代码收口 `9324bf8`。

- **2026-10-07 CR-02 合入（R-19 / T-27~T-28，major，三确认 陈卓）**：技能名称唯一性按创建人隔离 ——
  跨用户同名可上传；同用户同名互斥；overwrite 只覆盖本人。升级件 **V2.0.0_14**
  （DROP CONSTRAINT name_key + UNIQUE(name, coalesce(creator,''))），台账 **14 件**。
  坑：name_key 实为**约束**，DROP INDEX 报错（L-66）；drill 曾因库已被约束版改过而"假通过"，活库首跑才暴露。
  证据 `evidence/T-27_cr02_result.txt`。

- **2026-10-08 合入 `hotfix/ps1-utf8-bom`（安装链修复包，21 提交 / 19 文件）**：
  按用户口令「把他合并到2.0.0里，注意spec 账目层面也要合并」执行，正是该分支 `version.md` 预录的
  〔下次开版本时的动作〕。合并目标 = **v2.0.0 在途线**（当前分支 `feature/user-role-group-model`；
  字面 `v2.0.0` 分支为 HEAD 祖先、已滞后，冻结时自然带上）。
  - **代码面**：19 文件全为安装链/构建配置（docker scripts、Dockerfile.*.multistage、`.gitattributes`、
    `.gitignore`、`web-frontend/pnpm-lock.yaml` 新增、`releases/v1.7.0/sql/V1.7.0_02`）——
    **零 `.java`/`.ts`/`.vue`**，故运行中的应用代码与已部署镜像不受影响，无需重建。
  - **账目面**：`bugs.md` 并集 +11（BUG-141~151）、`lessons.md` 并集 +7（L-67~73）；
    冲突仅 3 个追加型账本文件，人工判定：BUG-115 与 L-32 的差异**均来自我方**（对方 == merge-base）⇒ 保留我方；
    并号后 **bugs 150 条唯一（范围 1~151，84 为历史缺口）/ lessons 73 条无重号无缺口**；表检全绿。
  - **顺手纠我方口径错误**：BUG-130/132/133/134 的版本列曾误填 spec 文档版（v2.4.0/v2.5.0/v2.6.0），
    已规范为发布版 **v2.0.0** 并保留 spec 版溯源；同时把 BUG-130/132/133/134 与本批 135~151
    共 **21 条补登「二、纳入缺陷」**（原表只到 BUG-127）。
  - **未纳入本版的 open 缺陷**（状态=新建，如实留账）：BUG-128（dev 代理冲突）、
    **BUG-129（P2 登录接口回传 password 等敏感列）**、**BUG-131（P2 用户管理端点缺管理员守卫）**。

- **2026-10-08 新 spec 立项并三重确认通过**：`specs/20261008_chat-attachment-understanding`
  （对话附件上传与大模型理解；入口=admin-ui+mobile-ui 两端；图片=真多模态）。
  requirements/plan/tasks 均 **v1.0.0 已确认（陈卓）**，随后 **CR-01（micro）** 合入 ⇒ 三文档 **v1.1.0**。
  施工分支 `feature/chat-attachment-understanding`（基点 = 本版分支）。**T-01 探针已完成**：
  `qwen3.8-max` 带 `image_url` 真实调用 HTTP 200 且读出图内编码（两把可用 key 各验一次）。
- **2026-10-08 运维配置动作（非代码、非迁移件）**：修复 CHAT 死链 —— deepseek 行 402 余额不足、qwen CHAT 行 400 欠费
  ⇒ id=7 换实测可用 key 并设默认、id=5 停用（api_key 原值保留可回退）+ 重启后端；
  **端到端复验**：`POST /api/admin/agent/chat` → HTTP 200 SSE **21 事件、内容含真实回答**（修复前 4 空事件 + 402）。
  证据 `specs/20261008_chat-attachment-understanding/evidence/OPS_chat-default-fix.txt`。
  衍生缺陷 **BUG-152**（`selectActiveByType` = `is_active LIMIT 1` 无 ORDER BY、不看 `is_default`，
  与 `getDefaultConfigByType` 语义不一致 ⇒ 管理页设的默认对话模型对图工作流无效）**已登记不顺手修**；教训 **L-74**。

## CR-03 会话空间隔离（2026-10-09，major，三轮确认）
- 挂接 spec: 20261008_chat-attachment-understanding（v1.5.0）
- 升级件: V2.0.0_16（source 列+回填+索引，见该 spec artifacts.md）
- 任务: T-10~T-12 全勾；事故 L-78 已记

## 部署面决定（2026-10-09，陈卓拍板）
- v2.0.0 **携带移动端**：前端镜像含 /m/ 挂载（docker/.stage/dist-mobile + nginx `location ^~ /m/`），
  mobile-ui 以 --base=/m/ 构建；BUG-154/155 修复后移动端登录与对话可用
- BUG-153（mobile `build` 脚本类型门常红）**不修复**（批准：陈卓）；mobile 出包走 `pnpm exec vite build` 单跑

- **2026-10-10 M3 汇总审计（第一动作，用户口令「把 2.0.0 里程碑走完，完成发布」）**：
  - 前置事实：本地 `v2.0.0` 先 `--ff-only` 对齐 `origin/v2.0.0`（`c423c56`→`39725a3`，远程 42 提交）；随后 `--no-ff` 合入 `hotfix/ollama-model-support`（34 提交，merge `391ffa8`，已 push）
  - 挂接对账（A∪B vs 纳入表）：**一致**，3 个 spec 无漏挂、无空行；9 个历史 spec 无「挂载:」行，**经用户 2026-10-10 裁决「不用管」**
  - 进度重算（事实判据）：**补正 2 处漏勾** —— `chat-attachment-understanding` 原记「1/9」实为 **12/12**；`ollama-model-support` 原记「10/13」实为 **13/13**；**未发现假勾**
  - SQL/配置：`sql/` 正向 **16** / 回滚 **16** 一一配对（原记 14/14）；`config/changes.md` 补落盘（1 处配置变更）
  - CR 对账：4 份 CR（user-role-group-model CR-01/CR-02、chat-attachment CR-01/CR-03）**八动作全勾**，0 未收口
  - 账证初对：本版提交 `Bug:` footer 全部可在 bugs.md 定位（旧号经「编号迁移（2026-10-10）」注记 + 各行「关联」列旧号标记对照，+13 一一对应）
  - **与 bugs.md（唯一权威）对齐 3 处**：BUG-116（本表原记已验证 → 已修复）、BUG-148、BUG-149（本表原记已修复 → 新建）
  - **补登第三批 BUG-174~187**（install-chain 批次原未登记进本表）
  - **硬阻断（1 项）**：本版口径下 **9 条 P0/P1 未达「已验证」**（BUG-116、140、142、145、147、149、161、164、185）。按冻结前置「P0/P1 全部已验证」，本里程碑**暂不具备冻结条件**
  - 处置（用户 2026-10-10 选「并行方案 C」）：本机可验 5 条（116/140/161/164/185）+ Windows 侧 3 条（142/145/147）分头验证；BUG-149 待核实台账状态（lockfile 已入库）
  - M3 交付物：`RELEASE-NOTES.md` / `UPGRADE.md` / `checklist.md` / `config/changes.md` 全部落盘；4 份 md 表格闸 `mdtable_check.py` 退出码 0
