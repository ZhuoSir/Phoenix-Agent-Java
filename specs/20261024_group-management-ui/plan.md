> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-24 | 确认人: （待确认）

# 技术方案：组管理界面治理（group-management-ui）

## 一、坑核对（必备节，置顶）

已核对 `specs/_project/lessons.md` 全部 **108 条** active 坑，相交 6 条，逐条给出避坑做法：

| 坑 | 与本方案的关系 | 本方案如何避开 |
|---|---|---|
| **L-61** vben 弹窗 `onConfirm` 空实现 = 「确定」按钮静默失效（**覆盖即替换，不是追加**） | 本方案新建详情抽屉（`useVbenDrawer`）、并复用 5 个 `assign-*-form.vue` modal | 新建/改造组件时 `onConfirm` 必须**实做并返回 Promise**；改既有 modal 时用**整段替换**其 `onConfirm`，不得只在末尾追加；实现后逐弹窗手点"确定"验证有实际请求发出 |
| **L-17** 动 UI 组件前先证可达（import 链核验） | 要复用 5 个 `assign-*-form.vue`、并改造 `group-info/index.vue` | 动手前先 `grep -rn "assign-people-form\|assign-*.vue"` 确认引用链（当前由 `index.vue` 静态 import ✅）；若详情页要改成动态引入，必须验证 vxe-table 列渲染后组件仍可达 |
| **L-14** md 表格错乱（台账类文档的暗伤） | 本 spec 文档与台账多处表格 | 每次写 md 后跑 `mdtable_check.py`，退出码 0 才算完成 |
| **L-95** 「口径散落多处」改一处等于没改 | "只读/可写"权限判定与"行内动作"收敛会散落在 actions 定义、详情页按钮、后端权限注解 | 权限判定只保留**一处函数**（`canWriteGroup()`），actions 与详情页都调用它；实现后 grep 校验无第二套判定 |
| **L-24** 路由页面模板必须单一根节点 | 本方案用**抽屉**，不新增路由页 ⇒ 不触发；但若后续改路由页需遵守 | 本 spec 不新增路由，天然规避；作为备注留给未来 |
| **L-108** 项目级台账必须在账房（版本分支）记 | 本 spec 立项时已踩过一次（BL/BUG 记在 feature 分支） | 本 spec 全部文档与台账提交均在 `2.1.0`（账房）上做；施工分支只提交代码 + 本 spec 目录文档 |

> 结论：**相交 6 条，均有明确避坑动作**；其中 L-61 是本方案最高风险点（要改既有 modal 的 `onConfirm`），Tasks 里将配置专项验证。

## 二、方案概述

**一句话**：列表行内动作 7 → 3；新增**组详情抽屉**承载"组信息 + 数量概览 + 五类页签（授予/移除）"；为成员列表**新增后端分页接口**（其余接口复用）。

```
列表页 group-info/index.vue
  ├─ 行内动作（3）：查看详情 | 编辑 | 启用/禁用
  └─ 查看详情 → GroupDetailDrawer（新）
        ├─ 头部：组信息（name/sn/status/description/创建/更新）
        ├─ 概览：人员/智能体/知识库/技能/MCP 已授权数量（5 个列表接口取长度/ total）
        └─ Tabs
             ├─ 人员  ：分页表格（**新接口** /platform/account-group-info/page-by-group）+ 移除成员 + 「添加成员」→ 复用 assign-people-form
             ├─ 智能体：列表 + 移除（DELETE .../group/{g}/agent/{a}）+ 「分配」→ 复用 assign-agent-form
             ├─ 知识库：列表 + 「分配」→ 复用 assign-kbase-form（覆盖式 assign）
             ├─ 技能  ：列表 + 「分配」→ 复用 assign-skill-form（覆盖式 assign）
             └─ MCP   ：列表 + 「分配」→ 复用 assign-mcp-form（覆盖式 assign）
```

**为什么这样切**：详情抽屉内**内嵌**全部分配逻辑（方案 A）改动面过大、且要重构 5 个表单；**只读展示 + 复用现有弹窗**（方案 B，采用）能以最小风险满足 R-03/R-05/R-11，并保留既有交互与后端契约。

## 三、改动面（文件清单）

**前端（admin-ui）**

| 文件 | 改动 |
|---|---|
| `views/account/group-info/index.vue` | 行内 actions 7→3；引入详情抽屉；新增 `openDetail(row)` |
| `views/account/group-info/group-detail-drawer.vue` | **新建**：抽屉容器 + 组信息 + 概览 + 五页签（Tabs） |
| `views/account/group-info/detail/people-panel.vue` | **新建**：成员分页表格 + 移除 + 打开 assign-people-form |
| `views/account/group-info/detail/agent-panel.vue` 等 4 个 | **新建**：智能体/知识库/技能/MCP 页签（列表 + 打开对应 assign 弹窗） |
| `api/core/platform-account-group-info.ts` | 新增 `getGroupMemberPageApi(groupId, page, size)` |
| `api/core/platform-account-info.ts` | （按需）成员行账号字段已在 VO 内返回，不再 N+1 查询 |

**后端（phoenix-platform）**

| 文件 | 改动 |
|---|---|
| `controller/front/AccountGroupInfoController.java` | 新增 `GET /page-by-group?groupId=&page=&size=` |
| `service/front/AccountGroupInfoService(+Impl)` | 新增 `pageByGroup(groupId, page, size) → Page<GroupMemberVO>` |
| `api/vo/GroupMemberVO.java`（或既有 vo 包） | 新增 VO：accountId/username/realName/role/joinTime |
| mapper/查询 | 先分页查关联（`account_group_info` by groupId），再按 accountId 批量查 `account_info`（1 次 IN，防 N+1） |

## 四、接口设计（遵守 api-design）

- 新增：`GET /platform/account-group-info/page-by-group?groupId={id}&page=1&size=20`
  - 资源/动作：关联资源 + 过滤参数（非动作型端点）✅；分页参数 `page`/`size`（与项目既有 `Page<>(page,size)` 一致），`size` 上限校验 **≤500**
  - 响应：既有信封 `ReturnVo<Page<GroupMemberVO>>`（与 `/page` 一致，不引入第二套信封）
  - 入参校验：`groupId` 必填非空；`page>=1`、`1<=size<=500`
  - 既有接口 `GET /group/{groupId}` **保留不动**（R-07），标注为"前端改用分页接口后仍保留（有其他调用方/兼容）"
- **不新增**任何删除/分配接口（成员移除、智能体移除、知识库/技能/MCP 分配均已存在）

## 五、共享面身份矩阵（触碰共享面，必备）

共享面判据自检：**"还有谁依赖这个路径/组件？"** —— 是（多处），故逐身份列出。

| 共享对象 | 既有身份（调用方/端点） | 变更后预期行为 |
|---|---|---|
| `GET /platform/account-group-info/group/{groupId}` | ① `assign-people-form.vue`（若引用）② 潜在外部调用方 | **不改**；仍返回 List，行为不变（R-07）。新增分页接口与之并存 |
| `GET /platform/account-group-info/page` | 账号管理页（若有）/ 其他 | **不改** |
| `DELETE /platform/account-group-info/group/{groupId}/account/{accountId}` | 现无前端调用（本次新增使用） | 语义不变；本 spec 首次从详情页调用 ⇒ 需验证删除后列表刷新且关联真实移除 |
| `PUT /platform/group-info/{id}/toggle-status` | `group-info/index.vue` 行内动作 | **不改**；仍是唯一状态切换入口（行内保留） |
| `GET /platform/account-info/getUnGroupPageByGroupId` | `assign-people-form.vue`（添加成员候选池） | **不改** |
| `assign-people-form.vue` / `assign-agent-form.vue` / `assign-kbase-form.vue` / `assign-skill-form.vue` / `assign-mcp-form.vue` | 仅 `group-info/index.vue` 引用 | 改由详情页打开；组件契约（props/emit）不变；`onConfirm` 需按 L-61 校验 |

> 矩阵没列的身份 = 未评估。Tasks 的验证断言以此表为源：每个身份一条"仍活着"的断言。

## 六、关键日志点清单（涉及后端改动，必备）

| 场景 | 级别 | 触发点 | 必带字段 | traceId |
|---|---|---|---|---|
| 成员分页查询 | DEBUG | `AccountGroupInfoServiceImpl.pageByGroup` 入参与结果 | `groupId`, `page`, `size`, `total` | 框架自带 |
| 成员移除 | INFO | 新增：`deleteByGroupAndAccount` 成功后（若原本无日志则补一条） | `groupId`, `accountId`, `operator` | 框架自带 |
| 分页参数非法 | WARN | Controller/Service 校验失败分支 | `groupId`, `page`, `size`, `reason` | 框架自带 |

四类强制日志点核对：**服务启停/定时批处理/外部调用**本 spec 不涉及；**安全事件**（权限拒绝）属既有 Sa-Token 机制，
前端只读降级不发请求 ⇒ 不新增；故上表三条为本次应落地的全部日志点（Tasks 中将逐条断言）。

## 七、被拒绝的替代方案

| 方案 | 拒绝理由 |
|---|---|
| 独立路由页（`/group/detail/:id`） | Q1 已定抽屉；新增路由要动菜单与权限，收益低 |
| 前端分页（一次拉全量前端切片） | Q3 已定后端分页；成员可能上百，全量拉取有性能与体验风险 |
| 详情抽屉内**内嵌**全部五个分配逻辑（重构 assign 表单为子组件） | 改动面 5 倍，回归成本高；复用弹窗已满足需求 |
| 顺带删除"无人使用"的后端分配接口 | R-07 明确既有接口不改；是否零引用需单独审计，另行立项 |
| 为数量概览新增聚合接口 | 5 次列表请求已足够（人员用分页 total，其余 list.length）；聚合接口属可选优化，留 backlog |

## 八、风险与规避

| 风险 | 规避 |
|---|---|
| 抽屉内再弹 modal 的层级/z-index 冲突（vben 常见） | 用 `useVbenModal` 的默认 teleport 行为；抽屉打开时弹窗仍可正常显示（实测验证一次） |
| 改既有 `assign-*-form.vue` 的 `onConfirm` 触发 L-61（静默失效） | 改后逐个手点"确定"，断言有真实请求发出 + 列表刷新 |
| 新增分页接口与既有 `/group/{groupId}` 返回字段不一致 | 分页 VO 字段为超集；既有接口不动，前端只在新面板用分页接口 |
| 权限判定散落（L-95） | 单一 `canWriteGroup()`；grep 校验 |
| 列表行内动作收敛后用户找不到"分配人员" | 详情页人员页签显著位置放"添加成员"按钮；变更说明写入 RELEASE-NOTES（M3 时） |
| 成员移除误删 | 移除前二次确认弹窗（与状态切换一致的 popConfirm 风格） |
