# 里程碑 v1.2.0
> 状态: 进行中 | 目标日期: 待定 | 负责人: 陈卓 | 立项: 2026-09-27

**版本号说明**：项目历史分支线已到 `phoenix-1.1.1-release` / `phoenix-1.1.2-dev`（从未打过 tag、无项目级 CHANGELOG）；
本次三批需求均为**向下兼容的新功能**（技能管理 / 对话智能体动态化 / AI 生成+默认模型），按 SemVer 取 MINOR = **v1.2.0**（用户 2026-09-27 确认）。
**序号约束**：本文件登记的 SQL 序号与版本号一经分配不再变动（新增插队用补号如 `_03a`）。

## 纳入需求
| spec | spec版本 | 状态 | SQL件 | 配置项 | 备注 |
|---|---|---|---|---|---|
| specs/20260927_agent-skill-management | v1.0.0 | 已合并 | 2 | 1 | 技能管理（上传/发布/授权/绑定）+ 前台技能区与显式执行；已随 ed2e3f9 合入 main |
| specs/20260927_dynamic-agent-types | v1.0.0 | 已合并 | 2 | 4 | 对话智能体运行配置 + 三工具（知识库/取数/深度分析）+ 类型去标签 + 列表隐藏存量；已随 f2e106a 合入 main |
| specs/20260927_agent-config-ai-generate | v1.0.0 | 已合并(main, 09-30, c90071d) | 1 | 1 | AI 生成描述/Prompt（Markdown）+ 模型「启用集合 / 默认模型」；13/13 任务全勾（09-30 走查+回归完成） |

## 纳入缺陷（M1 挂接）
| 编号 | 标题 | 严重度 | 修复落点 | 状态 |
|---|---|---|---|---|
| BUG-01 | 基线 `all_schema.sql` 缺 5 个序列 → 全新环境导入必失败 | P1 | `sql/all_schema.sql`；commit 34877da（已合并 main c90071d） | 已修复(v1.2.0) |
| BUG-06 | 技能无管理入口且技能池全局共享 | P3 | Spec: agent-skill-management | 已修复(v1.2.0) |
| BUG-07 | shell 与远程文件系统硬绑互斥、无开关 | P3 | Spec: dynamic-agent-types T-05 | 已修复(v1.2.0) |
| BUG-09 | 前台与 harness 智能体无对话通道 | P2 | Spec: agent-skill-management T-11/T-14 | 已修复(v1.2.0) |
| BUG-10 | 新建智能体 type 为空、harness 无法后台创建 | P2 | Spec: dynamic-agent-types T-04/T-14 | 已修复(v1.2.0) |
| BUG-12 | 只读 SQL 被 SqlSecurityValidator 误杀 | P2 | Spec: dynamic-agent-types T-08 | 已修复(v1.2.0) |
| BUG-13 | EMBEDDING 模型测试恒 404 | P2 | 模型配置值修正（见 config/changes.md 待登记） | 已修复(v1.2.0) |
| BUG-15 | 智能体列表关键字搜索 500 | P2 | commit 00eb0ff（已在 main） | 已修复(v1.2.0) |
| BUG-16 | 三张向量表缺主键 → ON CONFLICT 必失败 | P2 | commit c0fe1b9（已在 main） | 已修复(v1.2.0) |
| BUG-17 | 图链路在非 HTTP 调用方取登录态抛异常 | P2 | commit e74e7ec（已在 main） | 已修复(v1.2.0) |
| BUG-19 | harness 对话入参缺失返回 500 | P3 | commit dc9b333（已在 main） | 已修复(v1.2.0) |
| BUG-20 | 启用模型会把同类型其他模型一并启用 | P1 | Spec: agent-config-ai-generate T-02；commit b8f728a（已合并 main c90071d）；用户走查确认(09-30) | 已验证(v1.2.0) |
| BUG-22 | AI 生成「描述」回吐整段 JSON | P2 | commit edd9ad9（已合并 main c90071d）；用户复测(09-30) | 已验证(v1.2.0) |
| BUG-23 | 生成超时 60s 切断已成功的调用 | P2 | commit edd9ad9（已合并 main c90071d）；用户复测(09-30) | 已验证(v1.2.0) |
| BUG-26 | 技能上传前端未带 multipart 头 → HTTP 415 | P3 | Spec: agent-skill-management（`api/core/skill.ts`） | 已修复(v1.2.0) |
| BUG-27 | 技能 ZIP 校验报错信息误导（真实规则是「条目须有根目录」） | P3 | Spec: agent-skill-management（新增 `SkillZipSanitizer`） | 已修复(v1.2.0) |
| BUG-28 | `ReturnVo.ok(String)` 命中 msg 重载 → data 丢失 | P3 | Spec: agent-skill-management（改两参调用） | 已修复(v1.2.0) |
| BUG-29 | 关联表 `agent_id` varchar 与 bigint 比较报错（代码侧已绕过） | P3 | Spec: agent-skill-management（`String.valueOf`；列类型根因见 BL-07） | 已修复(v1.2.0) |
| BUG-30 | AI 生成走全局 30s 超时（实测 45~90s）前端掐断 | P2 | `api/core/agentProfile.ts`（timeout 120s，commit c916cb4）；用户复测(09-30) | 已验证(v1.2.0) |

未纳入本版本（保持 `新建`）：BUG-02/03/04/05/08/11/14/18/21/24/25 —— 其中 BUG-04 的「不修复」与 BUG-18 的修复口径需用户决策。

## 升级项（对外口径，M3 时从各 spec 的 R 条款聚合）
- **新增**：技能管理（ZIP 上传自动识别、发布/下线/删除、按组授权、智能体绑定、前台技能区与显式执行）；对话智能体运行配置（对话模型/知识库检索参数/数据库取数/数据库深度分析/计划模式/记忆/文件系统策略）；AI 生成智能体描述与 Markdown 提示词；模型管理「默认模型」与多启用集合
- **变更**：智能体列表移除四个类型标签、且仅显示平台内创建的智能体（存量 5 个自注册智能体仍可运行，但不再出现在列表）；模型「启用」不再互斥、未显式选择模型时加载该类型**默认**模型；智能体提示词改为 Markdown 编辑（存储与下发仍为原文）；后台新建智能体固定为「对话智能体」
- **修复**：见上表 19 条（技能管理 6 条 / 动态智能体 7 条 / 配置 1 条 / AI 生成与模型默认 5 条）

## 汇总进度（M3/M4 勾选）
- [ ] 全部 spec 已合并（M2 冻结前置）—— 当前差 `agent-config-ai-generate`（8/13，仅剩界面人工走查）
- [ ] SQL 汇总编号 + rollback 配对（建议顺序：01 技能 → 02 菜单 → 03 运行配置 → 04 知识库参数 → 05 模型默认，与开发期序号一致、依赖顺序天然满足）
- [ ] 配置汇总 `config/changes.md`（含 BUG-13 的 EMBEDDING 配置值变更）
- [ ] `RELEASE-NOTES.md`
- [ ] `UPGRADE.md` + 回滚步骤
- [ ] 演练环境走通 UPGRADE.md
- [ ] `checklist.md` 全勾 → tag

## 备注与风险
1. **M2 冻结前置未满足两项**：① `agent-config-ai-generate` 未合并；② 本版本已挂接的 **P1 缺陷（BUG-01、BUG-20）尚未达「已验证」**（修复已在代码/脚本中并经实测，但按细则「已验证」需重跑复现步骤且验证人=用户或其明确委托）。
2. **分支归属**：BUG-01/20/22/23 的修复位于 `feature/agent-config-ai-generate`，合并进 main 后其状态才能随 v1.2.0 出（届时无需改状态，但 M2 必须确认已合并）。
3. `releases/v1.2.0/sql/` 与 `config/` 目录已建（含 `.gitkeep`），**M3 前不放任何散件**（铁律：升级件只在 M3 按版本整理）。
