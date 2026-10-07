> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-07

# 需求规格：user-role-group-model

## 背景与目标

### 背景（立项调研事实，2026-10-07）

当前用户体系里**并排存在五套身份/组织概念**，外加**两套「组」**：

| 概念 | 落地物 | 是否参与权限判定 |
|---|---|---|
| 用户 | `tbl_privilege_user` | 是 |
| 角色 | `tbl_privilege_role` + `tbl_privilege_user_role`（多对多，含 `end_date`/`valid_month` 有效期） | 是（菜单授权） |
| 企业 | `tbl_privilege_company`（含 `idm_company_id`/`third_id` 外部对接列） | **否** |
| 部门 | `tbl_privilege_department`（`company_id` NOT NULL，树形 `pid`） | **否** |
| 员工 | `tbl_privilege_employee` | **否** |
| 组（平台侧） | `tbl_platform_group_info` + `tbl_platform_group_{agent,kbase,skill,mcp}_info` + `tbl_platform_account_group_info` | 用于资源授权 |
| 组（后台侧） | `tbl_privilege_group`（数据组，含 `super_id`/`type`/`state`） | 未接权限判定 |

实测引用面（本次立项调研，命令可复现）：

- Java 侧 `PrivilegeCompany|PrivilegeDepartment|PrivilegeEmployee` 引用 **227 处**，横跨 `phoenix-privilege`（实体/DTO/VO/Query/Mapper/Service/Controller）、`phoenix-platform`（`AccountInfo`）、`phoenix-common`（`AbstractPlatformSyncStrategy` + 钉钉/飞书/微信同步策略）。
- `companyId|deptId|employeeId` 字段出现 **198 处**。
- `tbl_privilege_user` 的 `company_id`、`dept_id` 是 **NOT NULL** 列——组织维度是用户表的**强约束**，不是可选项；另有 `employee_id`、`it_user_id`、`it_user_name`、`is_leader` 等组织衍生列。
- 前端 `web-frontend/apps/admin-ui`：独立「组织管理」菜单族四条路由（`/organization`、`/organization/company`、`/organization/department`、`/organization/employee`）+ 选择器组件（`src/components/dept/DepartmentSelector.vue`、`EmployeeSelector.vue`）+ 三个 API 客户端（`src/api/core/privilege-{company,department,employee}.ts`）。
- 权限设施（`role` / `user_role` / `module` / `acl` / `pvalue`）**不依赖**企业/部门，可原样保留。

**结论**：组织维度对目标模型（一个用户、若干角色、若干组、组直接授权资源）是**净负担**——它不参与任何权限判定，却强制每个用户必须挂在一个企业和部门上，并让「组」的语义分散在两套表里。

### 目标（成功长什么样）

1. 后台用户模型只剩三要素：**用户 → 角色（可多）→ 组（可多）**；企业、部门、员工在 UI、API、库表中完全消失。
2. 建用户只需填账号信息 + 勾角色 + 勾组。
3. 组能直接关联**智能体 / 技能 / 知识库 / MCP 插件**四类资源，组内用户据此获得可见性——前台授权不再是「任一登录用户可见」。
4. 菜单按角色过滤生效（不再 26 个菜单对全员可见）。

## 需求条款

### R-01 组织维度下线（用户可见面）

WHEN 管理员登录后台管理端并浏览导航,
系统 SHALL 不再呈现「组织管理」及其「企业 / 部门 / 员工」子项，且不再提供任何组织维度的查询、新增、修改、删除入口。

边界：`/organization`、`/organization/company`、`/organization/department`、`/organization/employee` 四条菜单记录及对应前端页面/路由/API 客户端一并移除；直接以 URL 访问时 SHALL 落 404 或首页，不得渲染空白页或残留半截表单。

#### 验收场景

- **GIVEN** 管理员已登录后台
  **WHEN** 打开左侧导航
  **THEN** 无「组织管理」节点及其三个子项
- **GIVEN** 管理员已登录后台
  **WHEN** 手工访问 `/organization/department`
  **THEN** 页面 404 或跳首页，浏览器控制台无未捕获异常
- **GIVEN** 组织维度已删除
  **WHEN** 调用原企业/部门/员工 REST 端点
  **THEN** 返回 404（端点不存在），不是 500

### R-02 用户模型三维度化（用户 / 角色 / 组）

WHEN 管理员新建或编辑一个用户,
系统 SHALL 只要求并提供「账号信息 + 角色（可多选）+ 组（可多选）」三类输入，不得要求或展示企业、部门、员工、工号字段。

边界：用户↔角色、用户↔组均为多对多；角色分配沿用 `tbl_privilege_user_role` 语义（含有效期）。

#### 验收场景

- **GIVEN** 管理员打开用户表单
  **WHEN** 查看字段
  **THEN** 无企业/部门/员工选择项；「角色」「组」为多选控件
- **GIVEN** 管理员只填账号信息并勾选 1 个角色
  **WHEN** 提交
  **THEN** 创建成功，且该用户可登录

### R-03 组织维度的结构与数据处置（破坏性）

WHEN 新版本部署执行升级脚本,
系统 SHALL 删除企业、部门、员工三张表及用户表中的组织归属列，并 SHALL 提供与之**成对**的回滚脚本，使回滚后库结构与数据恢复升级前状态。

边界：数据处置口径（丢弃 / 归档）在 plan 定稿并写入 `UPGRADE.md`；回滚脚本 SHALL 与本版 SQL 同目录配对存在。

#### 验收场景

- **GIVEN** 存量库（含公司/部门/员工数据与用户组织归属）
  **WHEN** 执行升级脚本
  **THEN** 三张表与用户表组织列均不存在，应用启动无 SQL 报错
- **GIVEN** 升级后的库
  **WHEN** 执行回滚脚本
  **THEN** 三张表与用户表列恢复，且行数与升级前一致
- **GIVEN** 全新空库
  **WHEN** 重放本版全部 SQL
  **THEN** 全部成功（幂等），无「表已存在/不存在」类错误

### R-04 组直接关联四类资源

WHEN 管理员在组管理中为一个组授予资源,
系统 SHALL 支持关联**智能体、技能、知识库、MCP 插件**四类资源，且关联结果对该组内用户**即时生效**（无需重启、无需重新登录）。

边界：沿用既有 `tbl_platform_group_{agent,skill,kbase,mcp}_info` 语义；仅组内用户可见被授权资源。

#### 验收场景

- **GIVEN** 组 G 含用户 U，且 G 关联智能体 A
  **WHEN** U 打开智能体中心
  **THEN** A 可见
- **GIVEN** 组 G 未关联技能 S
  **WHEN** U 打开技能列表
  **THEN** S 不出现
- **GIVEN** 管理员刚为 G 新增知识库 K 的关联
  **WHEN** U 立即刷新知识库列表
  **THEN** K 可见（无需重启/重新登录）

### R-05 角色过滤生效

WHILE 用户已登录后台,
系统 SHALL 只呈现其角色所授权的菜单与操作；未授权菜单不出现在导航中，直接以 URL 访问未授权页面 SHALL 被拒绝。

边界：本期覆盖后台菜单可见性；既有 `tbl_privilege_acl` 预置行由「数据」转为「生效判定源」。

#### 验收场景

- **GIVEN** 用户 U 仅具「普通用户」角色
  **WHEN** 打开后台
  **THEN** 仅见其角色授权菜单（非全量 26 项）
- **GIVEN** 上述 U
  **WHEN** 手工访问未授权菜单 URL
  **THEN** 被拒绝（403 或跳首页），不渲染页面内容

### R-06 外部平台同步适配

WHEN 系统执行钉钉 / 飞书 / 微信等外部平台的用户同步,
系统 SHALL 在不再依赖企业、部门、员工维度的前提下完成用户与角色的同步，且同步过程不因组织维度缺失而失败。

边界：后端全量构建 SHALL 通过（现有 4 个同步策略不得因本次变更编译失败或运行时抛错）。

#### 验收场景

- **GIVEN** 组织维度已删除
  **WHEN** 全量构建后端
  **THEN** 同步模块编译通过
- **GIVEN** 已配置某第三方平台
  **WHEN** 触发一次同步
  **THEN** 用户被正确创建/更新，日志无组织维度相关异常

### R-07 存量账号的角色与组归属补齐

WHEN 升级完成,
系统 SHALL 为存量可登录用户补齐至少一个角色（及必要的组归属），使升级后不存在「无任何角色的可登录用户」。

边界：升级后「可登录用户中角色关联数为 0」的计数 MUST 为 0；已有多个角色的用户，其角色关联 SHALL 不被覆盖或截断。

#### 验收场景

- **GIVEN** 升级后的库
  **WHEN** 统计可登录用户
  **THEN** 无角色关联的用户数为 0
- **GIVEN** 用户 U 升级前已有 2 个角色
  **WHEN** 升级完成
  **THEN** U 的角色关联仍为 2 条

### R-08 预设问题接口的角色限制（承接 BL-05 遗留，待定纳入）

WHEN 非授权角色调用预设问题新增 / 删除接口,
系统 SHALL 拒绝该请求并返回统一错误响应。

边界：本条为 BL-05 备注中「预设问题 add/delete 接口角色限制随体系统一一并做」的遗留项；`unified-account-center` spec 未覆盖。是否纳入本期见待确认问题 Q6。

#### 验收场景

- **GIVEN** 以普通用户角色登录
  **WHEN** 调用预设问题新增接口
  **THEN** 返回权限拒绝（统一错误码），且数据未变更

## Non-goals（范围外）

- **不做**账号历史数据质量清理（孤儿账号 id 引用、共用 `employee_id`、`system` 哨兵 creator）→ 已登记 **BL-30**，独立排期。
- **不做**智能体执行面硬隔离（沙箱 / 独立执行身份）→ **BL-32**。
- **不做**知识库数据面收敛（去明文原件、剥离 DB 凭据）→ **BL-33**。
- **不做**存量向量重建与 embedding 指纹校验 → **BL-34**。
- **不做**功能级/数据行级 ACL 的细粒度重设计——本期只把既有 ACL 行从「预置数据」转为「生效判定」。
- **不新增**组织架构双向同步能力（只删不建）。
- **不保留**「按部门/企业统计」类报表能力。
- **不处理** `tbl_privilege_user.del_flag`/`status` 语义不一致（BUG-101 已延期，另行处置）。

## 我正在做的假设

1. 「组」指平台侧 `tbl_platform_group_info`（资源授权组）；后台侧 `tbl_privilege_group`（数据组）**默认不在本期统一范围**——两者关系见 Q1，若判定需合并则本 spec 扩范围并重走确认。
2. 企业/部门/员工的存量数据允许**直接删除**（不做归档表）；如需留档请现在纠正。
3. 外部平台同步的「用户同步」能力保留，仅去掉组织维度依赖。
4. 用户↔角色、用户↔组均为多对多。
5. 破坏性变更 → 挂 **v2.0.0**（MAJOR），按 MAJOR 语义写升级说明与回滚。
6. 本期**一次做完**「角色过滤生效」与「组↔资源纳管归一」（若 Q2 判分期，则本 spec 缩为阶段一）。

现在纠正，否则按此执行。

## 待确认问题（阻塞，确认①前必须答复）

- **Q1 组的唯一性**：后台侧 `tbl_privilege_group`（数据组）与平台侧 `tbl_platform_group_info`（资源授权组）是否合并为一套？还是本期只动平台侧、`tbl_privilege_group` 原样保留？
- **Q2 分期**：按 backlog 原建议分两期（阶段一＝角色过滤生效；阶段二＝组↔资源纳管归一），还是本期一次做完？
- **Q3 存量组织数据**：直接删除 / 归档留档 / 仅解除约束不动数据？
- **Q4 三方同步**：钉钉/飞书/微信同步是否仍在使用？若已停用，是否一并移除同步模块以缩小改动面？
- **Q5 员工维度外部对接**：`tbl_privilege_company.idm_company_id`/`third_id`、`tbl_privilege_user.it_user_id`/`employee_id` 是否还有外部 IDM/HR 系统在读写？
- **Q6 R-08 是否纳入本期**（预设问题接口角色限制，BL-05 遗留）？
