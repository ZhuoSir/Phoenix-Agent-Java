# 变更记录：group-management-ui

## 2026-10-24 v0.1.0 创建 + 挂载

- 来源：用户 2026-10-24 反馈组管理页（操作列拥挤 + 缺查看详情）⇒ `backlog.md` BL-69；
  相关 `BUG-206` 经核实**关闭为非缺陷**（状态切换按钮存在，仅被挤出可视区）。
- 挂载: **v2.1.0**（2026-10-24，用户确认；在途版本，分支 `2.1.0`）
- 关联: BL-69（已立项 → 本 spec 目录）
- 立项前里程碑审计：发现 v2.1.0 `MILESTONE.md` 中 `chat-runtime-hardening` 行版本头过期
  （仍写 v0.1.0 待确认 / plan 未开始 / tasks 未开始），已按 §1.3 先校正（commit `4928e664`）。
- 四件套建立：requirements.md / plan.md / tasks.md / changelog.md，均 v0.1.0 草稿。

## 2026-10-24 requirements v1.0.0 确认①

- 用户「确认」requirements v0.2.0 → 定版 v1.0.0，确认人 **陈卓**，确认日期 2026-10-24。
- 定稿要点：操作列 3 项 / 抽屉详情 / 后端分页 / 五页签授予+移除 / 数量概览 / 既有接口不改（新增分页接口）。
- 下一步：Phase 2 Plan。

## 2026-10-24 plan v1.0.0 确认②

- 用户「确认」plan v0.1.0 → 定版 v1.0.0，确认人 **陈卓**，确认日期 2026-10-24。
- 定稿要点：列表 7→3 / 详情抽屉（复用 5 个 assign 弹窗）/ 新增成员分页接口（其余接口不改）/ 5 类数量概览 /
  坑核对 108 条相交 6 条（L-61 最高风险）/ 共享面 6 身份 / 关键日志点 3 条。
- 下一步：Phase 3 Tasks。

## 2026-10-24 tasks v1.0.0 确认③ + 建施工分支

- 用户「确认」tasks v0.1.0 → 定版 v1.0.0，确认人 **陈卓**，确认日期 2026-10-24。
- 三重确认自检通过：requirements v1.0.0 / plan v1.0.0 / tasks v1.0.0，均「已确认」且确认人非空。
- 施工分支：`feature/group-management-ui`（自 `2.1.0` 切出）。

## 2026-10-24 T-01 完成（成员分页接口，真栈实测）

- 新增 `GET /platform/account-group-info/page-by-group?groupId=&page=&size=`（默认 size=20，上限 500）+
  `GroupMemberVO`（relationId/accountId/username/realName/accountStatus/joinTime）+ service `pageMembers`（批量取账号，防 N+1）。
- **实测（9091 真栈，token=admin）**：
  - ① 分页生效：`groupId=433382915273580544&page=1&size=2` → `total=3, records=2, pageSize=2, pageNumber=1` ✅
  - ② `size=501` → `{"code":"101","msg":"size 需在 1~500 之间","success":false}` ✅
  - ③ `groupId=` → `{"code":"101","msg":"groupId 不能为空","success":false}` ✅
  - ④ 日志：`INFO … [group-members] groupId=433382915273580544, page=1, size=2, total=3` ✅；非法参数 `WARN … 非法分页参数 …` ✅
  - ⑤ 共享面未变：`/group/{groupId}` 200、`/page` 200 ✅
- 备注（**已纠正**）：`realName`/`accountStatus` 为 null 的真实原因是**悬挂关联** —— 该组 3 条关联中 2 条指向的账号已被删除（`left join account_info` 为空），非源数据为空、也非映射缺陷；首行 username 来自关联表冗余字段 `account_name`（兜底生效）。已登记 **BUG-207**（账号删除后组关联未清理）。

## 2026-10-24 T-02 / T-03 完成（真栈实测）

**T-02 成员移除（可自我恢复）**
- 补审计日志：`deleteByGroupIdAndAccountId` 成功 INFO `[group-members] 移除成员 groupId=…, accountId=…`，失败 WARN
- 实测：`total 3 → DELETE(group/account) 成功 → total 2` ✅；日志有行 ✅；测试后把关联 `del_flag` 复位为 0，`total` 回到 3 ✅
- 坑（**已纠正两次，如实记录**）：该删除是**物理删**（行被移除），我先误判为逻辑删（`del_flag`），`UPDATE del_flag=0` 恢复无效；
  最终用备份行的完整 JSON 重新 INSERT 才恢复（`total` 回到 3、行存在）。教训：恢复动作必须**回读验证**，不能只看命令是否报错；
  用 `>/dev/null` 吞输出等于放弃验证（参见 L-104 同类）。

**T-03 共享面六身份回归（全部 200）**
① `GET /group/{g}` 200 ② `GET /page` 200 ③ `DELETE /group/{g}/account/{a}` 200（T-02 已验证真删+恢复）
④ `PUT /group-info/{g}/toggle-status`：status 0→1→0 复原 ✅ ⑤ `GET /account-info/getUnGroupPageByGroupId` 200
⑥ `index.vue` 仍 import 5 个 assign 表单 ✅

## 2026-10-24 T-04 / T-05 完成（构建产物断言通过，目视待确认）

- 已部署 9091：前端镜像重建 + nginx 重建（`group-info-CNGRGRKb.js`、`group-detail-drawer` chunk 存在，首页 200）
- **构建产物断言（Python 精确匹配，避免 shell 转义误判）**：
  - 组页 chunk 含：`查看详情` ✅ / `编辑` ✅ / `禁用` ✅ / `启用` ✅
  - 组页 chunk **不含**：`分配人员` / `分配智能体` / `分配知识库` / `分配技能` / `分配 MCP` ✅（已迁出）
- 详情抽屉实现：组信息含**创建/更新时间**、五类数量概览、**五个页签**（人员分页/智能体/知识库/技能/MCP，各带分配入口），
  分配弹窗复用既有 `assign-*-form`（通过 `@assign` 事件由 index.vue 打开）
- **未做（诚实标注）**：1440×900 下的**目视**断言（3 个按钮齐全、状态按钮不被截断、抽屉可打开）——需人工确认

## 2026-10-24 T-06 / T-07 完成（构建通过 + 已部署）

- T-06：人员页签加「移除」列（二次确认 → `deleteAccountGroupInfoByGroupAndAccountApi` → 重载详情），
  抽屉 `defineExpose({ reload })`；`index.vue` 5 个分配弹窗成功后同时 `detailRef.reload()`（T-07 刷新诉求一并满足）
- T-07：四页签（智能体/知识库/技能/MCP）列表 + 分配入口 → 复用既有 assign-*-form；成功后详情与列表双刷新
- 构建：`vite build` ✓ 4.72s；前端镜像与 nginx 已重建，9091 首页 200
- **未做（诚实标注）**：交互目视（移除弹窗、添加成员后刷新、四页签授予后刷新）需人工确认

## 2026-10-24 CR-01 合入（R-06 收窄 → 父 v1.1.0）

- 起因：T-08 施工核实——admin-ui **无按钮级权限体系**（`useAccess`/`hasAccessByCodes` 零使用、无 roles/permissions 数据、
  库无组权限码）⇒ R-06"只读降级"前提不存在，硬做只能硬编码（假实现）。
- 结果：R-06 **标变更**（改为沿用现状、不做按钮级控制）；T-08 **作废**（未施工，无回退）；
  权限化需求转 **BL-70**；requirements/tasks 版本 **v1.0.0 → v1.1.0**（陈卓 2026-10-24 确认）。

## 2026-10-24 T-09 完成 / T-10 部分完成

- **T-09（L-61 专项）通过**：5 个 assign 弹窗全部实现 `async onConfirm(...)`（无空实现）⇒ 不存在"确定"静默失效风险
- **T-10 部分完成**：
  - 通过：`vite build` 0 error、前端镜像/nginx 部署 9091 首页 200、搜索（name 过滤 total=1）、
    新建（临时组创建成功）、删除（删除后查询为空）、状态筛选（total=1）、状态切换（0→1→0 已复原）
  - **未通过**：编辑接口以我构造的 PUT body 调用返回 `Failed to read HTTP message`（服务端反序列化失败）；
    本 spec **未改动**编辑接口，疑为"用错请求体格式"或既有接口对 body 有特殊要求 ⇒ 需按前端实际格式复测
  - **未做**：UI 六项人工目视
  - 测试数据：临时组已删除；组状态已复原为 0
