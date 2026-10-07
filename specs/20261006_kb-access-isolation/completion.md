> 版本: v0.1.0 | 生成: 2026-10-06 | 挂载: v1.7.0（在途）

# 完成清单：kb-access-isolation（知识库访问面隔离与提示面对齐）

## 一、任务完成情况

| 任务 | 状态 | 证据 / 说明 |
|---|---|---|
| T-01 提示面三档位 spike | ✅ 完成 | 离线实证：默认档 2391 字含 `## Domain Knowledge` + `## Memory Recall/Persistence`（**同源同块**）；`setAdditionalContextFiles` 仅追加；`GUIDANCE_TEMPLATE` 为单一常量 ⇒ 定档"不整体关段、改末尾注入"。证据 `evidence/T-01-T-02_framework-probe.txt` |
| T-02 护栏钩子点 spike | ✅ 完成 | 定档 **MiddlewareBase 拦截**；候选①「官方 deny 规则」经字节码实测**否掉**（`ToolBase.matchRule` 默认仅 `return ruleContent == null`，无工具覆盖 ⇒ 带模式规则永不命中）。同上证据 |
| T-03 归属校验口径取证 | ✅ 完成 | 判定 SQL + allow/deny 双侧对照；一期宽松面（bound-agent-session 为代理判据）指向二期 R-05/BL-31。证据 `evidence/T-03_ownership-verdict.txt` |
| T-04 静态可达面收口 | ✅ 完成并已验证 | 敏感前缀 → 404 且正文无泄漏（修复前 200 + 155,827B 正文）；头像仍 200。证据 `evidence/T-04-T-05_deploy-verify.txt` |
| T-05 受控下载接口 | ✅ 完成并已验证 | `GET /api/agent-knowledge/{id}/raw`：未登录=业务码 401、无事权=403 无正文、属主/管理员=200+正文 |
| T-06 既有用途回归 | ✅ 完成 | 用户实测：知识库页正常 / 文件面板正常 / 头像问题定位为 BUG-110（非本次回归）并修复复验 |
| T-07 路径护栏匹配器 | ✅ 完成 | `KnowledgePathGuard` 纯函数 + **表驱动 16/16**（过程暴露并修掉 5 处误判）。证据 `evidence/T-07_path-guard-table.txt` |
| T-08 护栏接入 + 拒绝审计 | ⏳ 代码完成并部署（**observe 模式**） | `KnowledgePathGuardMiddleware` 已注册并上线，命中记四要素审计；**enforce 未启用**——待 T-09 验证 |
| T-09 绕过面覆盖测试 | ⏳ **待一次真实对话** | 需在会话内逐个尝试 `python3 -c` / base64 / 符号链接 / `find -exec` 等，如实记录"拦住/未拦住"；enforce 亦在此时翻 |
| T-10 提示面定档实施 | ✅ 完成并已部署 | `KnowledgeGuidanceMiddleware` 末尾追加权威指引（幂等；未关框架段）；注入文本离线验证通过。证据 `evidence/T-08-T-10_deploy-verify.txt` |
| T-11 检索失败可见化 | ✅ 代码完成并已部署 | `AbstractHybridRetrievalStrategy` 记 ERROR 全栈 + 抛带 rootCause 异常；`KnowledgeRetrievalTool` 返回「检索失败（注意：这不是「没有相关资料」）…」；**行为验证待一次真实失败** |
| T-12 模型行为回归 | ⏳ **待一次真实对话** | 需从会话上下文核对：是否仍先翻工作区、是否优先 `getRagInfo` |
| T-13 BUG 翻账 + BUG-86 复测 | ⏳ **待一次真实对话** | 需按 BUG-86 原场景（索取未绑定库内容）复测；不可复现才翻「已验证」 |
| T-14 回归门禁与部署三段证明 | ✅ 完成 | `verify.sh` **13 PASS / 0 FAIL**；部署：backend `11:01:02Z` healthy + `/echo/ok=200`；typecheck 212（基线不变） |
| T-15 收口文档 | ✅ 本文件 + `artifacts.md` | — |

## 二、未完成项与去向（如实登记）

| 项 | 原因分类 | 去向 |
|---|---|---|
| T-09 / T-11 / T-12 / T-13 的 runtime 断言 | **需一次真实对话**（agent 行为类验收无法离线证明） | 下轮：用户在会话内执行一段固定脚本（或由我用 API 驱动），逐条取证后翻账 |
| F-08 enforce 模式启用 | 依赖 T-09 的"误伤/绕过"结论 | 同上；未验证前保持 observe（零风险） |
| 存量向量重建（BUG-112） | 用户口令「不要」纳入本 spec | **BL-34** 独立排期 |
| 硬隔离（沙箱/独立执行身份） | 架级改造，用户选方案 A 时明确移出 | **BL-32 / BL-33** |

## 三、部署与影响记录

- 本轮部署（2026-10-06）：`backend started=11:01:02Z`，含 T-08(observe) + T-10 + T-11；**只重启 backend**（nginx 未动）
- 影响：在线会话失效一次（用户已授权）；未改数据库结构（**零 DDL**）、未动向量库与既有条目
- 早前同批（09:33）：T-04 + T-05 + BUG-106 后端加固，均已断言通过

## 四、边界声明（不得含糊）

1. 本 spec 交付的是**软件层收敛**：HTTP 面受控 + 运行时拦截（observe/enforce）+ 提示面对齐。
   因 agent shell 与后端 JVM **同容器同 UID**（BUG-109 实测），**不承诺"权限级不可读"**。
2. `KnowledgePathGuard` 只覆盖经由中间件判定的工具调用；**绕过面以 T-09 实测为准**，未拦住项如实登记并指向 BL-32。
3. 知识库"查不到"的另一独立成因（存量向量与当前 embedding 模型不一致，BUG-112）**不在本 spec**。
