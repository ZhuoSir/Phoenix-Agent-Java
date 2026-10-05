# MILESTONE: v1.6.0

> 创建: 2026-10-04 | **状态: 已发布（M4，2026-10-05）** | 后端 10 文件（2 新：WorkspacePaths/WorkspaceMigrationRunner）；零 DDL 零 DB 重写 | 0（**verify 13/13 全 PASS**；对面五连全绿） | v1.0.0：workspace 按智能体隔离+文件归属精确化+存量归档；**v1.1.0(R-06)：会话级工作区 `{root}/{agentKey}/{sessionId}`+shell cwd 统一(BUG-79)+读路径统一(BUG-78/82)+`/app` 清理**；BUG-67/68/78/79/80 已验证；**记忆重置提示（会话级后按会话落）移交 M3/BL-17 与 LTR 合并成一条**；**布局唯一 owner**（LTR 未改布局，见 artifacts 归属对账） |
| specs/20261005_long-turn-resilience | req v1.0.0 / plan v1.0.0 / tasks v1.0.0 | **实现完成（7/8 收口；T-06 转 `BL-27`）** | SQL 1 件 `V1.6.0_03`（运行配置加 3 列）+ rollback 1 件；后端 10 文件（1 新）+ 前端 6 文件；config 新键 ×5 | 0（verify 13/13 全 PASS；typecheck 基线 213 条预存、本次 0 新增） | 长轮可靠性：看门狗活性化（空闲 600s 判据、总时长闸默认关）+ 服务端 100ms 帧合并 + join 追流续渲 + 中断五分类文案（**用户实测"基本通过"**）；BUG-70/71/72/73/74/75/76/77/81/82 已验证；T-03 实现位置偏差（服务端合并替代客户端批量）已入档；T-06 参数形态被否→`BL-27`（改为模型上下文 token/输出最大 token/压缩比例）；记忆重置与 config 变更移交 M3 RELEASE-NOTES |

## 二、汇总进度（M3/M4 勾选）

- [x] M2 范围冻结（2026-10-05：4 spec 全实现；唯一开口 T-06 去向在案=BL-27；分支无分叉（v1.6.0 独有 0）→ 冻结零冲突）
- [x] M3 汇总（2026-10-05：SQL `V1.6.0_01~03`+rollback 配对；**全新库全序重放绿**（临时库断言 三列=3/MCP三表=3/菜单=1/台账12）；**回滚逆序零残留**（3→0/3→0/1→0）；config/changes+RELEASE-NOTES+UPGRADE+checklist 四件套落盘）
- [x] M4 发布（演练+tag）（2026-10-05：verify 13/13 + typecheck 基线（0 新增）+ 线上四项实测；bugs 18 条翻已发布(v1.6.0)；CHANGELOG 落账；**tag v1.6.0**；**打包经用户裁决省略**）
