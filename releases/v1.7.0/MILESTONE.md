# v1.7.0 里程碑（M1）

> 创建: 2026-10-05 | 状态: 立项（需求① 已确认，plan 待确认②） | 类型: MINOR | 验证日期: -
> **版本分支**: `v1.7.0`（2026-10-05 建，基点 = main tip `456f8ab`；v1.6.0 已发布并 `--no-ff` 合 main，**无悬空**）

**版本语义**：MINOR——长轮可观测性增强（静默心跳 + 模型调用超时）＋ 会话文件面板改文件夹树 ＋ 工程清账；**零 DDL、零工作区布局改动**，向下兼容。

## 一、需求挂接表（M1）

| spec | 版本 | 状态 | 新增 | 变更 | 摘要 |
|---|---|---|---|---|---|
| specs/20261005_visibility-filetree-hygiene | requirements v1.0.0 已确认 | Phase 2（plan v0.1.0 待确认②） | 后端：轮次阶段与心跳、文件树接口；前端：面板树 UI、静默指示 | 无 DDL；清账 6 项 | BUG-85 长轮静默可见性+模型调用超时；BL-28 会话文件文件夹树；BL-11/12/15 + §四 + agent-config 清账销账 |

## 二、汇总进度（M3/M4 勾选）
- [ ] M2 范围冻结
- [ ] M3 汇总（SQL/config/RELEASE-NOTES/UPGRADE/checklist）
- [ ] M4 发布（演练+tag）

## 三、审计记录
- **2026-10-05 立项**：requirements v0.1.0 → **v1.0.0 已确认（陈卓）**；Q1~Q6 按 agent 建议暂定并记于 requirements §七；双建完成（version.md 在途行 + 本目录 + `v1.7.0` 分支 + `feature/visibility-filetree-hygiene`）；待办挂载 BL-11/12/15/28→已立项(v1.7.0)、BUG-85→已规划(v1.7.0)
