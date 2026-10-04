# Plan: agent-publish-group-grant（发布时关联组 + 空授权=全公开）

> 版本: v0.1.0 | 状态: 草稿 | 更新: 2026-10-04
> 规范路由：api-design=global / database=global（零 DDL）/ code-backend=global / code-frontend=none / git-workflow=global（v1.6.0 同版第二 spec）

## 〇、坑核对（lessons 全 15 条 active 逐条过）

| L | 相交 | 规避 |
|---|---|---|
| L-06 多入口枚举 | ✅✅✅ | **本 spec 命门**：公开分支必须落到全部读点——已实勘枚举（§1.2 读点清单），每读点单独断言；前端三处门面（admin-ui/views/front、pc-ui、mobile-ui）都吃同一服务端判定，服务端改齐即全覆盖 |
| L-01 补丁脱靶 | ✅ | 编辑带 assert 锚点；提交前验证声称内容在盘 |
| L-03 部署三段证明 | ✅ | 后端改动 inspect 镜像+标记串+功能探针；前端 compose build+容器内 grep（今天刚犯过） |
| L-07 假阳性验证 | ✅ | 可见性断言双侧取证（DB 授权行数 + 前台 API 实测）；技能/MCP 域对面断言防语义串门 |
| L-14 表格闸 | ✅ | 全部 md 产物过检 |
| L-15 命名净化 | 弱 | 无模型可见命名新增 |
| L-02/04/05/08~13 | 弱相交 | 日志全量/磁盘/确定性轮询/git add 逐文件等照常 |

**结论**：15 条全过，L-06 为本 spec 头号纪律。

## 一、采用方案

### 1.1 后端
- **发布扩展**（phoenix-data，镜像技能 publish+SkillPublishDTO 先例）：`POST /api/agent/{id}/publish` 增 `@RequestBody(required=false) AgentPublishDTO{groupIds}`——无 body=仅置状态不动授权行（A-1 向后兼容）；有 body=置状态+覆盖式重写组授权（物理删后重建，组存在性 Db 直查校验，技能 replaceGroupGrants 同构）。跨域写 tbl_platform_group_agent_info 有 BUG-14 级联 n3 先例。
- **授权读写端点**（R-02 编辑面）：`GET /api/agent/{id}/groups`（回显）+ `PUT /api/agent/{id}/groups`（覆盖式调整），镜像技能 /{id}/groups。
- **validateVisible 双分支**（FrontSkillAccessServiceImpl，R-03 核心）：先数授权行 `select count(*) from tbl_platform_group_agent_info where agent_id=?(varchar String化) and del_flag=0`——**0 行→直接可见（全公开分支）**；>0 行→现有交集 SQL 原样。
- **getMyAgents 公开合并**（AccountInfoServiceImpl）：现「组交集→findByIds(published)」结果 ∪ 「无授权行且 published 且 sn 空」直查集（NOT EXISTS 子查询，id::text 对齐 varchar 坑）；**无组账号也走公开集**（现状返回 null 的分支要改）。

### 1.2 读点清单（L-06 全量枚举，实勘定案）
| # | 读点 | 处置 |
|---|---|---|
| 1 | FrontSkillAccessServiceImpl.validateVisible（前台 chat 入口校验+resolveVisibleAgentSn 复用） | **改**：双分支 |
| 2 | AccountInfoServiceImpl.getMyAgents（前台智能体列表，/platform/account-info/getMyAgents） | **改**：公开合并+无组账号分支 |
| 3 | GroupAgentInfoController/Service（组管理侧维护 CRUD） | 零变化（对面断言） |
| 4 | AgentServiceImpl 级联 n3（删除清理） | 零变化 |
| 5 | AgentKbaseServiceImpl / GroupInfoServiceImpl（组域聚合查询） | 零变化（plan 落地时再 grep 复核一遍，防漏） |
| 6 | FrontHarnessController.chat / HarnessFrontController（消费 #1 判定） | 代码零改动，行为随 #1（E2E 断言） |

### 1.3 前端（admin-ui）
- **发布弹窗**：list/index.vue `handlePublish` 从 ElMessageBox 升级为组多选弹窗（数据源 getGroupInfoPageApi(1,200)；已发布智能体重发布时回显现有授权；**空选警示 Alert「不授权任何组时，所有前台用户均可见」**，R-05）→ publishAgentApi(id, groupIds)
- **抽屉授权区块**：编辑抽屉新增左菜单「授权组」+ AgentGroupGrant.vue（镜像 AgentPluginConfig 形态：多选+保存 PUT groups；已发布才可编辑，草稿提示先发布）
- api/core/agent.ts：publishAgentApi 增 body + getAgentGroupsApi/updateAgentGroupsApi
- 前台三处门面（views/front、pc-ui、mobile-ui）**零改动**——判定全在服务端

## 二、涉及模块与数据流
```
发布: admin UI 弹窗 → AgentController.publish(dto) → 置状态+覆盖式授权 → 立即生效
前台: getMyAgents(交集∪公开) → 列表; chat → validateVisible(双分支) → 对话
写入面: 发布弹窗 / 抽屉授权块 / 组管理侧(保留) → 同表覆盖式，后写胜（同技能现状）
```
修改：phoenix-data 2 文件（Controller/ServiceImpl）+ phoenix-agent 1（FrontSkillAccessServiceImpl）+ phoenix-platform 1（AccountInfoServiceImpl）+ admin-ui 4（list/抽屉/api/新组件）；新增：AgentPublishDTO、AgentGroupGrant.vue。

## 三、共享面身份矩阵
| 共享对象 | 既有身份 | 变更后预期 |
|---|---|---|
| validateVisible | ①前台 chat 校验 ②resolveVisibleAgentSn | 有授权行行为逐字节不变；无行→可见（新语义）；技能交集判定（同文件他方法）零变化 |
| getMyAgents | ①前台列表 | 有组用户=交集∪公开；无组用户=null→公开集；admin 列表（listCreatedInPlatform）零变化 |
| publish 端点 | ①UI 发布 | 无 body=老行为；有 body=新增授权语义 |
| 编辑抽屉 | ①六个既有区块 | +授权组区块，既有零变化 |
| 组管理侧 | ①授权 CRUD | 零变化（对面断言） |
| 技能/MCP 域 | ①默认非公开 | 零变化（对面断言：无授权技能前台仍不可见） |

## 四、被拒绝的替代方案
| 方案 | 拒绝理由 |
|---|---|
| 前端本地过滤公开逻辑 | 判定必须服务端（三处门面一致性+绕过风险） |
| 新表/新列存"公开标志" | 用户语义=无授权行即公开，零 DDL 天然达成，加列反而造双真相 |
| 组管理侧入口收敛废弃 | 用户 Q1 裁决保留双入口 |
| 发布+授权拆两事务补偿 | 技能先例同请求顺序写+日志足够，量级小 |

## 五、风险与规避
| # | 风险 | 规避 |
|---|---|---|
| 1 | **读点漏改**（列表可见但 chat 拒绝等不一致） | §1.2 清单逐点断言；E2E 矩阵含无组用户（新语义最敏感人群） |
| 2 | 存量翻转（id=24 制度专家上线即全公开） | 用户知情（A-5 实测量化）；M3 入 RELEASE-NOTES/UPGRADE |
| 3 | varchar/bigint join 坑（group_agent.agent_id） | String.valueOf 参数化+id::text（现码两处先例照抄） |
| 4 | 无组账号 getMyAgents 现返回 null 的调用方假设 | 前端已 ?? [] 兜底（api/front/agent.ts catch 实勘）；返回空列表语义不变 |
| 5 | 双入口并发写 | 覆盖式后写胜=技能现状语义，不新造锁 |

## 六、测试策略（真做）
- validateVisible 三态（无行公开/有行交集命中/有行不命中）curl+DB 双侧取证
- getMyAgents：有组用户（交集∪公开）/无组夹具用户（仅公开）双账号实测
- publish 三态（无 body/空数组/带组）+ 覆盖式（重发布换组，旧组移除）+ 假组拒绝
- E2E：发布空授权 → 无组夹具用户前台列表可见+对话成功（chat 段新逻辑实弹）→ 勾 G1 收敛 → 无组用户不可见
- 对面断言：技能无授权仍不可见；组管理侧 CRUD 行为不变；admin 列表不变
- 回归：verify 13/13、vue-tsc 基线 213、MCP 链抽查（同版共存互扰排查）
- 夹具全清（无组账号/发布态还原）

## 七、任务预告（Phase 3 细化）
T-01 发布扩展+groups 读写端点（data 模块）→ T-02 validateVisible 双分支 → T-03 getMyAgents 公开合并 → T-04 前端发布弹窗 → T-05 抽屉授权组区块 → T-06 E2E 矩阵+对面断言+回归 → T-07 台账收尾。
