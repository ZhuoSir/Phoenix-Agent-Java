# 升级件登记: agent-session-files

| 类型 | 内容摘要 | 来源任务 | 草案位置 | 已汇总至里程碑 |
|---|---|---|---|---|
| DDL | 新表 tbl_data_agent_file（id/agent_id/session_id/file_name/rel_path/size/mime/source/backend/store_key/creator/del_flag）+ 两个部分索引 | T-01 | sql/01_agent_file_table.sql（正/反） | -（M3 归 v1.3.0） |
| 配置 | `phoenix.agent.workspace-root`（默认 .agentscope/workspace 不变；交付包=/app/uploads/agent-workspace）、`phoenix.agent.files.root`（默认 ./uploads）、`phoenix.agent.files.max-size-bytes`（50MB） | T-03/T-09 | compose environment | - |
| 代码 | REST /api/agent/files 四端点、WorkspaceArtifactScanner（黑名单/时间窗/去重）、SSE agentFiles 事件、前端面板+物化按钮 | T-04~T-08 | 代码本体 | - |
| 回滚 | R01 仅 drop 表（物理文件不删，首期无 GC）；配置键均有默认值可回退旧镜像 | T-01 | sql/01_..._rollback.sql | - |

## 验证记录（2026-10-01，A 交付栈 v1.3.0-dev 镜像 + v1.2.3-dev 前端）
- AC-01 ✓ compose down/up 后 poem2.txt 列表+下载内容一致；AC-02 ✓ 临时库正/重/回三 0 错、回滚残留=0
- AC-03 ✓ tester2 列表与下载均 42031（测毕已删该账号）；AC-04 ✓ filename*=UTF-8''/MIME/attachment 实测
- AC-05 ✓ evil.sh inline=42034、rep.html inline 带 CSP sandbox；AC-06 ✓ agent 30 write_file→轮末扫描登记→下载"第二首测试诗"
- AC-07 ✓ SSE 帧含 agentFiles(JSON)；AC-08 ◐ nginx 413 拦物化超限，应用闸口代码就位但模型未产出 51MB 实证样本（如实标注）
- AC-09 ✓ evil.sh 删除→列表消失/DB del_flag=1 留档；AC-10 ✓ 对话主链路零回归（REMOTE/LOCAL 均正常应答）
- verify.sh 10/10（新增 [10] files 断言）；vue-tsc 改动文件零错；测试噪音（MEMORY.md/consolidation_state）入黑名单并软删历史行
