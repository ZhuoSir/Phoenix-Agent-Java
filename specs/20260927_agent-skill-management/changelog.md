# Changelog: agent-skill-management

## 已合并（2026-09-27）
- 合并到 main：`ed2e3f9`（merge --no-ff，保留 T-01~T-15 任务级提交），feature 分支已删除
- 合并前状态：15/15 任务勾选、逐任务验证证据齐备、R-01~R-09 回归矩阵通过、三文档均为「已确认」

## v1.1.0（2026-09-27）确认人: 陈卓
- 重确认通过：plan.md v1.1.0 已确认（显式执行改 middleware→系统提示注入），T-10 按新通道返工
- **修改 决策4**：显式执行通道 user message 注入 → **系统提示注入（ExplicitSkillMiddleware）**
  - 原因：Implement 实测（T-10）被模型判定为 prompt injection 而拒绝执行（DeepSeek 推理原文可查 /tmp/t10c.txt 语境）
  - 已核实不可行替代：RuntimeContext+SkillFilter 通道在 harness 链路不生效（字节码核实 applyVisibility 只读构建期 filter）
  - 附带修正：拒绝原因改 StreamingOutput 事件返回（原 error_message 未被控制器映射，实测拒绝响应为空）
- 影响任务：T-10 需按新通道返工（当前未勾选）；T-11 复用同一校验服务，接口契约不变
- 需求（R-05/R-09 语义）不变，requirements.md 无需重确认

## v0.1.0 plan 草稿期（2026-09-27）
- 陈卓对三个决策点表态并写入 plan.md 风险节：① 自主模式缝隙=接受为已知限制（显式路径严格校验）② saveBySn 验证任务照做 ③ 单轮≤3 技能可配置通过
- 补充说明 saveBySn 含义并当场实测其源码：**insert-only**，风险②消解（无覆写问题，取消附带修复任务）

## v1.0.0（2026-09-27）确认人: 陈卓
- 三重确认第①关通过：requirements.md（R-01~R-09 + Non-goals + 假设11条）
- 三重确认第②关通过：plan.md（风险①接受限制/②已消解/③通过）
- 三重确认第③关通过：tasks.md（T-01~T-15），自检通过，**进入 Phase 4 Implement**
- 用户指令：RulesHarnessAgent 实验改动（shell+LocalFS）直接提交当前分支（main），与本 spec 分离

## v0.1.0（2026-09-27）草稿期修订（第二次）
- 用户拍板 Q1~Q4：**删除要做**（新增 R-08）；勾选不持久；列表全量；**前台技能 UI 要做**（新增 R-09，连带打通前台 harness 对话通道——原 B-09 依赖进本期）
- Non-goals 相应调整；假设 2 改为覆盖后台+前台两端

## v0.1.0（2026-09-27）草稿期修订
- 新增 R-07 组与技能关联：授权入口定在**技能发布界面**（发布时多选目标组，发布后可随时调整即时生效），不在组管理页加「分配技能」；未选组=可发布但前台全体不可见
- 用户明示：智能体↔组关联将来也要改成"智能体发布时关联组"（本期不做，已入 Non-goals，届时另立 spec）

## v0.1.0（2026-09-27）
- 初始化创建（requirements.md 草稿全文；plan.md / tasks.md 占位，待 Phase 2/3）
- 需求来源：用户口述——后台菜单新增技能管理（智能体管理下）、ZIP 上传自动识别、发布控制、智能体编辑页技能配置、运行页显式选择执行技能
- 立论依据：profile §运行时机制实测记录（skill 底座已验证）；bugs.md B-06（无管理入口、技能池全局共享）
