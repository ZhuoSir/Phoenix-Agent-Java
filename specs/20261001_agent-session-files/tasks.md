> 版本: v1.0.0 | 状态: 已确认 | 更新: 2026-10-01 | 确认人: 陈卓 | 确认日期: 2026-10-01（沿用用户既定授权：tasks 生成即确认、直接执行；分支 feature/agent-session-files）

# 任务清单：会话文件面板

> 验证口径：无测试基建 → A 交付栈真机 + curl/psql/SSE 留证；每条 AC 对号入座。

## 1. 数据与门面

- [ ] T-01 `V1.3.0_01__agent_file_table.sql` + `R1.3.0_01`（tbl_data_agent_file 全列+索引；可重入；回滚无损）
  关联: R-04 | 依赖: 无
  验证方式: 临时库正/反/重跑三断言 errors=0
  验收标准: Flyway 风格文件头注释块齐全（版本/序号/来源/前置/可重入/预估/回滚）

- [ ] T-02 `AgentFileService`（register/list/logicalDelete/load + 50MB 闸 + 路径清洗 + mime 探测）
  关联: R-03, R-05, R-06, R-10, R-18 | 依赖: T-01
  验证方式: 起服务后单元级 curl 触发（经 T-04 接口反打）+ psql 断言行数/字段；路径清洗用例：`../../etc/passwd`、换行符文件名 → 安全名落盘
  验收标准: canonical 前缀校验生效；超限拒绝；REMOTE 缺失 tee 时按 store_key 回源可下载

## 2. 捕获通路

- [ ] T-03 `FilesystemTeeDecorator` 包住 AbstractFilesystem（write/edit/uploadFiles 钩子）+ `phoenix.agent.workspace-root` 配置化（默认不变，交付包 env 指 uploads 卷）
  关联: R-01, R-02 | 依赖: T-02
  验证方式: 双策略实测各一轮对话让模型写文件：LOCAL 走 write_file、REMOTE 走 write_file（shell 已禁）→ 面板表出现登记行、uploads 落盘、workspace-root 变更日志可证
  验收标准: tee 异步不阻断会话（失败仅 WARN）；`.index/`、marker 类黑名单不登记

- [ ] T-05 shell 直写兜底：会话轮末扫 workspace 增量（过滤黑名单，source=scan）
  关联: R-12 风险② | 依赖: T-03
  验证方式: 实测 LOCAL 策略用 shell `echo x > out.txt` → 面板可见（scan 捕获）；对照 write_file 不重复登记（uuid/路径去重）
  验收标准: 若实测 shell 直写为 AgentScope 沙箱隔离不可见路径，如实回写 plan 风险②结论（不硬造捕获）

- [ ] T-06 SSE `file_created` 事件（Spring 事件→HarnessChatService merge→HarnessEventMapper）
  关联: R-14 | 依赖: T-03
  验证方式: curl SSE 对话触发 write_file，抓 data 帧含 file_created{id,name,size,mime}；断流场景改拉列表仍可见
  验收标准: 同 id 幂等去重；未知 type 旧客户端不报错

## 3. 接口与前端

- [ ] T-04 REST `/api/agent/files`（page/download/inline/delete）+ 错误码 AGENT_FILE_* + 属主校验
  关联: R-07~R-11 | 依赖: T-02
  验证方式: curl 三态（A属主 200/B 属主 403/无 token 401）；中文文件名下载 `filename*=UTF-8''`；HTML inline 带 CSP sandbox；.sh inline 拒绝
  验收标准: ReturnVo 信封统一；列表不回吐 rel_path

- [ ] T-08 「另存为文件」materialize 通道（报告 HTML 等；含 50MB 闸）
  关联: R-12 | 依赖: T-04
  验证方式: 对既有 report 消息调 materialize → 面板可见可下载；越权属主 403
  验收标准: 手动物化（P4），不自动落盘

- [ ] T-07 前端：`agentFiles.ts` + `ChatFilesPanel.vue`（右侧抽屉/列表/下载 blob/预览/删除/空态/实时插入）
  关联: R-15~R-17 | 依赖: T-04, T-06
  验证方式: 浏览器实操（用户验收）；vue-tsc 改动文件 0 新错；零新增依赖核验（lockfile diff）
  验收标准: 切会话刷新列表、file_created 实时插行、超限提示可读

## 4. 交付与收口

- [ ] T-09 docker 联动：compose env（workspace-root）、README 备份口径、verify.sh 增 files 断言
  关联: R-20 | 依赖: T-04
  验证方式: 重建前端/后端镜像滚动，9080 全链路；`compose down`+`up` 后文件仍在（AC-01）
  验收标准: 无新对外端口；升级=跑 V1.3.0_01

- [ ] T-10 端到端 AC-01~AC-11 留证 + 双策略回归（老会话不受影响）
  关联: 全部 | 依赖: T-01~T-09
  验证方式: 逐 AC 命令输出/日志/psql 留档 artifacts.md
  验收标准: 十项全绿方可勾；红项修复复跑后注明

- [ ] T-11 收口：artifacts.md 登记（DDL/配置键/env）、changelog、backlog BL-19 销账、MILESTONE v1.3.0 状态推进
  关联: R-20~R-22 | 依赖: T-10
  验收标准: `git diff main --stat` 无 docker/specs 之外的越界改动（装饰器/服务/前端为本 spec 正常增量）
