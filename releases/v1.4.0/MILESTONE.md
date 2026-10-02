# MILESTONE: v1.4.0

> 创建: 2026-10-02 | 状态: 进行中（M1 已挂 1 需求） | 类型: MINOR | 验证日期: -
> **版本分支**: `v1.4.0`（2026-10-02 对账补建，基点 main tip；此后 feature 分支从它切、合回它——见 specs/_project/version.md）

**版本语义**：MINOR——向下兼容的新功能。首批纳入 BL-22 断线续传（流恢复）。

## 一、需求挂接表（M1）
| spec | 版本 | 状态 | 新增 | 变更 | 摘要 |
|---|---|---|---|---|---|
| specs/20261002_detached-stream | v1.0.0 | **已合并 main（2026-10-02）** | 3 | 0 | 断线续传：TurnManager+join/cancel+落库移交+HITL 并轮；零 DDL，配置三键 | | 待定 | 待定 | 断线续传：服务端脱离式执行+重进会话追流（BL-22） |

## 二、汇总进度（M3/M4 勾选）
| specs/20261002_fix-bug-batch | v0.3.0 | Specify中（四裁决齐，待第①关） | 待定 | 待定 | 开放缺陷清仓 11 单（含 BUG-18 问答联合向量化） |
| （缺陷）nginx 登录页路由豁免（BUG-56） | - | 已修复(v1.4.0) 待发 | - | - | 登出后 500 真身：代理 regex 吞 SPA 路由 |

- [ ] M2 范围冻结
- [ ] M3 汇总（SQL/config/RELEASE-NOTES/UPGRADE/checklist）
- [ ] M4 发布（演练+tag）
