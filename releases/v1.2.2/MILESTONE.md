# 里程碑 v1.2.2
> 状态: 进行中 | 目标日期: 尽快 | 负责人: 陈卓 | 立项: 2026-10-01

**版本语义**：PATCH——BUG-33 根治（BL-18 前端 API 前缀治理），附带三个单前缀 fetch 历史坏点痊愈。无 SQL、无配置变更。

## 纳入需求
| spec | spec版本 | 状态 | SQL件 | 配置项 | 备注 |
|---|---|---|---|---|---|
| （无新 spec，BL-18 治理批） | - | - | 0 | 0 | Bug 旁路：BUG-33 根治 |

## 纳入缺陷（M1 挂接）
| 编号 | 标题 | 严重度 | 修复落点 | 状态 |
|---|---|---|---|---|
| BUG-33 | 前端 API 双重 /api 前缀（生产从未成功部署的结构性缺陷） | P1 | baseURL 置空 + 路径归一 + 代理透传（vite/nginx 同步） | 已修复(v1.2.2) |

## 汇总进度
- [x] 修复完成（分支 fix/v1.2.2-bl18-baseurl）
- [ ] 合并 main → 冻结 → M3/M4（checklist → tag v1.2.2 → CHANGELOG → 转已发布）

## 验证记录（2026-10-01，A 栈前端 v1.2.2-dev）
- verify 9/9（含负断言：双前缀 /api/api/* 必须失败——折叠规则已删）
- 真实路径回归：/api/prompt-config/list、/api/semantic-model/template/download、/api/agent/25/runtime-config、/api/datasource 均 200；SSE /api/admin/harness/chat 正常出流；/platform/group-info/page 裸域直连 200；apiKey 脱敏复核 sk-**** 形态恢复
- 事故复盘入档：.env IMAGE_TAG 与运行镜像不一致时 compose 静默把 backend 打回旧镜像（BUG-34/37 修复一度"消失"），verify 当场抓获；README 已立 IMAGE_TAG 纪律
