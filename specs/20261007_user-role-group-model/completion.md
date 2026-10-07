# 完成清单 · 20261007_user-role-group-model（用户体系三维度化）

> 版本: v2.0.0（MAJOR，破坏性） | 分支: `feature/user-role-group-model`（基点 `9a4b6c0`）
> 完成日期: 2026-10-07 | 任务总数: **17**，已完成 **17**（T-01~T-17）
> 交付状态: **实现完成**；发布（构建 v2.0.0 镜像 + 迁移活库 + 重启 release 栈 + tag/冻结）**待执行**

## 一、任务完成情况

| 任务 | 状态 | 关键产出 | 证据 |
|---|---|---|---|
| T-01 编译基线/数据门禁/专项备份 | ✅ 完成 | 编译基线、生产数据统计、迁移前全量+组织维度备份 | `evidence/T-01_*`、`backups/pre_v2.0.0_*` |
| T-02 删除组织维度整文件与死代码 VO | ✅ 完成 | 25 个文件删除（含死 VO） | `evidence/T-02_*` |
| T-03 三个部分引用文件摘除组织维度 | ✅ 完成 | `PrivilegeUserServiceImpl/Controller`、`AccountInfoServiceImpl`、`LoginVO`、`GraphServiceImpl` | `evidence/T-03_*`（编译绿 ERROR=0） |
| T-04 三方集成三层后端删除 | ✅ 完成 | 同步/免登/平台配置三层后端删除（30 文件） | `evidence/T-04_*`；T-16 三端点 404 实测 |
| T-05 角色与用户角色数据订正 | ✅ 完成 | `V2.0.0_02` 补角色、`V2.0.0_03` 组织菜单清理（+rollback） | `evidence/T-05_*` |
| T-06 ACL 基线重建 | ✅ 完成 | `V2.0.0_04`（超管全量 21 + 普通 7）+ rollback；三态演练 | `evidence/T-06_*` |
| T-07 菜单与权限位按角色过滤 | ✅ 完成 | `LoginServiceImpl` 唯一入口 + 祖先补全 + 按钮级位过滤；删 session ACL 快照 | `evidence/T-07_*`；T-16 实测 21/7、免重登、按钮位受控 |
| T-08 角色↔菜单授权界面修复 | ✅ 完成 | `assign-menu.vue` 补 `currentRole`；删死实现与 `debugger;` | `evidence/T-08_*`；T-16 接口级落库/撤销实测 |
| T-09 组侧技能/MCP + del_flag 统一 | ✅ 完成 | 组侧 8 文件 + 前端 4 文件；`GroupMcpInfo` 补逻辑删 + 读点补过滤 | `evidence/T-09_*`；T-16 落库/撤销实测 |
| T-10 预设问题接口角色校验 | ✅ 完成 | `AdminRoleGuard` + 两写接口 403 | `evidence/T-10_*`；T-16 实测 403/200 |
| T-11 admin-ui 页面删除 + 引用解除 | ✅ 完成 | 删 20 文件、改 7 文件；typecheck 207<基线 211、构建通过 | `evidence/T-11_*`；T-16 UI 导航实测 |
| T-12 mobile-ui 移除 SSO | ✅ 完成 | 删 3 文件、重写启动流程 | `evidence/T-12_*`；T-16 捕获 thirdLogin=0 |
| T-13 用户表单三维度化 | ✅ 完成 | DTO+事务化建号/更新、前端角色/组多选；**修 BUG-123** | `evidence/T-13_*`；T-16 建号/改角色/默认角色实测 |
| T-14 升级件 DDL + 演练 | ✅ 完成 | `V2.0.0_01`（+自包含 rollback）；同批实体/XML 清理 | `evidence/T-14_*`（drill 正反向 + 洁净库重放）；T-16 启动无 SQL 报错 |
| T-15 菜单行删除件 | ✅ 完成 | 两份基线各清 10 行 + `V2.0.0_05`（+rollback）；**修 BUG-127** | `evidence/T-15_*`；T-16 洁净库终态实测 |
| T-16 部署与端到端回归 | ✅ 完成 | 隔离栈真实升级序列 + 14 项断言全绿 | `evidence/T-16_result.txt`、`T-16_ui-nav-common-user.jpg` |
| T-17 收口 | ✅ 完成 | completion.md / artifacts.md / 台账翻账 / MILESTONE 同步 | 本文件 + `artifacts.md` |

## 二、执行中的偏差与处置（不静默，逐条留痕）

| # | 偏差 | 处置 | 留痕 |
|---|---|---|---|
| 1 | **运行期/HTTP 断言无法在 Implement 各任务内完成**（运行镜像是 v1.6.0-dev 旧代码） | 各任务"代码面完成 + 运行期断言挂 T-16"，**暂不勾选**；T-16 统一收口后再勾 | T-04/07/08/09/10/12/13/14/15 各自 evidence 的「延期至 T-16」段 |
| 2 | T-13 建号被 `tbl_privilege_user.company_id/dept_id` **NOT NULL** 拦下 | 锁定顺序依赖 **T-14 → T-16**；在 tasks.md T-14 下追加 Implement 期注记（不改验收口径） | T-13 evidence §5、tasks.md T-14 注记 |
| 3 | T-07「未授权按钮为 false」在基线 `acl_state` 全 31 时**无区分度** | 设计受控配方（改 acl_state 为 '1'/'3'）；T-16 实测 '31'→5、'1'→1、'3'→2、逐位 enabled 正确 | T-07 evidence §5、T-16 evidence §3④ |
| 4 | 用户裁定「不改已确认文档，偏差记录在 evidence」 | 全部偏差只落 evidence / tasks 注记 / 台账，未回改 requirements/plan（未触发重确认） | 各 evidence；本表 |
| 5 | T-16 按用户选择在**隔离栈**验证（不动活库/发布栈） | 活库迁移与镜像发布**留待发版步骤**；BL-31 状态如实标「待发版冻结」而非「已冻结」 | T-16 evidence §5、backlog BL-31 |
| 6 | 首轮全新库重放误报「all_data 0 error」（grep 模式不匹配 psql 输出） | 追加**勘误段**、修 BUG-127、记 **L-50** | `T-14_result.txt` 勘误段、`lessons.md` L-50 |
| 7 | T-13 探针用 `lower(sn)='common'` 冒充旧代码行为，险些得出反向结论 | 改正探针并记 **L-48**（复刻验证须与源码同构） | T-13 evidence §7、`lessons.md` L-48 |

## 三、未完成项（原因分类 + 去向）

| 项 | 原因分类 | 去向 |
|---|---|---|
| 活库 `phoenix` 迁移 + v2.0.0 镜像构建与发布栈重启 | **环境/发布流程**（需用户发版口令；本 spec 只做到隔离栈验证） | 发版步骤（`releases/v2.0.0/`，M2 冻结 → M3 汇总 → tag） |
| `releases/v2.0.0/UPGRADE.md`、`RELEASE-NOTES.md`、`checklist.md`、`config/changes.md` | **流程归属**（M3 汇总件，由里程碑审计生成） | M3（本 spec 已提供 `sql/` 10 件与升级序依据） |
| BUG-119（列 varchar vs 实体 Integer）、BUG-120（死代码 + 错误位运算，已无调用点）、BUG-125（BaseModel 未声明逻辑删）、BUG-126（预设问题 GET 枚举）、BUG-128（dev 代理 `/auth` 冲突）、BUG-129（登录信息接口外泄 password） | **范围外**（不属 R-01~R-11；发现即登记，未擅自扩范围） | `bugs.md` 留账；建议随后续 spec（BUG-125 建议另立） |
| 界面点击级验证：角色授权弹窗点选、组管理页「分配技能/MCP」点选 | **验证手段**（T-16 已做接口级落库/撤销；浏览器点选路径未走） | 发版后人工回归或下一轮 UI 自动化 |

## 四、验收自检

- 三重确认门：requirements v2.0.0 / plan v1.1.0 / tasks v1.0.0 均 `已确认`（陈卓 2026-10-07）✅
- 表格闸：`mdtable_check` 对 `specs/_project/*.md` 与本 spec 文档全绿 ✅
- 证据可复核：每条断言均指向 `evidence/` 下真实输出文件（含失败与勘误）✅
- 提交规范：所有提交带 `Spec: specs/20261007_user-role-group-model vX.Y.Z` + `Task: T-xx` footer ✅
