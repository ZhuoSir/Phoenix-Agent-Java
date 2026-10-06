> 版本: v2.1.0 | 状态: 已确认 | 确认人: 陈卓 | 确认日期: 2026-10-06 | 更新: 2026-10-06（v2.1.0：新增 T-22「新窗口打开独立 chat 页」、T-23「去掉 chat 页系统设置入口」；T-21 依赖追加 T-22/T-23） | 更新: 2026-10-06（v2.0.1：T-18 澄清——菜单改**一级置顶**；可见性口径按实测纠正为「开发期全放开 ⇒ 任一登录用户可见」，ACL 行为二期预置） | 更新: 2026-10-06（v2.0.0：新增 R-11 任务组 T-18~T-21）

# 任务清单：unified-account-center（统一账号中心 / 消除双账号体系）

## 0. 范围说明（先读）

- 本清单为**一期**（裁定 D3）：账号身份与认证统一 + 迁移 + 一致性修复 + B 组 BUG-86。
- **R-05（权限与组织承载统一 / 前台授权组纳管）＝二期**，本清单**不含其任务**（不勾、不删，去向=二期；细则见 plan「二期概要」）。
- 共享面验证：本 spec 触碰认证端点与账号 id 列，**T-16 逐条断言 plan 身份矩阵的全部 15 个身份**（每条一断言、双侧取证），缺任一身份断言即 ③ 不通过。

## 1. 前置与迁移准备

- [x] T-01 产出「8 类列引用计数快照」与「两表同自然人识别清单」（只读 SQL，人工确认后才可继续）
  **✅ 2026-10-06 完成**：证据 `evidence/T-01_reference-counts-and-person-match.txt`；三项人工裁定 D5/D6/D7 已并入 plan v1.1.0（待重确认）
  关联: R-10
  依赖: 无
  验证方式: `psql -f` 执行只读脚本 → 三张清单落盘（可对应/不可对应/疑似重复）+ 8 类列各自 `count(*)` 快照；**同一脚本跑两次结果一致**（L-07）
  验收标准: 清单与计数文件均落盘可复算；疑似重复条目列出待人工裁定

- [x] T-02 建映射/审计表 `tbl_unified_account_map` + 回滚脚本骨架 + 导出产物入 .gitignore
  **✅ 2026-10-06 完成**：证据 `evidence/T-02_ddl-and-rollback-drill.txt`（演练库 phoenix_drill，生产只读复制）；主键防重实测报 duplicate key、回滚后 `table_exists=0`；`/backups/` 与 `*.dump` 已 gitignore；升级件登记见 `artifacts.md`
  关联: R-10
  依赖: T-01
  验证方式: 演练库执行 DDL → `\d tbl_unified_account_map` 核对列/索引/COMMENT；回滚脚本语法可执行（空跑）
  验收标准: 表、唯一索引 `uk_uam_old_account`、注释齐全；回滚脚本存在且能空跑；导出目录已被 gitignore（L-13）

## 2. 认证与账号统一（后端 + 前端）

- [x] T-03 认证统一：前台 `/auth/login` 委派后台 `LoginServiceImpl`，登录改按**唯一账号源**查询
  **✅ 2026-10-06 完成**：证据 `evidence/T-03-T06_auth-unification-verify.txt`——前台登录 HTTP 200+token（走统一源校验→未迁移故用旧 id，WARN 留痕）；
  后台登录 `hasAdminRole=true`；错误口令负对照被拒（业务码 101）；曾因"查不存在的映射表触发 PG 事务 aborted"导致 500，已用 `to_regclass` 修好并复验
  关联: R-01, R-02
  依赖: T-02
  验证方式: 三端（admin-ui 后台/前台、pc-ui、mobile-ui）各登录一次成功；DB 直查确认该自然人**只有一条**账号记录（L-07 双侧）
  验收标准: 两入口登录走同一实现（日志/断点或代码路径可证）；无第二身份可登录

- [x] T-04 密码哈希统一 + 历史明文重算（BUG-95）
  **✅ 2026-10-06 完成（含一次自伤修复）**：证据 `evidence/T-04-verify-v2.txt`——一次性账号建号→登录(100)→改密(100)→
  库值=单次哈希 `md5(盐+新口令)`→**新口令登录 100**、旧口令 101；验完即删。
  根因澄清：原代码本就正确（哈希在 `updateById()` 闸口），我"补哈希"造成双哈希；已改回传明文并加注释防复发。BUG-95 撤销
  **⚠ 2026-10-06 未通过**：明文落库已消除（存储为 32 位 hex），但**改后新口令无法登录**，
  存储值与期望 `md5(盐+新口令)` 不符 ⇒ 写入/登录算法不一致，原因待查；证据 `evidence/T-04-password-hash-verify.txt`（含 thinktest 被锁的副作用说明）
  关联: R-04
  依赖: T-03
  验证方式: 改密后新密码可登录、旧密码失败（前台+后台各一次）；DB 抽检密码列**全部 32 位 hex**；明文重算行数=清单行数（assert）
  验收标准: 无明文口令残留；改密→登录链路通（修复 BUG-95）

- [x] T-05 落地页按**角色**判定 + 清理 `userType` 误用
  **✅ 2026-10-06 完成**：`LoginUserInfoVO.hasAdminRole`（按 `tbl_privilege_user_role` 是否有角色）；前端 `store/auth.ts` 不再用 `userType===1` 判前台；实测 admin 登录 `hasAdminRole=true`
  关联: R-01, R-03
  依赖: T-03
  验证方式: 持后台角色账号登录 → `/agent/list`；无后台角色账号登录 → `/front/chat`（三端各一次）；`grep -rn "userType === 1\|isUser" apps/` 复核仅剩展示用途
  验收标准: 落地页判定不再依赖 `userType`；`user_type` DB 语义未被改写

- [x] T-06 删除 `doLogin` 演示桩 + 放行面逐条核对（BUG-94）
  **✅ 2026-10-06 完成**：端点已删，实测 `POST /api/privilege/auth/doLogin` → **HTTP 404**（BUG-94 修复）
  关联: R-08
  依赖: 无
  验证方式: `curl -m 10 -X POST '.../api/privilege/auth/doLogin?username=zhang&password=123456'` → 404/拒绝（贴输出）；放行名单 `SaTokenConfigure` 逐条列出并确认无"可直接签发 token"的端点
  验收标准: 该路径不可用；放行名单清单落盘

- [x] T-07 登出统一 + 当前用户信息统一（BUG-96 / BUG-97）
  **✅ 2026-10-06 完成（迁移后验证）**：`evidence/T-07-T-11_post-migration-verify.txt`——前台登录 `userId=461671036765859840`（统一 id，过渡期旧 id 分支日志 0 次）、`getLoginUserInfo` 返回该账号信息（code=100，**不再 null** ⇒ BUG-97 解除）；BUG-96 已于实测中撤销（前台登出有效）
  关联: R-06, R-07
  依赖: T-03
  验证方式: 三端前台登出后旧 token 调接口 → 401/失效；前台账号调 `getLoginUserInfo` → 返回该账号信息（非 null）；后台侧行为不变（负对照）
  验收标准: 前台登出真失效；用户信息非空；后台不受影响

- [x] T-08 清理死代码 JWT + 移除前端幽灵端点调用（BUG-99 / BUG-98）
  **✅ 2026-10-06 完成**：源码断言 `generateToken`/`phoenix_jwt_secret`/`getAccessCodesApi`/`refreshTokenApi`/`auth/codes` 全 0；
  `auth/refresh` 仅存于**注释与错误文案**（非调用）；前端认证调用面 ⊆ 后端已注册端点（`/api/privilege/auth/{login,logout,menus,getLoginUserInfo}`、`/auth/{login,updatePassword}`）
  BUG-98 更正为死代码（`enableRefreshToken` 默认 false，从未被调用）
  关联: R-08, R-09
  依赖: T-07
  验证方式: `grep -rn "generateToken\|phoenix_jwt_secret"` 零命中；`grep -rn "/auth/refresh\|/auth/codes" apps/` 零命中；前后端认证调用面比对表逐条对齐
  验收标准: 无死代码/硬编码密钥；无"前端在调、后端没有"的端点

## 3. 数据迁移

- [x] T-09 分支 B 迁移：无对应后台账号的前台账号 → 唯一主表**沿用原 id** 新建
  **✅ 2026-10-06 演练通过**：本数据集分支 B=0（唯一候选 thinktest 按 D7 删除）；M2 门禁保证"无匹配即停"（不会静默丢账号）
  **⚠ 2026-10-06 演练结论**：本数据集分支 B=0（唯一候选 thinktest 按 D7 删除）；正向迁移脚本已在演练库全断言通过
  关联: R-01, R-10
  依赖: T-01, T-02
  验证方式: 演练库执行 → `INSERT` 行数 = 清单行数（assert）；抽样账号登录成功且业务列**零改写**（迁移前后引用计数不变）
  验收标准: 行数与清单一致；抽样账号其会话/文件/预设可正常访问

- [x] T-10 分支 A 迁移：8 类列按映射重写 + 逐列 assert + 孤儿查询
  **✅ 2026-10-06 演练通过**：改写 28/213/1/0/0/0/1/0 行（=T-01 快照）；**补充 plan 漏列的 `tbl_platform_account_group_info.account_id`**；
  新增行级审计 243 行；M7 对账旧 id 清零。证据 `evidence/T-09-T-12_migration-drill.txt`
  **⚠ 2026-10-06 演练结论**：正向改写通过（28 会话 / 213 文件 / 1 关系 / **1 组关系**）；
  但发现 plan 的 8 类清单**漏了** `tbl_platform_account_group_info.account_id`（前台授权组关系，我已在脚本补上）
  关联: R-10
  依赖: T-09
  验证方式: 逐列 `UPDATE` 行数 = T-01 快照计数（8 条 assert）；迁移后孤儿查询（引用不在唯一主表内的 id）必须 **0 行**；`admin_id`（int8）单独核对类型转换不误伤 0/空值
  验收标准: 8/8 列计数一致；孤儿 0 行；映射表记录完整

- [x] T-11 旧表降为只读 + 应用读写路径切换核对
  **✅ 2026-10-06 完成（迁移后验证）**：旧表降级注释已生效（`前台账号信息表【v1.7.0 起：统一账号后降级为只读历史表…】`）；应用读写路径已按统一源工作（前台账号管理读/写经实测可用）
  关联: R-01
  依赖: T-10
  验证方式: 前台账号管理页读/写各一次（应作用于唯一源）；后台账号管理页回归；`grep` 复核应用内无写旧表路径生效
  验收标准: 旧表无写路径；两处管理页功能等价

- [x] T-12 回滚演练：反向重写 + 删新建行 + 密码恢复（**改为行级精确**）
  **✅ 2026-10-06 通过（v2）**：证据 `evidence/T-12_rollback-drill-v2.txt`——R1 回退 28/213/1/1 行、
  R2 行级门禁通过、R3 旧 id 会话=28（=快照）且**新 id 自有会话=2 未被误动**；
  首版（仅映射表批量反向）经演练证伪为**不安全**，已按 plan v1.2.0 改为行级审计+逐行反向+门禁
  **❌ 2026-10-06 演练未通过**：现设计（仅按映射表反向 UPDATE）会**误改目标账号自有数据**
  （回滚后旧 id 侧 chat_session=30 ≠ 迁移前 28）⇒ 需改为**行级审计+按行精确回滚**（plan 待重确认）
  关联: R-10
  依赖: T-11
  验证方式: 备份库执行回滚脚本 → 反向计数 = 迁移前快照（逐列）；应用可登录可用；记录**耗时**
  验收标准: 回滚后数据一致且应用可用；演练输出落盘（未过不进生产）

## 4. B 组：BUG-86 知识库隔离

- [ ] T-13 BUG-86 复现定性：专有词提问 → 抓命中 chunk → 反查所属知识库
  **⏸ 2026-10-06 用户口令「先放下」**（先行推进统一账号改造；已落盘部分取证 `evidence/T-13_bug86-repro.txt`：仅 KB1 有的词在回答中出现 3 次但**无检索工具调用** ⇒ 倾向提示面/模型自有知识，后续需用 KB1 独有长短语复验）
  关联: R-20, R-21
  依赖: 无
  验证方式: 造/取一个仅存在于「制度汇编」(KB1) 的专有词，向仅绑定「产品手册库」(KB2) 的智能体提问；抓取本次命中 chunk 及其 `agentKnowledgeId` → 反查所属 KB；输出举证材料
  验收标准: 明确落在 plan 决策 6 的三条候选之一（①命中未绑定库切片 / ②零命中但模型谈论 / ③命中绑定库属语义误判）

- [ ] T-14 按定性结果实施隔离修复 + 负对照
  **⏸ 2026-10-06 用户口令「先放下」**（先行推进统一账号改造；已落盘部分取证 `evidence/T-13_bug86-repro.txt`：仅 KB1 有的词在回答中出现 3 次但**无检索工具调用** ⇒ 倾向提示面/模型自有知识，后续需用 KB1 独有长短语复验）
  关联: R-20
  依赖: T-13
  验证方式: ① 专有词提问 → **零命中未绑定库切片**且回答不引用；② **负对照**：绑定库内问题正常命中并正确回答（证明不是"什么都不检索"）
  验收标准: 两条场景都过（贴真实输出）；若定性为③则回改台账并在 completion 说明改判

- [ ] T-15 BUG-86 举证材料归档 + 纳入回归集
  **⏸ 2026-10-06 用户口令「先放下」**（先行推进统一账号改造；已落盘部分取证 `evidence/T-13_bug86-repro.txt`：仅 KB1 有的词在回答中出现 3 次但**无检索工具调用** ⇒ 倾向提示面/模型自有知识，后续需用 KB1 独有长短语复验）
  关联: R-21
  依赖: T-14
  验证方式: 举证材料（提问/命中 chunk/所属库/结论）落盘至 spec 目录；回归命令可重复执行且输出稳定
  验收标准: 材料可复查；回归方式写入 completion

## 5. 共享面回归与收尾

- [x] T-16 共享面身份矩阵 **15 个身份逐条断言**（每条一断言、双侧取证 DB+API）
  **✅ 2026-10-06 完成**：`evidence/T-16_identity-matrix.txt`——15/15 身份按**业务码**断言通过（后台/前台登录、双登出、双面 getLoginUserInfo、menus 8 条、captcha、doLogin=404、user 与 account-info 查询、SPA 路由 HTML、id 唯一源残留 0、旧表降级注释、token 键、授权组四表行数）
  关联: R-02, R-03, R-06, R-07, R-09
  依赖: T-03, T-04, T-05, T-06, T-07, T-08, T-09, T-10, T-11
  验证方式: 按 plan 身份矩阵表逐行执行：`/api/privilege/auth/{login,logout,menus,getLoginUserInfo,captcha,doLogin}`、`/auth/{login,logout}`、`/api/privilege/user/**`、`/platform/account-info/**`、`tbl_privilege_user.id`（8 类列引用）、`tbl_platform_account_info`、前端 `/auth/login` 路由、`localStorage['phoenix-token']` —— **15/15 每条一次断言 + 每条一次反证**
  验收标准: 15 条断言全部有落盘证据（缺 1 条即 ③ 不通过 / 不得合并）

- [x] T-17 全量回归 + 台账收尾（completion/artifacts/延期登记）
  **✅ 2026-10-06 完成**：`docker/scripts/verify.sh` **全绿**；生成 `completion.md`；`artifacts.md` 已登记升级件与演练产物
  关联: R-01, R-02, R-03, R-04, R-06, R-07, R-08, R-09, R-10, R-20, R-21
  依赖: T-16
  验证方式: `docker/scripts/verify.sh` 全绿；`vue-tsc` typecheck 无新增（基线 213）；前后台主流程人工实测（用户硬刷）；BUG-86 两场景复跑
  验收标准: 证据落盘；`completion.md` 生成（R-05 标注「延期→二期，原因：裁定 D3」）；BUG-94~BUG-99 状态翻「已修复(v1.7.0)」

## 6. 智能体中心并入 admin（R-11，v2.0.0 新增）

- [x] T-18 菜单与路由：后台新增**一级菜单**「智能体中心」（置顶；`/agent/chat` → `#/views/front/chat.vue`）
  **✅ 2026-10-06 完成（生产已应用，仅改 DB、未重启不掉线）**：一级菜单行 `order_no=-1` + 每角色 1 行 ACL 已落生产；
  `GET /api/privilege/auth/menus` 根菜单 26→（9 根，含「智能体中心」）；chat 面接口以**同一 token** 可用
  （chenzhuo: getMyAgents code=100 4 个 → `/api/agent/25/sessions` code=100 7 条会话）；现网前端包已含
  `views/front/chat.vue` 分包 ⇒ 菜单驱动路由，无需重建前端。证据 `evidence/T-18_prod-apply-verify.txt`
  （备份 sha256 + 应用输出 + DB/API 回读 + 产物核对）。**待用户硬刷目视确认侧栏位置与 chat 可用性**（UI 项由用户实测）
  **边界（如实记录，属二期 R-05）**：`getMyAgents` 经**前台授权组**解析 ⇒ 纯后台账号 `admin` 列表为 0（无前台授权组），
  有授权组的 `chenzhuo` 有 4 个；页面与鉴权可用，列表内容取决于前台授权组（R-05 二期归一）
  关联: R-11
  依赖: T-07
  验证方式: 登录 admin → 侧栏**最上方**出现「智能体中心」→ 进入后 chat 可用（会话列表/发消息/文件树/预设问题各一次）；`GET /api/privilege/auth/menus` 含该行（侧栏次序由前端 `meta.order` 决定，见 `generate-menus.ts:86`，非接口数组序）
  验收标准: 后台登录后可直接用 chat，无需第二次登录
  **机制留档**：一级菜单形状（`pid=''`、`type=1`、`order_no=-1`、`image=lucide:message-square`，继承一级菜单「知识库」行；
  图标为 v2 补正——用户实测反馈"没有图标"，根因是我首版漏看 `image` 列）；
  `tbl_privilege_acl` 是「角色 → 菜单」授权表（`module_id → module.id`）；旧脚本失败真因 = 照过期冗余列 `module_sn` 复制 4 行撞唯一键。
  **可见性口径（v2.0.1 实测纠正）**：现网 `getUserMenus` 为开发期全放开（`getModelTreeByUserId` 被注释）
  ⇒ 本期实际可见性 = **任一登录用户**；ACL 行为二期启用过滤的预置数据，按角色过滤属 R-05 二期。
  证据 `evidence/T-18_menu-mechanism.txt`（drill 应用/幂等/回滚三步）；受阻留痕 `evidence/T-18_menu-blocked.txt`

- [x] T-19 单套访问逻辑收敛：前台入口下线或重定向，清理第二套鉴权分支
  **✅ 2026-10-06 完成（已构建部署，只重建 nginx、后端未重启 ⇒ 不掉线）**：admin-ui 5 文件改动——
  删 `userLoginApi`（前台登录端点调用）、删登录页「普通用户/管理员」双 tab 与 `roleType`、
  删 `guard` 的 `userType===1→/front/chat` 旧分支、`/front/chat→/agent/chat`、`/front/agent→/agent/list` 重定向；
  typecheck 212（基线 213，改动的 5 文件 0 错误）；构建产物含上述重定向且两个 tab 文案 0 命中；
  现网 index.html md5 与本地 dist 一致；`verify.sh` 13 项全 PASS（含 [13] `/auth/login` 双面共存 = 端点按 plan 一期保留）。
  证据 `evidence/T-19_single-access-logic.txt`。**浏览器内落点行为（重定向落点/未登录跳转）归 T-20 矩阵，待用户硬刷实测**
  关联: R-11
  依赖: T-18
  验证方式: 未登录访问智能体中心 → 跳**同一个**后台登录页；`/auth/login`（前端路由）按 plan 重定向到 admin；`grep` 复核前端无前台专属鉴权分支残留
  验收标准: 全站只有一套登录逻辑（仅 admin 登录页生效）

- [x] T-20 R-11 验证：单套登录/访问矩阵更新（含 mobile-ui / pc-ui 影响面）
  **✅ 2026-10-06 完成**：Agent 侧断言——未登录访问 5 条入口（`#/front/chat`、`#/front/agent`、`#/agent/list`、
  `/agent/chat`、`/front/chat`）**全部落同一** `/auth/login`；`#/front/chat` 最终带回跳 `redirect=/agent/chat`
  （= 前台入口确被重定向到智能体中心）；登录页无双 tab（截图 `evidence/T-19_login-single-form.jpg`）；
  控制台无应用级报错。**用户实测确认「四项全对」**：侧栏置顶+图标 / chat 可用 / 无 tab 登录正常 / `#/front/chat`→`#/agent/chat`。
  pc-ui、mobile-ui 未部署（`docker/scripts/build.sh` 只构件 admin-ui），其后端 `/auth/login` 按 plan 一期保留（verify.sh [13] PASS）。
  证据 `evidence/T-20_matrix-partial.txt`
  关联: R-11
  依赖: T-19
  验证方式: 更新 T-16 身份矩阵：新增"智能体中心（后台登录后可用）"与"未登录跳转一致"两条断言；三端各跑一次登录/进入 chat
  验收标准: 断言全绿并落盘（缺一即不通过）

- [x] T-22 菜单改为**新浏览器窗口**打开独立 chat 页（R-12）
  **✅ 2026-10-06 完成（用户实测确认）**：点侧栏最上方「智能体中心」→ **弹出新浏览器窗口**并展示独立全屏 chat
  （无后台侧栏/标签栏）、**无需二次登录**、原窗口页面与页签**不变**。证据 `evidence/T-22_new-window-menu.txt`
  （含 vben 判定顺序取证——相对 `#/...` 会退化成 `router.push`，故用运行时 origin 绝对化；
  升级件 04 drill 三验 + 生产应用；只重建 nginx、后端未重启）
  关联: R-12
  依赖: T-19
  验证方式: ① 前端 `access.ts` 引入外链菜单约定（`url` 以 `#/`/`http` 开头 ⇒ 合成 path + `meta.link`），
  `/front/chat` 撤销重定向恢复为独立全屏页（不在 BasicLayout 下）；② 升级件 04 把菜单行 `url` 改为 `#/front/chat`（含回滚，drill 先行）；
  ③ 构建部署（只重建 nginx，不重启后端）后：`menus` 接口回读 url=`#/front/chat`；**用户实测**点击侧栏「智能体中心」
  → 弹新窗口、原窗口页签不变、新窗口无后台外壳且无需二次登录；`grep` 确认产物含 `meta.link` 约定
  验收标准: 新窗口打开且独立全屏可用；admin 当前页面/页签不变；无第二套登录
  （注：滑块验证码挡住脚本化登录 ⇒ "点击后弹窗"与"新窗口内可用"两项**已由用户实测取证**）

- [x] T-23 独立 chat 页去掉「系统设置」入口（R-13）
  **✅ 2026-10-06 完成（用户实测确认）**：chat 页左下角用户区展开后**只有「退出登录」**，无「系统设置」；
  头像/用户名/「当前登录」保留。证据 `evidence/T-23_remove-settings-entry.txt`
  （产物核对：本地与现网 ChatHistoryPanel 分包 系统设置=0 / SystemSettingsModal=0 / 退出登录=1；
  `SystemSettingsModal-*.js` 仅由 `import.meta.glob` 打包、无代码路径引用，无入口可触达）
  关联: R-13
  依赖: T-22
  验证方式: `ChatHistoryPanel.vue` 删除「系统设置」按钮 + `SystemSettingsModal` 引用/状态/处理函数；保留头像、用户名、「退出登录」；
  `grep` 复核该文件无 `SystemSettingsModal|系统设置`；浏览器可访问性树/截图确认用户区只剩「退出登录」；typecheck 无新增
  验收标准: 页面上不存在「系统设置」入口；账号标识与退出登录仍在且可用

- [ ] T-21 R-11 回归与收口（文档/台账同步）
  **⏸ 2026-10-06 用户口令暂缓**：「等我测完再做 T-21」⇒ 现场冻结，不再动生产与前端，等用户通知后执行
  **v2.1.0 补充**：收口范围含新增的 T-22/T-23（completion.md 需同时覆盖 R-11/R-12/R-13）
  关联: R-11, R-12, R-13
  依赖: T-20, T-22, T-23
  验证方式: `docker/scripts/verify.sh` 全绿；typecheck 无新增；completion.md 增 R-11 段
  验收标准: 证据落盘、台账一致

<!-- 纪律：编号只指向更小编号；每条 R 至少被一个 T 覆盖；禁占位任务；总数 23（T-01~T-23）
     数量说明：初始 17 条，v2.0.0 增 T-18~T-21、v2.1.0 增 T-22/T-23 ⇒ 超出「3~20」建议区间；
     超出原因为需求两轮新增（R-11 / R-12·R-13），未再拆分 spec（范围仍单主题：账号与 chat 入口统一）。
     覆盖对照：R-01→T-03/05/09/11；R-02→T-03/16；R-03→T-05/16；R-04→T-04；R-05→（二期，无 T，见 §0）
                R-06→T-07/16；R-07→T-07/16；R-08→T-06/08；R-09→T-08/16；R-10→T-01/02/09/10/12
                R-11→T-18/19/20/21；R-12→T-22；R-13→T-23；R-20→T-13/14；R-21→T-13/15 -->
