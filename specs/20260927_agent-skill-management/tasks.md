> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-09-27 | 确认人: 陈卓 | 确认日期: 2026-09-27

# 任务清单：智能体技能管理

> 验证口径说明：项目无测试基建（profile §测试基线），验证以「curl+SQL 断言+UI 人工步骤」为主，编译/lint 仅兜底。所有验证须留存真实输出。

## 1. 基础（数据与模型）

- [ ] T-01 编写并本地执行技能域 DDL 升级/回滚脚本
  关联: R-03, R-04, R-07
  依赖: 无
  验证方式: 在本地 phoenix-pg 执行升级脚本后 `\d tbl_harness_skills` 含 status 列、两张新表建成；用同名重复插入触发 uk 报错；执行回滚脚本后结构还原（psql 输出留档 specs/{dir}/artifacts.md 草案）
  验收标准: 升级+回滚两脚本齐备可重复执行（幂等：IF NOT EXISTS/DEFAULT），存量 2 条技能 status=draft

- [ ] T-02 新增 SkillStatusEnm 枚举、SkillErrorCode 错误码与两张关联表实体+Mapper（MyBatis-Flex），AgentSkill 相关查询 VO
  关联: R-01, R-03
  依赖: T-01
  验证方式: `mvn install -pl phoenix-agent/phoenix-agent-core -am` 编译通过 + 启动应用后 CommandLine 冒烟：任意 REST 端点前用 JShell 不可行 → 以 T-09 首个 GET 代替
  验收标准: 无魔法值（状态/错误码全枚举）、实体字段与 DDL COMMENT 一致

## 2. 后台管理功能（服务层）

- [ ] T-03 实现 SkillAdminService.list/detail：分页、关键字过滤（name/description）、详情含正文与资源清单
  关联: R-01
  依赖: T-02
  验证方式: T-09 完成后 curl 列表/详情断言（含搜索「poem」仅剩 1 条、未登录 401）
  验收标准: 查询显式列无 SELECT *；返回 VO 不含内部字段

- [ ] T-04 实现 ZIP 上传解析入库：复用 agentscope-core SkillUtil.createFromZip，事务内写 skill+resources；校验 ≤10MB、文本白名单、SKILL.md 必备；同名按 overwrite 参数处理（覆盖后回 draft）
  关联: R-02
  依赖: T-02
  验证方式: curl 三分支实测：标准包成功入库（含 scripts/fib.py 资源行）；无 SKILL.md 包→指定错误码且 `select count(*)` 不变；同名未带 overwrite→冲突错误、带 overwrite→更新且 status=draft
  验收标准: 失败零残留（事务回滚），错误 msg 指明具体原因不带堆栈

- [ ] T-05 实现发布/下线/组授权：publish(id, groupIds[])、offline(id)、putGroups(id, groupIds[])，授权记录覆盖式写 tbl_platform_group_skill_info
  关联: R-03, R-07
  依赖: T-02
  验证方式: curl：草稿→publish 带 2 组→查库两行授权；putGroups 改 1 组→即时生效行数变化；offline→status=draft；publish 空组→允许且无授权行
  验收标准: 全部路径带事务；非法状态流转（如重复 publish）返回明确错误码

- [ ] T-06 实现智能体绑定端点逻辑：binding GET/PUT（覆盖式），可选池查询（published 池 + 已绑定回显标记）
  关联: R-04
  依赖: T-02
  验证方式: curl PUT agent(RulesHarnessAgent id)=[skillA] 后 GET 回显一致；绑未发布技能→拒绝
  验收标准: 下线不级联删绑定（数据保留），池查询接口对已绑未发布技能返回但标记 offline

- [ ] T-07 实现删除与引用计数：refs(id) 返回绑定数/授权组数；delete(id) 仅 draft，级联清理绑定与授权
  关联: R-08
  依赖: T-02, T-05, T-06
  验证方式: curl：published 删除→拒绝码；草稿 C（预置 1 绑定+1 授权）删除→三表相关行全清、资源行随删
  验收标准: 级联在单事务内

- [ ] T-08 挂载 REST 控制器 `/api/skill/**`（对应 T-03~T-07 全部端点），确认 Sa-Token 拦截覆盖（登录态、无 token 401）
  关联: R-01, R-02, R-03, R-04, R-07, R-08
  依赖: T-03, T-04, T-05, T-06, T-07
  验证方式: 重启后 curl 全端点矩阵（每端点 1 正 1 负），401/403/200 断言留档
  验收标准: 路径/入参/错误码与 plan §接口设计一致

## 3. 运行时与对话

- [ ] T-09 实现 FilteredSkillRepository 装饰器（published ∧ 绑定该 sn 过滤 getAllSkills/getSkill），替换两个 harness 智能体 builder 的 skillRepository
  关联: R-06
  依赖: T-05, T-06
  验证方式: 端到端：未绑定状态与制度专家聊「报诗」→不触发；绑定 py-fib-demo 后同话术→触发（对话留证）；绑 A 后与 HumanInTheLoop 聊→感知不到 A
  验收标准: R-06 两场景全过；装饰器不缓存（绑定变更下一轮生效）

- [ ] T-10 实现显式技能注入：HarnessRequest 增 enabledSkillIds（后端二次校验 ≤3、published、绑定），buildUserMessage 前拼 `<active_skills>` 全文块，SSE 事件透出 loadedSkills[]
  关联: R-05
  依赖: T-09
  验证方式: curl /api/admin/harness/chat 带 enabledSkillIds 发**不含触发词**的消息→回复按技能指令；不带 ids 发「报诗」→行为同 T-09（回归）；勾 4 个/勾未绑定→拒绝码
  验收标准: R-05 三场景全过；不传字段时接口向后兼容

- [ ] T-11 实现 SkillAccessService 三重交集判定与前台端点：getMySkills?agentId=、/platform/harness/chat（platform 身份→agent 组可见性→skill 三重校验→自主模式追加"本轮可用技能仅…"约束提示）
  关联: R-07, R-09
  依赖: T-10
  验证方式: curl：前台 token 查 getMySkills（授权/未授权两组账号对照）；越权构造 enabledSkillIds 含未授权技能→权限错误；正常路径显式执行成功
  验收标准: R-09 三场景（可见性、越权拒绝、无勾选可用）全过

## 4. 前端（admin-ui）

- [ ] T-12 技能管理页：菜单注册（privilege_module SQL）+ 列表（搜索/状态徽标）+ 详情抽屉（正文+资源清单）+ 上传弹窗（含同名覆盖确认、错误提示）+ 发布弹窗（组多选）+ 组授权调整 + 下线 + 删除确认框（显示引用数）
  关联: R-01, R-02, R-03, R-07, R-08
  依赖: T-08
  验证方式: 人工步骤走查全动线（浏览器截图/记录），菜单出现在「智能体管理」下；上传→发布→下线→删除闭环；每步错误分支至少触发一次
  验收标准: 与后端契约一致（错误码文案直显 msg）；lint（pnpm lint）兜底通过

- [ ] T-13 智能体编辑页「技能配置」块：published 池多选、已绑回显、被下线技能灰显标记、非 harness 类型显示"仅对 Harness 类智能体生效"提示（假设8）
  关联: R-04
  依赖: T-06, T-12
  验证方式: 人工走查：编辑制度专家勾选保存→重开回显；下线该技能再开编辑页→灰显仍在
  验收标准: R-04 两场景过

- [ ] T-14 前台对话页技能区：api-transport 增加 harness 分流（type==='harness'→/platform/harness/chat），技能面板按 getMySkills 渲染、勾选随消息发送（不持久）、loadedSkills 可视
  关联: R-09
  依赖: T-11, T-12
  验证方式: 人工走查：通用组前台账号登录前台→对话 harness 智能体→勾选 py-fib-demo→非触发词消息被执行；未授权技能不出现在面板；三条既有通道（sql/react/后台harness）回归各一轮
  验收标准: R-09 场景 + 风险⑤回归通过

## 5. 验证收尾

- [ ] T-15 端到端总回归 + 升级件登记：按 R-01~R-09 验收场景逐条执行并留证据；完善 artifacts.md（DDL/菜单 SQL/新增配置项 phoenix.agent.skill.max-explicit）
  关联: R-01~R-09（汇总）
  依赖: T-09, T-10, T-11, T-12, T-13, T-14
  验证方式: 全场景清单逐项勾选记录（命令输出/截图索引），SQL 升级脚本在**空库**重放一次验证全新环境可建
  验收标准: 每条 R 的验收场景均有留档证据；空库重放零报错

<!-- 自检：R-01→T-03/08/12；R-02→T-04/08/12；R-03→T-05/08/12；R-04→T-06/08/13；R-05→T-10/15；R-06→T-09/15；R-07→T-05/11/12；R-08→T-07/08/12；R-09→T-11/14/15。全部 R 有 T 覆盖，无孤儿 T，无占位任务，依赖均指向更小编号。总数 15（≤20）。 -->
