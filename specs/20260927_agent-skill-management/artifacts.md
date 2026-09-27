# 升级件登记: agent-skill-management

| 类型 | 内容摘要 | 来源任务 | 草案位置 | 已汇总至里程碑 |
|---|---|---|---|---|
| DDL | `tbl_harness_skills` 增 `status` 列（默认 draft，含存在性判断容错） | T-01 / T-05 | `sql/01_agent_skill_upgrade.sql` | - |
| DDL | 新表 `tbl_data_agent_skill_info`（智能体-技能绑定，部分唯一索引防重） | T-01 | `sql/01_agent_skill_upgrade.sql` | - |
| DDL | 新表 `tbl_platform_group_skill_info`（组-技能授权，与组-智能体表同构） | T-01 | `sql/01_agent_skill_upgrade.sql` | - |
| DML | 菜单注册：`tbl_privilege_module` 新增「技能管理」(url=/agent/skill, component=#/views/agent/skill/index.vue, image=lucide:sparkles) | T-12 | `sql/02_skill_menu.sql` | - |
| DML | ACL 授权：按「智能体列表」模块的既有角色逐个复制到新模块 | T-12 | `sql/02_skill_menu.sql` | - |
| 配置 | `phoenix.agent.skill.max-explicit`（单轮显式执行技能上限，默认 3） | T-10 | plan §接口设计（代码默认值，无外部配置源） | - |
| 回滚 | `01_agent_skill_rollback.sql`（drop 两表 + drop status 列）；菜单/ACL 回滚语句见 `02_skill_menu.sql` 文末注释 | T-01 / T-12 | `sql/` | - |

## 重放验证记录（T-15）
- **场景A**（全新环境，应用尚未启动）：仅导入 `sql/all_schema.sql` 后执行 01 → `01 执行零报错`（打印 NOTICE 跳过 status 列变更，因 `tbl_harness_skills` 由应用启动创建），两张新表建成；执行 02 → 零报错，菜单行建成
- **场景B**（应用已建 harness 表后重放）：补建 harness 表后重放 01 → `status_col=1`，其余对象幂等跳过
- 校验 SQL：`new_tables=2`、`menu=1`
- 附注：基线 `sql/all_schema.sql` 自身导入有 64 处报错（建表引用了缺失序列等，属 bugs.md **B-01**，与本次升级件无关）
