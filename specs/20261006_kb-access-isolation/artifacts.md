> 版本: v0.1.0 | 生成: 2026-10-06 | 挂载: v1.7.0（在途）

# 产物清单：kb-access-isolation（知识库访问面隔离与提示面对齐）

## 一、数据库升级件

**无（零 DDL / 零 DML）**。本 spec 不新增表、不改表结构、不迁移数据；
`tbl_data_agent_knowledge.file_path` 等一律未动（决策 2 取 **2-a 不迁移**，用户 2026-10-06 确认）。

## 二、代码产物

| 模块 | 文件 | 作用 |
|---|---|---|
| phoenix-data-core | `config/WebConfig.java` | T-04：静态面敏感前缀拒绝器（`data-agent/agent-knowledge`、`agent-workspace` → 404 无正文 + WARN 审计） |
| phoenix-data-api/core/rest | `AgentKnowledgeMapper.canReadKnowledgeSource` / `AgentKnowledgeService(+Impl).getRawFileIfPermitted` / `AgentKnowledgeController.raw` | T-05：受鉴权原件下载（登录 + 归属/绑定 + 根内防逃逸 + 审计） |
| phoenix-agent-core | `harness/security/KnowledgePathGuard.java` | T-07：路径护栏纯函数（归一化 + 黑名单前置 + 工作区白名单 + 上传根收敛） |
| phoenix-agent-core | `harness/middleware/KnowledgePathGuardMiddleware.java` | T-08：拦截工具调用，命中记四要素审计；默认 observe，`PHOENIX_KB_PATH_GUARD=enforce` 才拒绝 |
| phoenix-agent-core | `harness/middleware/KnowledgeGuidanceMiddleware.java` | T-10：系统提示词末尾追加权威知识库指引（幂等） |
| phoenix-agent-core | `harness/factory/HarnessAgentFactory.java` | T-08/T-10：中间件注册 |
| phoenix-data-core | `service/hybrid/retrieval/AbstractHybridRetrievalStrategy.java` | T-11：包装异常记 ERROR 全栈 + 带 rootCause 摘要抛出 |
| phoenix-agent-core | `harness/tool/KnowledgeRetrievalTool.java` | T-11：检索失败返回可诊断文本并明确"这不是没有资料" |

## 三、配置与环境变量

| 项 | 默认 | 说明 |
|---|---|---|
| `PHOENIX_KB_PATH_GUARD` | `observe` | 护栏模式；`enforce` 才摘除被拒工具调用（翻转前须完成 T-09） |

## 四、证据文件（`evidence/`）

| 文件 | 内容 |
|---|---|
| `T-01-T-02_framework-probe.txt` | 五轮框架取证：提示面矛盾段定位与三档实测、权限引擎与 `matchRule` 语义（否掉候选①）、`MiddlewareBase`/`ActingInput`/`ToolUseBlock` 结构 |
| `T-03_ownership-verdict.txt` | 归属判定 SQL + allow/deny 双侧对照 + 一期宽松面 |
| `T-04-T-05_deploy-verify.txt` | 静态面与受控接口的部署断言（含"修复前 200+155,827B 正文"对照） |
| `T-07_path-guard-table.txt` | 路径护栏表驱动 16 条用例 + 两轮共 5 处误判的发现与修复 |
| `T-08-T-10_deploy-verify.txt` | 本轮部署三段证明 + 注入文本离线验证（含自伤留痕的探针措辞错误） |

## 五、升级/回滚说明

- 升级：按常规后端部署（重建 backend 镜像 + `up -d backend`）；**无数据库步骤**；
- 回滚：回退镜像到上一版本即可（护栏 observe 模式本身不改变行为；静态面与受控接口的回滚会恢复 BUG-108 的暴露面，故回滚需知悉该风险）；
- 风险提示：`enforce` 模式一旦启用并需回滚，应先把 `PHOENIX_KB_PATH_GUARD` 改回 `observe`（行为即刻恢复，无需回退代码）。
